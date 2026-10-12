package gamelife.streak;

import gamelife.IntegrationTest;
import gamelife.checkin.CheckinService;
import gamelife.common.GameException;
import gamelife.payout.ActionResult;
import gamelife.payout.PayoutService;
import gamelife.rest.RestDayService;
import gamelife.tasks.Task;
import gamelife.tasks.TaskService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.Duration;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Brief 7 through the real services: streak multiplier, rest days, comeback bonus. */
class StreakIntegrationTest extends IntegrationTest {
    @Autowired CheckinService checkin;
    @Autowired TaskService tasks;
    @Autowired RestDayService rest;
    @Autowired PayoutService payout;

    private void nextDay() {
        clock.advance(Duration.ofDays(1));
    }

    @Test
    void fourthDayPaysTimesOnePointTwoOnEverything() {
        for (int i = 0; i < 3; i++) {
            checkin.checkin(3);
            nextDay();
        }
        ActionResult a = checkin.checkin(3);                       // day 4
        assertThat(a.event().xp()).isEqualByComparingTo("6.00");   // 5 x 1.2
        Task t = tasks.create("Run", 30, null);
        assertThat(tasks.tick(t.id()).event().xp()).isEqualByComparingTo("36.00"); // same day, same x1.2
    }

    @Test
    void comebackDoublesOnlyTheFirstActionAndRestartsAtOne() {
        checkin.checkin(3);
        clock.advance(Duration.ofDays(4)); // 3 empty days in between
        Task t1 = tasks.create("Back at it", 10, null);
        Task t2 = tasks.create("Second thing", 10, null);
        ActionResult first = tasks.tick(t1.id());
        ActionResult second = tasks.tick(t2.id());
        assertThat(first.event().xp()).isEqualByComparingTo("22.00");  // 10 x 1.1 (streak 1) x 2
        assertThat(second.event().xp()).isEqualByComparingTo("11.00"); // 10 x 1.1
        assertThat(payout.streakNow(ledger.all()).streakDays()).isEqualTo(1);
    }

    @Test
    void plannedRestDayKeepsTheStreak() {
        checkin.checkin(3);
        nextDay();
        checkin.checkin(3);
        rest.plan(LocalDate.of(2026, 3, 4)); // Wednesday, planned on Tuesday
        nextDay();                            // Wednesday: resting
        nextDay();                            // Thursday
        assertThat(payout.streakNow(ledger.all()).streakDays()).isEqualTo(2);
        ActionResult r = checkin.checkin(3);
        assertThat(r.event().xp()).isEqualByComparingTo("5.87"); // 5 x (1 + 0.1 x sqrt(3)): day 3, rest day did not add
    }

    @Test
    void restDaysAreLimitedPerWeekAndOnlyInAdvance() {
        rest.plan(LocalDate.of(2026, 3, 3));
        rest.plan(LocalDate.of(2026, 3, 5));
        assertThatThrownBy(() -> rest.plan(LocalDate.of(2026, 3, 7))).isInstanceOf(GameException.class);
        assertThatThrownBy(() -> rest.plan(LocalDate.of(2026, 3, 1))).isInstanceOf(GameException.class);
        rest.plan(LocalDate.of(2026, 3, 9)); // next week is fine
        assertThat(rest.plan(LocalDate.of(2026, 3, 9)).paid()).isFalse(); // double tap
    }
}
