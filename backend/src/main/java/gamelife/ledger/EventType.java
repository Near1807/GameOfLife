package gamelife.ledger;

import java.util.Set;

/** Every kind of ledger event (brief section 5). */
public enum EventType {
    TASK_DONE,
    TASK_UNDONE,
    TIMER_VALIDATED,
    HABIT_DONE,
    CHECKIN,
    PURCHASE,
    UPGRADE,
    STAGE_CLEARED,
    ASCEND,
    FREEZE_USED,
    REST_DAY,
    CORRECTION;

    /** Events that are a "validated action": they make a day count for the streak (brief 7). */
    public static final Set<EventType> VALIDATIONS = Set.of(TASK_DONE, TIMER_VALIDATED, HABIT_DONE, CHECKIN);
}
