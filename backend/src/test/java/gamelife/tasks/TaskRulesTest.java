package gamelife.tasks;

import gamelife.IntegrationTest;
import gamelife.payout.ActionResult;
import gamelife.timer.TimerService;
import gamelife.timer.TimerView;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** Brief 3.2, 6 and 14: to-do payouts. */
class TaskRulesTest extends IntegrationTest {
    @Autowired TaskService tasks;
    @Autowired TimerService timers;

    @Test
    void tickPaysOneXpPerExpectedMinuteTimesStreak() {
        Task t = tasks.create("Study", 30, List.of("INTELLECT"));
        ActionResult r = tasks.tick(t.id());
        // first day of a streak: x1.1
        assertThat(r.event().xp()).isEqualByComparingTo("33.00");
        assertThat(r.event().statSplits()).isEqualTo("{\"INTELLECT\":\"30.00\"}"); // stats get base XP
    }

    @Test
    void creatingATaskPaysNothing() {
        tasks.create("Plan week", 15, null);
        assertThat(eventCount()).isZero();
    }

    @Test
    void untickThenRetickPaysOnceOverall() {
        Task t = tasks.create("Clean desk", 15, null);
        BigDecimal once = tasks.tick(t.id()).event().xp();
        tasks.untick(t.id());
        assertThat(balance()).isEqualByComparingTo("0");
        tasks.tick(t.id());
        tasks.tick(t.id()); // double tap
        assertThat(balance()).isEqualByComparingTo(once);
        tasks.untick(t.id());
        tasks.untick(t.id()); // double tap on un-tick
        assertThat(balance()).isEqualByComparingTo("0");
        tasks.tick(t.id());
        assertThat(balance()).isEqualByComparingTo(once);
        assertThat(ledger.verify().valid()).isTrue();
    }

    @Test
    void editingACompletedTaskChangesNothing() {
        Task t = tasks.create("Essay", 60, List.of("INTELLECT"));
        tasks.tick(t.id());
        BigDecimal before = balance();
        String statsBefore = ledger.all().get(0).statSplits();

        tasks.edit(t.id(), "Essay (long)", 120, List.of("CREATIVITY"));
        assertThat(balance()).isEqualByComparingTo(before);
        assertThat(ledger.all().get(0).statSplits()).isEqualTo(statsBefore);

        // even un-tick + re-tick pays the original base, not the edited one
        tasks.untick(t.id());
        tasks.tick(t.id());
        assertThat(balance()).isEqualByComparingTo(before);
    }

    @Test
    void rememberedMinutesAndTagsForTheSameName() {
        Task first = tasks.create("Guitar", 45, List.of("CREATIVITY"));
        tasks.tick(first.id());
        Task again = tasks.create("guitar", null, null); // only the name
        assertThat(again.expectedMinutes()).isEqualTo(45);
        assertThat(again.tags()).containsExactly("CREATIVITY");
    }

    @Test
    void suggestionsPayNothingAndSkipOpenTasks() {
        Task t = tasks.create("Groceries", 30, List.of("VITALITY"));
        tasks.tick(t.id());
        tasks.create("Call grandma", 15, List.of("SOCIAL")); // open
        assertThat(tasks.suggestions("")).isEmpty(); // done today: already on today's list
        clock.advance(Duration.ofDays(1));
        List<Suggestion> s = tasks.suggestions("");
        assertThat(s).extracting(Suggestion::name).containsExactly("Groceries");
        assertThat(tasks.suggestions("groc")).hasSize(1);
        assertThat(tasks.suggestions("xyz")).isEmpty();
        assertThat(eventCount()).isEqualTo(1);
    }

    @Test
    void taskDoneInsideAFocusBlockPaysOnceThroughTheBlock() {
        Task t = tasks.create("Refactor parser", 60, List.of("INTELLECT"));
        TimerView timer = timers.start(25, null, t.id(), null);
        clock.advance(Duration.ofMinutes(25));
        ActionResult paid = timers.answer(timer.id(), true);
        assertThat(paid.event().xp()).isEqualByComparingTo("27.50"); // 25 x 1.1, the task's 60 not paid

        // the task is now done, with 0 extra
        assertThat(tasks.list()).filteredOn(v -> v.id() == t.id()).first()
                .satisfies(v -> assertThat(v.done()).isTrue());
        BigDecimal afterBlock = balance();
        assertThat(afterBlock).isEqualByComparingTo("27.50");

        // un-tick and re-tick cannot add the task's own XP on top of the block
        tasks.untick(t.id());
        tasks.tick(t.id());
        assertThat(balance()).isEqualByComparingTo(afterBlock);
    }

    @Test
    void tasksDoneOnEarlierDaysLeaveTheList() {
        Task t = tasks.create("Taxes", 120, null);
        tasks.tick(t.id());
        assertThat(tasks.list()).hasSize(1);
        clock.advance(Duration.ofDays(1));
        assertThat(tasks.list()).isEmpty();
    }
}
