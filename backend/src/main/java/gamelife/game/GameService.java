package gamelife.game;

import gamelife.common.GameException;
import gamelife.common.Money;
import gamelife.config.GameConfig;
import gamelife.ledger.EventType;
import gamelife.ledger.LedgerEvent;
import gamelife.ledger.LedgerService;
import gamelife.ledger.NewEvent;
import gamelife.payout.ActionResult;
import gamelife.rules.LedgerFacts;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Spending currency on the map (v4 brief 2): unlock a spot, build, upgrade, sell, move to another
 * map. Every action is
 * one ledger event with a unique key, so a double tap or a retry never pays twice.
 *
 * Integrity: these events only ever take currency away, except a sale, which gives back exactly
 * what was paid for that tower. No tower and no wave creates currency or XP.
 *
 * synchronized: the balance check and the write happen as one step.
 */
@Service
public class GameService {
    private final LedgerService ledger;
    private final GameConfig config;
    private final GameContent content;

    public GameService(LedgerService ledger, GameConfig config, GameContent content) {
        this.ledger = ledger;
        this.config = config;
        this.content = content;
    }

    /** Everything the rules need about "now", computed from the ledger. */
    public record Context(GameLedger.State state, Map<String, Integer> stats, double discount, double income,
                          double balance) {}

    public Context context(List<LedgerEvent> events) {
        GameLedger.State state = GameLedger.derive(events, content.catalog());
        Map<String, Integer> stats = GameLedger.statLevels(LedgerFacts.statTotals(events), config);
        GameCatalog cat = content.catalog();
        return new Context(state, stats, GameMath.discount(state.build(), cat, stats, config.game()),
                GameMath.incomeMultiplier(state.build(), cat, stats, config.game()),
                LedgerFacts.balance(events).doubleValue());
    }

    /** The Market and Social factor on the currency of real actions (used by PayoutService). */
    public double incomeMultiplier(List<LedgerEvent> events) {
        return context(events).income();
    }

    /** True when the furthest wave cleared has reached the map's unlock wave. */
    public static boolean unlocked(GameCatalog.MapDef m, GameLedger.State state) {
        return state.furthestCleared() >= m.unlockAfterWave();
    }

    // ------------------------------------------------------------------ actions

    public synchronized ActionResult unlock(int spot) {
        Context c = context(ledger.all());
        GameCatalog.MapDef map = c.state().map();
        checkSpot(map, spot);
        String key = "spot:" + map.id() + ":" + spot;
        var existing = ledger.find(key);
        if (existing.isPresent()) {
            return new ActionResult(existing.get(), false, "Already unlocked");
        }
        if (c.state().build().openSpots().contains(spot)) {
            throw GameException.conflict("This spot is already open");
        }
        int price = GameMath.spotCost(c.state().spotsBought(), config.game());
        requireBalance(c, price);
        var r = ledger.append(new NewEvent(key, EventType.SPOT_UNLOCKED, "game", "Unlocked a tower spot",
                Money.ZERO, Money.of(-price), Map.of(), meta("map", map.id(), "spot", spot, "price", price)));
        return new ActionResult(r.event(), r.written(), "Spot unlocked");
    }

    public synchronized ActionResult build(int spot, String type) {
        GameCatalog.TowerDef def = content.catalog().tower(type);
        if (def == null) {
            throw GameException.notFound("No tower " + type);
        }
        Context c = context(ledger.all());
        GameCatalog.MapDef map = c.state().map();
        checkSpot(map, spot);
        Build build = c.state().build();
        if (!build.openSpots().contains(spot)) {
            throw GameException.conflict("Unlock this spot first");
        }
        Build.Tower here = build.towers().get(spot);
        int n = c.state().builds().getOrDefault(spot, 0);
        if (here != null) {
            if (here.type().equals(type) && here.a() == 0 && here.b() == 0) {
                // a double tap: answer with the first build
                return new ActionResult(ledger.find(towerKey(map, spot, n) + ":built").orElse(null), false, "Already built");
            }
            throw GameException.conflict("There is already a tower here. Sell it first.");
        }
        if (!GameMath.meetsRequirement(def, c.stats())) {
            throw GameException.conflict(def.name() + " needs " + statName(def.requires().stat()) + " level "
                    + def.requires().level() + " (you have " + GameMath.level(c.stats(), def.requires().stat()) + ")");
        }
        int price = GameMath.price(def.cost(), c.discount());
        requireBalance(c, price);
        var r = ledger.append(new NewEvent(towerKey(map, spot, n + 1) + ":built", EventType.TOWER_BUILT, "game",
                "Built " + def.name(), Money.ZERO, Money.of(-price), Map.of(),
                meta("map", map.id(), "spot", spot, "n", n + 1, "tower", type, "price", price, "listPrice", def.cost())));
        return new ActionResult(r.event(), r.written(), def.name() + " built");
    }

