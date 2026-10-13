package gamelife.fight;

import gamelife.IntegrationTest;
import gamelife.ledger.EventType;
import gamelife.rules.LedgerFacts;
import gamelife.tasks.TaskService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.io.IOException;
import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** defeatMode "hard": a fall also costs 20% of the coins. Stat XP is never taken. */
class HardDefeatTest extends IntegrationTest {
    @DynamicPropertySource
    static void mode(DynamicPropertyRegistry r) throws IOException {
        DefeatModes.use("hard", r);
    }

    @Autowired FightService fight;
    @Autowired TaskService tasks;

    @Test
    void fallCostsTwentyPercentOfTheCoinsButNoStatXp() {
        tasks.tick(tasks.create("Start", 500, List.of("INTELLECT")).id()); // 550 coins
        var stats = LedgerFacts.statTotals(ledger.all());
        clock.advance(Duration.ofDays(8));
        fight.sync();
        var fell = ledger.all().stream().filter(e -> e.type() == EventType.HERO_FELL).findFirst().orElseThrow();
        assertThat(fell.currencyDelta()).isEqualByComparingTo("-110"); // 20% of 550
        assertThat(LedgerFacts.balance(ledger.all())).isEqualByComparingTo("440");
        assertThat(LedgerFacts.statTotals(ledger.all())).isEqualTo(stats);
    }
}
