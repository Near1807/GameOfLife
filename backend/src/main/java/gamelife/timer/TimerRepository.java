package gamelife.timer;

import gamelife.common.Json;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.stereotype.Repository;

import java.sql.PreparedStatement;
import java.sql.Statement;
import java.sql.Types;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Repository
public class TimerRepository {
    private final JdbcTemplate jdbc;

    public TimerRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    static final RowMapper<FocusTimer> MAPPER = (rs, n) -> {
        long taskId = rs.getLong("task_id");
        Long task = rs.wasNull() ? null : taskId;
        long stopped = rs.getLong("stopped_at");
        Instant stoppedAt = rs.wasNull() ? null : Instant.ofEpochMilli(stopped);
        return new FocusTimer(rs.getLong("id"), rs.getString("label"), task, rs.getInt("planned_minutes"),
                Json.toStringList(rs.getString("tags")), Instant.ofEpochMilli(rs.getLong("started_at")),
                stoppedAt, rs.getString("answer"));
    };

    public long insert(String label, Long taskId, int plannedMinutes, List<String> tags, Instant startedAt) {
        var keys = new GeneratedKeyHolder();
        jdbc.update(con -> {
            PreparedStatement ps = con.prepareStatement(
                    "INSERT INTO timers (label, task_id, planned_minutes, tags, started_at) VALUES (?, ?, ?, ?, ?)",
                    Statement.RETURN_GENERATED_KEYS);
            ps.setString(1, label);
            if (taskId == null) {
                ps.setNull(2, Types.INTEGER);
            } else {
                ps.setLong(2, taskId);
            }
            ps.setInt(3, plannedMinutes);
            ps.setString(4, Json.canonical(tags));
            ps.setLong(5, startedAt.toEpochMilli());
            return ps;
        }, keys);
        return keys.getKey().longValue();
    }

    /** Restore from backup: keeps the original id. */
    public void insertWithId(FocusTimer t) {
        jdbc.update("""
                INSERT INTO timers (id, label, task_id, planned_minutes, tags, started_at, stopped_at, answer)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?)""",
                t.id(), t.label(), t.taskId(), t.plannedMinutes(), Json.canonical(t.tags()),
                t.startedAt().toEpochMilli(), t.stoppedAt() == null ? null : t.stoppedAt().toEpochMilli(), t.answer());
    }

    public Optional<FocusTimer> findById(long id) {
        return jdbc.query("SELECT * FROM timers WHERE id = ?", MAPPER, id).stream().findFirst();
    }

    public List<FocusTimer> findAll() {
        return jdbc.query("SELECT * FROM timers ORDER BY id", MAPPER);
    }

    /** The timer still waiting for an answer, if any (at most one exists). */
    public Optional<FocusTimer> findUnanswered() {
        return jdbc.query("SELECT * FROM timers WHERE answer IS NULL ORDER BY id DESC LIMIT 1", MAPPER)
                .stream().findFirst();
    }

    public Optional<FocusTimer> findLastByLabel(String label) {
        return jdbc.query("SELECT * FROM timers WHERE lower(label) = lower(?) ORDER BY id DESC LIMIT 1", MAPPER, label)
                .stream().findFirst();
    }

    public boolean hasValidatedTimerForTask(long taskId) {
        Integer n = jdbc.queryForObject("SELECT count(*) FROM timers WHERE task_id = ? AND answer = 'YES'",
                Integer.class, taskId);
        return n != null && n > 0;
    }

    /** Sets stopped_at only if the timer is not already stopped. */
    public void stop(long id, Instant at) {
        jdbc.update("UPDATE timers SET stopped_at = ? WHERE id = ? AND stopped_at IS NULL", at.toEpochMilli(), id);
    }

    /** Records the answer only if there is none yet: the first answer wins. */
    public void answer(long id, String answer) {
        jdbc.update("UPDATE timers SET answer = ? WHERE id = ? AND answer IS NULL", answer, id);
    }

    public void deleteAllForRestore() {
        jdbc.update("DELETE FROM timers");
    }
}
