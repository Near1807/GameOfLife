package gamelife.stage;

import gamelife.IntegrationTest;
import gamelife.config.GameConfig;
import gamelife.habits.HabitService;
import gamelife.ledger.EventType;
import gamelife.payout.PayoutService;
import gamelife.review.ReviewService;
import gamelife.tasks.Task;
import gamelife.tasks.TaskService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

import java.time.DayOfWeek;
import java.time.Duration;
import java.time.LocalDate;
import java.util.EnumSet;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;

/**
 * Brief 9.1 and v2 4.4: weekly boss damage, kill, week rollover, replay of a missed week,
 * ascension, weekly review and dashboard. Tests start on Monday 2 March 2026, 10:00 Brussels.
 */
@AutoConfigureMockMvc
class StageTest extends IntegrationTest {
    @Autowired StageService stages;
    @Autowired TaskService tasks;
    @Autowired HabitService habits;
    @Autowired PayoutService payout;
    @Autowired ReviewService review;
    @Autowired GameConfig config;
    @Autowired MockMvc mvc;

    /** Creates and ticks a task worth `minutes` base XP; returns the final XP paid. */
    private double hit(int minutes) {
        Task t = tasks.create("Hit " + minutes + " " + clock.instant(), minutes, List.of("INTELLECT"));
        return tasks.tick(t.id()).event().xp().doubleValue();
    }

    private long clears() {
        return ledger.all().stream().filter(e -> e.type() == EventType.STAGE_CLEARED).count();
    }

    @Test
    void targetsMatchTheBriefTable() {
        // the brief's table, except stage 7: 800 x 1.3^6 = 3861.45 rounds to 3861 (the table says 3862)
        double[] expected = {800, 1040, 1352, 1758, 2285, 2970, 3861, 5020, 6526, 8484};
        for (int k = 1; k <= 10; k++) {
            assertThat(config.stages().target(k)).isEqualTo(expected[k - 1]);
        }
    }

    @Test
    void freshGameFightsStageOneAtFullHealth() {
        var s = stages.current();
        assertThat(s.stage()).isEqualTo(1);
        assertThat(s.hp()).isEqualTo(800);
        assertThat(s.boss().name()).isEqualTo("Bug Queen");
        assertThat(s.weekStart()).isEqualTo(LocalDate.of(2026, 3, 2));
        assertThat(s.endsAt()).isEqualTo(at(2026, 3, 9, 4, 0));
        assertThat(s.map()).extracting(StageService.Node::status).first().isEqualTo("CURRENT");
    }

    @Test
    void bossDamageIsTheWeeksFinalXpAndUntickHealsIt() {
        Task t = tasks.create("Big study block", 300, List.of("INTELLECT"));
        double xp = tasks.tick(t.id()).event().xp().doubleValue(); // 300 x 1.1 = 330
        assertThat(stages.current().damage()).isEqualTo(xp).isEqualTo(330.0);
        assertThat(stages.current().hp()).isEqualTo(470.0);
        tasks.untick(t.id());
        assertThat(stages.current().hp()).isEqualTo(800.0);
    }

    @Test
    void killWritesOneStageClearedWithItsCoinRewardOnly() {
        double xp = hit(800); // 880 damage
        var s = stages.current();
        assertThat(s.cleared()).isTrue();
        assertThat(s.hp()).isZero();
        assertThat(s.overkill()).isEqualTo(80.0);
        assertThat(clears()).isEqualTo(1);
        var clear = ledger.all().stream().filter(e -> e.type() == EventType.STAGE_CLEARED).findFirst().orElseThrow();
        assertThat(clear.xp()).isEqualByComparingTo("0");          // no XP, no damage, no stat XP
        assertThat(clear.currencyDelta()).isEqualByComparingTo("100");
        assertThat(clear.statSplits()).isEqualTo("{}");
        assertThat(balance().doubleValue()).isEqualTo(xp + 100);

        hit(100); // more XP the same week: overkill only, no second kill
        assertThat(clears()).isEqualTo(1);
        assertThat(stages.current().overkill()).isGreaterThan(80.0);
        assertThat(ledger.verify().valid()).isTrue();
    }

