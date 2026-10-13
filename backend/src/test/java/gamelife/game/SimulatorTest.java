package gamelife.game;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static gamelife.game.GameFixtures.*;
import static org.junit.jupiter.api.Assertions.*;

/** The simulation is a pure, deterministic function (v4 brief 5). */
class SimulatorTest {
    private static final ObjectMapper JSON = new ObjectMapper();

    static final Build THREE_ARCHERS = build(0, "archer", 0, 0, 1, "archer", 0, 0, 2, "archer", 0, 0);
    static final Build MIXED = build(0, "archer", 2, 0, 1, "cannon", 0, 1, 2, "frost", 1, 0, 3, "mage", 0, 1);
    static final Build ADVANCED = build(0, "tesla", 1, 0, 1, "venom", 0, 2, 2, "mage", 2, 0, 3, "banner", 0, 1,
            5, "archer", 0, 3);
    /** On the second map, with tier-4 towers (v4.1). */
    static final Build LATE = buildOn(GameFixtures.CAT.map("map_02"), 0, "tesla", 0, 4, 1, "mage", 4, 0, 2, "cannon", 4, 0,
            3, "frost", 0, 4, 4, "archer", 0, 4, 5, "venom", 4, 0, 6, "banner", 0, 4, 7, "cannon", 4, 0);

    @Test
    void sameInputsGiveIdenticalResultsAndReplays() throws Exception {
        String late1 = JSON.writeValueAsString(Simulator.simulate(CAT.map("map_02"), LATE, stats(12), 40, CAT, G, true));
        String late2 = JSON.writeValueAsString(Simulator.simulate(CAT.map("map_02"), LATE, stats(12), 40, CAT, G, true));
        assertEquals(late1, late2);
        for (Build b : new Build[]{THREE_ARCHERS, MIXED, ADVANCED}) {
            for (int wave : new int[]{1, 7, 10}) {
                String first = JSON.writeValueAsString(run(b, stats(6), wave));
                String second = JSON.writeValueAsString(run(b, stats(6), wave));
                assertEquals(first, second);
            }
        }
    }

    /** won, lives left, killed, leaked, seconds: fixed numbers for 3 builds on waves 1, 5 and 10. */
    @Test
    void goldenResults() {
        golden(THREE_ARCHERS, 1, true, 11, 6, 0, 7.25);
        golden(THREE_ARCHERS, 5, true, 6, 13, 5, 49.7);
        golden(THREE_ARCHERS, 10, false, 0, 14, 11, 39.52);
        golden(MIXED, 1, true, 11, 6, 0, 3.78);
        golden(MIXED, 5, true, 11, 18, 0, 17.5);
        golden(MIXED, 10, true, 6, 33, 1, 96.28);
        golden(ADVANCED, 1, true, 11, 6, 0, 4.0);
        golden(ADVANCED, 5, true, 11, 18, 0, 15.22);
        golden(ADVANCED, 10, true, 6, 33, 1, 88.98);
    }

    /** A tier-4 build on the second map, every stat at level 12 (v4.1). */
    @Test
    void goldenResultsOnTheSecondMapWithTier4() {
        goldenLate(30, true, 13, 78, 0, 115.17);
        goldenLate(50, true, 8, 119, 1, 190.45);
        goldenLate(70, false, 0, 3, 13, 71.58);
    }

    private void goldenLate(int wave, boolean won, int lives, int killed, int leaked, double seconds) {
        Simulator.Result r = Simulator.simulate(CAT.map("map_02"), LATE, stats(12), wave, CAT, G, false);
        assertEquals(won, r.won(), "wave " + wave);
        assertEquals(lives, r.lives(), "wave " + wave);
        assertEquals(killed, r.killed(), "wave " + wave);
        assertEquals(leaked, r.leaked(), "wave " + wave);
        assertEquals(seconds, r.seconds(), 1e-9, "wave " + wave);
    }

