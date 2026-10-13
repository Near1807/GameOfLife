package gamelife.game;

import gamelife.config.GameConfig;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * The tower-defense simulation (v4 brief 3): a pure, deterministic function
 *
 *     simulate(map, build, stats, wave) -> result + replay
 *
 * Fixed time step of 1/60 s, no random numbers, sorted loops: the same inputs always give the
 * same result, to the last digit. The rules are the prototype's (gamelife-defense-prototype.html,
 * step() and hit()). The client never simulates: it only plays back the replay.
 *
 * One step, in order: spawn enemies whose time has come; for each enemy (newest first) apply burn
 * and poison, count down slow, move, remove the dead (spreading poison) and the leaked (losing
 * lives); then every attack tower, in spot order, counts down its cooldown and fires at the enemy
 * furthest along the path; the wave is lost at 0 lives and won when nothing is left.
 */
public final class Simulator {
    public static final double DT = 1.0 / 60;

    private Simulator() {}

    /**
     * enemies: one row per enemy on the map [id, x, y, hp percent, flags (1 slowed, 2 burning, 4 poisoned)].
     * shots: [spot, x, y, area radius] (radius 0 = a beam to the target).
     * chains: lightning jumps [x1, y1, x2, y2, spot]. pops: deaths [x, y, enemy id].
     */
    public record Frame(double t, int lives, List<int[]> enemies, List<int[]> shots, List<int[]> chains,
                        List<int[]> pops) {}

    /** types[id] is the enemy type of enemy id. Frames are sampled fps times per second. */
    public record Replay(int fps, List<String> types, List<Frame> frames) {}

    /** damage: damage dealt per spot (hits, burn and poison), for the tuning report. */
    public record Result(int wave, boolean won, int lives, int livesMax, int enemies, int killed, int leaked,
                         double seconds, Map<Integer, Double> damage, Replay replay) {}

    /** One spawn of the wave's queue. */
    public record Spawn(double t, String type, double hp) {}

    /** The prototype's makeWave(n): groups one after the other, groupPause seconds apart. */
    public static List<Spawn> makeWave(int wave, GameCatalog cat, GameConfig.Game g) {
        List<Spawn> q = new ArrayList<>();
        double hp = g.enemyBaseHp() * Math.pow(g.enemyHpGrowth(), wave - 1);
        double t = 0;
        for (GameCatalog.WaveGroup grp : cat.groups()) {
            if (!grp.appearsIn(wave)) {
                continue;
            }
            for (int i = 0; i < grp.count(wave); i++) {
                q.add(new Spawn(t, grp.type(), hp * cat.enemies().get(grp.type()).hp()));
                t += grp.gap();
            }
            t += cat.groupPause();
        }
        return q;
    }

    /** One enemy during the wave. */
    private static final class Enemy {
        final int id;
        final GameCatalog.EnemyDef def;
        final double max;
        double hp, d, slow, slowT, burn, burnT, pois, poisT;
        boolean spread;
        int burnSrc = -1, poisSrc = -1;
        double x, y; // position, updated after every move

        Enemy(int id, GameCatalog.EnemyDef def, double hp) {
            this.id = id;
            this.def = def;
            this.hp = hp;
            this.max = hp;
        }
    }

    /** One attack tower during the wave, with its effective numbers. */
    private static final class Gun {
        final int spot;
        final double x, y;
        final TowerStats s;
        double cd;

        Gun(int spot, double[] p, TowerStats s) {
            this.spot = spot;
            this.x = p[0];
            this.y = p[1];
            this.s = s;
        }
    }

    public static Result simulate(GameCatalog.MapDef map, Build build, Map<String, Integer> stats, int wave,
                                  GameCatalog cat, GameConfig.Game g, boolean withReplay) {
        return new Run(map, build, stats, wave, cat, g, withReplay).run();
    }

    /** The mutable state of one run. Private: the only entry point is simulate(). */
    private static final class Run {
        final GameCatalog cat;
        final GameConfig.Game g;
        final int wave;
        final PathGeometry path;
        final List<Spawn> queue;
        final List<Gun> guns = new ArrayList<>();
        final List<Enemy> enemies = new ArrayList<>();
        final Map<Integer, Double> damage = new TreeMap<>();
        final boolean withReplay;
        final int livesMax;
        final int sampleEvery;
        int lives, next, killed, leaked, nextId;
        double t;
        // replay
        final List<String> types = new ArrayList<>();
        final List<Frame> frames = new ArrayList<>();
        List<int[]> shots = new ArrayList<>(), chains = new ArrayList<>(), pops = new ArrayList<>();

