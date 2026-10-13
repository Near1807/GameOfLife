package gamelife.sim;

import gamelife.config.GameConfig;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

/** Brief 9.4 and 14: the simulation runs and all three profiles can progress, at different speeds. */
class PacingSimulationTest {
    private final GameConfig cfg = GameConfig.load(Path.of("../config/gamelife.json"));

    @Test
    void allThreeProfilesProgressAtDifferentSpeeds() {
        var r = PacingSimulation.runAll(cfg);
        assertThat(r).allMatch(PacingSimulation.Result::progresses);
        var lazy = r.get(0);
        var average = r.get(1);
        var strong = r.get(2);
        assertThat(lazy.daysToBoss5()).isGreaterThan(average.daysToBoss5());
        assertThat(average.daysToBoss5()).isGreaterThan(strong.daysToBoss5());
    }

    @Test
    void pacingTargetsOfTheBrief() {
        var average = PacingSimulation.run(PacingSimulation.PROFILES.get(1), cfg, 365);
        assertThat(average.daysToBoss1()).isBetween(6, 8);              // about 6 to 8 days
        assertThat(PacingSimulation.zeroDaysToFall(cfg)).isBetween(5, 6); // a lazy stretch risks a fall in ~5 days
        assertThat(PacingSimulation.strongWeekDaysAtDay60(cfg)).isBetween(1, 2); // a strong week clears a boss in 1-2 days
    }
}
