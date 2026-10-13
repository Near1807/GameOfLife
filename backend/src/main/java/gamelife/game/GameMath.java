package gamelife.game;

import gamelife.config.GameConfig;

import java.util.Map;

/**
 * The game's formulas (v4 brief 2), all numbers from config "game" and content/towers.json.
 * Pure functions. stats maps a stat id (STRENGTH, ...) to its level; a stat's bonus uses its
 * level, as in the prototype.
 */
public final class GameMath {
    private GameMath() {}

    public static int level(Map<String, Integer> stats, String stat) {
        return stats.getOrDefault(stat, 1);
    }

    /** Lives at the start of a wave: base + floor(VIT / livesPerVitalityLevels). */
    public static int livesMax(Map<String, Integer> stats, GameConfig.Game g) {
        return g.baseLives() + level(stats, "VITALITY") / Math.max(1, g.livesPerVitalityLevels());
    }

    /**
     * A tower's effective numbers in a build: tiers, then every Banner whose range covers it,
     * then the stat bonuses (Strength/Intellect damage, Discipline rate, Spirit slow).
     */
    public static TowerStats effective(int spot, Build build, GameCatalog cat, GameCatalog.MapDef map,
                                       Map<String, Integer> stats, GameConfig.Game g) {
        Build.Tower t = build.towers().get(spot);
        GameCatalog.TowerDef def = cat.tower(t.type());
        TowerStats s = TowerStats.of(def, t);
        if ("attack".equals(def.role())) {
            double[] me = map.spots().get(spot);
            for (var e : build.towers().entrySet()) {
                GameCatalog.TowerDef od = cat.tower(e.getValue().type());
                if (!"support".equals(od.role())) {
                    continue;
                }
                TowerStats os = TowerStats.of(od, e.getValue());
                double[] op = map.spots().get(e.getKey());
                if (Math.hypot(op[0] - me[0], op[1] - me[1]) <= os.range) {
                    s.rate *= 1 + os.rbuff;
                    s.dmg *= 1 + os.dbuff;
                }
            }
        }
        s.dmg *= 1 + (s.phys ? g.strengthDamagePerLevel() * level(stats, "STRENGTH")
                : g.intellectDamagePerLevel() * level(stats, "INTELLECT"));
        s.rate *= 1 + g.disciplineRatePerLevel() * level(stats, "DISCIPLINE");
        if (s.slow > 0) {
            s.slow = Math.min(g.maxSlow(), s.slow + g.spiritSlowPerLevel() * level(stats, "SPIRIT"));
        }
        return s;
    }

    /** Income multiplier on real-action currency: (1 + sum of Market coin) x (1 + SOC bonus). */
    public static double incomeMultiplier(Build build, GameCatalog cat, Map<String, Integer> stats, GameConfig.Game g) {
        double coin = 0;
        for (Build.Tower t : build.towers().values()) {
            if ("income".equals(cat.tower(t.type()).role())) {
                coin += TowerStats.of(cat.tower(t.type()), t).coin;
            }
        }
        return (1 + coin) * (1 + g.socialIncomePerLevel() * level(stats, "SOCIAL"));
    }

    /** Build discount: min(cap, Bargain) + min(cap, CRE bonus), capped at maxDiscount. */
    public static double discount(Build build, GameCatalog cat, Map<String, Integer> stats, GameConfig.Game g) {
        double d = 0;
        for (Build.Tower t : build.towers().values()) {
            if ("income".equals(cat.tower(t.type()).role())) {
                d += TowerStats.of(cat.tower(t.type()), t).disc;
            }
        }
        d = Math.min(g.maxMarketDiscount(), d);
        d += Math.min(g.maxCreativityDiscount(), g.creativityDiscountPerLevel() * level(stats, "CREATIVITY"));
        return Math.min(g.maxDiscount(), d);
    }

    /** A build or upgrade price after the discount, rounded up to a whole coin (prototype). */
    public static int price(int listPrice, double discount) {
        // the small epsilon keeps 100 x (1 - 0.05) = 95.00000001 at 95
        return (int) Math.ceil(listPrice * (1 - discount) - 1e-9);
    }

    /** Price of the next spot: spotBaseCost x spotCostGrowth ^ n, n = spots already bought. */
    public static int spotCost(int boughtSoFar, GameConfig.Game g) {
        return (int) Math.round(g.spotBaseCost() * Math.pow(g.spotCostGrowth(), boughtSoFar));
    }

    /** True when the stats allow building this tower (some need a stat level). */
    public static boolean meetsRequirement(GameCatalog.TowerDef def, Map<String, Integer> stats) {
        return def.requires() == null || level(stats, def.requires().stat()) >= def.requires().level();
    }
}
