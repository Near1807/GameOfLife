package gamelife.rules;

import gamelife.config.GameConfig;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

/** Brief section 7: streak formula, freezes, rest days, comeback. */
class StreakCalculatorTest {
    private final GameConfig.Streak cfg = GameConfig.load(Path.of("../config/gamelife.json")).streak();
    private final LocalDate today = LocalDate.of(2026, 6, 1);

    /** count consecutive days ending the day before end. */
    private Set<LocalDate> daysBefore(LocalDate end, int count) {
        Set<LocalDate> s = new HashSet<>();
        for (int i = 1; i <= count; i++) {
            s.add(end.minusDays(i));
        }
        return s;
    }

    @Test
    void multiplierMatchesBriefExamples() {
        assertThat(StreakCalculator.multiplier(4, cfg)).isCloseTo(1.2, within(1e-9));
        assertThat(StreakCalculator.multiplier(25, cfg)).isCloseTo(1.5, within(1e-9));
        assertThat(StreakCalculator.multiplier(100, cfg)).isCloseTo(2.0, within(1e-9));
        assertThat(StreakCalculator.multiplier(365, cfg)).isCloseTo(2.91, within(0.01));
        assertThat(StreakCalculator.multiplier(0, cfg)).isEqualTo(1.0);
    }

    @Test
    void multiplierHasNoCapAndGrowsMoreSlowly() {
        double prevGain = Double.MAX_VALUE;
        for (int d = 1; d < 3000; d++) {
            double gain = StreakCalculator.multiplier(d + 1, cfg) - StreakCalculator.multiplier(d, cfg);
            assertThat(gain).isPositive().isLessThan(prevGain);
            prevGain = gain;
        }
        assertThat(StreakCalculator.multiplier(10_000, cfg)).isGreaterThan(10.9);
    }

    @Test
    void streakCountsConsecutiveDaysIncludingTodayOnceCounted() {
        Set<LocalDate> counted = daysBefore(today, 3);
        StreakState before = StreakCalculator.compute(counted, Set.of(), today, cfg);
        assertThat(before.streakDays()).isEqualTo(3);
        assertThat(before.todayCounted()).isFalse();
        assertThat(before.streakForPayment()).isEqualTo(4); // first action today makes it 4

        counted.add(today);
        StreakState after = StreakCalculator.compute(counted, Set.of(), today, cfg);
        assertThat(after.streakDays()).isEqualTo(4);
        assertThat(after.streakForPayment()).isEqualTo(4);
    }

    @Test
    void missedDayWithoutFreezeBreaksStreak() {
        Set<LocalDate> counted = daysBefore(today.minusDays(1), 5); // yesterday missed
        StreakState s = StreakCalculator.compute(counted, Set.of(), today, cfg);
        assertThat(s.streakDays()).isZero();
        assertThat(s.streakForPayment()).isEqualTo(1);
    }

    @Test
    void freezeEarnedEvery14DaysAndUsedAutomatically() {
        Set<LocalDate> counted = daysBefore(today.minusDays(1), 14); // then yesterday missed
        StreakState s = StreakCalculator.compute(counted, Set.of(), today, cfg);
        assertThat(s.streakDays()).isEqualTo(14); // kept, not increased
        assertThat(s.freezes()).isZero();          // the one freeze was used
    }

    @Test
    void thirteenDaysEarnNoFreeze() {
        Set<LocalDate> counted = daysBefore(today.minusDays(1), 13);
        StreakState s = StreakCalculator.compute(counted, Set.of(), today, cfg);
        assertThat(s.streakDays()).isZero();
    }

    @Test
    void freezesAreCappedAtTwo() {
        Set<LocalDate> counted = daysBefore(today, 60); // 4 earned, max 2 stored
        StreakState s = StreakCalculator.compute(counted, Set.of(), today, cfg);
        assertThat(s.freezes()).isEqualTo(2);
    }

    @Test
    void withoutFreezeLeftTheSecondMissBreaks() {
        Set<LocalDate> counted = daysBefore(today.minusDays(2), 14); // 1 freeze, 2 missed days
        StreakState s = StreakCalculator.compute(counted, Set.of(), today, cfg);
        assertThat(s.streakDays()).isZero();
    }

    @Test
    void restDayKeepsStreakWithoutIncreasingItOrUsingAFreeze() {
        Set<LocalDate> counted = daysBefore(today.minusDays(1), 5);
        StreakState s = StreakCalculator.compute(counted, Set.of(today.minusDays(1)), today, cfg);
        assertThat(s.streakDays()).isEqualTo(5);
        assertThat(s.freezes()).isZero();
    }

    @Test
    void comebackAfterThreeEmptyDaysThenStreakRestartsAtOne() {
        Set<LocalDate> counted = daysBefore(today.minusDays(3), 5); // 3 empty days before today
        StreakState s = StreakCalculator.compute(counted, Set.of(), today, cfg);
        assertThat(s.comebackPending()).isTrue();
        assertThat(s.streakForPayment()).isEqualTo(1);

        counted.add(today);
        StreakState after = StreakCalculator.compute(counted, Set.of(), today, cfg);
        assertThat(after.comebackPending()).isFalse(); // only the first action of the day
        assertThat(after.streakDays()).isEqualTo(1);
    }

    @Test
    void noComebackAfterOnlyTwoEmptyDays() {
        Set<LocalDate> counted = daysBefore(today.minusDays(2), 5);
        assertThat(StreakCalculator.compute(counted, Set.of(), today, cfg).comebackPending()).isFalse();
    }

    @Test
    void noComebackOnVeryFirstAction() {
        StreakState s = StreakCalculator.compute(Set.of(), Set.of(), today, cfg);
        assertThat(s.comebackPending()).isFalse();
        assertThat(s.streakForPayment()).isEqualTo(1);
    }
}
