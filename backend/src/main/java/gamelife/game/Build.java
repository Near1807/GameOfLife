package gamelife.game;

import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

/**
 * What stands on the map: the open spots and the tower on each spot (v4 brief 2).
 * Spots are numbered from 0 in the order of the map file. Sorted collections, so every loop over
 * a build runs in the same order (the simulation must be deterministic).
 */
public record Build(Set<Integer> openSpots, Map<Integer, Tower> towers) {

    /**
     * One tower. a and b are the tiers bought on path A and path B (one of them is always 0:
     * the first upgrade of a path locks the other). paid = everything paid for it, in currency,
     * at the prices actually charged; a sale refunds exactly this.
     */
    public record Tower(String type, int a, int b, double paid) {
        public Tower upgraded(int path, double price) {
            return new Tower(type, path == 0 ? a + 1 : a, path == 1 ? b + 1 : b, paid + price);
        }

        /** The path chosen so far: 0 (A), 1 (B) or -1 (none yet). */
        public int chosenPath() {
            return a > 0 ? 0 : b > 0 ? 1 : -1;
        }

        public int tier(int path) {
            return path == 0 ? a : b;
        }
    }

    public Build {
        openSpots = Collections.unmodifiableSet(new TreeSet<>(openSpots));
        towers = Collections.unmodifiableMap(new TreeMap<>(towers));
    }

    /** A map with only its free spots open and nothing built. */
    public static Build empty(int freeSpots) {
        Set<Integer> open = new TreeSet<>();
        for (int i = 0; i < freeSpots; i++) {
            open.add(i);
        }
        return new Build(open, Map.of());
    }

    public Build withTower(int spot, Tower tower) {
        Map<Integer, Tower> t = new TreeMap<>(towers);
        if (tower == null) {
            t.remove(spot);
        } else {
            t.put(spot, tower);
        }
        return new Build(openSpots, t);
    }

    public Build withOpenSpot(int spot) {
        Set<Integer> open = new TreeSet<>(openSpots);
        open.add(spot);
        return new Build(open, towers);
    }

    /** Text that changes whenever the build changes in a way the simulation can see. */
    public String fingerprint() {
        StringBuilder sb = new StringBuilder();
        towers.forEach((spot, t) -> sb.append(spot).append(':').append(t.type()).append(':')
                .append(t.a()).append(':').append(t.b()).append(';'));
        return sb.toString();
    }
}