        Run(GameCatalog.MapDef map, Build build, Map<String, Integer> stats, int wave, GameCatalog cat,
            GameConfig.Game g, boolean withReplay) {
            this.cat = cat;
            this.g = g;
            this.wave = wave;
            this.withReplay = withReplay;
            this.path = new PathGeometry(map.path());
            this.queue = makeWave(wave, cat, g);
            this.livesMax = GameMath.livesMax(stats, g);
            this.lives = livesMax;
            this.sampleEvery = Math.max(1, (int) Math.round(1 / DT / Math.max(1, g.replayFps())));
            for (var e : build.towers().entrySet()) {
                if ("attack".equals(cat.tower(e.getValue().type()).role())) {
                    guns.add(new Gun(e.getKey(), map.spots().get(e.getKey()),
                            GameMath.effective(e.getKey(), build, cat, map, stats, g)));
                }
            }
        }

        Result run() {
            int steps = 0;
            int maxSteps = (int) Math.ceil(g.maxWaveSeconds() / DT);
            Boolean won = null;
            while (won == null) {
                won = step();
                steps++;
                if (withReplay && (steps % sampleEvery == 0 || won != null)) {
                    sample();
                }
                if (won == null && steps >= maxSteps) {
                    won = false; // safety net: a wave that never ends counts as lost
                }
            }
            return new Result(wave, won, Math.max(0, lives), livesMax, queue.size(), killed, leaked,
                    Math.round(t * 100) / 100.0, damage,
                    withReplay ? new Replay(Math.max(1, g.replayFps()), types, frames) : null);
        }

        /** One fixed step. Returns true (won), false (lost) or null (still running). */
        Boolean step() {
            t += DT;
            while (next < queue.size() && queue.get(next).t() <= t) {
                Spawn s = queue.get(next++);
                Enemy e = new Enemy(nextId++, cat.enemies().get(s.type()), s.hp());
                types.add(s.type());
                place(e);
                enemies.add(e);
            }
            for (int i = enemies.size() - 1; i >= 0; i--) {
                Enemy e = enemies.get(i);
                if (e.burnT > 0) {
                    hurt(e, e.burn * DT, e.burnSrc);
                    e.burnT -= DT;
                }
                if (e.poisT > 0) {
                    hurt(e, e.pois * DT, e.poisSrc);
                    e.poisT -= DT;
                }
                if (e.slowT > 0) {
                    e.slowT -= DT;
                } else {
                    e.slow = 0;
                }
                e.d += g.enemySpeed() * e.def.speed() * (1 - e.slow) * DT;
                place(e);
                if (e.hp <= 0) {
                    die(e);
                    enemies.remove(i);
                    continue;
                }
                if (e.d >= path.total) {
                    lives -= e.def.leak();
                    leaked++;
                    enemies.remove(i);
                }
            }
            for (Gun gun : guns) {
                gun.cd -= DT;
                if (gun.cd > 0) {
                    continue;
                }
                List<Enemy> cand = new ArrayList<>();
                for (Enemy e : enemies) {
                    // enemies already at 0 HP are left alone (they are removed on the next step)
                    if (e.hp > 0 && (gun.s.air || !e.def.air())
                            && Math.hypot(e.x - gun.x, e.y - gun.y) <= gun.s.range) {
                        cand.add(e);
                    }
                }
                if (cand.isEmpty()) {
                    continue;
                }
                // furthest along the path first; ties by the older enemy, so the order is fixed
                cand.sort(Comparator.comparingDouble((Enemy e) -> -e.d).thenComparingInt(e -> e.id));
                gun.cd = 1 / gun.s.rate;
                for (int m = 0; m < Math.min((int) gun.s.multi, cand.size()); m++) {
                    hit(gun, cand.get(m));
                }
            }
            if (lives <= 0) {
                return false;
            }
            if (next >= queue.size() && enemies.isEmpty()) {
                return true;
            }
            return null;
        }

        void place(Enemy e) {
            double[] p = path.at(e.d);
            e.x = p[0];
            e.y = p[1];
        }

        void hurt(Enemy e, double amount, int spot) {
            e.hp -= amount;
            if (spot >= 0) {
                damage.merge(spot, amount, Double::sum);
            }
        }

        /** Damage after resistance: dmg x (1 - resist x (1 - pierce)). */
        static double dealt(Enemy e, TowerStats s, double dmg) {
            double r = (s.phys ? e.def.armor() : e.def.ward()) * (1 - s.pierce);
            return dmg * (1 - r);
        }

