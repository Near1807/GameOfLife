package gamelife.ledger;

import com.fasterxml.jackson.annotation.JsonRawValue;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

/**
 * One row of the append-only ledger, exactly as stored.
 *
 * statSplits and meta are kept as the exact JSON text that was hashed, so the hash can
 * always be recomputed byte for byte. @JsonRawValue sends them to the frontend as real
 * JSON objects instead of strings.
 */
public record LedgerEvent(
        long id,
        String key,
        EventType type,
        Instant occurredAt,
        LocalDate gameDay,
        String module,
        String label,
        BigDecimal xp,
        BigDecimal currencyDelta,
        @JsonRawValue String statSplits,
        @JsonRawValue String meta,
        String prevHash,
        String hash) {
}
