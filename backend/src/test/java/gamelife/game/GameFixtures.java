package gamelife.game;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import gamelife.config.GameConfig;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

/** The shipped game data and config, plus small helpers to build test scenarios. */
final class GameFixtures {
    static final GameConfig CONFIG = GameConfig.load(Path.of("../config/gamelife.json"));
    static final GameConfig.Game G = CONFIG.game();
    static final GameCatalog CAT = GameCatalog.load(Path.of("../content"));
    static final GameCatalog.MapDef MAP = CAT.firstMap();

    private GameFixtures() {}

    /** Every stat at the given level. */
    static Map<String, Integer> stats(int level) {
        Map<String, Integer> s = new HashMap<>();
        CONFIG.stats().forEach(st -> s.put(st.id(), level));
        return s;
    }

    /** A build with all 15 spots open and the given towers: spot, type, tier A, tier B. */
    static Build build(Object... spotTypeAB) {
        Build b = Build.empty(MAP.spots().size());
        for (int i = 0; i < spotTypeAB.length; i += 4) {
            b = b.withTower((Integer) spotTypeAB[i], new Build.Tower((String) spotTypeAB[i + 1],
                    (Integer) spotTypeAB[i + 2], (Integer) spotTypeAB[i + 3], 0));
        }
        return b;
    }

    static Simulator.Result run(Build b, Map<String, Integer> stats, int wave) {
        return Simulator.simulate(MAP, b, stats, wave, CAT, G, true);
    }

    /** Like build(), on another map: all its spots open. */
    static Build buildOn(GameCatalog.MapDef map, Object... spotTypeAB) {
        Build b = Build.empty(map.spots().size());
        for (int i = 0; i < spotTypeAB.length; i += 4) {
            b = b.withTower((Integer) spotTypeAB[i], new Build.Tower((String) spotTypeAB[i + 1],
                    (Integer) spotTypeAB[i + 2], (Integer) spotTypeAB[i + 3], 0));
        }
        return b;
    }

    /** The shipped "game" config with some numbers changed. */
    static GameConfig.Game game(Map<String, Object> changes) {
        try {
            ObjectMapper m = new ObjectMapper().configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
            ObjectNode node = (ObjectNode) m.readTree(Files.readString(Path.of("../config/gamelife.json"))).get("game");
            changes.forEach((k, v) -> node.set(k, m.valueToTree(v)));
            return m.treeToValue(node, GameConfig.Game.class);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    /** The shipped catalogue with one tower's base stat changed. */
    static GameCatalog withBase(String tower, String stat, double value) {
        Map<String, GameCatalog.TowerDef> towers = new java.util.LinkedHashMap<>(CAT.towers());
        GameCatalog.TowerDef d = towers.get(tower);
        Map<String, Double> base = new java.util.LinkedHashMap<>(d.base());
        base.put(stat, value);
        towers.put(tower, new GameCatalog.TowerDef(d.id(), d.name(), d.kind(), d.role(), d.cost(), d.color(),
                d.shape(), d.requires(), base, d.paths()));
        return new GameCatalog(towers, CAT.enemies(), CAT.groups(), CAT.groupPause(), CAT.maps());
    }
}
