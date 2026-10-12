package gamelife.time;

import gamelife.config.GameConfig;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;

/**
 * Game calendar. A game day runs from 04:00 to 04:00 local time (brief section 4),
 * so a task ticked at 01:30 still counts for the evening before.
 * A game week runs from Monday 04:00 to the next Monday 04:00 (brief 9.1).
 */
@Component
public class GameTime {
    private final Clock clock;
    private final GameConfig config;

    public GameTime(Clock clock, GameConfig config) {
        this.clock = clock;
        this.config = config;
    }

    /** Current server time. The only source of time for the ledger. */
    public Instant now() {
        return clock.instant();
    }

    /** The game day the current moment belongs to. */
    public LocalDate today() {
        return gameDay(now());
    }

    /** The game day an instant belongs to: local date of (instant - dayStartHour hours). */
    public LocalDate gameDay(Instant instant) {
        return instant.atZone(config.time().zoneId())
                .minusHours(config.time().dayStartHour())
                .toLocalDate();
    }

    /** Monday of the game week a game day belongs to. */
    public static LocalDate weekStart(LocalDate gameDay) {
        return gameDay.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
    }
}
