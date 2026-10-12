package gamelife.habits;

import java.time.DayOfWeek;
import java.time.Instant;
import java.util.List;
import java.util.Set;

/** A habit and the days of the week it is scheduled on. */
public record Habit(long id, String name, Set<DayOfWeek> days, List<String> tags, Instant createdAt,
                    boolean archived) {
}
