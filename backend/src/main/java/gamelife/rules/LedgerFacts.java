package gamelife.rules;

import gamelife.common.Json;
import gamelife.common.Money;
import gamelife.ledger.EventType;
import gamelife.ledger.LedgerEvent;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/**
 * Everything the game "knows" is computed here from the list of ledger events
 * (brief 3.2 and 5: no editable totals, always recomputed). Pure functions, easy to test.
 */
public final class LedgerFacts {
    private LedgerFacts() {}

    /** Balance = sum of all currency deltas. */
    public static BigDecimal balance(List<LedgerEvent> events) {
        return events.stream().map(LedgerEvent::currencyDelta).reduce(Money.ZERO, BigDecimal::add);
    }

    /** Final XP validated on one game day (reversals included, so an un-tick removes it). */
    public static BigDecimal xpOnDay(List<LedgerEvent> events, LocalDate day) {
        return events.stream().filter(e -> e.gameDay().equals(day))
                .map(LedgerEvent::xp).reduce(Money.ZERO, BigDecimal::add);
    }

    /** Total stat XP per stat id (reversals carry negative splits). */
    public static Map<String, BigDecimal> statTotals(List<LedgerEvent> events) {
        Map<String, BigDecimal> totals = new TreeMap<>();
        for (LedgerEvent e : events) {
            Json.toMap(e.statSplits()).forEach((stat, value) ->
                    totals.merge(stat, Money.parse(value.toString()), BigDecimal::add));
        }
        return totals;
    }

    /**
     * Game days with at least one validated action that was not reversed afterwards
     * (brief 7: "a day counts when it has at least one validated action").
     */
    public static Set<LocalDate> countedDays(List<LedgerEvent> events) {
        Set<String> reversedKeys = new HashSet<>();
        for (LedgerEvent e : events) {
            if (e.type() == EventType.TASK_UNDONE) {
                Object reverses = Json.toMap(e.meta()).get("reverses");
                if (reverses != null) {
                    reversedKeys.add(reverses.toString());
                }
            }
        }
        Set<LocalDate> days = new HashSet<>();
        for (LedgerEvent e : events) {
            if (EventType.VALIDATIONS.contains(e.type()) && !reversedKeys.contains(e.key())) {
                days.add(e.gameDay());
            }
        }
        return days;
    }

    /** Planned rest days. The date is the part after "rest:" in the key. */
    public static Set<LocalDate> restDays(List<LedgerEvent> events) {
        Set<LocalDate> days = new HashSet<>();
        for (LedgerEvent e : events) {
            if (e.type() == EventType.REST_DAY) {
                days.add(LocalDate.parse(e.key().substring("rest:".length())));
            }
        }
        return days;
    }

    /** Number of ASCEND events (milestone 4; 0 until then). */
    public static int ascensions(List<LedgerEvent> events) {
        return (int) events.stream().filter(e -> e.type() == EventType.ASCEND).count();
    }

    /** Weapon level = UPGRADE events since the last ASCEND (brief 9.1: ascension resets it). */
    public static int weaponLevel(List<LedgerEvent> events) {
        int level = 0;
        for (LedgerEvent e : events) {
            if (e.type() == EventType.UPGRADE) {
                level++;
            } else if (e.type() == EventType.ASCEND) {
                level = 0;
            }
        }
        return level;
    }
}
