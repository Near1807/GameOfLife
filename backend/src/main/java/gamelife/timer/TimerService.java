package gamelife.timer;

import gamelife.common.GameException;
import gamelife.common.Money;
import gamelife.config.GameConfig;
import gamelife.ledger.EventType;
import gamelife.ledger.LedgerService;
import gamelife.payout.ActionResult;
import gamelife.payout.PayRequest;
import gamelife.payout.PayoutService;
import gamelife.rules.Tags;
import gamelife.tasks.Task;
import gamelife.tasks.TaskService;
import gamelife.time.GameTime;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Focus timer (brief 3.2 and 6).
 *  - Times come only from stored server timestamps. The screen staying open is irrelevant:
 *    if the app was closed, the question is asked on reopening.
 *  - The Yes / No question exists only once the timer has ended or was stopped.
 *  - Only Yes pays. Ledger key timer:{id}, so it pays at most once.
 *  - Counted minutes never exceed the planned length, whatever the clocks did.
 *  - Stopped early: pays the elapsed whole minutes only if they reach earlyStopMinMinutes.
 */
@Service
public class TimerService {
    private static final String MODULE = "timer";

    private final TimerRepository timers;
    private final TaskService tasks;
    private final LedgerService ledger;
    private final PayoutService payout;
    private final GameConfig config;
    private final GameTime time;

    public TimerService(TimerRepository timers, TaskService tasks, LedgerService ledger, PayoutService payout,
                        GameConfig config, GameTime time) {
        this.timers = timers;
        this.tasks = tasks;
        this.ledger = ledger;
        this.payout = payout;
        this.config = config;
        this.time = time;
    }

    /**
     * Starts a timer. Only one timer can wait for an answer at a time. Starting pays nothing.
     * The client cannot give a start time: it is always the server's now.
     */
    public synchronized TimerView start(Integer plannedMinutes, String label, Long taskId, List<String> tags) {
        if (timers.findUnanswered().isPresent()) {
            throw GameException.conflict("A timer is already running. Finish it first.");
        }
        GameConfig.Timer cfg = config.timer();
        int planned = plannedMinutes != null ? plannedMinutes : cfg.defaultMinutes();
        if (planned < cfg.minMinutes() || planned > cfg.maxMinutes()) {
            throw GameException.badRequest("Planned length must be between " + cfg.minMinutes() + " and "
                    + cfg.maxMinutes() + " minutes");
        }

        String name;
        List<String> t;
        if (taskId != null) {
            Task task = tasks.get(taskId);
            if (task.archived()) {
                throw GameException.badRequest("That task was removed");
            }
            if (tasks.list().stream().anyMatch(v -> v.id() == taskId && v.done())) {
                throw GameException.badRequest("That task is already done");
            }
            name = task.name();
            t = tags != null ? Tags.clean(tags, config) : task.tags();
        } else {
            name = (label == null || label.isBlank()) ? "Focus" : label.trim();
            Optional<FocusTimer> remembered = timers.findLastByLabel(name);
            t = tags != null ? Tags.clean(tags, config)
                    : remembered.map(FocusTimer::tags).orElse(Tags.clean(cfg.defaultTags(), config));
        }
        long id = timers.insert(name, taskId, planned, t, time.now());
        return view(get(id));
    }

    /** The timer waiting for an answer, if any. Called by the app on every (re)open. */
    public Optional<TimerView> current() {
        return timers.findUnanswered().map(this::view);
    }

    /** Stops a running timer early. Pays nothing by itself: the question comes next. */
    public synchronized TimerView stop(long id) {
        FocusTimer t = get(id);
        if (t.answer() == null && !t.isOver(time.now())) {
            timers.stop(id, time.now());
        }
        return view(get(id));
    }

    /** The owner's answer. Only Yes pays, once. */
    public synchronized ActionResult answer(long id, boolean yes) {
        FocusTimer t = get(id);
        Instant now = time.now();
        if (t.answer() != null) {
            // Second answer (double tap, retry): nothing changes, nothing is paid.
            return ledger.find("timer:" + id)
                    .map(e -> new ActionResult(e, false, "Already answered"))
                    .orElse(ActionResult.nothing("Already answered"));
        }
        if (!t.isOver(now)) {
            throw GameException.conflict("The timer is still running. The question comes when it ends.");
        }
        if (!yes) {
            timers.answer(id, "NO");
            return ActionResult.nothing("No XP for this block. That's fine.");
        }

        int minutes = countedMinutes(t);
        if (t.stoppedEarly() && minutes < config.timer().earlyStopMinMinutes()) {
            timers.answer(id, "YES");
            return ActionResult.nothing("Stopped before " + config.timer().earlyStopMinMinutes()
                    + " minutes: no XP this time.");
        }
        ActionResult result = payout.pay(payRequest(t, minutes));
        timers.answer(id, "YES");
        if (t.taskId() != null) {
            tasks.markDoneByTimer(t.taskId(), t.id()); // the task is done, paid 0: the block already paid
        }
        return result;
    }

    // ---------------------------------------------------------------- rules

    /**
     * Whole minutes between start and the end (planned end, or the stop if earlier),
     * clamped to [0, planned]. A clock that jumped forward cannot add minutes, one that jumped
     * back cannot make it negative.
     */
    public static int countedMinutes(FocusTimer t) {
        Instant end = t.plannedEnd();
        if (t.stoppedAt() != null && t.stoppedAt().isBefore(end)) {
            end = t.stoppedAt();
        }
        long minutes = Duration.between(t.startedAt(), end).toMinutes();
        return (int) Math.max(0, Math.min(t.plannedMinutes(), minutes));
    }

    private PayRequest payRequest(FocusTimer t, int minutes) {
        Map<String, Object> meta = new LinkedHashMap<>();
        meta.put("timerId", t.id());
        meta.put("plannedMinutes", t.plannedMinutes());
        meta.put("countedMinutes", minutes);
        if (t.taskId() != null) {
            meta.put("taskId", t.taskId());
        }
        return new PayRequest("timer:" + t.id(), EventType.TIMER_VALIDATED, MODULE, t.label(),
                Money.of(minutes * config.timer().xpPerMinute()), t.tags(), meta);
    }

    private FocusTimer get(long id) {
        return timers.findById(id).orElseThrow(() -> GameException.notFound("No timer " + id));
    }

    private TimerView view(FocusTimer t) {
        Instant now = time.now();
        String status = t.answer() != null ? "DONE" : (t.isOver(now) ? "ASK" : "RUNNING");
        int counted = countedMinutes(t);
        boolean wouldPay = !t.stoppedEarly() || counted >= config.timer().earlyStopMinMinutes();
        return new TimerView(t.id(), t.label(), t.taskId(), t.plannedMinutes(), t.tags(), t.startedAt(),
                t.plannedEnd(), t.stoppedAt(), now, status, counted, wouldPay, t.answer());
    }
}
