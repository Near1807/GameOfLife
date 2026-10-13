package gamelife.content;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * Folder-driven content (brief 12b). The owner drops files into content/ and the app picks them
 * up at startup or on "reload content", with no code change:
 *
 *   enemies/boss_NN.png   (+ optional boss_NN.json  {"name", "title"})   64x64, or a 4-frame strip
 *   hero/hero_NN.png      (+ optional hero_NN.json  {"name"})            32x32, or a 4-frame strip
 *   regions/region_NN.png (+ optional region_NN.json {"name"})           320x180
 *   items/NAME.png + NAME.json   a shop item (weapon, armor, charm, outfit, skin)   32x32 (outfits: a 4-frame strip)
 *   themes/NAME.json             a theme (design tokens), optional NAME.png preview
 *
 * Ordered content is sorted by its number. A bad file never crashes the app: it is skipped and
 * listed in warnings, shown in Settings.
 */
@Service
public class ContentService {
    private static final Logger log = LoggerFactory.getLogger(ContentService.class);
    private static final ObjectMapper JSON = new ObjectMapper()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    public static final Set<String> KINDS = Set.of("weapon", "armor", "charm", "outfit", "skin", "theme");

    /** One ordered picture: boss, hero look or region. file is the URL path, e.g. content/enemies/boss_03.png */
    public record Art(int number, String name, String title, String file, int frames) {}

    public record Bonus(double damagePct, double maxHp, double reduction) {
        public static final Bonus NONE = new Bonus(0, 0, 0);
    }

    public record Requirement(String stat, int level) {}

    /** A buyable thing (brief 10). tokens is only used by themes. */
    public record Item(String id, String name, String kind, String slot, double price, Bonus bonus, String effect,
                       double value, Requirement requires, String description, String sprite, int frames,
                       Map<String, String> tokens) {
        Item withFrames(int f) {
            return new Item(id, name, kind, slot, price, bonus, effect, value, requires, description, sprite, f, tokens);
        }
    }

    public record Catalog(List<Art> bosses, List<Art> heroes, List<Art> regions, List<Item> items,
                          List<String> warnings) {}

    private final Path root;
    private volatile Catalog catalog;

    public ContentService(@Value("${gamelife.content-dir}") String contentDir) {
        this.root = Path.of(contentDir);
        reload();
    }

    public Catalog catalog() {
        return catalog;
    }

    /** Scans every folder again (Settings, "reload content"). */
    public synchronized Catalog reload() {
        List<String> warnings = new ArrayList<>();
        List<Art> bosses = scanOrdered("enemies", "boss", 64, 64, warnings);
        List<Art> heroes = scanOrdered("hero", "hero", 32, 32, warnings);
        List<Art> regions = scanOrdered("regions", "region", 320, 180, warnings);
        List<Item> items = new ArrayList<>(scanItems(warnings));
        items.addAll(scanThemes(warnings));
        if (bosses.isEmpty()) {
            warnings.add("enemies/: no boss_NN.png found. Add at least one boss picture.");
        }
        if (heroes.isEmpty()) {
            warnings.add("hero/: no hero_NN.png found.");
        }
        catalog = new Catalog(bosses, heroes, regions, items, warnings);
        warnings.forEach(w -> log.warn("Content: {}", w));
        return catalog;
    }

    public Item item(String id) {
        return catalog.items().stream().filter(i -> i.id().equals(id)).findFirst().orElse(null);
    }

    // ---------------------------------------------------------------- ordered art

