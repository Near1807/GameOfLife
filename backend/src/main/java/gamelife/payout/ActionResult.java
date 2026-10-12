package gamelife.payout;

import gamelife.ledger.LedgerEvent;

/**
 * Answer to any action. paid=true when a NEW ledger event was written by this call.
 * paid=false with an event means "already paid before" (double tap, retry): harmless.
 * event is null when nothing was paid at all (for example a timer answered No).
 */
public record ActionResult(LedgerEvent event, boolean paid, String message) {
    public static ActionResult nothing(String message) {
        return new ActionResult(null, false, message);
    }
}
