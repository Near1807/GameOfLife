package gamelife.review;

import gamelife.common.Json;
import gamelife.common.Money;
import gamelife.config.GameConfig;
import gamelife.game.GameLedger;
import gamelife.game.WaveService;
import gamelife.habits.Habit;
import gamelife.habits.HabitRepository;
import gamelife.ledger.EventType;
import gamelife.ledger.LedgerEvent;
import gamelife.ledger.LedgerService;
import gamelife.rules.LedgerFacts;
import gamelife.rules.StreakCalculator;
import gamelife.time.GameTime;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/**
 * Weekly review (v4 brief 4) and stats dashboard (v2 4.5). Read-only, computed from the ledger.
 */
@Service
public class ReviewService {
    private final LedgerService ledger;
    private final HabitRepository habits;
    private final GameConfig config;
    private final GameTime time;
    private final WaveService waves;

    public ReviewService(LedgerService ledger, HabitRepository habits, GameConfig config, GameTime time,
                         WaveService waves) {
        this.waves = waves;
        this.ledger = ledger;
        this.habits = habits;
        this.config = config;
        this.time = time;
    }

    public record DayXp(LocalDate date, double xp, boolean counted, boolean rest, boolean future) {}

    public record SkippedHabit(String name, int missed, int scheduled) {}

    public record Correction(LocalDate day, String label, double xp, String reason) {}

    /**
     * A calendar-week report (v4 brief 4). xp = XP validated that week. Waves: attempts whose slot
     * falls in the week (an automatic attempt counts for its slot, even if written later).
     * furthestWave = the highest wave cleared by the end of the week. coinsEarned = coins paid by real
     * actions; coinsSpent = towers, spots and shop, minus sale refunds.
     */
    public record WeekReview(LocalDate weekStart, LocalDate previousWeek, LocalDate nextWeek, boolean currentWeek,
                             double xp, int wavesCleared, int wavesFailed, int furthestWave, double coinsEarned,
                             double coinsSpent, int towersBuilt, List<DayXp> days, Map<String, Double> statXp, int tasksDone,
                             int focusMinutes, int habitsDone, Double averageMood, int streakAtEnd,
                             List<SkippedHabit> skippedHabits, List<Correction> corrections) {}

    /** Review of the game week containing `day` (default: this week). */
    public WeekReview week(LocalDate day) {
        waves.sync(); // the wave attempts missed while the app was closed come first
        LocalDate today = time.today();
        LocalDate start = GameTime.weekStart(day == null ? today : day);
        LocalDate end = start.plusDays(7); // exclusive
        List<LedgerEvent> all = ledger.all();
        List<LedgerEvent> inWeek = all.stream()
                .filter(e -> !e.gameDay().isBefore(start) && e.gameDay().isBefore(end)).toList();

        Set<LocalDate> counted = LedgerFacts.countedDays(all);
        Set<LocalDate> rest = LedgerFacts.restOrPausedDays(all);

        List<DayXp> days = new ArrayList<>();
        for (LocalDate d = start; d.isBefore(end); d = d.plusDays(1)) {
            days.add(new DayXp(d, LedgerFacts.xpOnDay(inWeek, d).doubleValue(), counted.contains(d), rest.contains(d),
                    d.isAfter(today)));
        }

        Map<String, Double> statXp = new LinkedHashMap<>();
        Map<String, BigDecimal> totals = LedgerFacts.statTotals(inWeek);
        config.stats().forEach(s -> statXp.put(s.id(), totals.getOrDefault(s.id(), Money.ZERO).doubleValue()));

        int tasksDone = 0;
        int focus = 0;
        int habitsDone = 0;
        List<Integer> moods = new ArrayList<>();
        List<Correction> corrections = new ArrayList<>();
        double xp = 0, earned = 0, spent = 0;
        int cleared = 0, failed = 0, furthest = 0, built = 0;
        for (LedgerEvent e : all) { // an attempt belongs to the day of its slot, maybe written later
            if (e.type() == EventType.WAVE_CLEARED || e.type() == EventType.WAVE_FAILED) {
                Map<String, Object> m = Json.toMap(e.meta());
                LocalDate d = time.gameDay(java.time.Instant.parse(String.valueOf(m.get("at"))));
                boolean won = e.type() == EventType.WAVE_CLEARED;
                if (won && d.isBefore(end)) {
                    furthest = Math.max(furthest, ((Number) m.get("wave")).intValue());
                }
                if (!d.isBefore(start) && d.isBefore(end)) {
                    if (won) cleared++;
                    else failed++;
                }
            }
        }
        for (LedgerEvent e : inWeek) {
            if (e.xp().signum() > 0) xp += e.xp().doubleValue();
            if (EventType.VALIDATIONS.contains(e.type()) || e.type() == EventType.TASK_UNDONE) {
                earned += e.currencyDelta().doubleValue();
            } else if (e.type() == EventType.PURCHASE || GameLedger.BUILD_TYPES.contains(e.type())) {
                spent -= e.currencyDelta().doubleValue();
            }
            if (e.type() == EventType.TOWER_BUILT) built++;
            Map<String, Object> meta = Json.toMap(e.meta());
            switch (e.type()) {
                case TASK_DONE -> tasksDone++;
                case TASK_UNDONE -> tasksDone--;
                case TIMER_VALIDATED -> focus += ((Number) meta.getOrDefault("countedMinutes", 0)).intValue();
                case HABIT_DONE -> habitsDone++;
                case CHECKIN -> moods.add(((Number) meta.get("mood")).intValue());
                case CORRECTION -> corrections.add(new Correction(e.gameDay(), e.label(), e.xp().doubleValue(),
                        String.valueOf(meta.getOrDefault("reason", ""))));
                default -> { }
            }
        }
        Double mood = moods.isEmpty() ? null
                : Math.round(moods.stream().mapToInt(Integer::intValue).average().orElse(0) * 10) / 10.0;

        LocalDate lastDay = end.minusDays(1).isAfter(today) ? today : end.minusDays(1);
        int streakAtEnd = StreakCalculator.compute(counted, rest, lastDay, config.streak()).streakDays();

        LocalDate first = all.isEmpty() ? today : all.get(0).gameDay();
        return new WeekReview(start, start.isAfter(GameTime.weekStart(first)) ? start.minusDays(7) : null,
                end.isAfter(today) ? null : end, start.equals(GameTime.weekStart(today)),
                Math.round(xp * 100) / 100.0, cleared, failed, furthest, Math.round(earned * 100) / 100.0,
                Math.round(spent * 100) / 100.0, built, days, statXp, tasksDone,
                focus, habitsDone, mood, streakAtEnd, skippedHabits(start, end, today, rest), corrections);
    }

