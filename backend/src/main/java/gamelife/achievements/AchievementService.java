package gamelife.achievements;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import gamelife.character.CharacterService;
import gamelife.common.Json;
import gamelife.config.GameConfig;
import gamelife.game.WaveService;
import gamelife.gear.GearService;
import gamelife.ledger.EventType;
import gamelife.ledger.LedgerEvent;
import gamelife.ledger.LedgerService;
import gamelife.rules.LedgerFacts;
import gamelife.rules.StreakCalculator;
import gamelife.time.GameTime;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Achievements (brief 8): data in content/achievements.json, each unlocked by a rule computed
 * from the ledger. Nothing is stored: an achievement is unlocked whenever its fact is reached,
 * so it can never disagree with the ledger.
 */
@Service
public class AchievementService {
    private static final ObjectMapper JSON = new ObjectMapper()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    public record Rule(String fact, String stat, double min) {}

    public record Achievement(String id, String name, String description, String icon, Rule rule) {}

    record File(List<Achievement> achievements) {}

    public record Status(String id, String name, String description, String icon, boolean unlocked, double current,
                         double target) {}

    /** The fact names a rule may use. statLevel needs "stat". */
    public static final Set<String> FACTS = Set.of("tasksDone", "focusBlocks", "focusHours", "habitsDone", "checkins",
            "bestStreak", "furthestWave", "towersBuilt", "spotsUnlocked", "maxTier", "towerTypes", "marketsBuilt",
            "retriedClears", "purchases",
            "itemsOwned", "loot", "bestDayXp", "xpTotal", "comebacks", "restDays", "overallLevel", "minStatLevel",
            "balanced", "statLevel");

    private final Path file;
    private final LedgerService ledger;
    private final CharacterService character;
    private final GearService gear;
    private final WaveService waves;
    private final GameConfig config;
    private final GameTime time;

    public AchievementService(@Value("${gamelife.content-dir}") String contentDir, LedgerService ledger,
                              CharacterService character, GearService gear, WaveService waves, GameConfig config,
                              GameTime time) {
        this.file = Path.of(contentDir, "achievements.json");
        this.ledger = ledger;
        this.character = character;
        this.gear = gear;
        this.waves = waves;
        this.config = config;
        this.time = time;
    }

    /** Read on every call, so editing the file needs no restart. A broken file gives an empty list. */
    public List<Achievement> definitions() {
        try {
            return JSON.readValue(Files.readString(file), File.class).achievements().stream()
                    .filter(a -> a.rule() != null && FACTS.contains(a.rule().fact()))
                    .toList();
        } catch (IOException e) {
            return List.of();
        }
    }

    public List<Status> statuses() {
        waves.sync();
        List<LedgerEvent> events = ledger.all();
        Map<String, Double> facts = facts(events);
        var stats = character.compute(events).stats();
        List<Status> result = new ArrayList<>();
        for (Achievement a : definitions()) {
            double current = "statLevel".equals(a.rule().fact())
                    ? stats.stream().filter(s -> s.id().equalsIgnoreCase(String.valueOf(a.rule().stat())))
                        .mapToInt(CharacterService.StatView::level).findFirst().orElse(0)
                    : facts.getOrDefault(a.rule().fact(), 0.0);
            result.add(new Status(a.id(), a.name(), a.description(), a.icon(), current >= a.rule().min(),
                    Math.round(current * 10) / 10.0, a.rule().min()));
        }
        return result;
    }

    /** Every fact a rule can use, computed from the ledger. */
    Map<String, Double> facts(List<LedgerEvent> events) {
        Map<String, Double> f = new HashMap<>();
        double tasks = 0, focusMin = 0, blocks = 0, habits = 0, checkins = 0, furthest = 0, built = 0, spots = 0;
        double maxTier = 0, markets = 0, retried = 0;
        java.util.Set<Object> types = new java.util.HashSet<>();
        double purchases = 0, loot = 0, xpTotal = 0, comebacks = 0;
        Map<LocalDate, Double> xpByDay = new LinkedHashMap<>();
        for (LedgerEvent e : events) {
            Map<String, Object> m = Json.toMap(e.meta());
            switch (e.type()) {
                case TASK_DONE -> tasks++;
                case TASK_UNDONE -> tasks--;
                case TIMER_VALIDATED -> {
                    blocks++;
                    focusMin += ((Number) m.getOrDefault("countedMinutes", 0)).doubleValue();
                }
                case HABIT_DONE -> habits++;
                case CHECKIN -> checkins++;
                case WAVE_CLEARED -> {
                    furthest = Math.max(furthest, ((Number) m.get("wave")).doubleValue());
                    if (((Number) m.get("attempt")).intValue() > 1) retried++;
                }
                case TOWER_BUILT -> {
                    built++;
                    types.add(m.get("tower"));
                    if ("income".equals(m.get("tower"))) markets++;
                }
                case TOWER_UPGRADED -> maxTier = Math.max(maxTier, ((Number) m.get("tier")).doubleValue());
                case SPOT_UNLOCKED -> spots++;
                case PURCHASE -> {
                    if (Boolean.TRUE.equals(m.get("loot"))) loot++;
                    else purchases++;
                }
                default -> { }
            }
            xpTotal += e.xp().doubleValue();
            xpByDay.merge(e.gameDay(), e.xp().doubleValue(), Double::sum);
            Object mult = m.get("mult");
            if (mult instanceof Map<?, ?> mm && mm.get("comeback") instanceof Number c && c.doubleValue() > 1) {
                comebacks++;
            }
        }
        var counted = LedgerFacts.countedDays(events);
        var rest = LedgerFacts.restOrPausedDays(events);
        int best = 0;
        LocalDate first = counted.stream().min(LocalDate::compareTo).orElse(null);
        for (LocalDate d = first; d != null && !d.isAfter(time.today()); d = d.plusDays(1)) {
            best = Math.max(best, StreakCalculator.compute(counted, rest, d, config.streak()).streakDays());
        }
        var ch = character.compute(events);
        GearService.Loadout l = gear.loadout(events);
        f.put("tasksDone", tasks);
        f.put("focusBlocks", blocks);
        f.put("focusHours", focusMin / 60.0);
        f.put("habitsDone", habits);
        f.put("checkins", checkins);
        f.put("bestStreak", (double) best);
        f.put("furthestWave", furthest);
        f.put("towersBuilt", built);
        f.put("spotsUnlocked", spots);
        f.put("maxTier", maxTier);
        f.put("towerTypes", (double) types.size());
        f.put("marketsBuilt", markets);
        f.put("retriedClears", retried);
        f.put("purchases", purchases);
        f.put("itemsOwned", (double) l.owned().stream().filter(id -> !id.equals("theme_nebula")).count());
        f.put("loot", loot);
        f.put("bestDayXp", xpByDay.values().stream().mapToDouble(Double::doubleValue).max().orElse(0));
        f.put("xpTotal", xpTotal);
        f.put("comebacks", comebacks);
        f.put("restDays", (double) LedgerFacts.restDays(events).size());
        f.put("overallLevel", (double) ch.overallLevel());
        f.put("minStatLevel", (double) ch.stats().stream().mapToInt(CharacterService.StatView::level).min().orElse(1));
        f.put("balanced", ch.balanced() ? 1.0 : 0.0);
        return f;
    }
}
