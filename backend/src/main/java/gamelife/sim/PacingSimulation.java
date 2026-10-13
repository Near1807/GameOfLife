package gamelife.sim;

import gamelife.config.GameConfig;
import gamelife.game.Build;
import gamelife.game.GameCatalog;
import gamelife.game.GameMath;
import gamelife.game.Simulator;
import gamelife.rules.LevelCurve;
import gamelife.rules.StreakCalculator;

import java.math.BigDecimal;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

/**
 * Pacing simulation (v4 brief 6): three players play the tower-defense map for a year with the real
 * rules (the server's Simulator, GameMath, streaks and stat levels from the config).
 *
 *   lazy     0 base XP on 30% of days (spread out), 100 on the others
 *   average  100 base XP every day
 *   strong   200 base XP every day
 *
 * Every day: the day's XP is paid (coins = XP x income), split equally over the seven stats; when a
 * newer map has opened, the player moves there (selling everything for a full refund, as the app
 * does); then the player spends greedily; then the day's 12 automatic attempts run, stopping when a
 * wave fails with nothing changed (stuck), exactly like the server.
 *
 * Greedy spending: while something is affordable, buy the option that most improves the current
 * wave per coin (each option is tried with the real simulation on that wave). A Market is bought
 * when it pays for itself within MARKET_PAYBACK_DAYS, at most one per five open spots. A spot is
 * bought when no affordable tower or tier helps and every open spot is taken.
 *
 * Run: npm run simulate   (or mvn -f backend/pom.xml compile exec:java)
 */
public final class PacingSimulation {
    private PacingSimulation() {}

    /** The profiles of the brief. */
    public enum Profile {
        LAZY, AVERAGE, STRONG;

        double baseXp(int day) {
            return switch (this) {
                case LAZY -> (day * 3) % 10 < 3 ? 0 : 100; // 3 days in 10, spread out
                case AVERAGE -> 100;
                case STRONG -> 200;
            };
        }
    }

    static final int ATTEMPTS_PER_DAY_FALLBACK = 12;
    static final int MARKET_PAYBACK_DAYS = 40;

    /**
     * What one profile reached. firstStuckDay: the first day that cleared no new wave (the day's
     * attempts all stopped on an unchanged failure). longestStall: the most days in a row without a new
     * wave. towers: the build at the end, counted by "type path tier".
     */
    public record Report(Profile profile, Map<Integer, Integer> waveOnDay, int firstStuckDay, int longestStall,
                         int lastPurchaseDay, int purchases, double coinsEarned, double incomeMultiplier,
                         Map<String, Integer> towers, int openSpots, int optionsLeft, String map,
                         Map<Integer, String> moves, int lastNewWaveDay) {}

    /** The state of one simulated player. */
    static final class Player {
        final Profile profile;
        final GameConfig cfg;
        final GameCatalog cat;
        GameCatalog.MapDef map;
        final Set<LocalDate> counted = new HashSet<>();
        final Map<String, Double> statXp = new TreeMap<>();
        final Set<Integer> boughtHere = new TreeSet<>();
        Build build;
        int wave = 1, spotsBought, purchases, lastPurchaseDay, moves;
        double coins, earned;
        String failedInputs; // inputs of the last failed attempt, null after a win

        Player(Profile p, GameConfig cfg, GameCatalog cat) {
            this.profile = p;
            this.cfg = cfg;
            this.cat = cat;
            this.map = cat.firstMap();
            this.build = Build.empty(map.freeSpots());
            cfg.stats().forEach(s -> statXp.put(s.id(), 0.0));
        }

        /** Open spots as in GameLedger: those bought here, then the first ones up to free + bought. */
        Set<Integer> openSpots() {
            Set<Integer> open = new TreeSet<>(boughtHere);
            int want = Math.min(map.spots().size(), map.freeSpots() + spotsBought);
            for (int i = 0; i < map.spots().size() && open.size() < want; i++) {
                open.add(i);
            }
            return open;
        }

        /** Moves to a newer map: every tower is sold for what it cost, the spot count carries over. */
        void moveTo(GameCatalog.MapDef next) {
            coins += build.towers().values().stream().mapToDouble(Build.Tower::paid).sum();
            map = next;
            boughtHere.clear();
            build = new Build(openSpots(), Map.of());
            failedInputs = null;
            moves++;
        }

