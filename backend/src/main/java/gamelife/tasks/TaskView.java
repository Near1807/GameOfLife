package gamelife.tasks;

import java.math.BigDecimal;
import java.util.List;

/** A task as the frontend sees it, with its done state read from the ledger. */
public record TaskView(long id, String name, int expectedMinutes, List<String> tags, boolean done,
                       BigDecimal xpPaid, boolean viaTimer) {
}
