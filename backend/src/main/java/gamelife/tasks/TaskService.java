package gamelife.tasks;

import gamelife.common.GameException;
import gamelife.common.Json;
import gamelife.common.Money;
import gamelife.config.GameConfig;
import gamelife.ledger.EventType;
import gamelife.ledger.LedgerEvent;
import gamelife.ledger.LedgerService;
import gamelife.ledger.NewEvent;
import gamelife.payout.ActionResult;
import gamelife.payout.PayRequest;
import gamelife.payout.PayoutService;
import gamelife.rules.Tags;
import gamelife.time.GameTime;
import gamelife.timer.TimerRepository;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * To-do list (brief 6). Ledger keys:
 *   task:{id}:done:{n}    the n-th tick of the task (n = number of un-ticks before it)
 *   task:{id}:undone:{n}  reverses task:{id}:done:{n}
 * Two taps on the same tick produce the same key, so they pay once (brief 3.2).
 * Un-tick appends a reversal; ticking again pays the SAME base XP as the first tick, so the
 * task pays once overall and editing it afterwards changes nothing.
 */
@Service
public class TaskService {
    private static final String MODULE = "todo";

    private final TaskRepository tasks;
    private final TimerRepository timers;
    private final LedgerService ledger;
    private final PayoutService payout;
    private final GameConfig config;
    private final GameTime time;

    public TaskService(TaskRepository tasks, TimerRepository timers, LedgerService ledger, PayoutService payout,
                       GameConfig config, GameTime time) {
        this.tasks = tasks;
        this.timers = timers;
        this.ledger = ledger;
        this.payout = payout;
        this.config = config;
        this.time = time;
    }

    // ---------------------------------------------------------------- create / edit

    /**
     * Creates a task. Only the name is mandatory (brief 3.1). Missing minutes or tags take
     * the values last used with the same name, else the configured default minutes and no tags.
     * Creating a task pays nothing.
     */
    public Task create(String name, Integer minutes, List<String> tags) {
        String cleanName = cleanName(name);
        Optional<Task> remembered = tasks.findLastByName(cleanName);
        int m = minutes != null ? minutes : remembered.map(Task::expectedMinutes).orElse(config.todo().defaultMinutes());
        if (m < 1) {
            throw GameException.badRequest("Expected minutes must be at least 1");
        }
        List<String> t = tags != null ? Tags.clean(tags, config) : remembered.map(Task::tags).orElse(List.of());
        long id = tasks.insert(cleanName, m, t, time.now());
        return get(id);
    }

    /** Editing never touches the ledger: what was paid stays paid (brief 3.2). */
    public Task edit(long id, String name, Integer minutes, List<String> tags) {
        Task task = get(id);
        String n = name != null ? cleanName(name) : task.name();
        int m = minutes != null ? minutes : task.expectedMinutes();
        if (m < 1) {
            throw GameException.badRequest("Expected minutes must be at least 1");
        }
        List<String> t = tags != null ? Tags.clean(tags, config) : task.tags();
        tasks.update(id, n, m, t);
        return get(id);
    }

    /** Removes an open task from the list. Pays nothing, takes nothing back. */
    public void archive(long id) {
        get(id);
        tasks.archive(id);
    }

    // ---------------------------------------------------------------- tick / un-tick

    /** The tick IS the confirmation (brief 6). */
    public synchronized ActionResult tick(long id) {
        Task task = get(id);
        TaskLedger tl = taskLedger(id);
        if (tl.isDone()) {
            return new ActionResult(tl.lastDone(), false, "Already done");
        }
        String key = "task:" + id + ":done:" + tl.undoneCount();

        // A task already paid through a focus block pays nothing more (brief 6).
        boolean paidByTimer = timers.hasValidatedTimerForTask(id);
        // Re-ticking pays the same base as the first tick, whatever was edited since.
        BigDecimal base = paidByTimer ? Money.ZERO
                : tl.firstDone().map(TaskService::baseOf)
                .orElse(Money.of(task.expectedMinutes() * config.todo().xpPerMinute()));

        Map<String, Object> meta = new LinkedHashMap<>();
        meta.put("taskId", id);
        meta.put("expectedMinutes", task.expectedMinutes());
        if (paidByTimer) {
            meta.put("paidThroughTimer", true);
        }
        return payout.pay(new PayRequest(key, EventType.TASK_DONE, MODULE, task.name(), base,
                paidByTimer ? List.of() : task.tags(), meta));
    }

    /** Marks a task done after a validated focus block, paying 0 (the block already paid). */
    public synchronized ActionResult markDoneByTimer(long taskId, long timerId) {
        TaskLedger tl = taskLedger(taskId);
        if (tl.isDone()) {
            return new ActionResult(tl.lastDone(), false, "Already done");
        }
        Task task = get(taskId);
        String key = "task:" + taskId + ":done:" + tl.undoneCount();
        Map<String, Object> meta = new LinkedHashMap<>();
        meta.put("taskId", taskId);
        meta.put("paidThroughTimer", true);
        meta.put("timerId", timerId);
        return payout.pay(new PayRequest(key, EventType.TASK_DONE, MODULE, task.name(), Money.ZERO, List.of(), meta));
    }

