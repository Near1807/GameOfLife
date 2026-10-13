package gamelife.fight;

import gamelife.IntegrationTest;
import gamelife.ledger.EventType;
import gamelife.tasks.TaskService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.io.IOException;
import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** defeatMode "medium": after a fall, the next kill of that boss pays no coin reward. */
class MediumDefeatTest extends IntegrationTest {
    @DynamicPropertySource
    static void mode(DynamicPropertyRegistry r) throws IOException {
        DefeatModes.use("medium", r);
    }

    @Autowired FightService fight;
    @Autowired TaskService tasks;

    @Test
    void nextKillAfterAFallPaysNoReward() {
        tasks.tick(tasks.create("Start", 10, List.of()).id());
        clock.advance(Duration.ofDays(8)); // seven zero days: the hero falls
        fight.sync();
        assertThat(fight.view().falls()).isEqualTo(1);
        var kill = tasks.tick(tasks.create("Revenge", 800, List.of()).id());
        assertThat(kill.paid()).isTrue();
        var clear = ledger.all().stream().filter(e -> e.type() == EventType.STAGE_CLEARED).findFirst().orElseThrow();
        assertThat(clear.currencyDelta()).isEqualByComparingTo("0");

        // the boss after that pays normally again
        tasks.tick(tasks.create("Next", 1000, List.of()).id());
        var second = ledger.all().stream().filter(e -> e.type() == EventType.STAGE_CLEARED).skip(1).findFirst().orElseThrow();
        assertThat(second.currencyDelta()).isEqualByComparingTo("100");
    }
}
