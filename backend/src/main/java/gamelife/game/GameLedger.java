package gamelife.game;

import gamelife.common.Json;
import gamelife.common.Money;
import gamelife.config.GameConfig;
import gamelife.ledger.EventType;
import gamelife.ledger.LedgerEvent;
import gamelife.rules.LevelCurve;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * The game state is never stored: it is derived from the ledger (v4 brief 3). Pure functions.
 *
 *   SPOT_UNLOCKED   meta {spot, price}               opens a spot
 *   TOWER_BUILT     meta {spot, n, tower, price}     n = how many towers this spot has had
 *   TOWER_UPGRADED  meta {spot, n, path, tier, price}
 *   TOWER_SOLD      meta {spot, n, refund}           the refund is the tower's "paid"
 *   WAVE_CLEARED / WAVE_FAILED  meta {wave, attempt, at, manual, inputs, towers, stats, lives, ...}
 */
public final class GameLedger {
    private GameLedger() {}

    public static final List<EventType> BUILD_TYPES = List.of(EventType.SPOT_UNLOCKED, EventType.TOWER_BUILT,
            EventType.TOWER_UPGRADED, EventType.TOWER_SOLD);

    /**
     * One wave attempt. at = the moment it stands for: its schedule slot for an automatic attempt
     * (written later by the catch-up), the real time for "Run now".
     */
    public record Attempt(String key, int wave, int attempt, boolean won, boolean manual, Instant at, String inputs,
                          Map<String, Object> meta) {}

    /**
     * wave = the wave to attempt next. builds = towers built so far per spot (for the keys).
     * spotsBought = spots unlocked with currency.
     */
    public record State(Build build, int wave, int spotsBought, Map<Integer, Integer> builds, List<Attempt> attempts) {
        public Attempt lastAttempt() {
            return attempts.isEmpty() ? null : attempts.get(attempts.size() - 1);
        }

        public int furthestCleared() {
            return wave - 1;
        }
    }

    public static State derive(List<LedgerEvent> events, int freeSpots) {
        Folder f = new Folder(freeSpots);
        events.forEach(f::apply);
        return f.state();
    }

    /** Applies events one by one; also used to follow the build through time. */
    static final class Folder {
        Build build;
        int wave = 1, spotsBought;
        final Map<Integer, Integer> builds = new TreeMap<>();
        final List<Attempt> attempts = new ArrayList<>();

        Folder(int freeSpots) {
            build = Build.empty(freeSpots);
        }

        /** Returns true when the event changed the build. */
        boolean apply(LedgerEvent e) {
            switch (e.type()) {
                case SPOT_UNLOCKED -> {
                    build = build.withOpenSpot(intOf(meta(e), "spot"));
                    spotsBought++;
                    return true;
                }
                case TOWER_BUILT -> {
                    Map<String, Object> m = meta(e);
                    int spot = intOf(m, "spot");
                    build = build.withTower(spot, new Build.Tower((String) m.get("tower"), 0, 0,
                            -e.currencyDelta().doubleValue()));
                    builds.put(spot, intOf(m, "n"));
                    return true;
                }
                case TOWER_UPGRADED -> {
                    Map<String, Object> m = meta(e);
                    int spot = intOf(m, "spot");
                    Build.Tower t = build.towers().get(spot);
                    if (t != null) {
                        build = build.withTower(spot, t.upgraded(intOf(m, "path"), -e.currencyDelta().doubleValue()));
                    }
                    return true;
                }
                case TOWER_SOLD -> {
                    build = build.withTower(intOf(meta(e), "spot"), null);
                    return true;
                }
                case WAVE_CLEARED, WAVE_FAILED -> {
                    Map<String, Object> m = meta(e);
                    boolean won = e.type() == EventType.WAVE_CLEARED;
                    attempts.add(new Attempt(e.key(), intOf(m, "wave"), intOf(m, "attempt"), won,
                            Boolean.TRUE.equals(m.get("manual")), Instant.parse((String) m.get("at")),
                            (String) m.get("inputs"), m));
                    if (won) {
                        wave = intOf(m, "wave") + 1;
                    }
                    return false;
                }
                default -> {
                    return false;
                }
            }
        }

        State state() {
            return new State(build, wave, spotsBought, new TreeMap<>(builds), List.copyOf(attempts));
        }
    }

    /** Stat levels from stat XP totals (the hero's levels). */
    public static Map<String, Integer> statLevels(Map<String, BigDecimal> totals, GameConfig config) {
        Map<String, Integer> levels = new LinkedHashMap<>();
        for (GameConfig.Stat s : config.stats()) {
            levels.put(s.id(), LevelCurve.of(totals.getOrDefault(s.id(), Money.ZERO), config.statLevels()).level());
        }
        return levels;
    }

    /**
     * The build and stat levels through time: one snapshot after every event that changed
     * either. Used by the catch-up to see the game exactly as it was at each scheduled slot.
     */
    public record Snapshot(Instant at, Build build, Map<String, Integer> stats) {}

    public static List<Snapshot> timeline(List<LedgerEvent> events, int freeSpots, GameConfig config) {
        List<Snapshot> out = new ArrayList<>();
        Folder f = new Folder(freeSpots);
        Map<String, BigDecimal> totals = new HashMap<>();
        Map<String, Integer> levels = statLevels(totals, config);
        out.add(new Snapshot(Instant.EPOCH, f.build, levels));
        for (LedgerEvent e : events) {
            boolean changed = f.apply(e);
            if (!"{}".equals(e.statSplits())) {
                Json.toMap(e.statSplits()).forEach((stat, v) ->
                        totals.merge(stat, Money.parse(v.toString()), BigDecimal::add));
                Map<String, Integer> now = statLevels(totals, config);
                if (!now.equals(levels)) {
                    levels = now;
                    changed = true;
                }
            }
            if (changed) {
                out.add(new Snapshot(e.occurredAt(), f.build, levels));
            }
        }
        return out;
    }

    /** The last snapshot at or before t. */
    public static Snapshot at(List<Snapshot> timeline, Instant t) {
        Snapshot found = timeline.get(0);
        for (Snapshot s : timeline) {
            if (s.at().isAfter(t)) {
                break;
            }
            found = s;
        }
        return found;
    }

    static Map<String, Object> meta(LedgerEvent e) {
        return Json.toMap(e.meta());
    }

    static int intOf(Map<String, Object> m, String k) {
        return ((Number) m.get(k)).intValue();
    }
}