        Map<String, Integer> stats() {
            Map<String, Integer> levels = new LinkedHashMap<>();
            statXp.forEach((k, v) -> levels.put(k, LevelCurve.of(BigDecimal.valueOf(v), cfg.statLevels()).level()));
            return levels;
        }

        double discount() {
            return GameMath.discount(build, cat, stats(), cfg.game());
        }

        String inputs() {
            return wave + "|" + map.id() + "|" + build.fingerprint() + "|" + stats();
        }
    }

    /** One possible purchase. spot: the spot it opens, or -1. */
    record Option(String label, int price, Build after, int spot) {
        boolean isSpot() {
            return spot >= 0;
        }
    }

    public static Report run(Profile profile, int days, GameConfig cfg, GameCatalog cat) {
        Player p = new Player(profile, cfg, cat);
        Map<Integer, Integer> waveOnDay = new TreeMap<>();
        int firstStuck = -1, stall = 0, longestStall = 0, lastNewWave = 0;
        Map<Integer, String> moves = new TreeMap<>();
        LocalDate start = LocalDate.of(2026, 1, 5);
        int attemptsPerDay = Math.min(cfg.game().maxAttemptsPerDay(),
                cfg.game().waveIntervalMinutes() > 0 ? 24 * 60 / cfg.game().waveIntervalMinutes() : ATTEMPTS_PER_DAY_FALLBACK);
        for (int day = 1; day <= days; day++) {
            LocalDate today = start.plusDays(day - 1);
            double base = profile.baseXp(day);
            if (base > 0) {
                p.counted.add(today);
                double streak = StreakCalculator.multiplier(
                        StreakCalculator.compute(p.counted, Set.of(), today, cfg.streak()).streakForPayment(), cfg.streak());
                double xp = base * streak;
                double income = GameMath.incomeMultiplier(p.build, cat, p.stats(), cfg.game());
                p.coins += xp * income;
                p.earned += xp * income;
                p.statXp.replaceAll((k, v) -> v + base / p.statXp.size());
            }
            GameCatalog.MapDef newest = cat.maps().stream().filter(m -> p.wave - 1 >= m.unlockAfterWave())
                    .reduce((a, b) -> b).orElse(p.map);
            if (!newest.id().equals(p.map.id()) && newest.spots().size() > p.map.spots().size()) {
                p.moveTo(newest);
                moves.put(day, newest.name());
            }
            spend(p, day, days);
            boolean stuck = false;
            for (int a = 0; a < attemptsPerDay; a++) {
                String in = p.inputs();
                if (in.equals(p.failedInputs)) {
                    stuck = true;
                    break;
                }
                Simulator.Result r = Simulator.simulate(p.map, p.build, p.stats(), p.wave, cat, cfg.game(), false);
                if (r.won()) {
                    p.wave++;
                    p.failedInputs = null;
                } else {
                    p.failedInputs = in;
                }
            }
            int before = waveOnDay.getOrDefault(day - 1, 0);
            waveOnDay.put(day, p.wave - 1);
            if (p.wave - 1 > before) {
                lastNewWave = day;
            }
            if (p.wave - 1 == before && stuck) {
                stall++;
                longestStall = Math.max(longestStall, stall);
                if (firstStuck < 0) firstStuck = day;
            } else {
                stall = 0;
            }
        }
        Map<String, Integer> towers = new TreeMap<>();
        p.build.towers().values().forEach(t -> {
            GameCatalog.TowerDef d = cat.tower(t.type());
            String path = t.chosenPath() < 0 ? "base" : d.paths().get(t.chosenPath()).name() + " " + t.tier(t.chosenPath());
            towers.merge(d.name() + " (" + path + ")", 1, Integer::sum);
        });
        return new Report(profile, waveOnDay, firstStuck, longestStall, p.lastPurchaseDay, p.purchases, round(p.earned),
                round(GameMath.incomeMultiplier(p.build, cat, p.stats(), cfg.game())), towers, p.build.openSpots().size(),
                options(p).size(), p.map.name(), moves, lastNewWave);
    }

