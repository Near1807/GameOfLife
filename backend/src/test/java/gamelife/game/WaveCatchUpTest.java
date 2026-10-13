package gamelife.game;

import gamelife.IntegrationTest;
import gamelife.tasks.TaskService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** Desktop brief 5: after the computer was off for days, the startup catch-up plays the missed slots. */
class WaveCatchUpTest extends IntegrationTest {
    @Autowired GameService game;
    @Autowired WaveCatchUp catchUp;
    @Autowired TaskService tasks;

    @Test
    void startupWorksOutTheSlotsMissedWhileTheComputerWasOff() {
        tasks.tick(tasks.create("Earn", 600, List.of()).id());
        game.build(0, "archer");
        game.build(1, "archer");
        game.build(2, "archer");
        clock.advance(Duration.ofDays(4));           // the computer was off for four days
        catchUp.atStartup();                         // what runs when the server is ready
        var attempts = GameLedger.derive(ledger.all(), GameFixtures.CAT).attempts();
        assertThat(attempts).isNotEmpty();
        assertThat(attempts.get(0).at()).isEqualTo(at(2026, 3, 2, 12, 0));   // the first missed slot
        // in order, each slot once, and nothing more on a second run
        for (int i = 1; i < attempts.size(); i++) {
            assertThat(attempts.get(i).at()).isAfter(attempts.get(i - 1).at());
        }
        int n = eventCount();
        catchUp.atStartup();
        assertThat(eventCount()).isEqualTo(n);
    }
}
