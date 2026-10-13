package gamelife.game;

import gamelife.config.GameConfig;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.stream.Stream;

/**
 * Holds the game data read from content/ (towers, waves, maps) and a short hash of everything
 * that decides a wave's outcome (those files plus config "game"). A changed hash means a retune:
 * a stuck wave is tried again, because the result may now differ.
 */
@Service
public class GameContent {
    private final Path root;
    private final GameConfig config;
    private volatile GameCatalog catalog;
    private volatile String rulesHash;

    public GameContent(@Value("${gamelife.content-dir}") String contentDir, GameConfig config) {
        this.root = Path.of(contentDir);
        this.config = config;
        reload();
    }

    public GameCatalog catalog() {
        return catalog;
    }

    public GameCatalog.MapDef map() {
        return catalog.firstMap();
    }

    public String rulesHash() {
        return rulesHash;
    }

    /** Reads the files again (Settings, "Reload content"). A broken file keeps the old data. */
    public synchronized void reload() {
        GameCatalog fresh = GameCatalog.load(root);
        try {
            MessageDigest sha = MessageDigest.getInstance("SHA-256");
            sha.update(config.game().toString().getBytes(StandardCharsets.UTF_8));
            sha.update(Files.readAllBytes(root.resolve("towers.json")));
            sha.update(Files.readAllBytes(root.resolve("waves.json")));
            try (Stream<Path> maps = Files.list(root.resolve("maps"))) {
                for (Path p : maps.sorted().toList()) {
                    sha.update(Files.readAllBytes(p));
                }
            }
            rulesHash = HexFormat.of().formatHex(sha.digest()).substring(0, 12);
        } catch (IOException | NoSuchAlgorithmException e) {
            throw new IllegalStateException("Cannot read the game data: " + e.getMessage(), e);
        }
        catalog = fresh;
    }
}
