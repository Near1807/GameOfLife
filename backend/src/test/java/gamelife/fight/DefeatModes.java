package gamelife.fight;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.test.context.DynamicPropertyRegistry;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/** Starts the app on a copy of the config with another defeatMode (brief 9.1). */
final class DefeatModes {
    private DefeatModes() {}

    static void use(String mode, DynamicPropertyRegistry registry) throws IOException {
        ObjectMapper mapper = new ObjectMapper();
        ObjectNode cfg = (ObjectNode) mapper.readTree(Files.readString(Path.of("../config/gamelife.json")));
        ((ObjectNode) cfg.get("fight")).put("defeatMode", mode);
        Path file = Path.of("target/test-data/config-" + mode + ".json");
        Files.createDirectories(file.getParent());
        Files.writeString(file, cfg.toPrettyString());
        registry.add("gamelife.config-path", file::toString);
        registry.add("gamelife.db-path", () -> "target/test-data/" + mode + ".db");
    }
}