    // ------------------------------------------------------------------ greedy spending

    static void spend(Player p, int day, int days) {
        for (int guard = 0; guard < 40; guard++) {
            List<Option> all = options(p);
            List<Option> affordable = all.stream().filter(o -> o.price() <= p.coins).toList();
            if (affordable.isEmpty()) {
                return;
            }
            Option market = bestMarket(p, affordable, days - day);
            Option pick = market != null ? market : bestForFight(p, affordable);
            if (pick == null) {
                return;
            }
            p.coins -= pick.price();
            p.build = pick.after();
            if (pick.isSpot()) {
                p.spotsBought++;
                p.boughtHere.add(pick.spot());
            }
            p.purchases++;
            p.lastPurchaseDay = day;
        }
    }

    /** Every purchase possible now: a spot, a tower on an empty open spot, the next tier of a tower. */
    static List<Option> options(Player p) {
        List<Option> out = new ArrayList<>();
        Map<String, Integer> stats = p.stats();
        double disc = p.discount();
        GameCatalog.MapDef map = p.map;
        // the next locked spot: the one covering most of the path
        List<Integer> byCover = spotsByCoverage(map);
        for (int s : byCover) {
            if (!p.build.openSpots().contains(s)) {
                out.add(new Option("spot " + s, GameMath.spotCost(p.spotsBought, p.cfg.game()), p.build.withOpenSpot(s), s));
                break;
            }
        }
        // new towers: each type, on the best empty open spot
        Integer empty = byCover.stream().filter(s -> p.build.openSpots().contains(s) && !p.build.towers().containsKey(s))
                .findFirst().orElse(null);
        if (empty != null) {
            for (GameCatalog.TowerDef d : p.cat.towers().values()) {
                if (GameMath.meetsRequirement(d, stats)) {
                    out.add(new Option("build " + d.id(), GameMath.price(d.cost(), disc),
                            p.build.withTower(empty, new Build.Tower(d.id(), 0, 0, 0)), -1));
                }
            }
        }
        // upgrades: towers of the same type, path and tier are the same option, so only the one on the
        // best spot is listed (this keeps the simulation fast on the big maps)
        Set<String> seen = new HashSet<>();
        for (int spot : byCover) {
            Build.Tower t = p.build.towers().get(spot);
            if (t == null || !seen.add(t.type() + ":" + t.a() + ":" + t.b())) continue;
            GameCatalog.TowerDef d = p.cat.tower(t.type());
            for (int path = 0; path < d.paths().size(); path++) {
                if (t.chosenPath() >= 0 && t.chosenPath() != path) continue;
                int tier = t.tier(path);
                if (tier >= d.paths().get(path).tiers().size()) continue;
                GameCatalog.Tier next = d.paths().get(path).tiers().get(tier);
                if (next.requires() != null && GameMath.level(stats, next.requires().stat()) < next.requires().level()) continue;
                int price = GameMath.price(next.cost(), disc);
                out.add(new Option("upgrade " + d.id() + " " + d.paths().get(path).name() + " " + (tier + 1), price,
                        p.build.withTower(spot, t.upgraded(path, price)), -1));
            }
        }
        return out;
    }

    /** A Market (Commerce) option that pays for itself in time, or null. */
    static Option bestMarket(Player p, List<Option> affordable, int daysLeft) {
        // Markets take spots from the defence: at most one per five open spots
        long markets = p.build.towers().values().stream().filter(t -> t.type().equals("income")).count();
        boolean roomForAnother = markets < Math.max(1, p.build.openSpots().size() / 5);
        double dailyCoins = Math.max(1, p.profile.baseXp(1) == 0 ? 70 : p.profile.baseXp(1) * 0.7 * 1.3);
        double incomeNow = GameMath.incomeMultiplier(p.build, p.cat, p.stats(), p.cfg.game());
        Option best = null;
        double bestPayback = Double.MAX_VALUE;
        for (Option o : affordable) {
            boolean marketOption = (o.label().equals("build income") && roomForAnother)
                    || o.label().startsWith("upgrade income Commerce");
            if (!marketOption) continue;
            double gain = GameMath.incomeMultiplier(o.after(), p.cat, p.stats(), p.cfg.game()) - incomeNow;
            double payback = o.price() / Math.max(1e-9, gain * dailyCoins / incomeNow);
            if (payback < bestPayback) {
                bestPayback = payback;
                best = o;
            }
        }
        return best != null && bestPayback <= MARKET_PAYBACK_DAYS && daysLeft > 2 * bestPayback ? best : null;
    }

