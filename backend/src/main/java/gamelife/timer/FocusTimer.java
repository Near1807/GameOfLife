package gamelife.timer;

import java.time.Instant;
import java.util.List;

/**
 * A focus timer as stored. startedAt and stoppedAt are SERVER timestamps (brief 3.2).
 * answer is null until the owner answers, then "YES" or "NO".
 */
public record FocusTimer(long id, String label, Long taskId, int plannedMinutes, List<String> tags,
                         Instant startedAt, Instant stoppedAt, String answer) {

    /** The moment the planned length is over. */
    public Instant plannedEnd() {
        return startedAt.plusSeconds(plannedMinutes * 60L);
    }

    /** True once the timer has ended or was stopped: only then may the question be asked. */
    public boolean isOver(Instant now) {
        return stoppedAt != null || !now.isBefore(plannedEnd());
    }

    /** True when the owner stopped it before the planned end. */
    public boolean stoppedEarly() {
        return stoppedAt != null && stoppedAt.isBefore(plannedEnd());
    }
}
