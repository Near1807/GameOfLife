package gamelife.character;

import gamelife.common.Money;
import gamelife.config.GameConfig;
import gamelife.ledger.LedgerEvent;
import gamelife.rules.LedgerFacts;
import gamelife.rules.LevelCurve;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/**
 * Character sheet (brief 8), computed from the ledger every time (never stored):
 * seven stat levels, overall level = sum of levels - 6, a class from the two highest stats,
 * a rank from the overall level, and the "Balanced" title.
 */
@Service
public class CharacterService {
    private final GameConfig config;
    private final CharacterContent content;

    public CharacterService(GameConfig config, CharacterContent content) {
        this.config = config;
        this.content = content;
    }

    public record StatView(String id, String name, String hint, double xp, int level, double xpIntoLevel,
                           double xpForNext, double progress) {}

    public record Character(List<StatView> stats, int overallLevel, String className, String rank,
                            boolean balanced, List<String> topStats) {}

    public Character compute(List<LedgerEvent> events) {
        Map<String, BigDecimal> totals = LedgerFacts.statTotals(events);
        List<StatView> stats = new ArrayList<>();
        for (GameConfig.Stat s : config.stats()) {
            LevelCurve.Level l = LevelCurve.of(totals.getOrDefault(s.id(), Money.ZERO), config.statLevels());
            stats.add(new StatView(s.id(), s.name(), s.hint(), round2(l.totalXp()), l.level(),
                    round2(l.xpIntoLevel()), round2(l.xpForNext()), l.progress()));
        }

        int overall = stats.stream().mapToInt(StatView::level).sum() - (stats.size() - 1);

        // Two highest stats by XP; ties keep the order of the config list (stable sort).
        List<StatView> ranked = stats.stream()
                .sorted(Comparator.comparingDouble(StatView::xp).reversed())
                .toList();
        List<String> top = ranked.stream().filter(s -> s.xp() > 0).limit(2).map(StatView::id).toList();

        int min = stats.stream().mapToInt(StatView::level).min().orElse(1);
        int max = stats.stream().mapToInt(StatView::level).max().orElse(1);
        boolean balanced = min >= config.character().balancedMinLevel()
                && max - min <= config.character().balancedMaxGap();

        String className;
        if (balanced) {
            className = content.data().balancedClass();
        } else if (top.size() < 2) {
            className = content.data().noClass();
        } else {
            className = content.data().classes().getOrDefault(pairKey(top.get(0), top.get(1)),
                    content.data().noClass());
        }
        return new Character(stats, overall, className, content.rankFor(overall), balanced, top);
    }

    /** "A+B" with A before B in the order of the config stat list. */
    String pairKey(String a, String b) {
        List<String> order = config.stats().stream().map(GameConfig.Stat::id).toList();
        return order.indexOf(a) <= order.indexOf(b) ? a + "+" + b : b + "+" + a;
    }

    private static double round2(double v) {
        return Math.round(v * 100.0) / 100.0;
    }
}