    /**
     * Scans folder/prefix_NN.png. Accepted sizes: exactly w x h, or a horizontal strip of frames
     * (width a multiple of w, same height). Duplicate numbers (boss_3 and boss_03): the first
     * file in name order wins, the other is a warning.
     */
    List<Art> scanOrdered(String folder, String prefix, int w, int h, List<String> warnings) {
        Path dir = root.resolve(folder);
        Map<Integer, Art> byNumber = new TreeMap<>();
        Pattern name = Pattern.compile(prefix + "_(\\d+)\\.png");
        for (Path file : list(dir)) {
            String fn = file.getFileName().toString();
            if (fn.endsWith(".json") || fn.startsWith(".") || fn.equalsIgnoreCase("README.md")) {
                continue;
            }
            Matcher m = name.matcher(fn);
            if (!m.matches()) {
                warnings.add(folder + "/" + fn + ": wrong name or format, expected " + prefix + "_NN.png");
                continue;
            }
            int number = Integer.parseInt(m.group(1));
            if (byNumber.containsKey(number)) {
                warnings.add(folder + "/" + fn + ": duplicate number " + number + ", ignored");
                continue;
            }
            int[] size = imageSize(file, folder, warnings);
            if (size == null) {
                continue;
            }
            if (size[1] != h || size[0] % w != 0 || size[0] == 0) {
                warnings.add(folder + "/" + fn + ": wrong size " + size[0] + "x" + size[1] + ", expected " + w + "x" + h
                        + (w == 320 ? "" : " (or a strip of " + w + "x" + h + " frames)"));
                continue;
            }
            JsonNode side = sidecar(file, folder, warnings);
            String defaultName = Character.toUpperCase(prefix.charAt(0)) + prefix.substring(1) + " " + number;
            byNumber.put(number, new Art(number, text(side, "name", defaultName), text(side, "title", ""),
                    "content/" + folder + "/" + fn, size[0] / w));
        }
        return new ArrayList<>(byNumber.values());
    }

    // ---------------------------------------------------------------- shop items

    List<Item> scanItems(List<String> warnings) {
        Path dir = root.resolve("items");
        List<Item> items = new ArrayList<>();
        Set<String> ids = new HashSet<>();
        for (Path file : list(dir)) {
            String fn = file.getFileName().toString();
            if (fn.startsWith(".") || fn.equalsIgnoreCase("README.md")) {
                continue;
            }
            String base = fn.replaceAll("\\.[^.]+$", "");
            if (fn.endsWith(".png")) {
                if (!Files.exists(dir.resolve(base + ".json"))) {
                    warnings.add("items/" + fn + ": shop image without " + base + ".json, not buyable");
                }
                continue;
            }
            if (!fn.endsWith(".json")) {
                warnings.add("items/" + fn + ": wrong format, only .png and .json");
                continue;
            }
            Path png = dir.resolve(base + ".png");
            if (!Files.exists(png)) {
                warnings.add("items/" + fn + ": missing picture " + base + ".png");
                continue;
            }
            int[] size = imageSize(png, "items", warnings);
            if (size == null) {
                continue;
            }
            if (size[1] != 32 || size[0] % 32 != 0) {
                warnings.add("items/" + base + ".png: wrong size " + size[0] + "x" + size[1]
                        + ", expected 32x32 (or a strip of 32x32 frames)");
                continue;
            }
            Item item = parseItem(file, "content/items/" + base + ".png", warnings);
            if (item != null) {
                item = item.withFrames(size[0] / 32);
            }
            if (item == null) {
                continue;
            }
            if (!ids.add(item.id())) {
                warnings.add("items/" + fn + ": duplicate id " + item.id() + ", ignored");
                continue;
            }
            items.add(item);
        }
        items.sort(Comparator.comparing(Item::kind).thenComparingDouble(Item::price));
        return items;
    }

    List<Item> scanThemes(List<String> warnings) {
        Path dir = root.resolve("themes");
        List<Item> themes = new ArrayList<>();
        for (Path file : list(dir)) {
            String fn = file.getFileName().toString();
            if (!fn.endsWith(".json")) {
                if (!fn.endsWith(".png") && !fn.startsWith(".") && !fn.equalsIgnoreCase("README.md")) {
                    warnings.add("themes/" + fn + ": wrong format, a theme is one .json file (optional .png preview)");
                }
                continue;
            }
            String base = fn.replaceAll("\\.json$", "");
            String preview = Files.exists(dir.resolve(base + ".png")) ? "content/themes/" + base + ".png" : null;
            Item item = parseItem(file, preview, warnings);
            if (item == null) {
                continue;
            }
            if (!"theme".equals(item.kind())) {
                warnings.add("themes/" + fn + ": kind must be \"theme\"");
                continue;
            }
            if (item.tokens().isEmpty()) {
                warnings.add("themes/" + fn + ": no tokens, nothing to restyle");
                continue;
            }
            themes.add(item);
        }
        themes.sort(Comparator.comparingDouble(Item::price));
        return themes;
    }

