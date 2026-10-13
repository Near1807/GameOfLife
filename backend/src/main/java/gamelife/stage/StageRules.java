package gamelife.stage;

import gamelife.config.GameConfig;
import gamelife.ledger.EventType;
import gamelife.ledger.LedgerEvent;
import gamelife.time.GameTime;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * Pure rules of the weekly boss (brief 9.1). Everything comes from the ledger:
 *  - A game week runs Monday 04:00 to Monday 04:00. Damage = the final XP validated that week
 *    (un-ticks count negative).
 *  - The stage fought this week = 1 + the bosses killed in earlier weeks of the current cycle
 *    (a cycle starts at the last ASCEND). So a week without a kill simply replays the same stage
 *    next week, and since damage only counts this week's XP, the boss is back to full health.
 *  - A kill is the STAGE_CLEARED event with key stage:{ascensions}:{weekStart}, so at most one
 *    per week and cycle.
 *  - After stage `count` is cleared, there is no boss until the owner ascends.
 */
public final class StageRules {
    private StageRules() {}

    public record Progress(int stage, int stageCount, double target, double damage, boolean cleared,
                           boolean ascendReady, int ascensions, LocalDate weekStart) {
        public double hp() {
            return Math.max(0, target - damage);
        }

        public double overkill() {
            return Math.max(0, damage - target);
        }

        /** True when there is a living boss this week. */
        public boolean bossAlive() {
            return !ascendReady && !cleared;
        }
    }

    public static String clearKey(int ascensions, LocalDate weekStart) {
        return "stage:" + ascensions + ":" + weekStart;
    }

    /**
     * @param events    the ledger, in id order (events after the week are ignored)
     * @param weekStart Monday of the week to evaluate
     */
    public static Progress compute(List<LedgerEvent> events, LocalDate weekStart, GameConfig.Stages cfg) {
        LocalDate nextWeek = weekStart.plusDays(7);
        // the current cycle: events after the last ASCEND, up to the end of that week
        List<LedgerEvent> cycle = new ArrayList<>();
        int ascensions = 0;
        for (LedgerEvent e : events) {
            if (!e.gameDay().isBefore(nextWeek)) {
                break;
            }
            if (e.type() == EventType.ASCEND) {
                ascensions++;
                cycle.clear();
            } else {
                cycle.add(e);
            }
        }
        String thisWeekKey = clearKey(ascensions, weekStart);
        boolean cleared = false;
        int clearsBefore = 0;
        double damage = 0;
        for (LedgerEvent e : cycle) {
            if (e.type() == EventType.STAGE_CLEARED) {
                if (e.key().equals(thisWeekKey)) {
                    cleared = true;
                } else {
                    clearsBefore++;
                }
            }
            if (GameTime.weekStart(e.gameDay()).equals(weekStart)) {
                damage += e.xp().doubleValue();
            }
        }
        int stage = Math.min(clearsBefore + 1, cfg.count());
        boolean ascendReady = clearsBefore >= cfg.count() || (cleared && stage == cfg.count());
        damage = Math.round(damage * 100.0) / 100.0;
        return new Progress(stage, cfg.count(), cfg.target(stage), damage, cleared && !(clearsBefore >= cfg.count()),
                ascendReady, ascensions, weekStart);
    }
}
