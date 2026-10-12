package gamelife.timer;

import gamelife.IntegrationTest;
import gamelife.common.GameException;
import gamelife.payout.ActionResult;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;

/** Brief 3.2, 6 and 14: a timer never pays more than planned, never without Yes. */
@AutoConfigureMockMvc
class TimerRulesTest extends IntegrationTest {
    @Autowired TimerService timers;
    @Autowired MockMvc mvc;

    @Test
    void endingPaysNothingOnlyYesPays() {
        TimerView t = timers.start(25, "Deep work", null, null);
        clock.advance(Duration.ofMinutes(30));
        assertThat(timers.current().orElseThrow().status()).isEqualTo("ASK");
        assertThat(eventCount()).isZero();

        ActionResult r = timers.answer(t.id(), true);
        assertThat(r.paid()).isTrue();
        assertThat(r.event().xp()).isEqualByComparingTo("27.50"); // 25 x 1.1
    }

    @Test
    void noPaysNothing() {
        TimerView t = timers.start(45, null, null, null);
        clock.advance(Duration.ofMinutes(45));
        timers.answer(t.id(), false);
        assertThat(eventCount()).isZero();
        // changing the answer later is impossible
        timers.answer(t.id(), true);
        assertThat(eventCount()).isZero();
    }

    @Test
    void questionIsRefusedWhileRunning() {
        TimerView t = timers.start(25, null, null, null);
        clock.advance(Duration.ofMinutes(10));
        assertThatThrownBy(() -> timers.answer(t.id(), true)).isInstanceOf(GameException.class);
        assertThat(eventCount()).isZero();
    }

    @Test
    void doubleYesPaysOnce() {
        TimerView t = timers.start(60, null, null, null);
        clock.advance(Duration.ofMinutes(60));
        timers.answer(t.id(), true);
        ActionResult again = timers.answer(t.id(), true);
        assertThat(again.paid()).isFalse();
        assertThat(eventCount()).isEqualTo(1);
    }

    @Test
    void appClosedOrDeviceAsleepForHoursPaysAtMostPlanned() {
        TimerView t = timers.start(90, null, null, null);
        clock.advance(Duration.ofHours(14)); // laptop slept all night
        assertThat(timers.current()).isPresent(); // asked on reopening
        ActionResult r = timers.answer(t.id(), true);
        assertThat(r.event().meta()).contains("\"countedMinutes\":90");
        assertThat(r.event().xp()).isEqualByComparingTo("99.00"); // 90 x 1.1, not 14 hours
    }

    @Test
    void serverClockJumpingBackwardsPaysNothingExtra() {
        TimerView t = timers.start(25, null, null, null);
        timers.stop(t.id()); // stopped immediately
        clock.advance(Duration.ofHours(-5)); // clock moved back
        timers.answer(t.id(), true);
        assertThat(eventCount()).isZero(); // 0 minutes counted: below the 10 minute minimum
    }

    @Test
    void clientCannotSendItsOwnTimes() throws Exception {
        // A forged startedAt in the request is ignored: the server uses its own clock.
        mvc.perform(post("/api/timers").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"minutes\":25,\"startedAt\":\"2020-01-01T00:00:00Z\"}"))
                .andExpect(jsonPath("$.startedAt").value(clock.instant().toString()))
                .andExpect(jsonPath("$.status").value("RUNNING"));
    }

    @Test
    void earlyStopPaysElapsedMinutesOnlyFromTen() {
        TimerView a = timers.start(45, null, null, null);
        clock.advance(Duration.ofMinutes(9).plusSeconds(59));
        timers.stop(a.id());
        timers.answer(a.id(), true);
        assertThat(eventCount()).isZero();

        TimerView b = timers.start(45, null, null, null);
        clock.advance(Duration.ofMinutes(20).plusSeconds(30));
        timers.stop(b.id());
        clock.advance(Duration.ofHours(2)); // answering later does not add time
        ActionResult r = timers.answer(b.id(), true);
        assertThat(r.event().meta()).contains("\"countedMinutes\":20");
    }

    @Test
    void plannedLengthLimits() {
        assertThatThrownBy(() -> timers.start(5, null, null, null)).isInstanceOf(GameException.class);
        assertThatThrownBy(() -> timers.start(91, null, null, null)).isInstanceOf(GameException.class);
    }

    @Test
    void onlyOneTimerAtATime() {
        timers.start(25, null, null, null);
        assertThatThrownBy(() -> timers.start(25, null, null, null)).isInstanceOf(GameException.class);
    }
}
