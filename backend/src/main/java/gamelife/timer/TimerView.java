package gamelife.timer;

import java.time.Instant;

/**
 * A timer as the frontend sees it. The frontend counts down using endsAt and serverNow
 * (so its own clock does not matter) but the server decides everything.
 * status: RUNNING (no question yet), ASK (ended or stopped: show Yes / No), DONE (answered).
 */
public record TimerView(long id, String label, Long taskId, int plannedMinutes, java.util.List<String> tags,
                        Instant startedAt, Instant endsAt, Instant stoppedAt, Instant serverNow,
                        String status, int countedMinutes, boolean wouldPay, String answer) {
}
