package gamelife.tasks;

import java.time.Instant;
import java.util.List;

/** A to-do task as stored. Whether it is done is NOT stored here: it comes from the ledger. */
public record Task(long id, String name, int expectedMinutes, List<String> tags, Instant createdAt, boolean archived) {
}
