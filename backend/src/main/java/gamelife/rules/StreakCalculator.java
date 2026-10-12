package gamelife.rules;

import gamelife.config.GameConfig;

import java.time.LocalDate;
import java.util.Collections;
import java.util.Set;

/**
 * Pure streak rules of brief section 7. No database, no clock: give it the counted days,
 * the planned rest days and today, it returns the streak. This makes the rules easy to test.
 *
 * Day by day, from the first counted day up to yesterday:
 *  - counted day (at least one validated action): streak + 1. Every freezeEveryCountedDays
 *    counted days earn one freeze, at most maxFreezes stored.
 *  - planned rest day: streak kept, not increased.
 *  - other missed day: uses a freeze automatically if one is stored (streak kept, not
 *    increased), otherwise the streak drops to 0.
 * Today: if already counted, streak + 1. An uncounted today never breaks the streak (the day
 * is not over).
 * Comeback: if the last comebackGapDays days (or more) right before today had no validated
 * action, the first validated action of today gets the comeback multiplier.
 */
public final class StreakCalculator {
    private StreakCalculator() {}

    public static StreakState compute(Set<LocalDate> counted, Set<LocalDate> restDays, LocalDate today,
                                      GameConfig.Streak cfg) {
        if (restDays == null) {
            restDays = Collections.emptySet();
        }
        LocalDate first = counted.stream().min(LocalDate::compareTo).orElse(null);
        if (first == null) {
            return new StreakState(0, 0, false, 0, false, 1, 0, null);
        }

        int streak = 0;
        int freezes = 0;
        int total = 0;
        int gap = 0;
        LocalDate lastCounted = null;

        for (LocalDate d = first; d.isBefore(today); d = d.plusDays(1)) {
            if (counted.contains(d)) {
                streak++;
                total++;
                freezes = earnFreeze(freezes, total, cfg);
                gap = 0;
                lastCounted = d;
            } else {
                gap++;
                if (restDays.contains(d)) {
                    // planned rest: keeps the streak, adds nothing
                } else if (freezes > 0) {
                    freezes--; // automatic freeze: keeps the streak, adds nothing
                } else {
                    streak = 0;
                }
            }
        }

        boolean todayCounted = counted.contains(today);
        boolean comebackPending = false;
        if (todayCounted) {
            streak++;
            total++;
            freezes = earnFreeze(freezes, total, cfg);
            lastCounted = today;
        } else {
            comebackPending = lastCounted != null && gap >= cfg.comebackGapDays();
        }
        int streakForPayment = todayCounted ? streak : streak + 1;
        return new StreakState(streak, freezes, todayCounted, gap, comebackPending, streakForPayment, total,
                lastCounted);
    }

    /** Streak multiplier = 1 + factor x days ^ exponent, no cap (brief 7). */
    public static double multiplier(int streakDays, GameConfig.Streak cfg) {
        if (streakDays <= 0) {
            return 1.0;
        }
        return 1.0 + cfg.factor() * Math.pow(streakDays, cfg.exponent());
    }

    private static int earnFreeze(int freezes, int totalCounted, GameConfig.Streak cfg) {
        if (totalCounted % cfg.freezeEveryCountedDays() == 0) {
            return Math.min(cfg.maxFreezes(), freezes + 1);
        }
        return freezes;
    }
}
