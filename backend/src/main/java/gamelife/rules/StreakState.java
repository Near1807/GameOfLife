package gamelife.rules;

import java.time.LocalDate;

/**
 * Result of the streak computation (brief section 7). Never stored: always recomputed
 * from the ledger.
 *
 * @param streakDays       consecutive counted days (today included once it is counted)
 * @param freezes          freezes currently stored
 * @param todayCounted     today already has a validated action
 * @param gapDays          days without any validated action right before today
 * @param comebackPending  the next validated action today gets the comeback bonus
 * @param streakForPayment streak value used for a payment made now (today counts once paid)
 * @param countedDaysTotal all counted days ever (freezes are earned from this)
 * @param lastCountedDay   most recent counted day, or null
 */
public record StreakState(
        int streakDays,
        int freezes,
        boolean todayCounted,
        int gapDays,
        boolean comebackPending,
        int streakForPayment,
        int countedDaysTotal,
        LocalDate lastCountedDay) {
}
