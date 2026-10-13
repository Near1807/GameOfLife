package gamelife.review;

import gamelife.IntegrationTest;
import gamelife.habits.HabitService;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;

/** Brief 9.1: the weekly review is a calendar-week report, and the stats dashboard. */
@AutoConfigureMockMvc
class ReviewTest extends IntegrationTest {
    @Autowired ReviewService review;
    @Autowired HabitService habits;
    @Autowired TaskService tasks;
    @Autowired MockMvc mvc;

    @Test
    void weekReviewShowsDamageDealtTakenAndSkippedHabits() throws Exception {
        var h = habits.create("Read", EnumSet.allOf(DayOfWeek.class), List.of("INTELLECT"));
        habits.done(h.id());                                         // Monday: 22 XP
        tasks.tick(tasks.create("Task", 60, List.of("INTELLECT")).id()); // + 66 XP
        clock.advance(Duration.ofDays(2));                           // Tuesday missed, Wednesday today
        var r = review.week(null);
        assertThat(r.weekStart()).isEqualTo(LocalDate.of(2026, 3, 2));
        assertThat(r.days()).hasSize(7);
        assertThat(r.days().get(0).xp()).isEqualTo(88.0);
        assertThat(r.damageDealt()).isEqualTo(88.0);
        // Monday 88 XP: shield 0.88, 20 x 0.12 = 2.4; Tuesday zero XP: 20
        assertThat(r.damageTaken()).isEqualTo(22.4);
        assertThat(r.falls()).isZero();
        assertThat(r.statXp().get("INTELLECT")).isEqualTo(80.0);
        assertThat(r.skippedHabits()).singleElement().satisfies(s -> assertThat(s.missed()).isEqualTo(1));
        mvc.perform(get("/api/review").param("week", "2026-03-04"))
                .andExpect(jsonPath("$.weekStart").value("2026-03-02"))
                .andExpect(jsonPath("$.days.length()").value(7));
    }

    @Test
    void dashboardHistoryAndStreaks() throws Exception {
        for (int i = 0; i < 3; i++) {
            tasks.tick(tasks.create("T" + i, 10, List.of()).id());
            clock.advance(Duration.ofDays(1));
        }
        var d = review.dashboard(30);
        assertThat(d.history()).hasSize(30);
        assertThat(d.bestStreak()).isEqualTo(3);
        assertThat(d.history().get(29).streak()).isEqualTo(3);
        assertThat(d.tasksDoneAllTime()).isEqualTo(3);
        mvc.perform(get("/api/dashboard").param("days", "14")).andExpect(jsonPath("$.history.length()").value(14));
    }
}
