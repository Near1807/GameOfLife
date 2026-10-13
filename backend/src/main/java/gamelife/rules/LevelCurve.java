package gamelife.rules;

import gamelife.config.GameConfig;

import java.math.BigDecimal;

/**
 * Stat levels (brief 8). Every stat starts at level 1. Going from level L to L+1 needs
 * base x growth ^ (L - 1) stat XP (100, 115, 132.25, ... with the shipped config).
 * Pure function, no database.
 */
public final class LevelCurve {
    private LevelCurve() {}

    /**
     * @param level       current level (1 or more)
     * @param xpIntoLevel stat XP earned since reaching this level
     * @param xpForNext   stat XP this level needs in total to reach the next one
     * @param totalXp     all stat XP of the stat
     */
    public record Level(int level, double xpIntoLevel, double xpForNext, double totalXp) {
        public double progress() {
            return xpForNext <= 0 ? 0 : xpIntoLevel / xpForNext;
        }
    }

    /** XP needed to go from level L to L+1. */
    public static double xpForLevel(int level, GameConfig.StatLevels cfg) {
        return cfg.base() * Math.pow(cfg.growth(), level - 1);
    }

    /** Total stat XP needed to reach a level from zero (level 1 needs 0). */
    public static double totalXpToReach(int level, GameConfig.StatLevels cfg) {
        double total = 0;
        for (int l = 1; l < level; l++) {
            total += xpForLevel(l, cfg);
        }
        return total;
    }

    public static Level of(BigDecimal statXp, GameConfig.StatLevels cfg) {
        double total = Math.max(0, statXp.doubleValue());
        double left = total;
        int level = 1;
        // small epsilon so that exactly 100.00 XP reaches level 2 despite floating point
        while (left + 1e-9 >= xpForLevel(level, cfg)) {
            left -= xpForLevel(level, cfg);
            level++;
        }
        return new Level(level, Math.max(0, left), xpForLevel(level, cfg), total);
    }
}
