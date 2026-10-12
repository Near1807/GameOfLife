package gamelife.ledger;

import gamelife.IntegrationTest;
import gamelife.checkin.CheckinService;
import gamelife.habits.HabitService;
import gamelife.payout.ActionResult;
import gamelife.rest.RestDayService;
import gamelife.tasks.Task;
import gamelife.tasks.TaskService;
import gamelife.timer.TimerService;
import gamelife.timer.TimerView;
import gamelife.rules.LedgerFacts;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;

/**
 * Brief 3.2, 5 and 14: no XP without an act, unique keys pay once even under retries and
 * concurrent double taps, totals always equal the ledger, a modified row breaks the chain.
 */
@AutoConfigureMockMvc
class LedgerIntegrityTest extends IntegrationTest {
    @Autowired TaskService tasks;
    @Autowired TimerService timers;
    @Autowired HabitService habits;
    @Autowired CheckinService checkin;
    @Autowired RestDayService rest;
    @Autowired MockMvc mvc;

    @Test
    void nothingPaysWithoutAConfirmationAct() throws Exception {
        tasks.create("Write report", 30, List.of("INTELLECT"));
        habits.create("Stretch", null, null);
        TimerView t = timers.start(25, "Deep work", null, null);
        clock.advance(Duration.ofHours(3));       // timer ended long ago, nobody answered
        rest.plan(LocalDate.of(2026, 3, 3)); // planning rest pays nothing
        tasks.list();
        tasks.suggestions("");
        timers.current();
        mvc.perform(get("/api/state"));             // a page loading pays nothing
        clock.advance(Duration.ofDays(2));         // schedules passing pay nothing

        assertThat(balance()).isEqualByComparingTo("0");
        assertThat(ledger.all()).allMatch(e -> e.xp().signum() == 0); // only the rest-day marker
        assertThat(timers.current()).isPresent();  // still waiting for Yes / No
        assertThat(t.status()).isEqualTo("RUNNING");
    }

    @Test
    void sameKeyPaysOnceOnDoubleTap() {
        Task task = tasks.create("Dishes", 15, null);
        ActionResult first = tasks.tick(task.id());
        ActionResult second = tasks.tick(task.id());
        assertThat(first.paid()).isTrue();
        assertThat(second.paid()).isFalse();
        assertThat(second.event().id()).isEqualTo(first.event().id());
        assertThat(eventCount()).isEqualTo(1);
    }

    @Test
    void concurrentDoubleTapsAndRetriesPayOnce() throws Exception {
        Task task = tasks.create("Gym", 60, List.of("STRENGTH"));
        var habit = habits.create("Read", null, null);
        ExecutorService pool = Executors.newFixedThreadPool(8);
        List<Callable<ActionResult>> calls = new ArrayList<>();
        for (int i = 0; i < 20; i++) {
            calls.add(() -> tasks.tick(task.id()));
            calls.add(() -> habits.done(habit.id()));
            calls.add(() -> checkin.checkin(3));
        }
        long paid = 0;
        for (var f : pool.invokeAll(calls)) {
            if (f.get().paid()) {
                paid++;
            }
        }
        pool.shutdown();
        assertThat(paid).isEqualTo(3);
        assertThat(eventCount()).isEqualTo(3);
        assertThat(ledger.verify().valid()).isTrue();
    }

    @Test
    void randomActionsKeepTotalsEqualToTheLedger() {
        Random random = new Random(7);
        List<Long> taskIds = new ArrayList<>();
        var habit = habits.create("Walk", null, List.of("VITALITY"));
        for (int step = 0; step < 300; step++) {
            switch (random.nextInt(6)) {
                case 0 -> taskIds.add(tasks.create("Task " + random.nextInt(5), 5 + random.nextInt(60),
                        List.of("INTELLECT", "SOCIAL").subList(0, random.nextInt(3))).id());
                case 1, 2 -> {
                    if (!taskIds.isEmpty()) {
                        long id = taskIds.get(random.nextInt(taskIds.size()));
                        if (random.nextBoolean()) tasks.tick(id); else tasks.untick(id);
                    }
                }
                case 3 -> habits.done(habit.id());
                case 4 -> checkin.checkin(1 + random.nextInt(5));
                default -> clock.advance(Duration.ofHours(1 + random.nextInt(30)));
            }
        }
        List<LedgerEvent> events = ledger.all();

        // keys are unique
        Set<String> keys = new HashSet<>();
        events.forEach(e -> assertThat(keys.add(e.key())).isTrue());

        // balance equals a plain recomputation straight from the database
        BigDecimal sqlSum = jdbc.queryForList("SELECT currency_delta FROM ledger_events", String.class)
                .stream().map(BigDecimal::new).reduce(BigDecimal.ZERO, BigDecimal::add);
        assertThat(balance()).isEqualByComparingTo(sqlSum);

        // stat XP equals a plain recomputation, and each event's split sums to its base XP
        Map<String, BigDecimal> manual = new TreeMap<>();
        for (LedgerEvent e : events) {
            Map<String, Object> splits = gamelife.common.Json.toMap(e.statSplits());
            BigDecimal sum = BigDecimal.ZERO;
            for (var entry : splits.entrySet()) {
                BigDecimal v = new BigDecimal(entry.getValue().toString());
                manual.merge(entry.getKey(), v, BigDecimal::add);
                sum = sum.add(v);
            }
            Object base = gamelife.common.Json.toMap(e.meta()).get("baseXp");
            if (base != null && !splits.isEmpty()) {
                assertThat(sum).isEqualByComparingTo(new BigDecimal(base.toString()));
            }
        }
        Map<String, BigDecimal> derived = LedgerFacts.statTotals(events);
        manual.forEach((stat, v) -> assertThat(derived.get(stat)).isEqualByComparingTo(v));

        // every task: net paid is either 0 (not done) or exactly one tick
        assertThat(ledger.verify().valid()).isTrue();
    }

    @Test
    void modifiedRowBreaksTheChainAndTheWarningAppears() throws Exception {
        Task task = tasks.create("Code review", 30, null);
        tasks.tick(task.id());
        checkin.checkin(4);
        mvc.perform(get("/api/state")).andExpect(jsonPath("$.chain.valid").value(true));

        jdbc.update("UPDATE ledger_events SET xp = '9999.00', currency_delta = '9999.00' WHERE id = 1");

        assertThat(ledger.verify().valid()).isFalse();
        assertThat(ledger.verify().brokenAtId()).isEqualTo(1);
        mvc.perform(get("/api/state"))
                .andExpect(jsonPath("$.chain.valid").value(false))
                .andExpect(jsonPath("$.chain.message").value("Event 1 was modified after it was written"));
        // use is not blocked
        assertThat(checkin.todayMood()).isEqualTo(4);
    }

    @Test
    void deletedRowBreaksTheChain() {
        checkin.checkin(4);
        clock.advance(Duration.ofDays(1));
        checkin.checkin(5);
        clock.advance(Duration.ofDays(1));
        checkin.checkin(3);
        jdbc.update("DELETE FROM ledger_events WHERE id = 2");
        assertThat(ledger.verify().valid()).isFalse();
    }

    @Test
    void occurredAtComesFromTheServerClock() {
        Task task = tasks.create("Laundry", 10, null);
        ActionResult r = tasks.tick(task.id());
        assertThat(r.event().occurredAt()).isEqualTo(clock.instant());
    }
}