    @Test
    void weekRolloverAtMondayFourAm() {
        hit(800); // stage 1 killed this week
        setLocal(2026, 3, 9, 3, 59); // Monday 03:59: still last week's game day (Sunday)
        assertThat(stages.current().stage()).isEqualTo(1);
        assertThat(stages.current().cleared()).isTrue();

        setLocal(2026, 3, 9, 4, 0); // new game week
        var s = stages.current();
        assertThat(s.stage()).isEqualTo(2);
        assertThat(s.target()).isEqualTo(1040.0);
        assertThat(s.damage()).isZero();
        assertThat(s.hp()).isEqualTo(1040.0);
        assertThat(s.cleared()).isFalse();
        assertThat(s.boss().name()).isEqualTo("Doomscroll Wraith");
        assertThat(s.map()).extracting(StageService.Node::status).startsWith("CLEARED", "CURRENT", "LOCKED");
    }

    @Test
    void missedWeekReplaysTheSameStageAtFullHealth() {
        hit(300); // 330 damage: boss survives the week
        setLocal(2026, 3, 9, 4, 0);
        var s = stages.current();
        assertThat(s.stage()).isEqualTo(1);           // same stage again
        assertThat(s.hp()).isEqualTo(800.0);          // healed: last week's damage does not carry over
        assertThat(s.damage()).isZero();
        assertThat(clears()).isZero();
        // and it can be killed in the replay week
        hit(800);
        assertThat(stages.current().cleared()).isTrue();
        setLocal(2026, 3, 16, 4, 0);
        assertThat(stages.current().stage()).isEqualTo(2);
    }

    @Test
    void ascensionAfterStageTenResetsStageAndWeaponAndBoostsXp() {
        assertThatThrownBy(() -> stages.ascend()).hasMessageContaining("Clear stage 10");
        LocalDate monday = LocalDate.of(2026, 3, 2);
        for (int k = 1; k <= 10; k++) {
            clock.set(at(monday.getYear(), monday.getMonthValue(), monday.getDayOfMonth(), 10, 0));
            hit((int) config.stages().target(k));
            assertThat(stages.current().cleared()).isTrue();
            monday = monday.plusDays(7);
        }
        assertThat(stages.current().ascendReady()).isTrue();
        clock.set(at(monday.getYear(), monday.getMonthValue(), monday.getDayOfMonth(), 10, 0));
        assertThat(stages.current().ascendReady()).isTrue(); // no boss until ascension
        assertThat(stages.current().cleared()).isFalse();

        stages.ascend();
        var s = stages.current();
        assertThat(s.stage()).isEqualTo(1);
        assertThat(s.ascensions()).isEqualTo(1);
        assertThat(s.damage()).isZero();
        assertThat(payout.multipliersNow(ledger.all()).ascension()).isEqualTo(1.05);
        assertThat(payout.multipliersNow(ledger.all()).weapon()).isEqualTo(1.0);
        assertThatThrownBy(() -> stages.ascend()).hasMessageContaining("Clear stage 10"); // not again until the next stage 10
    }

    @Test
    void weeklyReviewSummarisesTheWeek() throws Exception {
        var h = habits.create("Read", EnumSet.allOf(DayOfWeek.class), List.of("INTELLECT"));
        habits.done(h.id());                 // Monday done
        hit(60);                              // + task
        clock.advance(Duration.ofDays(2));   // Tuesday missed, Wednesday today
        var r = review.week(null);
        assertThat(r.weekStart()).isEqualTo(LocalDate.of(2026, 3, 2));
        assertThat(r.days()).hasSize(7);
        assertThat(r.days().get(0).xp()).isEqualTo(22.0 + 66.0);
        assertThat(r.days().get(0).counted()).isTrue();
        assertThat(r.tasksDone()).isEqualTo(1);
        assertThat(r.habitsDone()).isEqualTo(1);
        assertThat(r.statXp().get("INTELLECT")).isEqualTo(80.0);
        assertThat(r.skippedHabits()).singleElement()
                .satisfies(s -> assertThat(s.missed()).isEqualTo(1));
        assertThat(r.damage()).isEqualTo(88.0);
        mvc.perform(get("/api/review").param("week", "2026-03-04"))
                .andExpect(jsonPath("$.weekStart").value("2026-03-02"))
                .andExpect(jsonPath("$.days.length()").value(7));
    }

    @Test
    void dashboardHistoryAndStreaks() throws Exception {
        for (int i = 0; i < 3; i++) {
            hit(10);
            clock.advance(Duration.ofDays(1));
        }
        var d = review.dashboard(30);
        assertThat(d.history()).hasSize(30);
        assertThat(d.bestStreak()).isEqualTo(3);
        assertThat(d.history().get(29).streak()).isEqualTo(3); // today not done yet, streak alive
        assertThat(d.tasksDoneAllTime()).isEqualTo(3);
        mvc.perform(get("/api/dashboard").param("days", "14")).andExpect(jsonPath("$.history.length()").value(14));
    }
}
