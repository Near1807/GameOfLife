package gamelife.game;

import gamelife.IntegrationTest;
import gamelife.tasks.TaskService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.io.IOException;
import java.time.Duration;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** The daily cap, and the interval, come from the config (v4 brief 3). */
class WaveCapTest extends IntegrationTest {
    @DynamicPropertySource
    static void cfg(DynamicPropertyRegistry r) throws IOException {
        TestConfigs.use("wave-cap", Map.of("maxAttemptsPerDay", 2, "waveIntervalMinutes", 60), r);
    }

    @Autowired GameService game;
    @Autowired WaveService waves;
    @Autowired TaskService tasks;

    @Test
    void atMostTheCapPerGameDay() {
        tasks.tick(tasks.create("Earn", 600, List.of()).id());
        game.build(0, "archer");
        game.build(1, "archer");
        game.build(2, "archer");                   // Monday 10:00
        clock.advance(Duration.ofHours(6));        // 16:00
        waves.sync();
        var a = GameLedger.derive(ledger.all(), 3).attempts();
        assertThat(a).hasSize(2);
        assertThat(a.get(0).at()).isEqualTo(at(2026, 3, 2, 11, 0));
        assertThat(a.get(1).at()).isEqualTo(at(2026, 3, 2, 12, 0));
        assertThat(waves.status(ledger.all()).status()).isEqualTo("capped");
        assertThat(waves.status(ledger.all()).nextAt()).isEqualTo(at(2026, 3, 3, 4, 0));
        assertThatThrownBy(() -> waves.runNow()).hasMessageContaining("No more attempts today");
        // the next game day starts at 04:00
        setLocal(2026, 3, 3, 4, 30);
        waves.sync();
        assertThat(GameLedger.derive(ledger.all(), 3).attempts()).hasSize(3);
    }
}
