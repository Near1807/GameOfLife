package gamelife.character;

import gamelife.IntegrationTest;
import gamelife.checkin.CheckinService;
import gamelife.habits.HabitService;
import gamelife.tasks.Task;
import gamelife.tasks.TaskService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.DayOfWeek;
import java.time.Duration;
import java.util.EnumSet;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;

/** Version 2, section 4.1 and 4.2: stat tags on every action, character sheet from the ledger. */
@AutoConfigureMockMvc
class CharacterTest extends IntegrationTest {
    @Autowired CharacterService character;
    @Autowired TaskService tasks;
    @Autowired CheckinService checkin;
    @Autowired HabitService habits;
    @Autowired MockMvc mvc;

    private CharacterService.StatView stat(String id) {
        return character.compute(ledger.all()).stats().stream().filter(s -> s.id().equals(id)).findFirst().orElseThrow();
    }

    @Test
    void freshCharacterIsLevelOneEverywhere() {
        var c = character.compute(ledger.all());
        assertThat(c.stats()).hasSize(7).allMatch(s -> s.level() == 1 && s.xp() == 0);
        assertThat(c.overallLevel()).isEqualTo(1);
        assertThat(c.className()).isEqualTo("Unawakened");
        assertThat(c.rank()).isEqualTo("Novice");
    }

    @Test
    void earningTaggedXpRaisesLevelsAndSetsTheClass() {
        Task t = tasks.create("Big project", 240, List.of("INTELLECT", "DISCIPLINE")); // 120 base each
        tasks.tick(t.id());
        assertThat(stat("INTELLECT").level()).isEqualTo(2);
        assertThat(stat("INTELLECT").xp()).isEqualTo(120.0);
        assertThat(stat("DISCIPLINE").level()).isEqualTo(2);
        var c = character.compute(ledger.all());
        assertThat(c.overallLevel()).isEqualTo(3);
        assertThat(c.className()).isEqualTo("Code Monk");
        assertThat(c.topStats()).containsExactly("INTELLECT", "DISCIPLINE");
    }

    @Test
    void untickTakesTheStatXpBack() {
        Task t = tasks.create("Sprint", 150, List.of("STRENGTH"));
        tasks.tick(t.id());
        assertThat(stat("STRENGTH").level()).isEqualTo(2);
        tasks.untick(t.id());
        assertThat(stat("STRENGTH").level()).isEqualTo(1);
        assertThat(stat("STRENGTH").xp()).isZero();
    }

    @Test
    void untaggedActionEarnsCurrencyOnly() {
        Task t = tasks.create("Admin", 60, List.of());
        tasks.tick(t.id());
        assertThat(balance()).isPositive();
        assertThat(character.compute(ledger.all()).stats()).allMatch(s -> s.xp() == 0);
    }

    @Test
    void checkinTakesTagsAndRemembersThem() throws Exception {
        mvc.perform(post("/api/checkin").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"mood\":4,\"tags\":[\"SPIRIT\",\"VITALITY\"]}"))
                .andExpect(jsonPath("$.event.statSplits.SPIRIT").value("2.50"))
                .andExpect(jsonPath("$.event.statSplits.VITALITY").value("2.50"));
        assertThat(checkin.lastTags()).containsExactly("SPIRIT", "VITALITY");
        clock.advance(Duration.ofDays(1));
        var r = checkin.checkin(3); // no tags sent: the remembered ones
        assertThat(r.event().statSplits()).isEqualTo("{\"SPIRIT\":\"2.50\",\"VITALITY\":\"2.50\"}");
    }

    @Test
    void characterEndpoint() throws Exception {
        tasks.tick(tasks.create("Run", 100, List.of("STRENGTH")).id());
        mvc.perform(get("/api/character"))
                .andExpect(jsonPath("$.stats.length()").value(7))
                .andExpect(jsonPath("$.stats[0].id").value("STRENGTH"))
                .andExpect(jsonPath("$.stats[0].level").value(2))
                .andExpect(jsonPath("$.overallLevel").value(2));
    }

    @Test
    void habitHistoryAndStreak() {
        var h = habits.create("Walk", EnumSet.allOf(DayOfWeek.class), List.of("VITALITY"));
        habits.done(h.id());
        clock.advance(Duration.ofDays(1));
        habits.done(h.id());
        clock.advance(Duration.ofDays(1)); // today still open
        var view = habits.list().get(0);
        assertThat(view.streak()).isEqualTo(2);
        var last = view.history().subList(view.history().size() - 3, view.history().size());
        assertThat(last).extracting(HabitService.DayStatus::status).containsExactly("DONE", "DONE", "TODAY");
        clock.advance(Duration.ofDays(2)); // two days missed
        view = habits.list().get(0);
        assertThat(view.streak()).isZero();
        assertThat(view.history().get(view.history().size() - 2).status()).isEqualTo("MISSED");
    }
}
