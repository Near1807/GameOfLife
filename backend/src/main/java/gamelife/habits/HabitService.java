package gamelife.habits;

import gamelife.common.GameException;
import gamelife.common.Money;
import gamelife.config.GameConfig;
import gamelife.ledger.EventType;
import gamelife.ledger.LedgerService;
import gamelife.payout.ActionResult;
import gamelife.payout.PayRequest;
import gamelife.payout.PayoutService;
import gamelife.rules.Tags;
import gamelife.time.GameTime;
import org.springframework.stereotype.Service;

import gamelife.rules.LedgerFacts;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Habits (brief 6): pay on a scheduled day after one Yes tap, at most once per game day.
 * Ledger key habit:{id}:{gameDay}. The game day is always the server's TODAY: there is no
 * way to tick a past day, so a missed day can never be paid later (brief 3.2).
 */
@Service
public class HabitService {
    private static final String MODULE = "habit";

    private final HabitRepository habits;
    private final LedgerService ledger;
    private final PayoutService payout;
    private final GameConfig config;
    private final GameTime time;

    public HabitService(HabitRepository habits, LedgerService ledger, PayoutService payout, GameConfig config,
                        GameTime time) {
        this.habits = habits;
        this.ledger = ledger;
        this.payout = payout;
        this.config = config;
        this.time = time;
    }

    /** status per day: DONE, MISSED (scheduled, not done), OFF (not scheduled, rest day, or before creation), TODAY (still open). */
    public record DayStatus(LocalDate date, String status) {}

    public record HabitView(long id, String name, Set<DayOfWeek> days, List<String> tags,
                            boolean scheduledToday, boolean doneToday, int streak, List<DayStatus> history) {}

    /** Days shown in the heat strip of each habit. */
    static final int HISTORY_DAYS = 28;

    /** Creates a habit. Missing days = every day, missing tags = the configured default. */
    public Habit create(String name, Set<DayOfWeek> days, List<String> tags) {
        String n = name == null ? "" : name.trim();
        if (n.isEmpty()) {
            throw GameException.badRequest("A habit needs a name");
        }
        Set<DayOfWeek> d = (days == null || days.isEmpty()) ? EnumSet.allOf(DayOfWeek.class) : days;
        List<String> t = tags != null ? Tags.clean(tags, config) : Tags.clean(config.habit().defaultTags(), config);
        long id = habits.insert(n, d, t, time.now());
        return habits.findById(id).orElseThrow();
    }

    public List<HabitView> list() {
        LocalDate today = time.today();
        Set<LocalDate> rest = LedgerFacts.restDays(ledger.all());
        return habits.findNotArchived().stream().map(h -> {
            Set<LocalDate> done = doneDays(h.id());
            LocalDate created = time.gameDay(h.createdAt());
            List<DayStatus> history = new ArrayList<>();
            for (int i = HISTORY_DAYS - 1; i >= 0; i--) {
                LocalDate d = today.minusDays(i);
                history.add(new DayStatus(d, status(h, d, today, created, done, rest)));
            }
            return new HabitView(h.id(), h.name(), h.days(), h.tags(), h.days().contains(today.getDayOfWeek()),
                    done.contains(today), streak(h, today, created, done, rest), history);
        }).toList();
    }

    private static String status(Habit h, LocalDate d, LocalDate today, LocalDate created, Set<LocalDate> done,
                                 Set<LocalDate> rest) {
        if (done.contains(d)) return "DONE";
        if (d.equals(today)) return h.days().contains(d.getDayOfWeek()) ? "TODAY" : "OFF";
        if (d.isBefore(created) || !h.days().contains(d.getDayOfWeek()) || rest.contains(d)) return "OFF";
        return "MISSED";
    }

    /**
     * Per-habit streak: consecutive scheduled days done, counting back from today (or from
     * yesterday while today is still open). Unscheduled days and planned rest days are skipped.
     * Display only: the XP streak multiplier is the global streak of brief 7.
     */
    static int streak(Habit h, LocalDate today, LocalDate created, Set<LocalDate> done, Set<LocalDate> rest) {
        int streak = 0;
        LocalDate d = done.contains(today) ? today : today.minusDays(1);
        while (!d.isBefore(created)) {
            if (done.contains(d)) {
                streak++;
            } else if (h.days().contains(d.getDayOfWeek()) && !rest.contains(d)) {
                break;
            }
            d = d.minusDays(1);
        }
        return streak;
    }

    /** Game days on which the habit was ticked, read from the ledger keys habit:{id}:{day}. */
    private Set<LocalDate> doneDays(long habitId) {
        Set<LocalDate> days = new HashSet<>();
        String prefix = "habit:" + habitId + ":";
        for (var e : ledger.byKeyPrefix(prefix)) {
            days.add(LocalDate.parse(e.key().substring(prefix.length())));
        }
        return days;
    }

    /** The one Yes tap. Refused on a day the habit is not scheduled. */
    public ActionResult done(long id) {
        Habit h = habits.findById(id).orElseThrow(() -> GameException.notFound("No habit " + id));
        LocalDate today = time.today();
        if (h.archived()) {
            throw GameException.badRequest("That habit was removed");
        }
        if (!h.days().contains(today.getDayOfWeek())) {
            throw GameException.conflict("Not scheduled today");
        }
        return payout.pay(new PayRequest(key(id, today), EventType.HABIT_DONE, MODULE, h.name(),
                Money.of(config.habit().xp()), h.tags(), Map.of("habitId", id, "day", today.toString())));
    }

    public void archive(long id) {
        habits.archive(id);
    }

    static String key(long habitId, LocalDate day) {
        return "habit:" + habitId + ":" + day;
    }
}
