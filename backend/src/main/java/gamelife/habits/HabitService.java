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

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.EnumSet;
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

    public record HabitView(long id, String name, Set<DayOfWeek> days, List<String> tags,
                            boolean scheduledToday, boolean doneToday) {}

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
        return habits.findNotArchived().stream()
                .map(h -> new HabitView(h.id(), h.name(), h.days(), h.tags(),
                        h.days().contains(today.getDayOfWeek()),
                        ledger.find(key(h.id(), today)).isPresent()))
                .toList();
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
