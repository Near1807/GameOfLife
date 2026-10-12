package gamelife.backup;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import gamelife.IntegrationTest;
import gamelife.checkin.CheckinService;
import gamelife.habits.HabitService;
import gamelife.payout.PayoutService;
import gamelife.rules.LedgerFacts;
import gamelife.tasks.Task;
import gamelife.tasks.TaskService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Brief 3.3, 5 and 14: export then import gives the same balances and a valid chain. */
@AutoConfigureMockMvc
class BackupTest extends IntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired TaskService tasks;
    @Autowired HabitService habits;
    @Autowired CheckinService checkin;
    @Autowired PayoutService payout;
    @Autowired ObjectMapper mapper;

    private void playAFewDays() {
        var h = habits.create("Read", null, List.of("INTELLECT"));
        for (int day = 0; day < 5; day++) {
            Task t = tasks.create("Task day " + day, 15 + day * 10, List.of("STRENGTH", "SPIRIT"));
            tasks.tick(t.id());
            habits.done(h.id());
            checkin.checkin(1 + day % 5);
            clock.advance(Duration.ofDays(1));
        }
        tasks.create("Still open", 30, null);
    }

    @Test
    void exportThenImportGivesSameBalancesAndValidChain() throws Exception {
        playAFewDays();
        BigDecimal balance = balance();
        Map<String, BigDecimal> stats = LedgerFacts.statTotals(ledger.all());
        int streak = payout.streakNow(ledger.all()).streakDays();

        String file = mvc.perform(get("/api/backup/export"))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition", org.hamcrest.Matchers.containsString("gamelife-backup-")))
                .andReturn().getResponse().getContentAsString();

        // wipe everything, as on a new machine
        jdbc.update("DELETE FROM ledger_events");
        jdbc.update("DELETE FROM tasks");
        jdbc.update("DELETE FROM habits");
        assertThat(balance()).isEqualByComparingTo("0");

        mvc.perform(post("/api/backup/import").contentType(MediaType.APPLICATION_JSON).content(file))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.valid").value(true));

        assertThat(balance()).isEqualByComparingTo(balance);
        assertThat(LedgerFacts.statTotals(ledger.all())).isEqualTo(stats);
        assertThat(payout.streakNow(ledger.all()).streakDays()).isEqualTo(streak);
        assertThat(ledger.verify().valid()).isTrue();
        assertThat(tasks.list()).extracting(v -> v.name()).contains("Still open");
    }

    @Test
    void tamperedBackupIsRefusedAndNothingChanges() throws Exception {
        playAFewDays();
        BigDecimal balance = balance();
        String file = mvc.perform(get("/api/backup/export")).andReturn().getResponse().getContentAsString();

        ObjectNode json = (ObjectNode) mapper.readTree(file);
        ((ObjectNode) json.get("events").get(2)).put("xp", "5000.00");
        ((ObjectNode) json.get("events").get(2)).put("currencyDelta", "5000.00");

        mvc.perform(post("/api/backup/import").contentType(MediaType.APPLICATION_JSON).content(json.toString()))
                .andExpect(status().isBadRequest());
        assertThat(balance()).isEqualByComparingTo(balance);
        assertThat(ledger.verify().valid()).isTrue();
    }

    @Test
    void notABackupFileIsRefused() throws Exception {
        mvc.perform(post("/api/backup/import").contentType(MediaType.APPLICATION_JSON).content("{\"format\":\"other\"}"))
                .andExpect(status().isBadRequest());
    }
}
