package gamelife.config;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.ZoneId;
import java.util.List;

/**
 * All tunable numbers of the game, read from config/gamelife.json (brief section 4:
 * "all numbers and formulas live in one configuration file, never hard-coded").
 *
 * The JSON structure maps one-to-one onto these records. Unknown fields (like "_comment")
 * are ignored.
 */
public record GameConfig(
        Time time,
        List<Stat> stats,
        Todo todo,
        Home home,
        Character character,
        Timer timer,
        Habit habit,
        Checkin checkin,
        Streak streak,
        Weapon weapon,
        Ascension ascension,
        Stages stages,
        StatLevels statLevels,
        Loot loot) {

    public record Time(String zone, int dayStartHour) {
        public ZoneId zoneId() {
            return ZoneId.of(zone);
        }
    }

    public record Stat(String id, String name, String hint) {}

    public record Todo(double xpPerMinute, List<Integer> minuteChips, int defaultMinutes, int suggestionCount) {}

    public record Home(double dailyXpGoal) {}

    public record Character(int balancedMinLevel, int balancedMaxGap) {}

    public record Timer(double xpPerMinute, List<Integer> minuteChips, int minMinutes, int maxMinutes,
                        int defaultMinutes, int earlyStopMinMinutes, List<String> defaultTags) {}

    public record Habit(double xp, List<String> defaultTags) {}

    public record Checkin(double xp, int moodLevels, List<String> defaultTags) {}

    public record Streak(double factor, double exponent, int freezeEveryCountedDays, int maxFreezes,
                         int maxRestDaysPerWeek, int restDayMaxDaysAhead, int comebackGapDays,
                         double comebackMultiplier) {}

    public record Weapon(double bonusPerLevel, double baseCost, double costGrowth) {}

    public record Ascension(double perAscension) {}

    public record Stages(int count, double baseTarget, double growth) {}

    public record StatLevels(double base, double growth) {}

    public record Loot(double dropRate) {}

    /** True when the given id is one of the configured stats. */
    public boolean isStat(String id) {
        return stats.stream().anyMatch(s -> s.id().equals(id));
    }

    /** Reads and parses a config file. Fails loudly: a broken config must not start the game. */
    public static GameConfig load(Path file) {
        try {
            ObjectMapper mapper = new ObjectMapper()
                    .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
            return mapper.readValue(Files.readString(file), GameConfig.class);
        } catch (IOException e) {
            throw new IllegalStateException("Cannot read game config " + file.toAbsolutePath() + ": " + e.getMessage(), e);
        }
    }
}