    /** The option that most improves the current wave per coin, or null when none helps. */
    static Option bestForFight(Player p, List<Option> affordable) {
        double now = score(p, p.build);
        Option best = null;
        double bestValue = 0;
        for (Option o : affordable) {
            if (o.label().startsWith("build income") || o.label().startsWith("upgrade income")) continue;
            double value;
            if (o.isSpot()) {
                // a spot alone does not fight: value it as the best tower it would allow next
                value = 1e-6;
            } else {
                value = (score(p, o.after()) - now) / Math.max(1, o.price());
            }
            if (value > bestValue) {
                bestValue = value;
                best = o;
            }
        }
        // no tower option helps (or none fits): open a new spot if there is no empty one
        if (best == null || best.isSpot()) {
            boolean hasEmpty = p.build.openSpots().stream().anyMatch(s -> !p.build.towers().containsKey(s));
            Option spot = affordable.stream().filter(Option::isSpot).findFirst().orElse(null);
            return hasEmpty ? null : spot;
        }
        return best;
    }

    /** How well a build does on the current wave: damage share, plus lives, plus a win. */
    static double score(Player p, Build b) {
        Simulator.Result r = Simulator.simulate(p.map, b, p.stats(), p.wave, p.cat, p.cfg.game(), false);
        double total = Simulator.makeWave(p.wave, p.cat, p.cfg.game()).stream().mapToDouble(Simulator.Spawn::hp).sum();
        double dealt = r.damage().values().stream().mapToDouble(Double::doubleValue).sum();
        return Math.min(1, dealt / total) + 0.5 * r.lives() / r.livesMax() + (r.won() ? 1 : 0);
    }

    /** Spots ordered by how much of the path lies within 120 px (the best places first). */
    static List<Integer> spotsByCoverage(GameCatalog.MapDef map) {
        List<Integer> idx = new ArrayList<>();
        Map<Integer, Double> cover = new TreeMap<>();
        List<double[]> pts = map.path();
        for (int i = 0; i < map.spots().size(); i++) {
            double[] s = map.spots().get(i);
            double c = 0;
            for (int k = 0; k < pts.size() - 1; k++) {
                double[] a = pts.get(k), b = pts.get(k + 1);
                double len = Math.hypot(b[0] - a[0], b[1] - a[1]);
                for (double t = 0; t <= len; t += 5) {
                    double x = a[0] + (b[0] - a[0]) * t / len, y = a[1] + (b[1] - a[1]) * t / len;
                    if (Math.hypot(x - s[0], y - s[1]) <= 120) c += 5;
                }
            }
            cover.put(i, c);
            idx.add(i);
        }
        // free spots keep their place in front (they are already open)
        idx.sort(Comparator.comparing((Integer i) -> i >= map.freeSpots()).thenComparing(i -> -cover.get(i)));
        return idx;
    }

    // ------------------------------------------------------------------ tower dominance

    /** One line of the dominance table: a tower type and its full path (every tier), alone on the map. */
    public record Dominance(String tower, String path, int cost, int wavesAlone, int wavesThree) {}