    /** Scheduled habit days of the week (up to yesterday, after creation, not rest days) not ticked. */
    private List<SkippedHabit> skippedHabits(LocalDate start, LocalDate end, LocalDate today, Set<LocalDate> rest) {
        List<SkippedHabit> result = new ArrayList<>();
        for (Habit h : habits.findAll()) {
            Set<LocalDate> done = new HashSet<>();
            String prefix = "habit:" + h.id() + ":";
            ledger.byKeyPrefix(prefix).forEach(e -> done.add(LocalDate.parse(e.key().substring(prefix.length()))));
            LocalDate created = time.gameDay(h.createdAt());
            int scheduled = 0;
            int missed = 0;
            for (LocalDate d = start; d.isBefore(end) && d.isBefore(today); d = d.plusDays(1)) {
                if (d.isBefore(created) || !h.days().contains(d.getDayOfWeek()) || rest.contains(d)) continue;
                if (h.archived() && !done.contains(d)) continue;
                scheduled++;
                if (!done.contains(d)) missed++;
            }
            if (missed > 0) {
                result.add(new SkippedHabit(h.name(), missed, scheduled));
            }
        }
        return result;
    }

    // ---------------------------------------------------------------- dashboard

    public record DayStats(LocalDate date, double xp, Map<String, Double> statXp, int streak, boolean counted) {}

    public record Dashboard(int days, List<DayStats> history, Map<String, Double> statXpPeriod, double xpPeriod,
                            double xpAllTime, double focusHoursAllTime, int tasksDoneAllTime, int bestStreak,
                            int currentStreak, int countedDaysAllTime, int furthestWave) {}

    public Dashboard dashboard(int days) {
        waves.sync();
        int n = Math.max(7, Math.min(365, days));
        LocalDate today = time.today();
        LocalDate from = today.minusDays(n - 1);
        List<LedgerEvent> all = ledger.all();
        Set<LocalDate> counted = LedgerFacts.countedDays(all);
        Set<LocalDate> rest = LedgerFacts.restOrPausedDays(all);

        Map<LocalDate, List<LedgerEvent>> byDay = new TreeMap<>();
        all.forEach(e -> byDay.computeIfAbsent(e.gameDay(), k -> new ArrayList<>()).add(e));

        List<DayStats> history = new ArrayList<>();
        Map<String, Double> periodStats = new LinkedHashMap<>();
        config.stats().forEach(s -> periodStats.put(s.id(), 0.0));
        double xpPeriod = 0;
        for (LocalDate d = from; !d.isAfter(today); d = d.plusDays(1)) {
            List<LedgerEvent> ev = byDay.getOrDefault(d, List.of());
            Map<String, Double> stats = new LinkedHashMap<>();
            LedgerFacts.statTotals(ev).forEach((k, v) -> stats.put(k, v.doubleValue()));
            stats.forEach((k, v) -> periodStats.merge(k, v, Double::sum));
            double xp = LedgerFacts.xpOnDay(ev, d).doubleValue();
            xpPeriod += xp;
            int streak = StreakCalculator.compute(counted, rest, d, config.streak()).streakDays();
            history.add(new DayStats(d, xp, stats, streak, counted.contains(d)));
        }

        // all-time numbers
        double xpAll = 0;
        int focusMinutes = 0;
        int tasks = 0;
        int furthest = 0;
        for (LedgerEvent e : all) {
            xpAll += e.xp().doubleValue();
            if (e.type() == EventType.TIMER_VALIDATED) {
                focusMinutes += ((Number) Json.toMap(e.meta()).getOrDefault("countedMinutes", 0)).intValue();
            }
            if (e.type() == EventType.TASK_DONE) tasks++;
            if (e.type() == EventType.TASK_UNDONE) tasks--;
            if (e.type() == EventType.WAVE_CLEARED) {
                furthest = Math.max(furthest, ((Number) Json.toMap(e.meta()).get("wave")).intValue());
            }
        }
        int best = 0;
        if (!counted.isEmpty()) {
            LocalDate first = counted.stream().min(LocalDate::compareTo).orElse(today);
            for (LocalDate d = first; !d.isAfter(today); d = d.plusDays(1)) {
                best = Math.max(best, StreakCalculator.compute(counted, rest, d, config.streak()).streakDays());
            }
        }
        int current = StreakCalculator.compute(counted, rest, today, config.streak()).streakDays();
        periodStats.replaceAll((k, v) -> Math.round(v * 100) / 100.0);
        return new Dashboard(n, history, periodStats, Math.round(xpPeriod * 100) / 100.0,
                Math.round(xpAll * 100) / 100.0, Math.round(focusMinutes / 6.0) / 10.0, tasks, best, current,
                counted.size(), furthest);
    }
}
