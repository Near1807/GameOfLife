package gamelife.sim;

import gamelife.config.GameConfig;
import gamelife.fight.FightRules;
import gamelife.rules.LevelCurve;
import gamelife.rules.StreakCalculator;
import gamelife.rules.StreakState;

import java.math.BigDecimal;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Pacing simulation (brief 9.4). Reads config/gamelife.json and plays three profiles for a year
 * with the REAL rules (FightRules, StreakCalculator, LevelCurve), one payment a day:
 *   lazy:    30% of days with 0 XP (spread evenly), 100 base XP on the other days
 *   average: 100 base XP every day
 *   strong:  200 base XP every day
 * Base XP is spread over the seven stats (so Vitality, which raises max HP, grows slowly).
 * Coins buy the cheaper of the next weapon or armour level as soon as it is affordable.
 *
 * Run:  npm run simulate      (or mvn -f backend/pom.xml compile exec:java)
 */
public final class PacingSimulation {
    private PacingSimulation() {}

    public record Profile(String name, double baseXp, double zeroDayShare) {}

    public record Result(String profile, int daysToBoss1, int daysToBoss5, int bossesInAYear, int falls,
                         int weaponLevel, int armorLevel, double xpPerDayAtEnd, int fastestKillDays,
                         double day60BossMaxHp, double day60XpPerDay) {
        public boolean progresses() {
            return daysToBoss1 > 0 && daysToBoss5 > 0;
        }
    }

    public static final List<Profile> PROFILES = List.of(
            new Profile("lazy", 100, 0.3),
            new Profile("average", 100, 0),
            new Profile("strong", 200, 0));

    public static Result run(Profile p, GameConfig cfg, int days) {
        GameConfig.Fight f = cfg.fight();
        LocalDate start = LocalDate.of(2026, 1, 5);
        Set<LocalDate> counted = new HashSet<>();
        int boss = 1;
        double bossHp = FightRules.bossMaxHp(1, 0, f);
        double missing = 0;
        double coins = 0;
        double vitalityXp = 0;
        int weapon = 0, armor = 0, falls = 0, kills = 0;
        int dayBoss1 = -1, dayBoss5 = -1;
        int lastKillDay = 0, fastest = Integer.MAX_VALUE;
        double day60Hp = 0, day60Xp = 0;
        double lastFinal = 0;

        for (int day = 0; day < days; day++) {
            LocalDate d = start.plusDays(day);
            // lazy: zero days spread evenly (day 3, 6, 9 of every 10 for 30%)
            boolean zero = isZeroSlot(day, p);
            double base = zero ? 0 : p.baseXp();

            double finalXp = 0;
            if (base > 0) {
                StreakState s = StreakCalculator.compute(counted, Set.of(), d, cfg.streak());
                double mult = StreakCalculator.multiplier(s.streakForPayment(), cfg.streak())
                        * FightRules.weaponMultiplier(weapon, cfg.weapon())
                        * (s.comebackPending() ? cfg.streak().comebackMultiplier() : 1);
                finalXp = Math.round(base * mult * 100) / 100.0;
                counted.add(d);
                vitalityXp += base / 7.0;
                coins += finalXp;
                // damage, overkill discarded; a kill pays coins and the next boss appears at once
                bossHp -= Math.min(finalXp, bossHp);
                if (bossHp <= 0) {
                    kills++;
                    fastest = Math.min(fastest, day + 1 - lastKillDay);
                    lastKillDay = day + 1;
                    if (kills == 1) dayBoss1 = day + 1;
                    if (kills == 5) dayBoss5 = day + 1;
                    coins += f.killRewardCoins();
                    boss++;
                    bossHp = FightRules.bossMaxHp(boss, 0, f);
                    missing = 0;
                }
            }
            lastFinal = finalXp;
            if (day == 59) {
                day60Hp = FightRules.bossMaxHp(boss, 0, f);
                day60Xp = finalXp;
            }

            // shopping: the cheaper upgrade first, as soon as affordable
            while (true) {
                double wc = FightRules.weaponCost(weapon, cfg.weapon());
                double ac = FightRules.armorCost(armor, cfg.armor());
                if (ac < wc && coins >= ac) { coins -= ac; armor++; }
                else if (coins >= wc) { coins -= wc; weapon++; }
                else if (coins >= ac) { coins -= ac; armor++; }
                else break;
            }

            // the boss attacks at the reset; a day covered by a freeze is fully shielded
            boolean frozen = StreakCalculator.compute(counted, Set.of(), d.plusDays(1), cfg.streak()).frozenDays().contains(d);
            double atk = FightRules.bossAtk(boss, 0, f);
            double reduction = FightRules.armorReduction(armor, 0, cfg.armor());
            double shield = FightRules.shield(finalXp, cfg.home().dailyXpGoal(), 0, frozen);
            double dmg = FightRules.attackDamage(atk, shield, reduction);
            double heal = shield >= 1 ? f.fullShieldHeal() : 0;
            int vitality = LevelCurve.of(BigDecimal.valueOf(vitalityXp), cfg.statLevels()).level();
            double heroMax = FightRules.heroMaxHp(vitality, armor, 0, cfg);
            missing = Math.max(0, missing + dmg - heal);
            if (missing >= heroMax) {
                falls++;
                bossHp = FightRules.bossMaxHp(boss, 0, f); // the boss heals
                missing = 0;
            }
        }
        return new Result(p.name(), dayBoss1, dayBoss5, kills, falls, weapon, armor, lastFinal,
                fastest == Integer.MAX_VALUE ? -1 : fastest, day60Hp, day60Xp);
    }

