package gamelife.rules;

import gamelife.config.GameConfig;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

/** Brief 8: level L -> L+1 needs 100 x 1.15 ^ (L - 1) stat XP; every stat starts at level 1. */
class LevelCurveTest {
    private final GameConfig.StatLevels cfg = GameConfig.load(Path.of("../config/gamelife.json")).statLevels();

    private LevelCurve.Level at(String xp) {
        return LevelCurve.of(new BigDecimal(xp), cfg);
    }

    @Test
    void thresholds() {
        assertThat(at("0").level()).isEqualTo(1);
        assertThat(at("99.99").level()).isEqualTo(1);
        assertThat(at("100").level()).isEqualTo(2);         // 100
        assertThat(at("214.99").level()).isEqualTo(2);
        assertThat(at("215").level()).isEqualTo(3);         // 100 + 115
        assertThat(at("347.25").level()).isEqualTo(4);      // + 132.25
        assertThat(LevelCurve.xpForLevel(1, cfg)).isEqualTo(100.0);
        assertThat(LevelCurve.xpForLevel(2, cfg)).isCloseTo(115.0, within(1e-9));
        assertThat(LevelCurve.xpForLevel(3, cfg)).isCloseTo(132.25, within(1e-9));
    }

    @Test
    void progressInsideALevel() {
        LevelCurve.Level l = at("157.5"); // 57.5 into level 2, which needs 115
        assertThat(l.level()).isEqualTo(2);
        assertThat(l.xpIntoLevel()).isCloseTo(57.5, within(1e-6));
        assertThat(l.xpForNext()).isCloseTo(115, within(1e-6));
        assertThat(l.progress()).isCloseTo(0.5, within(1e-6));
    }

    @Test
    void reachingALevelExactlyMatchesTheSumOfThresholds() {
        for (int level = 1; level <= 40; level++) {
            double need = LevelCurve.totalXpToReach(level, cfg);
            assertThat(LevelCurve.of(BigDecimal.valueOf(need).setScale(2, java.math.RoundingMode.CEILING), cfg).level())
                    .isEqualTo(level);
        }
    }

    @Test
    void pacingSanityCheck() {
        // 30 stat XP per day: level 10 after ~56 days, level 20 after ~294 days (see DECISIONS.md)
        assertThat(LevelCurve.totalXpToReach(10, cfg) / 30).isCloseTo(56, within(1.0));
        assertThat(LevelCurve.totalXpToReach(20, cfg) / 30).isCloseTo(294, within(1.0));
    }
}
