package gamelife.habits;

import gamelife.common.Json;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.stereotype.Repository;

import java.sql.PreparedStatement;
import java.sql.Statement;
import java.time.DayOfWeek;
import java.time.Instant;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

@Repository
public class HabitRepository {
    private final JdbcTemplate jdbc;

    public HabitRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    static final RowMapper<Habit> MAPPER = (rs, n) -> new Habit(
            rs.getLong("id"),
            rs.getString("name"),
            parseDays(rs.getString("days")),
            Json.toStringList(rs.getString("tags")),
            Instant.ofEpochMilli(rs.getLong("created_at")),
            rs.getInt("archived") == 1);

    /** "MON,WED" -> {MONDAY, WEDNESDAY} */
    static Set<DayOfWeek> parseDays(String text) {
        Set<DayOfWeek> days = EnumSet.noneOf(DayOfWeek.class);
        for (String part : text.split(",")) {
            if (part.isBlank()) {
                continue;
            }
            for (DayOfWeek d : DayOfWeek.values()) {
                if (d.name().startsWith(part.trim().toUpperCase())) {
                    days.add(d);
                }
            }
        }
        return days;
    }

    static String formatDays(Set<DayOfWeek> days) {
        return Arrays.stream(DayOfWeek.values()).filter(days::contains)
                .map(d -> d.name().substring(0, 3)).collect(Collectors.joining(","));
    }

    public long insert(String name, Set<DayOfWeek> days, List<String> tags, Instant createdAt) {
        var keys = new GeneratedKeyHolder();
        jdbc.update(con -> {
            PreparedStatement ps = con.prepareStatement(
                    "INSERT INTO habits (name, days, tags, created_at) VALUES (?, ?, ?, ?)",
                    Statement.RETURN_GENERATED_KEYS);
            ps.setString(1, name);
            ps.setString(2, formatDays(days));
            ps.setString(3, Json.canonical(tags));
            ps.setLong(4, createdAt.toEpochMilli());
            return ps;
        }, keys);
        return keys.getKey().longValue();
    }

    public void insertWithId(Habit h) {
        jdbc.update("INSERT INTO habits (id, name, days, tags, created_at, archived) VALUES (?, ?, ?, ?, ?, ?)",
                h.id(), h.name(), formatDays(h.days()), Json.canonical(h.tags()), h.createdAt().toEpochMilli(),
                h.archived() ? 1 : 0);
    }

    public Optional<Habit> findById(long id) {
        return jdbc.query("SELECT * FROM habits WHERE id = ?", MAPPER, id).stream().findFirst();
    }

    public List<Habit> findAll() {
        return jdbc.query("SELECT * FROM habits ORDER BY id", MAPPER);
    }

    public List<Habit> findNotArchived() {
        return jdbc.query("SELECT * FROM habits WHERE archived = 0 ORDER BY id", MAPPER);
    }

    public void archive(long id) {
        jdbc.update("UPDATE habits SET archived = 1 WHERE id = ?", id);
    }

    public void deleteAllForRestore() {
        jdbc.update("DELETE FROM habits");
    }
}
