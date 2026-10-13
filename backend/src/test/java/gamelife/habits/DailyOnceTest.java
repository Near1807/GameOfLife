package gamelife.habits;

import gamelife.IntegrationTest;
import gamelife.checkin.CheckinService;
import gamelife.common.GameException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.DayOfWeek;
import java.time.Duration;
import java.util.EnumSet;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Brief 3.2 and 14: habits and the check-in pay once per game day; day ends at 04:00. */
class DailyOnceTest extends IntegrationTest {
    @Autowired HabitService habits;
    @Autowired CheckinService checkin;

    @Test
    void habitPaysOncePerDay() {
        Habit h = habits.create("Meditate", null, null);
        assertThat(habits.done(h.id()).paid()).isTrue();
        assertThat(habits.done(h.id()).paid()).isFalse();
        assertThat(eventCount()).isEqualTo(1);
        assertThat(ledger.all().get(0).xp()).isEqualByComparingTo("22.00"); // 20 x 1.1
    }

    @Test
    void habitOnlyOnScheduledDays() {
        // 2 March 2026 is a Monday
        Habit h = habits.create("Swim", EnumSet.of(DayOfWeek.TUESDAY), null);
        assertThatThrownBy(() -> habits.done(h.id())).isInstanceOf(GameException.class);
        clock.advance(Duration.ofDays(1));
        assertThat(habits.done(h.id()).paid()).isTrue();
    }

    @Test
    void missedDayIsNeverPaidLater() {
        Habit h = habits.create("Journal", null, null);
        clock.advance(Duration.ofDays(1)); // Monday missed
        habits.done(h.id());
        habits.done(h.id());
        assertThat(eventCount()).isEqualTo(1);
        assertThat(ledger.all().get(0).gameDay().toString()).isEqualTo("2026-03-03"); // paid for Tuesday only
    }

    @Test
    void dayBoundaryIsFourInTheMorning() {
        Habit h = habits.create("Floss", null, null);
        setLocal(2026, 3, 3, 1, 30); // 01:30 Tuesday still belongs to Monday
        habits.done(h.id());
        setLocal(2026, 3, 3, 3, 59);
        assertThat(habits.done(h.id()).paid()).isFalse(); // same game day
        setLocal(2026, 3, 3, 4, 0);
        assertThat(habits.done(h.id()).paid()).isTrue();  // new game day
        assertThat(ledger.all()).filteredOn(e -> e.type() == gamelife.ledger.EventType.HABIT_DONE)
                .extracting(e -> e.gameDay().toString())
                .containsExactly("2026-03-02", "2026-03-03");
    }

    @Test
    void checkinOncePerDayFirstAnswerKept() {
        assertThat(checkin.checkin(2).paid()).isTrue();
        assertThat(checkin.checkin(5).paid()).isFalse();
        assertThat(checkin.todayMood()).isEqualTo(2);
        assertThat(ledger.all().get(0).statSplits()).isEqualTo("{\"SPIRIT\":\"5.00\"}");
        setLocal(2026, 3, 3, 3, 0);
        assertThat(checkin.checkin(4).paid()).isFalse(); // still Monday's game day
        setLocal(2026, 3, 3, 4, 1);
        assertThat(checkin.checkin(4).paid()).isTrue();
    }

    @Test
    void checkinRejectsOutOfScaleMood() {
        assertThatThrownBy(() -> checkin.checkin(0)).isInstanceOf(GameException.class);
        assertThatThrownBy(() -> checkin.checkin(6)).isInstanceOf(GameException.class);
    }
}