    private void golden(Build b, int wave, boolean won, int lives, int killed, int leaked, double seconds) {
        Simulator.Result r = run(b, stats(6), wave);
        String what = "wave " + wave + " " + b.fingerprint();
        assertEquals(won, r.won(), what);
        assertEquals(lives, r.lives(), what);
        assertEquals(killed, r.killed(), what);
        assertEquals(leaked, r.leaked(), what);
        assertEquals(seconds, r.seconds(), 1e-9, what);
    }

    @Test
    void waveCompositionFollowsThePrototype() {
        assertEquals(6, Simulator.makeWave(1, CAT, G).size());                        // 5 + 1 grunts
        var w5 = Simulator.makeWave(5, CAT, G);
        assertEquals(10, w5.stream().filter(s -> s.type().equals("grunt")).count());
        assertEquals(6, w5.stream().filter(s -> s.type().equals("runner")).count());   // 4 + floor(5/2)
        assertEquals(2, w5.stream().filter(s -> s.type().equals("armored")).count());  // 1 + floor(5/3)
        var w10 = Simulator.makeWave(10, CAT, G);
        assertEquals(1, w10.stream().filter(s -> s.type().equals("boss")).count());
        assertEquals(4, w10.stream().filter(s -> s.type().equals("warded")).count());
        assertEquals(5, w10.stream().filter(s -> s.type().equals("flyer")).count());
        // HP: enemyBaseHp x enemyHpGrowth^(n-1) x the type's multiplier
        assertEquals(G.enemyBaseHp() * Math.pow(G.enemyHpGrowth(), 9) * 25, w10.get(w10.size() - 1).hp(), 1e-9);
    }

    @Test
    void wavesAreLostOnlyAtZeroLivesAndLivesComeFromVitality() {
        // wave 1 is 6 grunts: with no tower they all leak, but 10 lives survive 6 leaks (prototype rule)
        Simulator.Result r = run(Build.empty(3), stats(1), 1);
        assertTrue(r.won());
        assertEquals(4, r.lives());
        assertEquals(10, r.livesMax());           // level 1: no bonus
        assertFalse(run(Build.empty(3), stats(1), 3).won()); // 13 enemies
        assertEquals(0, run(Build.empty(3), stats(1), 3).lives());
        assertEquals(12, run(Build.empty(3), stats(7), 1).livesMax());   // 10 + floor(6 / 3)
    }

    @Test
    void bannerBuffsOnlyTowersInsideItsRange() {
        // Banner on spot 3 (420,280), range 110: spot 5 (330,320) is 98 px away, spot 1 (300,200) 144 px
        Build b = build(3, "banner", 0, 0, 5, "archer", 0, 0, 1, "archer", 0, 0);
        Build none = build(5, "archer", 0, 0, 1, "archer", 0, 0);
        var stats = stats(1);
        TowerStats inside = GameMath.effective(5, b, CAT, MAP, stats, G);
        TowerStats outside = GameMath.effective(1, b, CAT, MAP, stats, G);
        TowerStats plain = GameMath.effective(5, none, CAT, MAP, stats, G);
        assertEquals(plain.dmg * 1.05, inside.dmg, 1e-9);
        assertEquals(plain.rate * 1.05, inside.rate, 1e-9);
        assertEquals(plain.dmg, outside.dmg, 1e-9);
        assertEquals(plain.rate, outside.rate, 1e-9);
    }

    @Test
    void chainLightningJumpsToNearbyEnemies() {
        Build tesla = build(1, "tesla", 0, 0);
        Simulator.Result chained = Simulator.simulate(MAP, tesla, stats(6), 6, CAT, G, true);
        Simulator.Result single = Simulator.simulate(MAP, tesla, stats(6), 6,
                withBase("tesla", "chain", 0), G, true);
        assertTrue(chained.damage().get(1) > single.damage().get(1) * 1.5);
        long jumps = chained.replay().frames().stream().mapToLong(f -> f.chains().size()).sum();
        assertTrue(jumps > 0);
        chained.replay().frames().forEach(f -> f.chains().forEach(c ->
                assertTrue(Math.hypot(c[2] - c[0], c[3] - c[1]) < G.chainRange() + 1)));
        assertEquals(0, single.replay().frames().stream().mapToLong(f -> f.chains().size()).sum());
    }

