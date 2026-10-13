package gamelife.game;

import gamelife.config.GameConfig;
import gamelife.ledger.LedgerEvent;
import gamelife.ledger.LedgerService;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Everything the map screen shows (v4 brief 4), computed from the ledger on each request. */
@Service
public class GameViewService {
    private final LedgerService ledger;
    private final GameConfig config;
    private final GameContent content;
    private final GameService game;
    private final WaveService waves;

    public GameViewService(LedgerService ledger, GameConfig config, GameContent content, GameService game,
                           WaveService waves) {
        this.ledger = ledger;
        this.config = config;
        this.content = content;
        this.game = game;
        this.waves = waves;
    }

    /** One upgrade path of a tower on the map. price is null when nothing is left to buy or it is locked. */
    public record PathView(String name, int tier, boolean locked, Integer price, String next, List<String> tiers) {}

    public record TowerView(String type, String name, String kind, String role, String color, String shape, int a,
                            int b, double paid, Map<String, Object> stats, List<PathView> paths) {}

    public record SpotView(int index, double x, double y, boolean open, TowerView tower) {}

    /** A tower that can be built. locked: why not (a stat level), or null. */
    public record TowerOption(String id, String name, String kind, String role, String color, String shape,
                              int price, int listPrice, String locked) {}

    public record AttemptView(String key, int wave, int attempt, boolean won, boolean manual, Instant at,
                              int lives, int livesMax, int enemies, int killed, int leaked, double seconds) {}

    public record EnemyView(String name, String color, int radius, boolean air, double armor, double ward) {}

    public record GameView(String mapId, String mapName, int width, int height, List<double[]> path, double[] base,
                           List<SpotView> spots, List<TowerOption> towers, int wave, int furthestCleared,
                           int livesMax, String status, Instant nextAt, int attemptsToday, int maxAttemptsPerDay,
                           double balance, double incomeMultiplier, double discount, int nextSpotCost,
                           AttemptView last, List<AttemptView> recent, Map<String, EnemyView> enemies,
                           Map<String, Integer> stats) {}

    public GameView view() {
        waves.sync(); // missed attempts first
        List<LedgerEvent> events = ledger.all();
        GameService.Context c = game.context(events);
        WaveService.Status status = waves.status(events);
        GameCatalog cat = content.catalog();
        GameCatalog.MapDef map = content.map();
        Build build = c.state().build();

        List<SpotView> spots = new ArrayList<>();
        for (int i = 0; i < map.spots().size(); i++) {
            Build.Tower t = build.towers().get(i);
            spots.add(new SpotView(i, map.spots().get(i)[0], map.spots().get(i)[1], build.openSpots().contains(i),
                    t == null ? null : tower(i, t, build, c)));
        }
        List<TowerOption> options = new ArrayList<>();
        for (GameCatalog.TowerDef d : cat.towers().values()) {
            String locked = GameMath.meetsRequirement(d, c.stats()) ? null
                    : "Needs " + game.statName(d.requires().stat()) + " " + d.requires().level();
            options.add(new TowerOption(d.id(), d.name(), d.kind(), d.role(), d.color(), d.shape(),
                    GameMath.price(d.cost(), c.discount()), d.cost(), locked));
        }
        List<AttemptView> recent = new ArrayList<>();
        List<GameLedger.Attempt> attempts = c.state().attempts();
        for (int i = attempts.size() - 1; i >= 0 && recent.size() < 10; i--) {
            recent.add(attempt(attempts.get(i)));
        }
        Map<String, EnemyView> enemies = new LinkedHashMap<>();
        cat.enemies().forEach((id, e) -> enemies.put(id, new EnemyView(e.name(), e.color(), e.radius(), e.air(),
                e.armor(), e.ward())));
        return new GameView(map.id(), map.name(), map.width(), map.height(), map.path(), map.base(), spots, options,
                c.state().wave(), c.state().furthestCleared(), GameMath.livesMax(c.stats(), config.game()),
                status.status(), status.nextAt(), status.attemptsToday(), config.game().maxAttemptsPerDay(),
                c.balance(), round2(c.income()), round2(c.discount()),
                GameMath.spotCost(c.state().spotsBought(), config.game()),
                recent.isEmpty() ? null : recent.get(0), recent, enemies, c.stats());
    }

    private TowerView tower(int spot, Build.Tower t, Build build, GameService.Context c) {
        GameCatalog.TowerDef def = content.catalog().tower(t.type());
        TowerStats s = GameMath.effective(spot, build, content.catalog(), content.map(), c.stats(), config.game());
        List<PathView> paths = new ArrayList<>();
        for (int p = 0; p < def.paths().size(); p++) {
            GameCatalog.UpgradePath path = def.paths().get(p);
            boolean locked = t.chosenPath() >= 0 && t.chosenPath() != p;
            int tier = t.tier(p);
            boolean more = tier < path.tiers().size();
            paths.add(new PathView(path.name(), tier, locked,
                    !locked && more ? GameMath.price(path.tiers().get(tier).cost(), c.discount()) : null,
                    more ? path.tiers().get(tier).description() : null,
                    path.tiers().stream().map(GameCatalog.Tier::description).toList()));
        }
        return new TowerView(t.type(), def.name(), def.kind(), def.role(), def.color(), def.shape(), t.a(), t.b(),
                t.paid(), s.summary(), paths);
    }

    static AttemptView attempt(GameLedger.Attempt a) {
        Map<String, Object> m = a.meta();
        return new AttemptView(a.key(), a.wave(), a.attempt(), a.won(), a.manual(), a.at(), num(m, "lives"),
                num(m, "livesMax"), num(m, "enemies"), num(m, "killed"), num(m, "leaked"),
                ((Number) m.get("seconds")).doubleValue());
    }

    private static int num(Map<String, Object> m, String k) {
        return ((Number) m.get(k)).intValue();
    }

    private static double round2(double v) {
        return Math.round(v * 100.0) / 100.0;
    }
}
