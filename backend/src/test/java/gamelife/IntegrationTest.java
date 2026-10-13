package gamelife;

import gamelife.ledger.LedgerService;
import gamelife.rules.LedgerFacts;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;

/**
 * Base class of the integration tests: the real Spring app on a test SQLite file, with the
 * real config file and a clock the test controls. Every test starts with an empty database
 * on Monday 2 March 2026, 10:00 Brussels time.
 */
@SpringBootTest(properties = {
        "gamelife.db-path=target/test-data/gamelife-test.db",
        "gamelife.config-path=../config/gamelife.json",
        "gamelife.data-dir=target/test-data/data",
        "gamelife.backup.check-delay-ms=3600000000"
})
@Import(IntegrationTest.ClockConfig.class)
public abstract class IntegrationTest {

    public static final ZoneId BRUSSELS = ZoneId.of("Europe/Brussels");

    @TestConfiguration
    public static class ClockConfig {
        @Bean
        @Primary
        public MutableClock testClock() {
            return new MutableClock(at(2026, 3, 2, 10, 0));
        }
    }

    @Autowired
    protected MutableClock clock;
    @Autowired
    protected JdbcTemplate jdbc;
    @Autowired
    protected LedgerService ledger;

    @BeforeEach
    void cleanDatabase() {
        jdbc.update("DELETE FROM ledger_events");
        jdbc.update("DELETE FROM tasks");
        jdbc.update("DELETE FROM timers");
        jdbc.update("DELETE FROM habits");
        jdbc.update("DELETE FROM sqlite_sequence");
        clock.set(at(2026, 3, 2, 10, 0));
    }

    /** An instant given in Brussels local time. */
    public static Instant at(int y, int mo, int d, int h, int mi) {
        return LocalDateTime.of(y, mo, d, h, mi).atZone(BRUSSELS).toInstant();
    }

    protected void setLocal(int y, int mo, int d, int h, int mi) {
        clock.set(at(y, mo, d, h, mi));
    }

    protected BigDecimal balance() {
        return LedgerFacts.balance(ledger.all());
    }

    protected int eventCount() {
        return ledger.all().size();
    }
}