    /** For a share s (e.g. 0.3), the zero days are days 2, 5, 8 of each block of 10: spread, never in a row. */
    private static boolean isZeroSlot(int day, Profile p) {
        int k = (int) Math.round(p.zeroDayShare() * 10);
        int slot = day % 10;
        for (int i = 0; i < k; i++) {
            if (slot == 2 + 3 * i) return true;
        }
        return false;
    }

    /** Zero-XP days in a row that knock a fresh hero (no armour, Vitality 1) out against boss 1. */
    public static int zeroDaysToFall(GameConfig cfg) {
        double hp = FightRules.heroMaxHp(1, 0, 0, cfg);
        double atk = FightRules.bossAtk(1, 0, cfg.fight());
        return (int) Math.ceil(hp / atk);
    }

    /**
     * "A strong week with a good weapon should be able to clear a boss in 1 to 2 days" (brief 9.1):
     * an average player at day 60 (with the weapon bought by then) who doubles their effort.
     * Days needed to clear a fresh boss of that moment.
     */
    public static int strongWeekDaysAtDay60(GameConfig cfg) {
        Result avg = run(PROFILES.get(1), cfg, 60);
        return (int) Math.ceil(avg.day60BossMaxHp() / (2 * avg.day60XpPerDay()));
    }

    public static List<Result> runAll(GameConfig cfg) {
        List<Result> results = new ArrayList<>();
        for (Profile p : PROFILES) results.add(run(p, cfg, 365));
        return results;
    }

    public static void main(String[] args) {
        Path configFile = Path.of(args.length > 0 ? args[0] : "../config/gamelife.json");
        GameConfig cfg = GameConfig.load(configFile);
        System.out.println("GameLife pacing simulation, one year, config " + configFile.toAbsolutePath().normalize());
        System.out.printf("%-8s %10s %10s %13s %6s %7s %6s %12s %13s%n", "profile", "boss 1", "boss 5", "bosses/year", "falls",
                "weapon", "armour", "XP/day end", "fastest kill");
        boolean ok = true;
        for (Result r : runAll(cfg)) {
            System.out.printf("%-8s %10s %10s %13d %6d %7d %6d %12.0f %13s%s%n", r.profile(), days(r.daysToBoss1()),
                    days(r.daysToBoss5()), r.bossesInAYear(), r.falls(), r.weaponLevel(), r.armorLevel(),
                    r.xpPerDayAtEnd(), r.fastestKillDays() + " days", r.progresses() ? "" : "   <-- CANNOT PROGRESS");
            ok &= r.progresses();
        }
        System.out.println("A fresh hero falls after " + zeroDaysToFall(cfg) + " zero-XP days in a row against boss 1.");
        System.out.println("An average player at day 60 who has a strong week (2x XP) clears a fresh boss in "
                + strongWeekDaysAtDay60(cfg) + " day(s).");
        System.out.println(ok ? "All profiles progress." : "Some profile cannot progress: tune config/gamelife.json.");
    }

    private static String days(int d) {
        return d < 0 ? "never" : "day " + d;
    }
}