    @Test
    void poisonSpreadsToNeighboursWhenTheVictimDies() {
        // a Plague venom with a strong poison next to the path's first bend, where enemies bunch up
        Build venom = build(0, "venom", 2, 0);
        GameCatalog spreading = withBase("venom", "spread", 1);
        Simulator.Result with = Simulator.simulate(MAP, venom, stats(6), 4, spreading, G, false);
        Simulator.Result without = Simulator.simulate(MAP, venom, stats(6), 4, CAT, G, false);
        assertTrue(with.damage().get(0) > without.damage().get(0));
        assertTrue(with.killed() >= without.killed());
    }

    @Test
    void slowKeepsEnemiesOnTheMapLonger() {
        {
            var lots = game(Map.of("baseLives", 1000));
            // a Blizzard frost with no damage: enemies only slow down, nobody dies
            GameCatalog harmless = withBase("frost", "dmg", 0);
            Simulator.Result slowed = Simulator.simulate(MAP, build(1, "frost", 0, 2), stats(1), 1, harmless, lots, true);
            Simulator.Result free = Simulator.simulate(MAP, Build.empty(3), stats(1), 1, harmless, lots, true);
            assertEquals(0, slowed.killed());
            assertTrue(slowed.seconds() > free.seconds() + 1, slowed.seconds() + " vs " + free.seconds());
            assertTrue(slowed.replay().frames().stream().anyMatch(f -> f.enemies().stream().anyMatch(e -> (e[4] & 1) != 0)));
        }
    }

    @Test
    void spiritRaisesSlowUpToTheCap() {
        Build b = build(1, "frost", 3, 0);
        assertEquals(0.75 + G.spiritSlowPerLevel() * 5, GameMath.effective(1, b, CAT, MAP, stats(6), G).slow, 1e-9);
        assertEquals(G.maxSlow(), GameMath.effective(1, b, CAT, MAP, stats(100), G).slow, 1e-9);
    }

    @Test
    void burnDamagesOverTime() {
        // a wave one Mage cannot finish, and lives enough for everyone to walk through: all damage counts
        var lots = game(Map.of("baseLives", 1000));
        Build fire = build(1, "mage", 1, 0);
        Simulator.Result burning = Simulator.simulate(MAP, fire, stats(1), 8, CAT, lots, true);
        Simulator.Result noBurn = Simulator.simulate(MAP, build(1, "mage", 0, 0), stats(1), 8, CAT, lots, true);
        assertTrue(burning.damage().get(1) > noBurn.damage().get(1));
        assertTrue(burning.replay().frames().stream().anyMatch(f -> f.enemies().stream().anyMatch(e -> (e[4] & 2) != 0)));
    }

    @Test
    void resistanceAndPierce() {
        // Arcane tier 3 ignores all resistance: warded enemies take full magic damage
        TowerStats arcane = TowerStats.of(CAT.tower("mage"), new Build.Tower("mage", 0, 3, 0));
        assertEquals(1, arcane.pierce, 1e-9);
        assertEquals(20 * 1.7 * 1.65 * 1.8, arcane.dmg, 1e-9);
    }

    @Test
    void configChangesAlterTheOutcome() {
        Simulator.Result normal = Simulator.simulate(MAP, THREE_ARCHERS, stats(6), 5, CAT, G, false);
        Simulator.Result tougher = Simulator.simulate(MAP, THREE_ARCHERS, stats(6), 5, CAT,
                game(Map.of("enemyHpGrowth", 1.5)), false);
        assertTrue(normal.won());
        assertFalse(tougher.won());
    }

    @Test
    void replayEndsWithTheServerResult() {
        for (int wave : new int[]{1, 5, 10}) {
            Simulator.Result r = run(MIXED, stats(6), wave);
            var last = r.replay().frames().get(r.replay().frames().size() - 1);
            assertEquals(r.lives(), last.lives());
            assertEquals(r.seconds(), last.t(), 1e-9);
            long pops = r.replay().frames().stream().mapToLong(f -> f.pops().size()).sum();
            assertEquals(r.killed(), pops);
            if (r.won()) {
                assertTrue(last.enemies().isEmpty());
            }
        }
    }
}