    /**
     * Buys tier `tier` (1..3) of path 0 (A) or 1 (B). The client sends the tier it saw, so a double
     * tap asks for the same tier twice and the second is answered with the first.
     */
    public synchronized ActionResult upgrade(int spot, int path, int tier) {
        Context c = context(ledger.all());
        Build.Tower t = c.state().build().towers().get(spot);
        if (t == null) {
            throw GameException.conflict("No tower on this spot");
        }
        GameCatalog.TowerDef def = content.catalog().tower(t.type());
        if (path < 0 || path >= def.paths().size()) {
            throw GameException.badRequest("No such upgrade path");
        }
        GameCatalog.MapDef map = c.state().map();
        String key = towerKey(map, spot, c.state().builds().get(spot)) + ":path" + path + ":tier" + tier;
        if (t.tier(path) >= tier) {
            return new ActionResult(ledger.find(key).orElse(null), false, "Already bought");
        }
        if (t.chosenPath() >= 0 && t.chosenPath() != path) {
            throw GameException.conflict("Locked: you chose the " + def.paths().get(t.chosenPath()).name()
                    + " path. Sell the tower to choose again.");
        }
        List<GameCatalog.Tier> tiers = def.paths().get(path).tiers();
        if (tier != t.tier(path) + 1 || tier > tiers.size()) {
            throw GameException.conflict("Buy the tiers in order");
        }
        GameCatalog.Requirement need = tiers.get(tier - 1).requires();
        if (need != null && GameMath.level(c.stats(), need.stat()) < need.level()) {
            throw GameException.conflict(def.paths().get(path).name() + " " + tier + " needs " + statName(need.stat())
                    + " level " + need.level() + " (you have " + GameMath.level(c.stats(), need.stat()) + ")");
        }
        int price = GameMath.price(tiers.get(tier - 1).cost(), c.discount());
        requireBalance(c, price);
        String name = def.paths().get(path).name();
        var r = ledger.append(new NewEvent(key, EventType.TOWER_UPGRADED, "game",
                def.name() + ": " + name + " " + tier, Money.ZERO, Money.of(-price), Map.of(),
                meta("map", map.id(), "spot", spot, "n", c.state().builds().get(spot), "path", path, "tier", tier,
                        "price", price)));
        return new ActionResult(r.event(), r.written(), name + " " + tier);
    }

    /** Sells a tower for exactly what was paid for it. confirm: the second tap (brief 4). */
    public synchronized ActionResult sell(int spot, boolean confirm) {
        if (!confirm) {
            throw GameException.badRequest("Selling needs a confirmation");
        }
        Context c = context(ledger.all());
        return sell(c, spot);
    }

    private ActionResult sell(Context c, int spot) {
        GameCatalog.MapDef map = c.state().map();
        Build.Tower t = c.state().build().towers().get(spot);
        String key = towerKey(map, spot, c.state().builds().getOrDefault(spot, 0)) + ":sold";
        if (t == null) {
            var done = ledger.find(key);
            if (done.isPresent()) {
                return new ActionResult(done.get(), false, "Already sold");
            }
            throw GameException.conflict("No tower on this spot");
        }
        double refund = t.paid();
        var r = ledger.append(new NewEvent(key, EventType.TOWER_SOLD, "game",
                "Sold " + content.catalog().tower(t.type()).name(), Money.ZERO, Money.of(refund), Map.of(),
                meta("map", map.id(), "spot", spot, "n", c.state().builds().get(spot), "refund", refund)));
        return new ActionResult(r.event(), r.written(), "Sold for " + Math.round(refund));
    }

    /**
     * Moves to another unlocked map (a new chapter). Every tower of the current map is sold first,
     * for exactly what was paid, so moving costs nothing; the number of open spots carries over.
     * confirm: the second tap. The key counts the moves, so a double tap moves once.
     */
    public synchronized ActionResult move(String mapId, boolean confirm) {
        if (!confirm) {
            throw GameException.badRequest("Moving needs a confirmation");
        }
        GameCatalog.MapDef target = content.catalog().maps().stream().filter(m -> m.id().equals(mapId)).findFirst()
                .orElseThrow(() -> GameException.notFound("No map " + mapId));
        Context c = context(ledger.all());
        if (c.state().map().id().equals(target.id())) {
            return ActionResult.nothing("Already on " + target.name());
        }
        if (!unlocked(target, c.state())) {
            throw GameException.conflict(target.name() + " opens after wave " + target.unlockAfterWave());
        }
        for (int spot : List.copyOf(c.state().build().towers().keySet())) {
            sell(context(ledger.all()), spot);
        }
        var r = ledger.append(new NewEvent("map-move:" + (c.state().mapChoices() + 1), EventType.MAP_CHOSEN, "game",
                "Moved to " + target.name(), Money.ZERO, Money.ZERO, Map.of(),
                meta("map", target.id(), "from", c.state().map().id())));
        return new ActionResult(r.event(), r.written(), "Moved to " + target.name());
    }

    // ------------------------------------------------------------------ helpers

    private static String towerKey(GameCatalog.MapDef map, int spot, int n) {
        return "tower:" + map.id() + ":" + spot + ":" + n;
    }

    private static void checkSpot(GameCatalog.MapDef map, int spot) {
        if (spot < 0 || spot >= map.spots().size()) {
            throw GameException.badRequest("No spot " + spot);
        }
    }

    private static void requireBalance(Context c, int price) {
        if (c.balance() < price) {
            throw GameException.conflict("Not enough coins: " + (long) Math.floor(c.balance()) + " of " + price);
        }
    }

    String statName(String id) {
        return config.stats().stream().filter(s -> s.id().equals(id)).map(GameConfig.Stat::name)
                .findFirst().orElse(id);
    }

    static Map<String, Object> meta(Object... kv) {
        Map<String, Object> m = new LinkedHashMap<>();
        for (int i = 0; i < kv.length; i += 2) {
            m.put((String) kv[i], kv[i + 1]);
        }
        return m;
    }
}
