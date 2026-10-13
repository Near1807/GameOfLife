package gamelife.game;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.test.context.DynamicPropertyRegistry;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

/** Starts the app on a copy of the config with some "game" numbers changed, and its own database. */
final class TestConfigs {
    private TestConfigs() {}

    static void use(String name, Map<String, Object> gameChanges, DynamicPropertyRegistry registry) throws IOException {
        ObjectMapper mapper = new ObjectMapper();
        ObjectNode cfg = (ObjectNode) mapper.readTree(Files.readString(Path.of("../config/gamelife.json")));
        gameChanges.forEach((k, v) -> ((ObjectNode) cfg.get("game")).set(k, mapper.valueToTree(v)));
        Path file = Path.of("target/test-data/config-" + name + ".json");
        Files.createDirectories(file.getParent());
        Files.writeString(file, cfg.toPrettyString());
        registry.add("gamelife.config-path", file::toString);
        registry.add("gamelife.db-path", () -> "target/test-data/" + name + ".db");
    }
}
