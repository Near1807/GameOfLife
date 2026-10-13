package gamelife.game;

import gamelife.IntegrationTest;
import gamelife.ledger.EventType;
import gamelife.ledger.LedgerEvent;
import gamelife.tasks.TaskService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** v4 brief 3 and 5: automatic attempts, lazy catch-up exactly once and in order, the stuck rule. */
class WaveScheduleTest extends IntegrationTest {
    @Autowired GameService game;
    @Autowired WaveService waves;
    @Autowired TaskService tasks;

    private void earn(int minutes) {
        tasks.tick(tasks.create("Earn " + minutes + " " + clock.instant(), minutes, List.of()).id());
    }

    private List<GameLedger.Attempt> attempts() {
        return GameLedger.derive(ledger.all(), GameFixtures.CAT).attempts();
    }

    @Test
    void nothingHappensBeforeTheFirstTower() {
        clock.advance(Duration.ofDays(2));
        waves.sync();
        assertThat(attempts()).isEmpty();
        assertThat(waves.status(ledger.all()).status()).isEqualTo("idle");
        assertThatThrownBy(() -> waves.runNow()).hasMessageContaining("Build a tower first");
    }

    @Test
    void catchUpComputesEachMissedAttemptOnceInOrderThenStopsWhenStuck() {
        earn(250);
        Instant anchor = clock.instant();
        game.build(0, "archer");
        game.build(1, "archer");
        clock.advance(Duration.ofHours(20));       // 10 slots passed while the app was closed
        waves.sync();
        List<GameLedger.Attempt> first = attempts();
        assertThat(first).isNotEmpty();
        // in order: slot i is anchor + 2h x i, one wave after the other, the keys follow the rule
        for (int i = 0; i < first.size(); i++) {
            GameLedger.Attempt a = first.get(i);
            assertThat(a.at()).isEqualTo(anchor.plus(Duration.ofHours(2L * (i + 1))));
            assertThat(a.key()).isEqualTo("wave-attempt:" + a.wave() + ":1");
            assertThat(a.wave()).isEqualTo(i + 1);
        }
        // the last one failed, and with the same build every later slot was skipped
        GameLedger.Attempt last = first.get(first.size() - 1);
        assertThat(last.won()).isFalse();
        assertThat(first.size()).isLessThan(10);
        assertThat(waves.status(ledger.all()).status()).isEqualTo("stuck");

        // looking again computes nothing twice
        int events = eventCount();
        waves.sync();
        clock.advance(Duration.ofHours(6));
        waves.sync();
        assertThat(eventCount()).isEqualTo(events);

        // a Market cannot change a fight: still stuck
        earn(400);
        game.build(2, "income");
        clock.advance(Duration.ofHours(2));
        waves.sync();
        assertThat(attempts()).hasSize(first.size());

        // a real change: the next slot tries again (attempt 2 of the same wave)
        game.upgrade(0, 1, 1);
        assertThat(waves.status(ledger.all()).status()).isEqualTo("waiting");
        clock.advance(Duration.ofHours(2));
        waves.sync();
        List<GameLedger.Attempt> after = attempts();
        assertThat(after).hasSizeGreaterThan(first.size());
        assertThat(after.get(first.size()).key()).isEqualTo("wave-attempt:" + last.wave() + ":2");
    }

    @Test
    void eachSlotSeesTheBuildAsItWasAtThatTime() {
        earn(400);
        game.build(0, "archer");                   // 10:00, the schedule starts
        clock.advance(Duration.ofHours(3));        // 13:00: slot 12:00 has passed but nobody looked
        game.build(1, "archer");
        clock.advance(Duration.ofHours(2));        // 15:00
        waves.sync();
        List<GameLedger.Attempt> a = attempts();
        assertThat(a.get(0).meta().get("towers").toString()).isEqualTo("[[0, archer, 0, 0]]");
        if (a.size() > 1) {
            assertThat(a.get(1).meta().get("towers").toString()).isEqualTo("[[0, archer, 0, 0], [1, archer, 0, 0]]");
        }
    }

    @Test
    void runNowFollowsTheSameRules() {
        earn(250);
        game.build(0, "archer");
        var r = waves.runNow();
        assertThat(r.paid()).isTrue();
        assertThat(r.event().key()).isEqualTo("wave-attempt:1:1");
        assertThat(r.event().currencyDelta().signum()).isZero();
        assertThat(r.event().xp().signum()).isZero();
        // keep running until a wave fails
        while (attempts().get(attempts().size() - 1).won()) {
            waves.runNow();
        }
        assertThatThrownBy(() -> waves.runNow()).hasMessageContaining("Nothing changed");
        game.build(1, "archer");
        assertThat(waves.runNow().paid()).isTrue();
    }

    @Test
    void replayMatchesTheRecordedResult() {
        earn(250);
        game.build(0, "archer");
        game.build(1, "archer");
        clock.advance(Duration.ofHours(8));
        waves.sync();
        for (GameLedger.Attempt a : attempts()) {
            WaveService.Watch w = waves.watch(a.key());
            assertThat(w.sameAsRecorded()).isTrue();
            assertThat(w.result().won()).isEqualTo(a.won());
            var frames = w.result().replay().frames();
            assertThat(frames.get(frames.size() - 1).lives()).isEqualTo(((Number) a.meta().get("lives")).intValue());
            // simulated again from the ledger alone: the same answer
            assertThat(waves.replay(a.meta()).lives()).isEqualTo(w.result().lives());
        }
        LedgerEvent e = ledger.all().stream().filter(x -> x.type() == EventType.WAVE_CLEARED).findFirst().orElseThrow();
        assertThat(e.meta()).contains("\"towers\"");
    }
}
