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
    CORRECTION,
    /** v3 boss fight (kept so old ledgers still read). The boss hits the hero at the daily reset (xp 0). One per past game day. */
    BOSS_ATTACK,
    /** Hero HP reached 0: the boss heals, the hero gets back up. */
    HERO_FELL,
    /** A paused day (illness, travel): fully shielded. */
    PAUSE,
    /** An item was equipped or unequipped (no coins, no XP). */
    EQUIP,
    /** v4 tower defense: a locked spot was bought (currency minus). */
    SPOT_UNLOCKED,
    /** A tower was built on a spot (currency minus, the price actually paid). */
    TOWER_BUILT,
    /** One tier of one upgrade path was bought (currency minus). */
    TOWER_UPGRADED,
    /** A tower was sold: currency plus, exactly what was paid for it. */
    TOWER_SOLD,
    /** An attempt of a wave that was won (no XP, no currency). */
    WAVE_CLEARED,
    /** An attempt of a wave that was lost (no XP, no currency). */
    WAVE_FAILED;

    /** Events that are a "validated action": they make a day count for the streak (brief 7). */
    public static final Set<EventType> VALIDATIONS = Set.of(TASK_DONE, TIMER_VALIDATED, HABIT_DONE, CHECKIN);
}
