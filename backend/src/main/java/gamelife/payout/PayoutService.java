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

    public PayoutService(LedgerService ledger, GameConfig config, GameTime time) {
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
        return new ActionResult(result.event(), result.written(), result.written() ? "Paid" : "Already counted");
    }

    /** Multipliers for a payment made now, computed from the ledger. */
    public Multipliers multipliersNow(List<LedgerEvent> events) {
        StreakState streak = streakNow(events);
        double streakMult = StreakCalculator.multiplier(streak.streakForPayment(), config.streak());
        double weaponMult = Math.pow(config.weapon().bonusPerLevel(), LedgerFacts.weaponLevel(events));
        double ascensionMult = Math.pow(config.ascension().perAscension(), LedgerFacts.ascensions(events));
        double comebackMult = streak.comebackPending() ? config.streak().comebackMultiplier() : 1.0;
        return new Multipliers(streakMult, weaponMult, ascensionMult, comebackMult);
    }

    public StreakState streakNow(List<LedgerEvent> events) {
        return StreakCalculator.compute(LedgerFacts.countedDays(events), LedgerFacts.restDays(events),
                time.today(), config.streak());
    }

    private static double round4(double v) {
        return Math.round(v * 10000.0) / 10000.0;
    }
}
