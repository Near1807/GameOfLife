package gamelife.fight;

import gamelife.config.GameConfig;

/**
 * The numbers of the losable boss fight (brief 9.1). Pure functions, no database: used by the
 * real fight, the shop forecasts and the pacing simulation, so all three always agree.
 */
public final class FightRules {
    private FightRules() {}

    /** Ascension makes bosses stronger: x (1 + scale x ascensions). */
    static double scale(int ascensions, GameConfig.Fight f) {
        return 1 + f.ascensionBossScale() * ascensions;
    }

    /** Boss k HP = base x growth^(k-1), whole HP. */
    public static double bossMaxHp(int k, int ascensions, GameConfig.Fight f) {
        return Math.round(f.bossBaseHp() * Math.pow(f.bossHpGrowth(), k - 1) * scale(ascensions, f));
    }

    /** Boss k attack per day = base + perBoss x (k-1). */
    public static double bossAtk(int k, int ascensions, GameConfig.Fight f) {
        return round2((f.bossBaseAtk() + f.bossAtkPerBoss() * (k - 1)) * scale(ascensions, f));
    }

    /** Hero max HP = base + perVitality x Vitality level + armour HP + item HP. */
    public static double heroMaxHp(int vitalityLevel, int armorLevel, double itemHp, GameConfig cfg) {
        return cfg.fight().heroBaseHp() + cfg.fight().heroHpPerVitalityLevel() * vitalityLevel
                + cfg.armor().hpPerLevel() * armorLevel + itemHp;
    }

    /** Damage reduction = min(cap, perLevel x armour level + item reduction). */
    public static double armorReduction(int armorLevel, double itemReduction, GameConfig.Armor a) {
        return Math.min(a.maxReduction(), a.reductionPerLevel() * armorLevel + itemReduction);
    }

    /**
     * Shield of a day = min(1, XP of the day / daily goal + charm bonus). A frozen, rest or paused
     * day is fully shielded.
     */
    public static double shield(double dayXp, double dailyGoal, double charmBonus, boolean protectedDay) {
        if (protectedDay) {
            return 1.0;
        }
        return Math.max(0, Math.min(1.0, dayXp / dailyGoal + charmBonus));
    }

    /** Damage taken = ATK x (1 - shield) x (1 - armour reduction). */
    public static double attackDamage(double atk, double shield, double reduction) {
        return round2(atk * (1 - shield) * (1 - reduction));
    }

    public static double weaponCost(int level, GameConfig.Weapon w) {
        return Math.round(w.baseCost() * Math.pow(w.costGrowth(), level));
    }

    public static double armorCost(int level, GameConfig.Armor a) {
        return Math.round(a.baseCost() * Math.pow(a.costGrowth(), level));
    }

    public static double weaponMultiplier(int level, GameConfig.Weapon w) {
        return Math.pow(w.bonusPerLevel(), level);
    }

    static double round2(double v) {
        return Math.round(v * 100.0) / 100.0;
    }
}
