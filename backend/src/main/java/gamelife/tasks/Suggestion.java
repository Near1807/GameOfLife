package gamelife.tasks;

import java.util.List;

/** A known task name with its remembered minutes and tags, for autocomplete and repeat chips. */
public record Suggestion(String name, int expectedMinutes, List<String> tags, int timesUsed) {
}
