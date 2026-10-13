package gamelife.fight;

import gamelife.common.Json;
import gamelife.config.GameConfig;
import gamelife.ledger.EventType;
import gamelife.ledger.LedgerEvent;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Rebuilds the state of the boss fight by replaying the ledger in order (brief 9.1:
 * "boss HP, hero HP, defeat counters and the current boss are computed from the ledger").
 * Pure: no database, no clock. The rules per event:
 *
 *  - an event with XP > 0 deals that XP as damage; overkill is discarded (the boss stops at 0);
 *  - an un-tick (TASK_UNDONE) gives back the damage its tick dealt, but only if that tick hit the
 *    boss that is still being fought (a kill is never undone);
 *  - STAGE_CLEARED: the boss is dead, the next one appears at full HP, the hero starts at full HP;
 *  - BOSS_ATTACK: the hero loses meta.damage and heals meta.heal (kept as "missing HP", so a
 *    higher max HP from Vitality or armour raises current HP too);
 *  - HERO_FELL: the boss heals to full, the hero is back at full HP, the boss's defeat counter +1;
 *  - ASCEND: back to boss 1 of a new, stronger cycle.
 */
public final class FightReplay {
    private FightReplay() {}

    public static final class State {
        public int ascensions;
        public int boss = 1;               // k: number of the boss in this cycle (1, 2, 3, ...)
        public double bossMaxHp;
        public double bossHp;
        public double heroMissing;         // HP lost since the hero was last at full health
        public int kills;                  // all kills, all cycles
        public int killsThisCycle;
        public int falls;                  // all falls
        public boolean fellThisFight;      // for defeatMode "medium"
        public final Map<String, Integer> defeatsByBoss = new HashMap<>(); // "asc-k" -> falls against it
        public final Set<LocalDate> attackedDays = new HashSet<>();
        public final Set<LocalDate> pausedDays = new HashSet<>();
        public final Set<LocalDate> fallDays = new HashSet<>();
        public double damageThisFight;

        public String fightId() {
            return ascensions + "-" + boss;
        }

        public int defeatsByCurrentBoss() {
            return defeatsByBoss.getOrDefault(fightId(), 0);
        }

        /** The boss is at 0 HP but its kill is not written yet. */
        public boolean killPending() {
            return bossHp <= 0;
        }
    }

    /** What each paying event did to which fight, so an un-tick can give it back. */
    private record Hit(String fightId, double damage) {}

    public static State replay(Iterable<LedgerEvent> events, GameConfig cfg) {
        State s = new State();
        s.bossMaxHp = FightRules.bossMaxHp(1, 0, cfg.fight());
        s.bossHp = s.bossMaxHp;
        Map<String, Hit> hits = new HashMap<>();

        for (LedgerEvent e : events) {
            switch (e.type()) {
                case ASCEND -> {
                    s.ascensions++;
                    s.killsThisCycle = 0;
                    nextBoss(s, 1, cfg);
                }
                case STAGE_CLEARED -> {
                    s.kills++;
                    s.killsThisCycle++;
                    nextBoss(s, s.boss + 1, cfg);
                }
                case HERO_FELL -> {
                    s.falls++;
                    s.defeatsByBoss.merge(s.fightId(), 1, Integer::sum);
                    s.fellThisFight = true;
                    s.bossHp = s.bossMaxHp;
                    s.damageThisFight = 0;
                    s.heroMissing = 0;
                    s.fallDays.add(e.gameDay());
                }
                case BOSS_ATTACK -> {
                    Map<String, Object> m = Json.toMap(e.meta());
                    double dmg = num(m.get("damage"));
                    double heal = num(m.get("heal"));
                    s.heroMissing = Math.max(0, s.heroMissing + dmg - heal);
                    s.attackedDays.add(LocalDate.parse(String.valueOf(m.get("date"))));
                }
                case PAUSE -> s.pausedDays.add(LocalDate.parse(e.key().substring("pause:".length())));
                case TASK_UNDONE -> {
                    Object reverses = Json.toMap(e.meta()).get("reverses");
                    Hit hit = reverses == null ? null : hits.remove(reverses.toString());
                    if (hit != null && hit.fightId().equals(s.fightId()) && !s.killPending()) {
                        s.bossHp = Math.min(s.bossMaxHp, s.bossHp + hit.damage());
                        s.damageThisFight -= hit.damage();
                    }
                }
                default -> {
                    double xp = e.xp().doubleValue();
                    if (xp > 0) {
                        double dmg = Math.min(xp, s.bossHp); // overkill is discarded
                        s.bossHp -= dmg;
                        s.damageThisFight += dmg;
                        hits.put(e.key(), new Hit(s.fightId(), dmg));
                    }
                }
            }
        }
        return s;
    }

    private static void nextBoss(State s, int k, GameConfig cfg) {
        s.boss = k;
        s.bossMaxHp = FightRules.bossMaxHp(k, s.ascensions, cfg.fight());
        s.bossHp = s.bossMaxHp;
        s.heroMissing = 0;
        s.fellThisFight = false;
        s.damageThisFight = 0;
    }

    private static double num(Object o) {
        return o == null ? 0 : Double.parseDouble(o.toString());
    }
}
