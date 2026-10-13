package gamelife.state;

import gamelife.checkin.CheckinService;
import gamelife.config.GameConfig;
import gamelife.content.ContentService;
import gamelife.fight.FightService;
import gamelife.gear.GearService;
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
    private final CheckinService checkin;
    private final FightService fight;
    private final GearService gear;
    private final ContentService content;
    private final GameConfig config;
    private final GameTime time;

    public StateController(LedgerService ledger, PayoutService payout, TaskService tasks, CheckinService checkin,
                           FightService fight, GearService gear, ContentService content, GameConfig config,
                           GameTime time) {
        this.gear = gear;
        this.content = content;
        this.checkin = checkin;
        this.fight = fight;
        this.ledger = ledger;
        this.payout = payout;
        this.tasks = tasks;
        this.config = config;
        this.time = time;
    }

    public record StreakInfo(int days, int freezes, boolean todayCounted, boolean comebackPending,
                             double multiplierToday, int nextMilestone) {}

    /**
     * How the app and the arena look right now: the equipped theme's design tokens, the hero
     * picture (an outfit overrides the level look), the arena skin filter, and the item pictures
     * the hero wears.
     */
    public record Look(String themeId, Map<String, String> themeTokens, String heroSprite, int heroFrames,
                       String arenaFilter, List<ContentService.Item> worn) {}

    private Look look(GearService.Loadout l, FightService.FightView f) {
        ContentService.Item theme = content.item(l.equipped().getOrDefault("theme", ""));
        ContentService.Item outfit = content.item(l.equipped().getOrDefault("outfit", ""));
        ContentService.Item skin = content.item(l.equipped().getOrDefault("skin", ""));
        List<ContentService.Item> worn = new java.util.ArrayList<>();
        for (String slot : List.of("weapon", "armor")) {
            ContentService.Item i = content.item(l.equipped().getOrDefault(slot, ""));
            if (i != null) worn.add(i);
        }
        l.charms().forEach(id -> worn.add(content.item(id)));
        return new Look(theme == null ? null : theme.id(), theme == null ? Map.of() : theme.tokens(),
                outfit != null ? outfit.sprite() : f.heroSprite(), outfit != null ? outfit.frames() : f.heroFrames(),
                skin == null ? null : skin.tokens().get("filter"), worn);
    }

    public record GameState(LocalDate today, Instant serverNow, BigDecimal balance, BigDecimal xpToday,
                            StreakInfo streak, PayoutService.Multipliers multipliers,
                            Map<String, BigDecimal> statXp, int weaponLevel, double nextUpgradeCost,
                            FightService.FightView fight, GearService.Loadout loadout, Look look,
                            HashChain.Check chain, List<LedgerEvent> log) {}

    @GetMapping("/state")
    public GameState state() {
        FightService.FightView fightView = fight.view(); // first: brings the boss attacks up to date
        List<LedgerEvent> events = ledger.all();
        GearService.Loadout loadout = gear.loadout(events);
        LocalDate today = time.today();
        StreakState s = payout.streakNow(events);
        double multToday = StreakCalculator.multiplier(s.streakForPayment(), config.streak());
        int next = List.of(7, 30, 100, 365).stream().filter(m -> m > s.streakDays()).findFirst().orElse(0);
        // Weapon upgrade cost n -> n+1 = baseCost x costGrowth ^ n (brief 9.2). Shown as the next goal.
        int weaponLevel = LedgerFacts.weaponLevel(events);
        double nextCost = config.weapon().baseCost() * Math.pow(config.weapon().costGrowth(), weaponLevel);
        StreakInfo streak = new StreakInfo(s.streakDays(), s.freezes(), s.todayCounted(), s.comebackPending(),
                multToday, next);
        return new GameState(today, time.now(), LedgerFacts.balance(events), LedgerFacts.xpOnDay(events, today),
                streak, payout.multipliersNow(events), LedgerFacts.statTotals(events), weaponLevel,
                Math.round(nextCost * 100.0) / 100.0, fightView, loadout, look(loadout, fightView), HashChain.verify(events),
                ledger.latest(20));
    }

    /** Static data for the UI: chips, stats, defaults. */
    public record Meta(List<GameConfig.Stat> stats, GameConfig.Todo todo, GameConfig.Timer timer,
                       GameConfig.Checkin checkin, double habitXp, int lastTaskMinutes, int maxRestDaysPerWeek,
                       double dailyXpGoal, List<String> lastCheckinTags) {}

    @GetMapping("/meta")
    public Meta meta() {
        return new Meta(config.stats(), config.todo(), config.timer(), config.checkin(), config.habit().xp(),
                tasks.lastMinutesUsed(), config.streak().maxRestDaysPerWeek(), config.home().dailyXpGoal(),
                checkin.lastTags());
    }
}
