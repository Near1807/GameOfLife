package gamelife.stage;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/** The bosses, read from content/bosses.json (data, not code). */
@Component
public class BossContent {

    public record Boss(int stage, String name, String title, String sprite) {}

    record File(List<Boss> bosses) {}

    private final List<Boss> bosses;

    public BossContent(@Value("${gamelife.content-dir}") String contentDir) {
        Path file = Path.of(contentDir, "bosses.json");
        try {
            this.bosses = new ObjectMapper().configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
                    .readValue(Files.readString(file), File.class).bosses();
        } catch (IOException e) {
            throw new IllegalStateException("Cannot read " + file.toAbsolutePath() + ": " + e.getMessage(), e);
        }
    }

    public List<Boss> all() {
        return bosses;
    }

    /** The boss of a stage; a generic one if the data file has fewer bosses than stages. */
    public Boss forStage(int stage) {
        return bosses.stream().filter(b -> b.stage() == stage).findFirst()
                .orElse(new Boss(stage, "Boss " + stage, "", bosses.isEmpty() ? "" : bosses.get(0).sprite()));
    }
}
