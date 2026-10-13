package gamelife.sim;

import gamelife.config.GameConfig;
import gamelife.game.GameCatalog;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/** v4 brief 6 and v4.1: the pacing targets the shipped config meets (see docs/PACING.md). */
class PacingSimulationTest {
    static final GameConfig CFG = GameConfig.load(Path.of("../config/gamelife.json"));
    static final GameCatalog CAT = GameCatalog.load(Path.of("../content"));
    static final Map<PacingSimulation.Profile, PacingSimulation.Report> YEAR = new EnumMap<>(PacingSimulation.Profile.class);

    @BeforeAll
    static void playAYear() {
        List.of(PacingSimulation.Profile.values()).parallelStream()
                .map(p -> PacingSimulation.run(p, 365, CFG, CAT)).toList()
                .forEach(r -> YEAR.put(r.profile(), r));
    }

    @Test
    void everyProfileKeepsProgressingAndBuyingAfterDay300() {
        for (var r : YEAR.values()) {
            var w = r.waveOnDay();
            assertThat(w.get(100)).as(r.profile() + " day 100").isGreaterThan(w.get(30));
            assertThat(w.get(300)).as(r.profile() + " day 300").isGreaterThan(w.get(200));
            assertThat(r.lastNewWaveDay()).as(r.profile() + " still clearing new waves").isGreaterThan(300);
            assertThat(r.lastPurchaseDay()).as(r.profile() + " still buying").isGreaterThan(300);
            assertThat(r.optionsLeft()).as(r.profile() + " something left to buy").isPositive();
            assertThat(r.longestStall()).as(r.profile() + " no long wall").isLessThan(70);
        }
    }

    @Test
    void everyoneMovesToTheSecondMapAndOnlyTheStrongReachTheThird() {
        assertThat(YEAR.values()).allMatch(r -> !r.moves().isEmpty());
        assertThat(YEAR.get(PacingSimulation.Profile.STRONG).map()).isEqualTo("Data Serpent");
        assertThat(YEAR.get(PacingSimulation.Profile.LAZY).map()).isEqualTo("Circuit Switchback");
    }

    @Test
    void strongStaysWellAhead() {
        var lazy = YEAR.get(PacingSimulation.Profile.LAZY).waveOnDay();
        var strong = YEAR.get(PacingSimulation.Profile.STRONG).waveOnDay();
        for (int day : new int[]{30, 100, 200, 300, 365}) {
            assertThat(strong.get(day)).as("day " + day).isGreaterThanOrEqualTo((int) Math.ceil(1.4 * lazy.get(day)));
        }
    }

    @Test
    void theSimulationIsDeterministic() {
        var a = PacingSimulation.run(PacingSimulation.Profile.AVERAGE, 60, CFG, CAT);
        var b = PacingSimulation.run(PacingSimulation.Profile.AVERAGE, 60, CFG, CAT);
        assertThat(a).isEqualTo(b);
    }
}
