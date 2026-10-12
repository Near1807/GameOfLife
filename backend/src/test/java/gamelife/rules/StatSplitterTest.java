package gamelife.rules;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Random;

import static org.assertj.core.api.Assertions.assertThat;

/** Brief 8 and 14: the stat split is equal and always sums to the base XP. */
class StatSplitterTest {
    private static final List<String> STATS =
            List.of("STRENGTH", "CREATIVITY", "INTELLECT", "DISCIPLINE", "SOCIAL", "VITALITY", "SPIRIT");

    @Test
    void briefExample() {
        Map<String, BigDecimal> s = StatSplitter.split(new BigDecimal("3"), List.of("INTELLECT", "DISCIPLINE"));
        assertThat(s.get("INTELLECT")).isEqualByComparingTo("1.50");
        assertThat(s.get("DISCIPLINE")).isEqualByComparingTo("1.50");
    }

    @Test
    void noTagsMeansNoStatXp() {
        assertThat(StatSplitter.split(new BigDecimal("30"), List.of())).isEmpty();
    }

    @Test
    void alwaysSumsToBaseForRandomAmountsAndTagCounts() {
        Random random = new Random(42);
        for (int i = 0; i < 5000; i++) {
            BigDecimal base = BigDecimal.valueOf(random.nextInt(100_000), 2);
            List<String> tags = STATS.subList(0, 1 + random.nextInt(STATS.size()));
            Map<String, BigDecimal> split = StatSplitter.split(base, tags);
            BigDecimal sum = split.values().stream().reduce(BigDecimal.ZERO, BigDecimal::add);
            assertThat(sum).isEqualByComparingTo(base);
            BigDecimal max = split.values().stream().max(BigDecimal::compareTo).orElseThrow();
            BigDecimal min = split.values().stream().min(BigDecimal::compareTo).orElseThrow();
            assertThat(max.subtract(min)).isLessThanOrEqualTo(new BigDecimal("0.01")); // equal up to a cent
        }
    }
}
