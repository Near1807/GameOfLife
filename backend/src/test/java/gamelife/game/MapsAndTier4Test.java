package gamelife.game;

import gamelife.IntegrationTest;
import gamelife.common.Money;
import gamelife.ledger.EventType;
import gamelife.ledger.NewEvent;
import gamelife.rules.LedgerFacts;
import gamelife.tasks.TaskService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Maps as chapters and the stat-gated tier 4 (v4.1). */
class MapsAndTier4Test extends IntegrationTest {
    @Autowired GameService game;
    @Autowired WaveService waves;
    @Autowired GameViewService views;
    @Autowired TaskService tasks;

    private void earn(int minutes, String... tags) {
        tasks.tick(tasks.create("Earn " + minutes + " " + clock.instant() + List.of(tags), minutes, List.of(tags)).id());
    }

    private double coins() {
        return LedgerFacts.balance(ledger.all()).doubleValue();
    }

    private GameLedger.State state() {
        return GameLedger.derive(ledger.all(), GameFixtures.CAT);
    }

    /** Stands for many cleared waves: a WAVE_CLEARED event as the scheduler writes it. */
    private void clearedUpTo(int wave) {
        Map<String, Object> meta = new LinkedHashMap<>();
        meta.put("wave", wave);
        meta.put("attempt", 1);
        meta.put("at", Instant.now(clock).toString());
        meta.put("manual", true);
        meta.put("inputs", "test");
        meta.put("map", "map_01");
        meta.put("towers", List.of());
        meta.put("stats", Map.of());
        for (String k : List.of("lives", "livesMax", "enemies", "killed", "leaked", "seconds")) {
            meta.put(k, 1);
        }
        ledger.append(new NewEvent("wave-attempt:" + wave + ":1", EventType.WAVE_CLEARED, "game", "Wave " + wave,
                Money.ZERO, Money.ZERO, Map.of(), meta));
    }

    @Test
    void mapsOpenAtTheirWaveAndMovingIsAFreeFullRefund() {
        earn(1500);
        game.unlock(5);                                        // one spot bought on the first map
        game.build(0, "archer");
        game.upgrade(0, 0, 1);
        game.build(5, "archer");
        assertThatThrownBy(() -> game.move("map_02", true)).hasMessageContaining("opens after wave 30");
        assertThat(views.view().maps()).extracting(GameViewService.MapChoice::unlocked).containsExactly(true, false, false);

        clearedUpTo(30);
        assertThatThrownBy(() -> game.move("map_02", false)).hasMessageContaining("confirmation");
        double before = coins();
        double inTowers = state().build().towers().values().stream().mapToDouble(Build.Tower::paid).sum();
        var r = game.move("map_02", true);
        assertThat(r.paid()).isTrue();
        assertThat(game.move("map_02", true).paid()).isFalse();          // double tap: moves once
        assertThat(coins()).isEqualTo(before + inTowers);                // every tower refunded

        GameLedger.State s = state();
        assertThat(s.map().id()).isEqualTo("map_02");
        assertThat(s.build().towers()).isEmpty();
        assertThat(s.wave()).isEqualTo(31);                              // the wave count continues
        assertThat(s.build().openSpots()).containsExactly(0, 1, 2, 3);   // 3 free + the 1 bought spot
        assertThat(views.view().maps()).extracting(GameViewService.MapChoice::active).containsExactly(false, true, false);

        // building, spot prices and keys continue on the new map
        assertThat(game.build(3, "archer").event().key()).isEqualTo("tower:map_02:3:1:built");
        double c0 = coins();
        game.unlock(10);
        assertThat(c0 - coins()).isEqualTo(GameMath.spotCost(1, GameFixtures.G));  // the second spot bought
        assertThat(state().build().openSpots()).contains(10).hasSize(5);
        // and the next attempt is played on the new map
        clock.advance(Duration.ofMinutes(5));
        waves.runNow();
        assertThat(state().lastAttempt().meta().get("map")).isEqualTo("map_02");
        assertThat(state().lastAttempt().wave()).isEqualTo(31);
    }

    @Test
    void movingBackIsAllowedAndKeepsTheSpotsBoughtThere() {
        earn(800);
        game.unlock(9);
        clearedUpTo(30);
        game.move("map_02", true);
        game.move("map_01", true);
        assertThat(state().map().id()).isEqualTo("map_01");
        assertThat(state().build().openSpots()).containsExactly(0, 1, 2, 9);
    }

    @Test
    void tier4NeedsItsStatLevelAndKeepsThePathLock() {
        earn(2500);
        game.build(0, "archer");
        game.upgrade(0, 0, 1);
        game.upgrade(0, 0, 2);
        game.upgrade(0, 0, 3);
        assertThatThrownBy(() -> game.upgrade(0, 0, 4)).hasMessageContaining("Sniper 4 needs Strength level 10");
        var path = views.view().spots().get(0).tower().paths().get(0);
        assertThat(path.needs()).isEqualTo("Needs Strength 10");
        assertThat(path.tiers()).hasSize(4);
        earn(1700, "STRENGTH");                                  // Strength level 10 needs 1679 stat XP
        assertThat(game.upgrade(0, 0, 4).paid()).isTrue();
        assertThatThrownBy(() -> game.upgrade(0, 1, 1)).hasMessageContaining("Locked");
        // the tier counts in the simulation: x1.8 damage
        TowerStats t3 = TowerStats.of(GameFixtures.CAT.tower("archer"), new Build.Tower("archer", 3, 0, 0));
        TowerStats t4 = TowerStats.of(GameFixtures.CAT.tower("archer"), new Build.Tower("archer", 4, 0, 0));
        assertThat(t4.dmg).isCloseTo(t3.dmg * 1.8, org.assertj.core.data.Offset.offset(1e-9));
    }
}