        void die(Enemy e) {
            killed++;
            if (withReplay) {
                pops.add(new int[]{(int) Math.round(e.x), (int) Math.round(e.y), e.id});
            }
            if (e.poisT > 0 && e.spread) {
                for (Enemy o : enemies) {
                    if (o != e && Math.hypot(o.x - e.x, o.y - e.y) <= g.poisonSpreadRange()) {
                        o.pois = Math.max(o.pois, e.pois);
                        o.poisT = 4;
                        o.spread = true;
                        o.poisSrc = e.poisSrc;
                    }
                }
            }
        }

        void hit(Gun gun, Enemy e) {
            TowerStats s = gun.s;
            List<Enemy> targets = new ArrayList<>();
            if (s.rad > 0) {
                for (Enemy o : enemies) {
                    if (o.hp > 0 && (s.air || !o.def.air()) && Math.hypot(o.x - e.x, o.y - e.y) <= s.rad) {
                        targets.add(o);
                    }
                }
            } else {
                targets.add(e);
            }
            for (Enemy o : targets) {
                hurt(o, dealt(o, s, s.dmg), gun.spot);
                if (s.slow > 0) {
                    o.slow = Math.max(o.slow, s.slow);
                    o.slowT = Math.max(o.slowT, s.slowT);
                }
                if (s.burn > 0) {
                    o.burn = Math.max(o.burn, s.burn);
                    o.burnT = 3;
                    o.burnSrc = gun.spot;
                }
                if (s.pois > 0) {
                    o.pois = Math.max(o.pois, s.pois);
                    o.poisT = 4;
                    o.spread = s.spread;
                    o.poisSrc = gun.spot;
                    if (s.pslow > 0) {
                        o.slow = Math.max(o.slow, s.pslow);
                        o.slowT = Math.max(o.slowT, 1.2);
                    }
                }
            }
            if (s.chain > 0) {
                Enemy cur = e;
                List<Enemy> done = new ArrayList<>(List.of(e));
                for (int c = 0; c < (int) s.chain; c++) {
                    Enemy nb = null;
                    double nd = g.chainRange();
                    for (Enemy o : enemies) {
                        if (done.contains(o) || o.hp <= 0 || (!s.air && o.def.air())) {
                            continue;
                        }
                        double dd = Math.hypot(o.x - cur.x, o.y - cur.y);
                        if (dd < nd) {
                            nd = dd;
                            nb = o;
                        }
                    }
                    if (nb == null) {
                        break;
                    }
                    hurt(nb, dealt(nb, s, s.dmg * g.chainDamage()), gun.spot);
                    if (withReplay) {
                        chains.add(new int[]{(int) Math.round(cur.x), (int) Math.round(cur.y),
                                (int) Math.round(nb.x), (int) Math.round(nb.y), gun.spot});
                    }
                    done.add(nb);
                    cur = nb;
                }
            }
            if (withReplay) {
                shots.add(new int[]{gun.spot, (int) Math.round(e.x), (int) Math.round(e.y), (int) Math.round(s.rad)});
            }
        }

        void sample() {
            List<int[]> rows = new ArrayList<>();
            for (Enemy e : enemies) {
                int flags = (e.slowT > 0 ? 1 : 0) | (e.burnT > 0 ? 2 : 0) | (e.poisT > 0 ? 4 : 0);
                rows.add(new int[]{e.id, (int) Math.round(e.x), (int) Math.round(e.y),
                        (int) Math.max(0, Math.ceil(100 * e.hp / e.max)), flags});
            }
            frames.add(new Frame(Math.round(t * 100) / 100.0, Math.max(0, lives), rows, shots, chains, pops));
            shots = new ArrayList<>();
            chains = new ArrayList<>();
            pops = new ArrayList<>();
        }
    }

    /** The path as segments, to turn "distance travelled" into a position (prototype posAt). */
    static final class PathGeometry {
        final double[][] a, b;
        final double[] start, len;
        final double total;

        PathGeometry(List<double[]> pts) {
            int n = pts.size() - 1;
            a = new double[n][];
            b = new double[n][];
            start = new double[n];
            len = new double[n];
            double sum = 0;
            for (int i = 0; i < n; i++) {
                a[i] = pts.get(i);
                b[i] = pts.get(i + 1);
                len[i] = Math.hypot(b[i][0] - a[i][0], b[i][1] - a[i][1]);
                start[i] = sum;
                sum += len[i];
            }
            total = sum;
        }

        double[] at(double d) {
            for (int i = 0; i < a.length; i++) {
                if (d <= start[i] + len[i] || i == a.length - 1) {
                    double f = Math.min(1, Math.max(0, (d - start[i]) / len[i]));
                    return new double[]{a[i][0] + (b[i][0] - a[i][0]) * f, a[i][1] + (b[i][1] - a[i][1]) * f};
                }
            }
            return a[0];
        }
    }
}