    /**
     * Each tower and path with every tier bought: how far one copy (on the best spot) and three copies
     * (on the three best spots of the first map) get with every stat at level 12 (so tier 4 is allowed).
     * Shows whether one choice beats all others.
     */
    public static List<Dominance> dominance(GameConfig cfg, GameCatalog cat) {
        Map<String, Integer> stats = new LinkedHashMap<>();
        cfg.stats().forEach(s -> stats.put(s.id(), 12));
        GameCatalog.MapDef map = cat.firstMap();
        List<Integer> spots = spotsByCoverage(map);
        List<Dominance> out = new ArrayList<>();
        for (GameCatalog.TowerDef d : cat.towers().values()) {
            if (!"attack".equals(d.role())) continue;
            for (int path = 0; path < d.paths().size(); path++) {
                int tiers = d.paths().get(path).tiers().size();
                Build.Tower t = new Build.Tower(d.id(), path == 0 ? tiers : 0, path == 1 ? tiers : 0, 0);
                int cost = d.cost() + d.paths().get(path).tiers().stream().mapToInt(GameCatalog.Tier::cost).sum();
                Build one = Build.empty(15).withTower(spots.get(0), t);
                Build three = one.withTower(spots.get(1), t).withTower(spots.get(2), t);
                out.add(new Dominance(d.name(), d.paths().get(path).name(), cost, furthest(map, one, stats, cat, cfg),
                        furthest(map, three, stats, cat, cfg)));
            }
        }
        return out;
    }

    static int furthest(GameCatalog.MapDef map, Build b, Map<String, Integer> stats, GameCatalog cat, GameConfig cfg) {
        int w = 1;
        while (w < 200 && Simulator.simulate(map, b, stats, w, cat, cfg.game(), false).won()) {
            w++;
        }
        return w - 1;
    }

    private static double round(double v) {
        return Math.round(v * 100) / 100.0;
    }

    // ------------------------------------------------------------------ command line

    public static void main(String[] args) {
        // run from the repo root (npm run simulate) or from backend/
        String root = java.nio.file.Files.exists(Path.of("config/gamelife.json")) ? "" : "../";
        GameConfig cfg = GameConfig.load(Path.of(args.length > 0 ? args[0] : root + "config/gamelife.json"));
        GameCatalog cat = GameCatalog.load(Path.of(args.length > 1 ? args[1] : root + "content"));
        long t0 = System.currentTimeMillis();
        List<Report> reports = List.of(Profile.values()).parallelStream().map(p -> run(p, 365, cfg, cat)).toList();
        System.out.println();
        System.out.println("GameLife pacing simulation: one year, " + cat.maps().size() + " maps");
        System.out.println();
        System.out.println("| Profile | Waves day 30 | Waves day 100 | Waves day 200 | Waves day 300 | Waves day 365 | Last new wave | Longest stall | Moves | Purchases | Last purchase | Income x | Spots open | Options left |");
        System.out.println("| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |");
        for (Report r : reports) {
            StringBuilder moves = new StringBuilder();
            r.moves().forEach((d, m) -> moves.append(moves.isEmpty() ? "" : ", ").append(m).append(" day ").append(d));
            System.out.printf("| %s | %d | %d | %d | %d | %d | day %d | %d days | %s | %d | day %d | %.2f | %d | %d |%n",
                    r.profile().name().toLowerCase(), r.waveOnDay().get(30), r.waveOnDay().get(100),
                    r.waveOnDay().get(200), r.waveOnDay().get(300), r.waveOnDay().get(365), r.lastNewWaveDay(),
                    r.longestStall(), moves.isEmpty() ? "none" : moves, r.purchases(), r.lastPurchaseDay(),
                    r.incomeMultiplier(), r.openSpots(), r.optionsLeft());
        }
        Report lazy = reports.get(0), strong = reports.get(2);
        System.out.printf("%nstrong / lazy at day 365: %.2f%n", strong.waveOnDay().get(365) / (double) Math.max(1, lazy.waveOnDay().get(365)));
        System.out.println();
        for (Report r : reports) {
            System.out.println(r.profile().name().toLowerCase() + " build at day 365: " + r.towers());
        }
        System.out.println();
        System.out.println("Tower dominance (full path, every stat level 12, first map): furthest wave cleared");
        System.out.println();
        System.out.println("| Tower | Path | Cost | 1 tower | 3 towers |");
        System.out.println("| --- | --- | --- | --- | --- |");
        for (Dominance d : dominance(cfg, cat)) {
            System.out.printf("| %s | %s | %d | %d | %d |%n", d.tower(), d.path(), d.cost(), d.wavesAlone(), d.wavesThree());
        }
        System.out.printf("%n(%.1f s)%n", (System.currentTimeMillis() - t0) / 1000.0);
    }
}
