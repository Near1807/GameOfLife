package gamelife.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import gamelife.IntegrationTest;
import gamelife.habits.Habit;
import gamelife.habits.HabitService;
import gamelife.rules.StreakCalculator;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Brief 4 and 14: changing a value in the configuration file changes the results without a
 * code change. This test starts the app on a copy of config/gamelife.json with two values
 * edited: habit XP 20 -> 50 and streak factor 0.1 -> 0.5.
 */
class ConfigChangeTest extends IntegrationTest {

    @DynamicPropertySource
    static void editedConfig(DynamicPropertyRegistry registry) throws IOException {
        ObjectMapper mapper = new ObjectMapper();
        ObjectNode cfg = (ObjectNode) mapper.readTree(Files.readString(Path.of("../config/gamelife.json")));
        ((ObjectNode) cfg.get("habit")).put("xp", 50);
        ((ObjectNode) cfg.get("streak")).put("factor", 0.5);
        Path edited = Path.of("target/test-data/edited-config.json");
        Files.createDirectories(edited.getParent());
        Files.writeString(edited, cfg.toPrettyString());
        registry.add("gamelife.config-path", edited::toString);
        registry.add("gamelife.db-path", () -> "target/test-data/config-test.db");
    }

    @Autowired HabitService habits;
    @Autowired GameConfig config;

    @Test
    void editedValuesChangeThePayout() {
        Habit h = habits.create("Push-ups", null, null);
        // 50 base x (1 + 0.5 x sqrt(1)) = 75, instead of 20 x 1.1 = 22 with the shipped config
        assertThat(habits.done(h.id()).event().xp()).isEqualByComparingTo("75.00");
        assertThat(StreakCalculator.multiplier(4, config.streak())).isEqualTo(2.0);
    }
}
