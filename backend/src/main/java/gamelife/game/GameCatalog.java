package gamelife.game;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

/**
 * The game's data (v4 brief 2): towers, enemies, wave groups and maps, read from
 * content/towers.json, content/waves.json and content/maps/*.json. Adding a tower, a path or a
 * map is a data change. Plain records, no Spring, so the simulation stays a pure function.
 */
public record GameCatalog(Map<String, TowerDef> towers, Map<String, EnemyDef> enemies, List<WaveGroup> groups,
                          double groupPause, List<MapDef> maps) {

    /** One change of a tier: "mul" multiplies the stat, "add" adds to it, "set" replaces it. */
    public record Change(String stat, String op, double value) {}

    /** requires: a stat level needed to buy this tier (tier 4), or null. */
    public record Tier(int cost, String description, List<Change> changes, Requirement requires) {}

    public record UpgradePath(String name, List<Tier> tiers) {}

    /** requires: a stat id and level needed to build, or null. role: attack, support or income. */
    public record TowerDef(String id, String name, String kind, String role, int cost, String color, String shape,
                           Requirement requires, Map<String, Double> base, List<UpgradePath> paths) {}

    public record Requirement(String stat, int level) {}

    public record EnemyDef(String id, String name, double hp, double speed, double armor, double ward, boolean air,
                           String color, int radius, int leak) {}

    /** count = base + floor(wave / per), per 0 = no growth. parity "odd"/"even", every N: optional filters. */
    public record WaveGroup(String type, int from, String parity, int every, int base, int per, double gap) {
        boolean appearsIn(int wave) {
            if (wave < from) {
                return false;
            }
            if ("odd".equals(parity) && wave % 2 == 0) {
                return false;
            }
            if ("even".equals(parity) && wave % 2 == 1) {
                return false;
            }
            return every <= 0 || wave % every == 0;
        }

        int count(int wave) {
            return base + (per > 0 ? wave / per : 0);
        }
    }

    public record MapDef(String id, String name, int width, int height, int freeSpots, int unlockAfterWave,
                         List<double[]> path, double[] base, List<double[]> spots) {}

    public TowerDef tower(String id) {
        return towers.get(id);
    }

    public MapDef firstMap() {
        return maps.get(0);
    }

    /** The map with this id, or the first map when the id is unknown (a deleted map file). */
    public MapDef map(String id) {
        return maps.stream().filter(m -> m.id().equals(id)).findFirst().orElse(maps.get(0));
    }

    // ------------------------------------------------------------------ loading

    private static final ObjectMapper JSON = new ObjectMapper()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    /** Reads the catalogue from a content folder. Throws with a clear message on a broken file. */
    public static GameCatalog load(Path contentDir) {
        try {
            return new GameCatalog(readTowers(contentDir.resolve("towers.json")),
                    readEnemies(contentDir.resolve("waves.json")),
                    readGroups(contentDir.resolve("waves.json")),
                    JSON.readTree(contentDir.resolve("waves.json").toFile()).path("groupPause").asDouble(1.4),
                    readMaps(contentDir.resolve("maps")));
        } catch (IOException | RuntimeException e) {
            throw new IllegalStateException("Cannot read the game data in " + contentDir.toAbsolutePath()
                    + ": " + e.getMessage(), e);
        }
    }

    private static Map<String, TowerDef> readTowers(Path file) throws IOException {
        Map<String, TowerDef> out = new LinkedHashMap<>();
        for (JsonNode t : JSON.readTree(file.toFile()).path("towers")) {
            Map<String, Double> base = new LinkedHashMap<>();
            t.path("base").fields().forEachRemaining(f -> base.put(f.getKey(), number(f.getValue())));
            List<UpgradePath> paths = new ArrayList<>();
            for (JsonNode p : t.path("paths")) {
                List<Tier> tiers = new ArrayList<>();
                for (JsonNode tier : p.path("tiers")) {
                    List<Change> changes = new ArrayList<>();
                    tier.path("set").fields().forEachRemaining(f -> {
                        String op = f.getValue().fieldNames().next();
                        changes.add(new Change(f.getKey(), op, number(f.getValue().get(op))));
                    });
                    JsonNode treq = tier.path("requires");
                    tiers.add(new Tier(tier.path("cost").asInt(), tier.path("description").asText(), changes,
                            treq.isObject() ? new Requirement(treq.path("stat").asText(), treq.path("level").asInt()) : null));
                }
                paths.add(new UpgradePath(p.path("name").asText(), tiers));
            }
            JsonNode req = t.path("requires");
            String id = t.path("id").asText();
            out.put(id, new TowerDef(id, t.path("name").asText(), t.path("kind").asText(),
                    t.path("role").asText("attack"), t.path("cost").asInt(), t.path("color").asText("#ffffff"),
                    t.path("shape").asText("dot"),
                    req.isObject() ? new Requirement(req.path("stat").asText(), req.path("level").asInt()) : null,
                    base, paths));
        }
        return out;
    }

    /** true/false count as 1/0 so a tier can "set" a flag like spread. */
    private static double number(JsonNode n) {
        return n.isBoolean() ? (n.asBoolean() ? 1 : 0) : n.asDouble();
    }

    private static Map<String, EnemyDef> readEnemies(Path file) throws IOException {
        Map<String, EnemyDef> out = new LinkedHashMap<>();
        JSON.readTree(file.toFile()).path("enemies").fields().forEachRemaining(f -> {
            JsonNode e = f.getValue();
            out.put(f.getKey(), new EnemyDef(f.getKey(), e.path("name").asText(), e.path("hp").asDouble(1),
                    e.path("speed").asDouble(1), e.path("armor").asDouble(), e.path("ward").asDouble(),
                    e.path("air").asBoolean(), e.path("color").asText("#ffffff"), e.path("radius").asInt(7),
                    e.path("leak").asInt(1)));
        });
        return out;
    }

    private static List<WaveGroup> readGroups(Path file) throws IOException {
        List<WaveGroup> out = new ArrayList<>();
        for (JsonNode g : JSON.readTree(file.toFile()).path("groups")) {
            out.add(new WaveGroup(g.path("type").asText(), g.path("from").asInt(1), g.path("parity").asText(null),
                    g.path("every").asInt(0), g.path("base").asInt(), g.path("per").asInt(), g.path("gap").asDouble()));
        }
        return out;
    }

    private static List<MapDef> readMaps(Path dir) throws IOException {
        List<MapDef> out = new ArrayList<>();
        try (Stream<Path> files = Files.list(dir)) {
            for (Path f : files.filter(p -> p.getFileName().toString().matches("map_\\d+\\.json")).sorted().toList()) {
                JsonNode m = JSON.readTree(f.toFile());
                out.add(new MapDef(m.path("id").asText(), m.path("name").asText(), m.path("width").asInt(960),
                        m.path("height").asInt(540), m.path("freeSpots").asInt(3), m.path("unlockAfterWave").asInt(),
                        points(m.path("path")), point(m.path("base")), points(m.path("spots"))));
            }
        }
        if (out.isEmpty()) {
            throw new IllegalStateException("maps/: no map_NN.json found");
        }
        return out;
    }

    private static List<double[]> points(JsonNode arr) {
        List<double[]> out = new ArrayList<>();
        arr.forEach(p -> out.add(point(p)));
        return out;
    }

    private static double[] point(JsonNode p) {
        return new double[]{p.path(0).asDouble(), p.path(1).asDouble()};
    }
}
