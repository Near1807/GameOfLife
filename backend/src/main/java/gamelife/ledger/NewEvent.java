package gamelife.ledger;

import java.math.BigDecimal;
import java.util.Map;

/**
 * What a tool asks the ledger to record. The ledger adds id, time, game day and hashes.
 * The tool never chooses the time (brief 3.2).
 */
public record NewEvent(
        String key,
        EventType type,
        String module,
        String label,
        BigDecimal xp,
        BigDecimal currencyDelta,
        Map<String, BigDecimal> statSplits,
        Map<String, Object> meta) {
}
