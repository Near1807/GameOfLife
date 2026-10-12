package gamelife.state;

import gamelife.config.GameConfig;
import gamelife.ledger.HashChain;
import gamelife.ledger.LedgerEvent;
import gamelife.ledger.LedgerService;
import gamelife.payout.PayoutService;
import gamelife.rules.LedgerFacts;
import gamelife.rules.StreakCalculator;
import gamelife.rules.StreakState;
import gamelife.tasks.TaskService;
import gamelife.time.GameTime;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * Read-only summaries for the home screen. Every number here is computed from the ledger
 * on each request (brief 3.2: no stored totals).
 */
@RestController
@RequestMapping("/api")
public class StateController {
    private final LedgerService ledger;
    private final PayoutService payout;
    private final TaskService tasks;
    private final GameConfig config;
    private final GameTime time;

    public StateController(LedgerService ledger, PayoutService payout, TaskService tasks, GameConfig config,
                           GameTime time) {
        this.ledger = ledger;
        this.payout = payout;
        this.tasks = tasks;
        this.config = config;
        this.time = time;
    }

    public record StreakInfo(int days, int freezes, boolean todayCounted, boolean comebackPending,
                             double multiplierToday, int nextMilestone) {}

    public record GameState(LocalDate today, Instant serverNow, BigDecimal balance, BigDecimal xpToday,
                            StreakInfo streak, PayoutService.Multipliers multipliers,
                            Map<String, BigDecimal> statXp, HashChain.Check chain, List<LedgerEvent> log) {}

    @GetMapping("/state")
    public GameState state() {
        List<LedgerEvent> events = ledger.all();
        LocalDate today = time.today();
        StreakState s = payout.streakNow(events);
        double multToday = StreakCalculator.multiplier(s.streakForPayment(), config.streak());
        int next = List.of(7, 30, 100, 365).stream().filter(m -> m > s.streakDays()).findFirst().orElse(0);
        StreakInfo streak = new StreakInfo(s.streakDays(), s.freezes(), s.todayCounted(), s.comebackPending(),
                multToday, next);
        return new GameState(today, time.now(), LedgerFacts.balance(events), LedgerFacts.xpOnDay(events, today),
                streak, payout.multipliersNow(events), LedgerFacts.statTotals(events), HashChain.verify(events),
                ledger.latest(20));
    }

    /** Static data for the UI: chips, stats, defaults. */
    public record Meta(List<GameConfig.Stat> stats, GameConfig.Todo todo, GameConfig.Timer timer,
                       GameConfig.Checkin checkin, double habitXp, int lastTaskMinutes, int maxRestDaysPerWeek) {}

    @GetMapping("/meta")
    public Meta meta() {
        return new Meta(config.stats(), config.todo(), config.timer(), config.checkin(), config.habit().xp(),
                tasks.lastMinutesUsed(), config.streak().maxRestDaysPerWeek());
    }
}