    /** Reads one item JSON; returns null (with a warning) when a required field is missing or wrong. */
    Item parseItem(Path file, String sprite, List<String> warnings) {
        String where = root.relativize(file).toString().replace('\\', '/');
        JsonNode n;
        try {
            n = JSON.readTree(Files.readString(file));
        } catch (IOException e) {
            warnings.add(where + ": not valid JSON (" + e.getMessage().split("\n")[0] + ")");
            return null;
        }
        String id = text(n, "id", null);
        String name = text(n, "name", null);
        String kind = text(n, "kind", null);
        if (id == null || name == null || kind == null || !n.has("price")) {
            warnings.add(where + ": needs at least id, name, kind and price");
            return null;
        }
        if (!KINDS.contains(kind)) {
            warnings.add(where + ": unknown kind \"" + kind + "\", expected one of " + KINDS);
            return null;
        }
        double price = n.path("price").asDouble(-1);
        if (price < 0) {
            warnings.add(where + ": price must be a number >= 0");
            return null;
        }
        JsonNode b = n.path("bonus");
        Bonus bonus = new Bonus(b.path("damagePct").asDouble(0), b.path("maxHp").asDouble(0), b.path("reduction").asDouble(0));
        JsonNode r = n.path("requires");
        Requirement req = r.isObject() && r.has("stat") ? new Requirement(r.path("stat").asText(), r.path("level").asInt(1)) : null;
        Map<String, String> tokens = new LinkedHashMap<>();
        n.path("tokens").fields().forEachRemaining(e -> tokens.put(e.getKey(), e.getValue().asText()));
        return new Item(id, name, kind, text(n, "slot", kind), price, bonus, text(n, "effect", null),
                n.path("value").asDouble(0), req, text(n, "description", ""), sprite, 1, tokens);
    }

    // ---------------------------------------------------------------- helpers

    private JsonNode sidecar(Path png, String folder, List<String> warnings) {
        Path side = png.resolveSibling(png.getFileName().toString().replace(".png", ".json"));
        if (!Files.exists(side)) {
            return null;
        }
        try {
            return JSON.readTree(Files.readString(side));
        } catch (IOException e) {
            warnings.add(folder + "/" + side.getFileName() + ": not valid JSON, default name used");
            return null;
        }
    }

    private static int[] imageSize(Path file, String folder, List<String> warnings) {
        try {
            BufferedImage img = ImageIO.read(file.toFile());
            if (img == null) {
                warnings.add(folder + "/" + file.getFileName() + ": not a readable PNG");
                return null;
            }
            return new int[] {img.getWidth(), img.getHeight()};
        } catch (IOException | RuntimeException e) {
            warnings.add(folder + "/" + file.getFileName() + ": cannot read (" + e.getMessage() + ")");
            return null;
        }
    }

    private static String text(JsonNode n, String field, String fallback) {
        return n != null && n.hasNonNull(field) && !n.get(field).asText().isBlank() ? n.get(field).asText() : fallback;
    }

    private static List<Path> list(Path dir) {
        if (!Files.isDirectory(dir)) {
            return List.of();
        }
        try (Stream<Path> s = Files.list(dir)) {
            return s.filter(Files::isRegularFile).sorted().toList();
        } catch (IOException e) {
            return List.of();
        }
    }

    // ---------------------------------------------------------------- ladder helpers

    /**
     * Boss picture for boss number k (1-based, counting all bosses fought in this cycle). Past the
     * last file it loops back to the first, as a "stronger variant" (brief 12b).
     */
    public Art bossArt(int k) {
        List<Art> b = catalog.bosses();
        if (b.isEmpty()) {
            return new Art(k, "Boss " + k, "", "", 1);
        }
        return b.get((k - 1) % b.size());
    }

    /** Hero look for an overall level: changes every n levels, capped at the last file. */
    public Art heroArt(int overallLevel, int everyN) {
        List<Art> h = catalog.heroes();
        if (h.isEmpty()) {
            return new Art(1, "Hero", "", "", 1);
        }
        int index = Math.min(h.size() - 1, (overallLevel - 1) / Math.max(1, everyN));
        return h.get(index);
    }

    /** Region background for boss number k: changes every n bosses, looping over the files. */
    public Art regionArt(int k, int everyN) {
        List<Art> r = catalog.regions();
        if (r.isEmpty()) {
            return null;
        }
        return r.get(((k - 1) / Math.max(1, everyN)) % r.size());
    }
}
