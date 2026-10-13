package gamelife.game;

import gamelife.common.GameException;
import gamelife.common.Money;
import gamelife.config.GameConfig;
import gamelife.ledger.EventType;
import gamelife.ledger.LedgerEvent;
import gamelife.ledger.LedgerService;
import gamelife.ledger.NewEvent;
import gamelife.payout.ActionResult;
import gamelife.time.GameTime;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Automatic waves (v4 brief 3), as a lazy catch-up: whenever the app reads the game, every
 * scheduled slot that has passed is worked out exactly once, in order.
 *
 * Slots: every game.waveIntervalMinutes after the first tower was built. At each slot the game is
 * seen exactly as it was at that moment (build and stat levels from the events up to then). A slot
 * is skipped, with nothing written, when
 *  - the day already has game.maxAttemptsPerDay attempts, or
 *  - the last attempt failed and nothing that decides the outcome changed since ("stuck").
 * Otherwise the wave is simulated and one WAVE_CLEARED or WAVE_FAILED event is written with the key
 * wave-attempt:{wave}:{attempt}. A skipped slot gives the same answer every time it is looked at,
 * so working it out again later is harmless.
 *
 * "Run now" is an extra attempt at the current moment, under the same cap and the same rule.
 */
@Service
public class WaveService {
    private final LedgerService ledger;
    private final GameConfig config;
    private final GameContent content;
    private final GameTime time;

