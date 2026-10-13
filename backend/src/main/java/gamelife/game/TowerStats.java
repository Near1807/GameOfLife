package gamelife.game;

import java.util.Map;

/**
 * The working numbers of one tower (the prototype's stat object). Built from the tower's base
 * stats, then each bought tier applies its changes in order.
 *
 * dmg, rate (shots/s), range, rad (area radius), slow (0..1), slowT (s), burn (/s), pierce (0..1),
 * multi (targets per shot), phys (physical or magic), air (can hit flyers), chain (jumps), pois (/s),
 * spread (poison spreads on death), pslow (poison slow), rbuff/dbuff (Banner aura), coin/disc (Market).
 */
public final class TowerStats {
    public double dmg, rate, range, rad, slow, slowT, burn, pierce, multi = 1, chain, pois, pslow, rbuff, dbuff, coin, disc;
    public boolean phys = true, air = true, spread;

    /** Base stats plus the tiers bought, without any aura or stat bonus. */
    public static TowerStats of(GameCatalog.TowerDef def, Build.Tower t) {
        TowerStats s = new TowerStats();
        def.base().forEach((k, v) -> s.apply(new GameCatalog.Change(k, "set", v)));
        for (int path = 0; path < def.paths().size(); path++) {
            var tiers = def.paths().get(path).tiers();
            for (int i = 0; i < t.tier(path) && i < tiers.size(); i++) {
                tiers.get(i).changes().forEach(s::apply);
            }
        }
        return s;
    }

    void apply(GameCatalog.Change c) {
        double v = switch (c.op()) {
            case "mul" -> get(c.stat()) * c.value();
            case "add" -> get(c.stat()) + c.value();
            default -> c.value();
        };
        set(c.stat(), v);
    }

    double get(String stat) {
        return switch (stat) {
            case "dmg" -> dmg;
            case "rate" -> rate;
            case "range" -> range;
            case "rad" -> rad;
            case "slow" -> slow;
            case "slowT" -> slowT;
            case "burn" -> burn;
            case "pierce" -> pierce;
            case "multi" -> multi;
            case "chain" -> chain;
            case "pois" -> pois;
            case "pslow" -> pslow;
            case "rbuff" -> rbuff;
            case "dbuff" -> dbuff;
            case "coin" -> coin;
            case "disc" -> disc;
            case "phys" -> phys ? 1 : 0;
            case "air" -> air ? 1 : 0;
            case "spread" -> spread ? 1 : 0;
            default -> throw new IllegalArgumentException("Unknown tower stat: " + stat);
        };
    }

    void set(String stat, double v) {
        switch (stat) {
            case "dmg" -> dmg = v;
            case "rate" -> rate = v;
            case "range" -> range = v;
            case "rad" -> rad = v;
            case "slow" -> slow = v;
            case "slowT" -> slowT = v;
            case "burn" -> burn = v;
            case "pierce" -> pierce = v;
            case "multi" -> multi = v;
            case "chain" -> chain = v;
            case "pois" -> pois = v;
            case "pslow" -> pslow = v;
            case "rbuff" -> rbuff = v;
            case "dbuff" -> dbuff = v;
            case "coin" -> coin = v;
            case "disc" -> disc = v;
            case "phys" -> phys = v != 0;
            case "air" -> air = v != 0;
            case "spread" -> spread = v != 0;
            default -> throw new IllegalArgumentException("Unknown tower stat: " + stat);
        }
    }

    /** For the UI: the numbers a player reads. */
    public Map<String, Object> summary() {
        return Map.ofEntries(Map.entry("dmg", r2(dmg)), Map.entry("rate", r2(rate)), Map.entry("range", r2(range)),
                Map.entry("rad", r2(rad)), Map.entry("slow", r2(slow)), Map.entry("burn", r2(burn)),
                Map.entry("pois", r2(pois)), Map.entry("chain", (int) chain), Map.entry("multi", (int) multi),
                Map.entry("pierce", r2(pierce)), Map.entry("rbuff", r2(rbuff)), Map.entry("dbuff", r2(dbuff)),
                Map.entry("coin", r2(coin)), Map.entry("disc", r2(disc)));
    }

    private static double r2(double v) {
        return Math.round(v * 100.0) / 100.0;
    }
}
