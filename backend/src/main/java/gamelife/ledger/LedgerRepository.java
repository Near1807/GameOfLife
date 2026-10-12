package gamelife.ledger;

import gamelife.common.Money;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/** Plain SQL access to the ledger table. Insert and read only: there is no update method. */
@Repository
public class LedgerRepository {
    private final JdbcTemplate jdbc;

    public LedgerRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    private static final RowMapper<LedgerEvent> MAPPER = (rs, n) -> new LedgerEvent(
            rs.getLong("id"),
            rs.getString("event_key"),
            EventType.valueOf(rs.getString("type")),
            Instant.ofEpochMilli(rs.getLong("occurred_at")),
            LocalDate.parse(rs.getString("game_day")),
            rs.getString("module"),
            rs.getString("label"),
            Money.parse(rs.getString("xp")),
            Money.parse(rs.getString("currency_delta")),
            rs.getString("stat_splits"),
            rs.getString("meta"),
            rs.getString("prev_hash"),
            rs.getString("hash"));

    public void insert(LedgerEvent e) {
        jdbc.update("""
                INSERT INTO ledger_events (id, event_key, type, occurred_at, game_day, module, label,
                    xp, currency_delta, stat_splits, meta, prev_hash, hash)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)""",
                e.id(), e.key(), e.type().name(), e.occurredAt().toEpochMilli(), e.gameDay().toString(),
                e.module(), e.label(), e.xp().toPlainString(), e.currencyDelta().toPlainString(),
                e.statSplits(), e.meta(), e.prevHash(), e.hash());
    }

    public List<LedgerEvent> findAll() {
        return jdbc.query("SELECT * FROM ledger_events ORDER BY id", MAPPER);
    }

    public Optional<LedgerEvent> findByKey(String key) {
        return jdbc.query("SELECT * FROM ledger_events WHERE event_key = ?", MAPPER, key).stream().findFirst();
    }

    /** All events whose key starts with the prefix, e.g. "task:12:" for every event of task 12. */
    public List<LedgerEvent> findByKeyPrefix(String prefix) {
        return jdbc.query("SELECT * FROM ledger_events WHERE substr(event_key, 1, ?) = ? ORDER BY id",
                MAPPER, prefix.length(), prefix);
    }

    public List<LedgerEvent> findLatest(int limit) {
        return jdbc.query("SELECT * FROM ledger_events ORDER BY id DESC LIMIT ?", MAPPER, limit);
    }

    public Optional<LedgerEvent> findLast() {
        return findLatest(1).stream().findFirst();
    }

    /** Only used by a full restore from a validated backup. */
    void deleteAllForRestore() {
        jdbc.update("DELETE FROM ledger_events");
    }
}
