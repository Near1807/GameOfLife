package gamelife.tasks;

import gamelife.common.Json;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.stereotype.Repository;

import java.sql.PreparedStatement;
import java.sql.Statement;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Repository
public class TaskRepository {
    private final JdbcTemplate jdbc;

    public TaskRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    static final RowMapper<Task> MAPPER = (rs, n) -> new Task(
            rs.getLong("id"),
            rs.getString("name"),
            rs.getInt("expected_minutes"),
            Json.toStringList(rs.getString("tags")),
            Instant.ofEpochMilli(rs.getLong("created_at")),
            rs.getInt("archived") == 1);

    public long insert(String name, int minutes, List<String> tags, Instant createdAt) {
        var keys = new GeneratedKeyHolder();
        jdbc.update(con -> {
            PreparedStatement ps = con.prepareStatement(
                    "INSERT INTO tasks (name, expected_minutes, tags, created_at) VALUES (?, ?, ?, ?)",
                    Statement.RETURN_GENERATED_KEYS);
            ps.setString(1, name);
            ps.setInt(2, minutes);
            ps.setString(3, Json.canonical(tags));
            ps.setLong(4, createdAt.toEpochMilli());
            return ps;
        }, keys);
        return keys.getKey().longValue();
    }

    /** Restore from backup: keeps the original id. */
    public void insertWithId(Task t) {
        jdbc.update("INSERT INTO tasks (id, name, expected_minutes, tags, created_at, archived) VALUES (?, ?, ?, ?, ?, ?)",
                t.id(), t.name(), t.expectedMinutes(), Json.canonical(t.tags()), t.createdAt().toEpochMilli(),
                t.archived() ? 1 : 0);
    }

    public Optional<Task> findById(long id) {
        return jdbc.query("SELECT * FROM tasks WHERE id = ?", MAPPER, id).stream().findFirst();
    }

    public List<Task> findAll() {
        return jdbc.query("SELECT * FROM tasks ORDER BY id", MAPPER);
    }

    public List<Task> findNotArchived() {
        return jdbc.query("SELECT * FROM tasks WHERE archived = 0 ORDER BY id", MAPPER);
    }

    /** Most recent task with the same name, ignoring case: gives the remembered minutes and tags. */
    public Optional<Task> findLastByName(String name) {
        return jdbc.query("SELECT * FROM tasks WHERE lower(name) = lower(?) ORDER BY id DESC LIMIT 1", MAPPER, name)
                .stream().findFirst();
    }

    public void update(long id, String name, int minutes, List<String> tags) {
        jdbc.update("UPDATE tasks SET name = ?, expected_minutes = ?, tags = ? WHERE id = ?",
                name, minutes, Json.canonical(tags), id);
    }

    public void archive(long id) {
        jdbc.update("UPDATE tasks SET archived = 1 WHERE id = ?", id);
    }

    public void deleteAllForRestore() {
        jdbc.update("DELETE FROM tasks");
    }
}
