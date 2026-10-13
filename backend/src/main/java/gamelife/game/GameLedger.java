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
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

/**
 * The game state is never stored: it is derived from the ledger (v4 brief 3). Pure functions.
 *
 *   SPOT_UNLOCKED   meta {map, spot, price}          opens one spot of a map
 *   TOWER_BUILT     meta {map, spot, n, tower, price} n = how many towers this spot has had
 *   TOWER_UPGRADED  meta {map, spot, n, path, tier, price}
 *   TOWER_SOLD      meta {map, spot, n, refund}      the refund is the tower's "paid"
 *   MAP_CHOSEN      meta {map}                       the map the player plays on from now
 *   WAVE_CLEARED / WAVE_FAILED  meta {wave, attempt, at, manual, map, inputs, towers, stats, lives, ...}
 *
 * Maps (chapters): the player plays on one map at a time (the first map until a MAP_CHOSEN). The
 * NUMBER of open spots carries over: on any map, open = the spots bought on that map, plus its first
 * spots in file order until there are freeSpots + all spots bought anywhere. Events written before
 * maps existed have no "map" in their meta: they belong to the first map.
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
     * map = the map played on. build = its open spots and towers. wave = the wave to attempt next (it
     * carries over between maps). builds = towers built so far per spot of this map (for the keys).
     * spotsBought = spots unlocked with currency, on all maps. mapChoices = MAP_CHOSEN events so far.
     */
    public record State(GameCatalog.MapDef map, Build build, int wave, int spotsBought, Map<Integer, Integer> builds,
                        List<Attempt> attempts, int mapChoices) {
        public Attempt lastAttempt() {
            return attempts.isEmpty() ? null : attempts.get(attempts.size() - 1);
        }

        public int furthestCleared() {
            return wave - 1;
        }
    }

    public static State derive(List<LedgerEvent> events, GameCatalog cat) {
        Folder f = new Folder(cat);
        events.forEach(f::apply);
        return f.state();
    }

    /** Applies events one by one; also used to follow the build through time. */
    static final class Folder {
        final GameCatalog cat;
        GameCatalog.MapDef map;
        final Map<String, Set<Integer>> bought = new HashMap<>();   // map id -> spots bought there
        final Map<Integer, Build.Tower> towers = new TreeMap<>();
        final Map<String, Integer> builds = new HashMap<>();         // "map:spot" -> towers built
        final List<Attempt> attempts = new ArrayList<>();
        int wave = 1, spotsBought, mapChoices;
        Build build;

        Folder(GameCatalog cat) {
            this.cat = cat;
            this.map = cat.firstMap();
            this.build = makeBuild();
        }

        /** Returns true when the event changed the build or the map. */
        boolean apply(LedgerEvent e) {
            switch (e.type()) {
                case SPOT_UNLOCKED -> {
                    Map<String, Object> m = meta(e);
                    bought.computeIfAbsent(mapOf(m), k -> new TreeSet<>()).add(intOf(m, "spot"));
                    spotsBought++;
                }
                case TOWER_BUILT -> {
                    Map<String, Object> m = meta(e);
                    int spot = intOf(m, "spot");
                    towers.put(spot, new Build.Tower((String) m.get("tower"), 0, 0, -e.currencyDelta().doubleValue()));
                    builds.put(mapOf(m) + ":" + spot, intOf(m, "n"));
                }
                case TOWER_UPGRADED -> {
                    Map<String, Object> m = meta(e);
                    int spot = intOf(m, "spot");
                    Build.Tower t = towers.get(spot);
                    if (t != null) {
                        towers.put(spot, t.upgraded(intOf(m, "path"), -e.currencyDelta().doubleValue()));
                    }
                }
                case TOWER_SOLD -> towers.remove(intOf(meta(e), "spot"));
                case MAP_CHOSEN -> {
                    GameCatalog.MapDef next = cat.map((String) meta(e).get("map"));
                    if (next != null) {
                        map = next;
                    }
                    towers.clear(); // the towers were sold just before the move
                    mapChoices++;
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
            build = makeBuild();
            return true;
        }

        /** The open spots of the current map (see the class comment) and its towers. */
        Build makeBuild() {
            Set<Integer> open = new TreeSet<>(bought.getOrDefault(map.id(), Set.of()));
            int want = Math.min(map.spots().size(), map.freeSpots() + spotsBought);
            for (int i = 0; i < map.spots().size() && open.size() < want; i++) {
                open.add(i);
            }
            return new Build(open, towers);
        }

        String mapOf(Map<String, Object> m) {
            Object id = m.get("map");
            return id == null ? cat.firstMap().id() : id.toString();
        }

        State state() {
            Map<Integer, Integer> here = new TreeMap<>();
            builds.forEach((k, n) -> {
                if (k.startsWith(map.id() + ":")) {
                    here.put(Integer.parseInt(k.substring(map.id().length() + 1)), n);
                }
            });
            return new State(map, build, wave, spotsBought, here, List.copyOf(attempts), mapChoices);
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
     * The map, build and stat levels through time: one snapshot after every event that changed
     * any of them. Used by the catch-up to see the game exactly as it was at each scheduled slot.
     */
    public record Snapshot(Instant at, GameCatalog.MapDef map, Build build, Map<String, Integer> stats) {}

    public static List<Snapshot> timeline(List<LedgerEvent> events, GameCatalog cat, GameConfig config) {
        List<Snapshot> out = new ArrayList<>();
        Folder f = new Folder(cat);
        Map<String, BigDecimal> totals = new HashMap<>();
        Map<String, Integer> levels = statLevels(totals, config);
        out.add(new Snapshot(Instant.EPOCH, f.map, f.build, levels));
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
                out.add(new Snapshot(e.occurredAt(), f.map, f.build, levels));
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
