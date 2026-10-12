package gamelife.backup;

import gamelife.habits.Habit;
import gamelife.tasks.Task;
import gamelife.timer.FocusTimer;

import java.util.List;

/**
 * The backup file format (brief 3.3 and 5). Plain JSON, readable by a human.
 * Ledger events keep statSplits and meta as the exact hashed text, so the chain can be
 * re-verified on import. ownedItems is empty until the shop exists (milestone 4); owned items
 * will be derived from PURCHASE events anyway.
 */
public record BackupFile(
        String format,
        int version,
        String exportedAt,
        List<EventRow> events,
        List<Task> tasks,
        List<Habit> habits,
        List<FocusTimer> timers,
        List<String> ownedItems) {

    public static final String FORMAT = "gamelife-backup";
    public static final int VERSION = 1;

    /** One ledger row, all fields as stored. */
    public record EventRow(long id, String key, String type, long occurredAt, String gameDay, String module,
                           String label, String xp, String currencyDelta, String statSplits, String meta,
                           String prevHash, String hash) {}
}
