package gamelife.character;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/** Class and rank names, read from content/character.json (data, not code: brief 8). */
@Component
public class CharacterContent {

    public record Rank(int fromLevel, String name) {}

    public record Data(String noClass, String balancedClass, Map<String, String> classes, List<Rank> ranks) {}

    private final Data data;

    public CharacterContent(@Value("${gamelife.content-dir}") String contentDir) {
        this.data = load(Path.of(contentDir, "character.json"));
    }

    static Data load(Path file) {
        try {
            return new ObjectMapper().configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
                    .readValue(Files.readString(file), Data.class);
        } catch (IOException e) {
            throw new IllegalStateException("Cannot read " + file.toAbsolutePath() + ": " + e.getMessage(), e);
        }
    }

    public Data data() {
        return data;
    }

    /** Highest rank whose fromLevel is reached. */
    public String rankFor(int overallLevel) {
        return data.ranks().stream()
                .filter(r -> overallLevel >= r.fromLevel())
                .max(Comparator.comparingInt(Rank::fromLevel))
                .map(Rank::name)
                .orElse("");
    }
}