    /** The replays of the last few attempts, so "Watch" does not simulate again. */
    private final Map<String, Simulator.Result> recent = new LinkedHashMap<>(8, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<String, Simulator.Result> eldest) {
            return size() > 4;
        }
    };

    public WaveService(LedgerService ledger, GameConfig config, GameContent content, GameTime time) {
        this.ledger = ledger;
        this.config = config;
        this.content = content;
        this.time = time;
    }

    /** idle: nothing built yet. waiting: next attempt at nextAt. stuck / capped: see the class comment. */
    public record Status(String status, Instant nextAt, int attemptsToday, String inputs) {}

    // ------------------------------------------------------------------ catch-up

    public synchronized void sync() {
        List<LedgerEvent> events = ledger.all();
        Instant anchor = anchor(events);
        if (anchor == null) {
            return;
        }
        Duration interval = Duration.ofMinutes(config.game().waveIntervalMinutes());
        List<GameLedger.Snapshot> timeline = GameLedger.timeline(events, content.catalog(), config);
        GameLedger.State state = GameLedger.derive(events, content.catalog());
        List<GameLedger.Attempt> attempts = new ArrayList<>(state.attempts());
        int wave = state.wave();
        Instant now = time.now();
        Instant from = attempts.isEmpty() ? anchor : attempts.get(attempts.size() - 1).at();
        long k = Duration.between(anchor, from).toMillis() / interval.toMillis() + 1;
        for (Instant slot = anchor.plus(interval.multipliedBy(k)); !slot.isAfter(now);
             slot = slot.plus(interval)) {
            GameLedger.Snapshot snap = GameLedger.at(timeline, slot);
            String inputs = inputs(wave, snap.map(), snap.build(), snap.stats());
            if (attemptsOn(attempts, time.gameDay(slot)) >= config.game().maxAttemptsPerDay() || stuck(attempts, inputs)) {
                continue;
            }
            GameLedger.Attempt a = attempt(wave, attempts, snap.map(), snap.build(), snap.stats(), inputs, slot, false);
            attempts.add(a);
            if (a.won()) {
                wave++;
            }
        }
    }

    /** "Run now": one extra attempt with the build of this moment. */
    public synchronized ActionResult runNow() {
        sync();
        List<LedgerEvent> events = ledger.all();
        if (anchor(events) == null) {
            throw GameException.conflict("Build a tower first");
        }
        Status s = status(events);
        if ("stuck".equals(s.status())) {
            throw GameException.conflict("Nothing changed since the last try: it would fail the same way. "
                    + "Build, upgrade or level up a stat first.");
        }
        if ("capped".equals(s.status())) {
            throw GameException.conflict("No more attempts today (" + config.game().maxAttemptsPerDay() + " a day).");
        }
        GameLedger.Snapshot now = GameLedger.at(GameLedger.timeline(events, content.catalog(), config),
                time.now());
        GameLedger.State state = GameLedger.derive(events, content.catalog());
        GameLedger.Attempt a = attempt(state.wave(), state.attempts(), now.map(), now.build(), now.stats(), s.inputs(),
                time.now(), true);
        return new ActionResult(ledger.find(a.key()).orElse(null), true,
                (a.won() ? "Wave " + a.wave() + " cleared" : "Wave " + a.wave() + " failed"));
    }

    /** Where the schedule stands now (after sync). */
    public Status status(List<LedgerEvent> events) {
        Instant anchor = anchor(events);
        GameLedger.State state = GameLedger.derive(events, content.catalog());
        GameLedger.Snapshot snap = GameLedger.at(GameLedger.timeline(events, content.catalog(), config),
                time.now());
        String inputs = inputs(state.wave(), snap.map(), snap.build(), snap.stats());
        int today = attemptsOn(state.attempts(), time.today());
        if (anchor == null) {
            return new Status("idle", null, today, inputs);
        }
        Duration interval = Duration.ofMinutes(config.game().waveIntervalMinutes());
        long k = Duration.between(anchor, time.now()).toMillis() / interval.toMillis() + 1;
        Instant next = anchor.plus(interval.multipliedBy(k));
        if (stuck(state.attempts(), inputs)) {
            return new Status("stuck", null, today, inputs);
        }
        if (today >= config.game().maxAttemptsPerDay()) {
            // the next slot on the next game day
            while (!time.gameDay(next).isAfter(time.today())) {
                next = next.plus(interval);
            }
            return new Status("capped", next, today, inputs);
        }
        return new Status("waiting", next, today, inputs);
    }

    // ------------------------------------------------------------------ replay

    /** The attempt with its replay; simulated again from its recorded inputs when not in memory. */
    public record Watch(GameLedger.Attempt attempt, Simulator.Result result, boolean sameAsRecorded) {}

    public synchronized Watch watch(String key) {
        List<LedgerEvent> events = ledger.all();
        List<GameLedger.Attempt> attempts = GameLedger.derive(events, content.catalog()).attempts();
        GameLedger.Attempt a = key == null || key.isBlank()
                ? (attempts.isEmpty() ? null : attempts.get(attempts.size() - 1))
                : attempts.stream().filter(x -> x.key().equals(key)).findFirst().orElse(null);
        if (a == null) {
            throw GameException.notFound("No wave attempt yet");
        }
        Simulator.Result r = recent.get(a.key());
        if (r == null) {
            r = replay(a.meta());
            recent.put(a.key(), r);
        }
        boolean same = r.won() == a.won() && r.lives() == ((Number) a.meta().get("lives")).intValue();
        return new Watch(a, r, same);
    }

    /** Runs the simulation again with the inputs recorded in an attempt's meta. */
    @SuppressWarnings("unchecked")
    Simulator.Result replay(Map<String, Object> meta) {
        Build build = Build.empty(0);
        for (Object row : (List<Object>) meta.get("towers")) {
            List<Object> t = (List<Object>) row;
            build = build.withTower(((Number) t.get(0)).intValue(), new Build.Tower((String) t.get(1),
                    ((Number) t.get(2)).intValue(), ((Number) t.get(3)).intValue(), 0));
        }
        Map<String, Integer> stats = new TreeMap<>();
        ((Map<String, Object>) meta.get("stats")).forEach((k, v) -> stats.put(k, ((Number) v).intValue()));
        GameCatalog.MapDef map = content.catalog().map(String.valueOf(meta.getOrDefault("map", "")));
        return Simulator.simulate(map, build, stats, ((Number) meta.get("wave")).intValue(),
                content.catalog(), config.game(), true);
    }

    // ------------------------------------------------------------------ helpers

    private GameLedger.Attempt attempt(int wave, List<GameLedger.Attempt> attempts, GameCatalog.MapDef map, Build build,
                                       Map<String, Integer> stats, String inputs, Instant at, boolean manual) {
        Simulator.Result r = Simulator.simulate(map, build, stats, wave, content.catalog(),
                config.game(), true);
        int n = (int) attempts.stream().filter(x -> x.wave() == wave).count() + 1;
        String key = "wave-attempt:" + wave + ":" + n;
        List<List<Object>> towers = new ArrayList<>();
        build.towers().forEach((spot, t) -> towers.add(List.of(spot, t.type(), t.a(), t.b())));
        Map<String, Object> meta = new LinkedHashMap<>();
        meta.put("wave", wave);
        meta.put("attempt", n);
        meta.put("at", at.toString());
        meta.put("manual", manual);
        meta.put("map", map.id());
        meta.put("inputs", inputs);
        meta.put("towers", towers);
        meta.put("stats", stats);
        meta.put("lives", r.lives());
        meta.put("livesMax", r.livesMax());
        meta.put("enemies", r.enemies());
        meta.put("killed", r.killed());
        meta.put("leaked", r.leaked());
        meta.put("seconds", r.seconds());
        String label = "Wave " + wave + (r.won() ? " cleared" : " failed") + (manual ? " (run now)" : "");
        ledger.append(new NewEvent(key, r.won() ? EventType.WAVE_CLEARED : EventType.WAVE_FAILED, "game", label,
                Money.ZERO, Money.ZERO, Map.of(), meta));
        recent.put(key, r);
        // read back what was stored, so the attempt carries the same meta as one derived later
        return GameLedger.derive(List.of(ledger.find(key).orElseThrow()), content.catalog()).lastAttempt();
    }

    /**
     * What decides a wave's outcome: the wave, the map, the attacking towers and Banners with their tiers, the
     * stats the simulation uses, and the rules (config + data files). Currency, Markets, Social and
     * Creativity cannot change a result, so they are left out: changing only them would replay the
     * same failure.
     */
    String inputs(int wave, GameCatalog.MapDef map, Build build, Map<String, Integer> stats) {
        StringBuilder sb = new StringBuilder("w").append(wave).append('|').append(map.id()).append('|');
        build.towers().forEach((spot, t) -> {
            if (!"income".equals(content.catalog().tower(t.type()).role())) {
                sb.append(spot).append(':').append(t.type()).append(':').append(t.a()).append(':').append(t.b()).append(';');
            }
        });
        sb.append('|');
        for (String s : List.of("STRENGTH", "INTELLECT", "DISCIPLINE", "SPIRIT", "VITALITY")) {
            sb.append(GameMath.level(stats, s)).append(',');
        }
        return sb.append('|').append(content.rulesHash()).toString();
    }

    private static boolean stuck(List<GameLedger.Attempt> attempts, String inputs) {
        if (attempts.isEmpty()) {
            return false;
        }
        GameLedger.Attempt last = attempts.get(attempts.size() - 1);
        return !last.won() && inputs.equals(last.inputs());
    }

    private int attemptsOn(List<GameLedger.Attempt> attempts, LocalDate day) {
        return (int) attempts.stream().filter(a -> time.gameDay(a.at()).equals(day)).count();
    }

    /** The schedule starts when the first tower is built. */
    private static Instant anchor(List<LedgerEvent> events) {
        return events.stream().filter(e -> e.type() == EventType.TOWER_BUILT).map(LedgerEvent::occurredAt)
                .findFirst().orElse(null);
    }
}
