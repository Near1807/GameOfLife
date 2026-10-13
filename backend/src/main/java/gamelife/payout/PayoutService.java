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
import gamelife.gear.GearService;
import gamelife.game.GameService;
import gamelife.time.GameTime;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Turns an explicit act into a ledger payment (brief 6):
 *   final XP = base x streak x comeback
 *   currency = final XP x income   (v4: income = Markets and Social, see GameMath.incomeMultiplier)
 * Stat XP = base XP split equally between the tags (brief 8: gear cannot inflate stats).
 *
 * This is the ONLY place that pays XP. There is no "add points" method anywhere (brief 3.2).
 */
@Service
public class PayoutService {
    private final LedgerService ledger;
    private final GameConfig config;
    private final GameTime time;
    private final GearService gear;
    private final GameService game;

    public PayoutService(LedgerService ledger, GameConfig config, GameTime time, GearService gear,
                         GameService game) {
        this.game = game;
        this.gear = gear;
        this.ledger = ledger;
        this.config = config;
        this.time = time;
    }

    /** All multipliers that apply to a payment made right now. */
    public record Multipliers(double streak, double comeback) {
        double product() {
            return streak * comeback;
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
        // v4: Markets and Social multiply only the currency part, never the XP or the stat XP
        double income = game.incomeMultiplier(ledger.all());
        BigDecimal currency = Money.of(finalXp.multiply(BigDecimal.valueOf(income)));

        Map<String, Object> meta = new LinkedHashMap<>();
        if (req.meta() != null) {
            meta.putAll(req.meta());
        }
        meta.put("baseXp", base.toPlainString());
        meta.put("tags", req.tags());
        meta.put("mult", Map.of(
                "streak", round4(m.streak()),
                "comeback", round4(m.comeback()),
                "income", round4(income)));

        NewEvent event = new NewEvent(req.key(), req.type(), req.module(), req.label(),
                finalXp, currency, StatSplitter.split(base, req.tags()), meta);
        LedgerService.AppendResult result = ledger.append(event);
        if (result.written() && finalXp.signum() > 0) {
            gear.maybeDropLoot(req.key());      // rare cosmetic drop (brief 10)
        }
        return new ActionResult(result.event(), result.written(), result.written() ? "Paid" : "Already counted");
    }

    /** Multipliers for a payment made now, computed from the ledger. */
    public Multipliers multipliersNow(List<LedgerEvent> events) {
        StreakState streak = streakNow(events);
        double streakMult = StreakCalculator.multiplier(streak.streakForPayment(), config.streak());
        double comebackMult = streak.comebackPending() ? config.streak().comebackMultiplier() : 1.0;
        return new Multipliers(streakMult, comebackMult);
    }

    public StreakState streakNow(List<LedgerEvent> events) {
        return StreakCalculator.compute(LedgerFacts.countedDays(events), LedgerFacts.restOrPausedDays(events),
                time.today(), config.streak());
    }

    private static double round4(double v) {
        return Math.round(v * 10000.0) / 10000.0;
    }
}
