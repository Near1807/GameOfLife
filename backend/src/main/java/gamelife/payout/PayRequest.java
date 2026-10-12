package gamelife.payout;

import gamelife.ledger.EventType;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * A tool asking to be paid for an explicit act of the owner.
 * baseXp is before any multiplier (brief 6). tags are the stat tags (may be empty).
 */
public record PayRequest(
        String key,
        EventType type,
        String module,
        String label,
        BigDecimal baseXp,
        List<String> tags,
        Map<String, Object> meta) {
}