    /** Un-tick appends a reversal of exactly what the last tick paid (brief 3.2). */
    public synchronized ActionResult untick(long id) {
        Task task = get(id);
        TaskLedger tl = taskLedger(id);
        if (!tl.isDone()) {
            return ActionResult.nothing("Not done");
        }
        LedgerEvent done = tl.lastDone();
        String key = "task:" + id + ":undone:" + tl.undoneCount();

        Map<String, BigDecimal> negSplits = new LinkedHashMap<>();
        Json.toMap(done.statSplits()).forEach((stat, v) -> negSplits.put(stat, Money.parse(v.toString()).negate()));
        Map<String, Object> meta = new LinkedHashMap<>();
        meta.put("taskId", id);
        meta.put("reverses", done.key());

        var result = ledger.append(new NewEvent(key, EventType.TASK_UNDONE, MODULE, task.name(),
                done.xp().negate(), done.currencyDelta().negate(), negSplits, meta));
        return new ActionResult(result.event(), result.written(), "Un-ticked");
    }

    // ---------------------------------------------------------------- reading

    public Task get(long id) {
        return tasks.findById(id).orElseThrow(() -> GameException.notFound("No task " + id));
    }

    /** Open tasks plus the tasks done today, oldest first, done ones last. */
    public List<TaskView> list() {
        Map<Long, TaskLedger> byTask = allTaskLedgers();
        LocalDate today = time.today();
        List<TaskView> result = new ArrayList<>();
        for (Task t : tasks.findNotArchived()) {
            TaskLedger tl = byTask.getOrDefault(t.id(), new TaskLedger(List.of()));
            boolean done = tl.isDone();
            if (done && !tl.lastDone().gameDay().equals(today)) {
                continue; // done on an earlier day: no longer in the list
            }
            BigDecimal paid = done ? tl.lastDone().xp() : Money.ZERO;
            boolean viaTimer = done && Boolean.TRUE.equals(Json.toMap(tl.lastDone().meta()).get("paidThroughTimer"));
            result.add(new TaskView(t.id(), t.name(), t.expectedMinutes(), t.tags(), done, paid, viaTimer));
        }
        result.sort(Comparator.comparing(TaskView::done));
        return result;
    }

    /**
     * Known task names, most used first, with remembered minutes and tags. Names already on
     * today's list (open, or done today) are skipped. query filters by "contains", ignoring case.
     * Suggestions pay nothing: each one must still be added and ticked (brief 3.2).
     */
    public List<Suggestion> suggestions(String query) {
        Map<Long, TaskLedger> byTask = allTaskLedgers();
        Map<String, Suggestion> byName = new LinkedHashMap<>();
        Map<String, Boolean> onListNames = new HashMap<>();
        LocalDate today = time.today();
        for (Task t : tasks.findAll()) { // oldest first, so later tasks overwrite remembered values
            String k = t.name().toLowerCase();
            TaskLedger tl = byTask.getOrDefault(t.id(), new TaskLedger(List.of()));
            boolean doneToday = tl.isDone() && tl.lastDone().gameDay().equals(today);
            if (!t.archived() && (!tl.isDone() || doneToday)) {
                onListNames.put(k, true);
            }
            int used = byName.containsKey(k) ? byName.get(k).timesUsed() : 0;
            byName.put(k, new Suggestion(t.name(), t.expectedMinutes(), t.tags(), used + 1));
        }
        String q = query == null ? "" : query.trim().toLowerCase();
        return byName.entrySet().stream()
                .filter(e -> !onListNames.containsKey(e.getKey()))
                .filter(e -> q.isEmpty() || e.getKey().contains(q))
                .map(Map.Entry::getValue)
                .sorted(Comparator.comparing(Suggestion::timesUsed).reversed())
                .limit(config.todo().suggestionCount())
                .toList();
    }

    /** Minutes of the most recently created task, used to preselect the chip for a new name. */
    public int lastMinutesUsed() {
        List<Task> all = tasks.findAll();
        return all.isEmpty() ? config.todo().defaultMinutes() : all.get(all.size() - 1).expectedMinutes();
    }

    // ---------------------------------------------------------------- helpers

    /** The ledger events of one task, in order. */
    record TaskLedger(List<LedgerEvent> events) {
        long doneCount() {
            return events.stream().filter(e -> e.type() == EventType.TASK_DONE).count();
        }

        long undoneCount() {
            return events.stream().filter(e -> e.type() == EventType.TASK_UNDONE).count();
        }

        boolean isDone() {
            return doneCount() > undoneCount();
        }

        LedgerEvent lastDone() {
            return events.stream().filter(e -> e.type() == EventType.TASK_DONE).reduce((a, b) -> b).orElse(null);
        }

        Optional<LedgerEvent> firstDone() {
            return events.stream().filter(e -> e.type() == EventType.TASK_DONE).findFirst();
        }
    }

    private TaskLedger taskLedger(long id) {
        return new TaskLedger(ledger.byKeyPrefix("task:" + id + ":"));
    }

    private Map<Long, TaskLedger> allTaskLedgers() {
        Map<Long, List<LedgerEvent>> grouped = new HashMap<>();
        for (LedgerEvent e : ledger.byKeyPrefix("task:")) {
            long taskId = Long.parseLong(e.key().split(":")[1]);
            grouped.computeIfAbsent(taskId, k -> new ArrayList<>()).add(e);
        }
        Map<Long, TaskLedger> result = new HashMap<>();
        grouped.forEach((id, events) -> result.put(id, new TaskLedger(events)));
        return result;
    }

    /** Base XP recorded in an event's meta. */
    private static BigDecimal baseOf(LedgerEvent e) {
        Object base = Json.toMap(e.meta()).get("baseXp");
        return base == null ? Money.ZERO : Money.parse(base.toString());
    }

    private static String cleanName(String name) {
        String n = name == null ? "" : name.trim();
        if (n.isEmpty()) {
            throw GameException.badRequest("A task needs a name");
        }
        if (n.length() > 200) {
            throw GameException.badRequest("Name is too long (200 characters max)");
        }
        return n;
    }
}
