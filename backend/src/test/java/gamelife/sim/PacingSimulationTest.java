package gamelife.sim;

import gamelife.config.GameConfig;
import gamelife.game.GameCatalog;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

/** v4 brief 6: the pacing targets that the shipped config meets (see docs/PACING.md). */
class PacingSimulationTest {
    static final GameConfig CFG = GameConfig.load(Path.of("../config/gamelife.json"));
    static final GameCatalog CAT = GameCatalog.load(Path.of("../content"));

    @Test
    void everyProfileProgressesAndKeepsBuyingAllYear() {
        for (PacingSimulation.Profile p : PacingSimulation.Profile.values()) {
            var r = PacingSimulation.run(p, 365, CFG, CAT);
            assertThat(r.waveOnDay().get(30)).as(p + " day 30").isGreaterThan(5);
            assertThat(r.waveOnDay().get(100)).as(p + " day 100").isGreaterThan(r.waveOnDay().get(30));
            assertThat(r.waveOnDay().get(365)).as(p + " day 365").isGreaterThan(r.waveOnDay().get(100));
            assertThat(r.lastPurchaseDay()).as(p + " still buying late in the year").isGreaterThan(250);
            assertThat(r.optionsLeft()).as(p + " something left to buy").isPositive();
        }
    }

    @Test
    void strongIsFarAheadInTheFirstMonthAndNeverBehind() {
        var lazy = PacingSimulation.run(PacingSimulation.Profile.LAZY, 365, CFG, CAT);
        var strong = PacingSimulation.run(PacingSimulation.Profile.STRONG, 365, CFG, CAT);
        assertThat(strong.waveOnDay().get(30)).isGreaterThanOrEqualTo((int) (1.7 * lazy.waveOnDay().get(30)));
        for (int day : new int[]{30, 100, 365}) {
            assertThat(strong.waveOnDay().get(day)).isGreaterThanOrEqualTo(lazy.waveOnDay().get(day));
        }
    }

    @Test
    void theSimulationIsDeterministic() {
        var a = PacingSimulation.run(PacingSimulation.Profile.AVERAGE, 60, CFG, CAT);
        var b = PacingSimulation.run(PacingSimulation.Profile.AVERAGE, 60, CFG, CAT);
        assertThat(a).isEqualTo(b);
    }
}
