package gamelife.payout;

import gamelife.common.Money;
import gamelife.config.GameConfig;
import gamelife.ledger.LedgerEvent;
import gamelife.ledger.LedgerService;
import gamelife.ledger.NewEvent;
import gamelife.rules.LedgerFacts;
import gamelife.rules.StatSplitter;
import gamelife.rules.StreakCalculator;
import gamelife.rules.StreakState;
import gamelife.fight.FightRules;
import gamelife.fight.FightService;
import gamelife.gear.GearService;
import gamelife.time.GameTime;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Turns an explicit act into a ledger payment (brief 6):
 *   final XP = base x streak x weapon x ascension x comeback
 * Final XP is both the currency earned and the XP against the weekly boss.
 * Stat XP = base XP split equally between the tags (brief 8: gear cannot inflate stats).
 *
 * This is the ONLY place that pays XP. There is no "add points" method anywhere (brief 3.2).
 */
@Service
public class PayoutService {
    private final LedgerService ledger;
    private final GameConfig config;
    private final GameTime time;
    private final FightService fight;
    private final GearService gear;

    public PayoutService(LedgerService ledger, GameConfig config, GameTime time, FightService fight, GearService gear) {
        this.fight = fight;
        this.gear = gear;
        this.ledger = ledger;
        this.config = config;
        this.time = time;
    }

    /** All multipliers that apply to a payment made right now. */
    public record Multipliers(double streak, double weapon, double ascension, double comeback) {
        double product() {
            return streak * weapon * ascension * comeback;
        }
    }

    /**
     * Pays once per key. synchronized so the streak and comeback state cannot change between
     * computing the multipliers and writing the event.
     */
    public synchronized ActionResult pay(PayRequest req) {
        var existing = ledger.find(req.key());
        if (existing.isPresent()) {
            return new ActionResult(existing.get(), false, "Already counted");
        }
        fight.sync(); // the boss's attacks of past days come first (brief 9.1)

        Multipliers m = multipliersNow(ledger.all());
        BigDecimal base = Money.of(req.baseXp());
        BigDecimal finalXp = Money.of(base.multiply(BigDecimal.valueOf(m.product())));

        Map<String, Object> meta = new LinkedHashMap<>();
        if (req.meta() != null) {
            meta.putAll(req.meta());
        }
        meta.put("baseXp", base.toPlainString());
        meta.put("tags", req.tags());
        meta.put("mult", Map.of(
                "streak", round4(m.streak()),
                "weapon", round4(m.weapon()),
                "ascension", round4(m.ascension()),
                "comeback", round4(m.comeback())));

        NewEvent event = new NewEvent(req.key(), req.type(), req.module(), req.label(),
                finalXp, finalXp, StatSplitter.split(base, req.tags()), meta);
        LedgerService.AppendResult result = ledger.append(event);
        if (result.written() && finalXp.signum() > 0) {
            fight.sync();                       // the boss may have just died: the kill is written now
            gear.maybeDropLoot(req.key());      // rare cosmetic drop (brief 10)
        }
        return new ActionResult(result.event(), result.written(), result.written() ? "Paid" : "Already counted");
    }

    /** Multipliers for a payment made now, computed from the ledger. */
    public Multipliers multipliersNow(List<LedgerEvent> events) {
        StreakState streak = streakNow(events);
        GearService.Loadout l = gear.loadout(events);
        double streakMult = StreakCalculator.multiplier(streak.streakForPayment(), config.streak());
        // weapon level (+10% each, compounding) x the damage bonus of an equipped weapon item
        double weaponMult = FightRules.weaponMultiplier(l.weaponLevel(), config.weapon()) * (1 + l.bonus().damagePct());
        double ascensionMult = Math.pow(config.ascension().perAscension(), LedgerFacts.ascensions(events));
        double comebackMult = streak.comebackPending()
                ? config.streak().comebackMultiplier() + l.effect(GearService.COMEBACK_BONUS) : 1.0;
        return new Multipliers(streakMult, weaponMult, ascensionMult, comebackMult);
    }

    public StreakState streakNow(List<LedgerEvent> events) {
        return StreakCalculator.compute(LedgerFacts.countedDays(events), LedgerFacts.restOrPausedDays(events),
                time.today(), config.streak(), (int) gear.loadout(events).effect(GearService.FREEZE_CAPACITY));
    }

    private static double round4(double v) {
        return Math.round(v * 10000.0) / 10000.0;
    }
}
