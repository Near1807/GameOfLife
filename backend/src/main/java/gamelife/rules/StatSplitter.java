package gamelife.rules;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Splits stat XP equally between the tags of an action (brief 8: 3 XP over 2 tags gives
 * 1.5 each). Amounts have 2 decimals, so leftover cents go one by one to the first tags:
 * the parts ALWAYS sum exactly to the input.
 */
public final class StatSplitter {
    private StatSplitter() {}

    public static Map<String, BigDecimal> split(BigDecimal statXp, List<String> tags) {
        Map<String, BigDecimal> result = new LinkedHashMap<>();
        if (tags == null || tags.isEmpty()) {
            return result; // no tag: currency only
        }
        long totalCents = statXp.setScale(2, RoundingMode.HALF_UP).movePointRight(2).longValueExact();
        int n = tags.size();
        long share = Math.floorDiv(totalCents, n);
        long leftover = totalCents - share * n;
        for (int i = 0; i < n; i++) {
            long cents = share + (i < leftover ? 1 : 0);
            result.put(tags.get(i), BigDecimal.valueOf(cents, 2));
        }
        return result;
    }
}
