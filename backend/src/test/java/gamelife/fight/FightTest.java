package gamelife.fight;

import gamelife.IntegrationTest;
import gamelife.checkin.CheckinService;
import gamelife.gear.ShopService;
import gamelife.ledger.EventType;
import gamelife.rest.RestDayService;
import gamelife.rules.LedgerFacts;
import gamelife.tasks.Task;
import gamelife.tasks.TaskService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.Duration;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Brief 9.1 and 14: the losable boss fight. Tests start on Monday 2 March 2026, 10:00 Brussels.
 * Boss 1: 800 HP, ATK 20. Hero: 100 + 10 x Vitality level 1 = 110 HP. Daily goal 100 XP.
 */
class FightTest extends IntegrationTest {
    @Autowired FightService fight;
    @Autowired TaskService tasks;
    @Autowired CheckinService checkin;
    @Autowired RestDayService rest;
    @Autowired ShopService shop;
    @Autowired gamelife.config.GameConfig config;

    /** Ticks a new task worth `minutes` base XP and returns the final XP paid. */
    private double earn(int minutes) {
        Task t = tasks.create("Work " + minutes + " " + clock.instant(), minutes, List.of());
        return tasks.tick(t.id()).event().xp().doubleValue();
    }

    private void nextDay() {
        clock.advance(Duration.ofDays(1));
    }

    private long count(EventType type) {
        return ledger.all().stream().filter(e -> e.type() == type).count();
    }

    private double lastAttackDamage() {
        return fight.view().recentAttacks().get(0).damage();
    }

    // ---------------------------------------------------------------- damage to the boss

    @Test
    void damageEqualsTheXpPaidAndUntickReversesIt() {
        Task t = tasks.create("Study", 300, List.of());
        double xp = tasks.tick(t.id()).event().xp().doubleValue(); // 330
        assertThat(fight.view().bossHp()).isEqualTo(800 - xp);
        tasks.untick(t.id());
        assertThat(fight.view().bossHp()).isEqualTo(800.0);
    }

    @Test
    void overkillIsDiscardedAndVictoryPaysTheRewardOnce() {
        double xp = earn(1000); // 1100 XP into an 800 HP boss
        assertThat(count(EventType.STAGE_CLEARED)).isEqualTo(1);
        var v = fight.view();
        assertThat(v.boss()).isEqualTo(2);                 // the next boss appears the same day
        double boss2 = FightRules.bossMaxHp(2, 0, config.fight()); // 800 x growth
        assertThat(v.bossHp()).isEqualTo(v.bossMaxHp()).isEqualTo(boss2); // overkill did not carry over
        assertThat(v.bossAtk()).isEqualTo(24.0);
        assertThat(LedgerFacts.balance(ledger.all()).doubleValue()).isEqualTo(xp + 100);
        fight.sync();
        fight.sync();
        assertThat(count(EventType.STAGE_CLEARED)).isEqualTo(1);
    }

    @Test
    void untickAfterAKillKeepsTheKill() {
        Task t = tasks.create("Huge", 800, List.of());
        tasks.tick(t.id());
        tasks.untick(t.id());
        assertThat(count(EventType.STAGE_CLEARED)).isEqualTo(1);
        assertThat(fight.view().boss()).isEqualTo(2);
        assertThat(fight.view().bossHp()).isEqualTo(FightRules.bossMaxHp(2, 0, config.fight())); // un-tick does not touch the new boss
    }

    // ---------------------------------------------------------------- daily attack

    @Test
    void zeroXpDayTakesFullDamage() {
        checkin.checkin(3);       // day 1 starts the game with 5.5 XP
        nextDay();
        nextDay();                // day 2 had zero XP
        var attacks = fight.view().recentAttacks();
        assertThat(attacks).hasSize(2);
        assertThat(attacks.get(0).date()).isEqualTo("2026-03-03");
        assertThat(attacks.get(0).damage()).isEqualTo(20.0);
        assertThat(fight.view().heroHp()).isEqualTo(110 - 20 - 20 * (1 - 0.055));
    }

    @Test
    void halfTheGoalTakesHalfAndAFullGoalTakesNoneAndHeals() {
        earn(45);                 // 49.5 XP: shield 0.495
        nextDay();
        assertThat(lastAttackDamage()).isEqualTo(10.1); // 20 x 0.505
        earn(100);                // 100 x 1.1 = 110 XP >= goal
        nextDay();
        var a = fight.view().recentAttacks().get(0);
        assertThat(a.damage()).isZero();
        assertThat(a.heal()).isEqualTo(10.0);
        assertThat(fight.view().heroHp()).isEqualTo(109.9); // 110 - 10.1 + 10 healed
    }

    @Test
    void freezeRestAndPauseShieldFully() {
        // rest day: Tuesday planned on Monday
        checkin.checkin(3);
        rest.plan(LocalDate.of(2026, 3, 3));
        nextDay();
        nextDay();
        // pause: Wednesday paused on Wednesday
        fight.pause(null);
        nextDay(); // Thursday: nothing planned, zero XP
        nextDay();
        var attacks = fight.view().recentAttacks(); // newest first: Thu, Wed, Tue, Mon
        assertThat(attacks.get(2).protectedDay()).isTrue();
        assertThat(attacks.get(2).damage()).isZero();       // rest day
        assertThat(attacks.get(1).protectedDay()).isTrue();
        assertThat(attacks.get(1).damage()).isZero();       // pause
        assertThat(attacks.get(0).damage()).isEqualTo(20.0); // plain zero day
    }

    @Test
    void aFreezeShieldsTheMissedDay() {
        for (int i = 0; i < 14; i++) { // 14 counted days earn a freeze
            checkin.checkin(3);
            nextDay();
        }
        nextDay(); // day 15 missed: the freeze covers it
        var a = fight.view().recentAttacks().get(0);
        assertThat(a.protectedDay()).isTrue();
        assertThat(a.damage()).isZero();
    }

    @Test
    void eachDayIsProcessedExactlyOnceEvenAfterManyDaysAway() {
        checkin.checkin(3);
        clock.advance(Duration.ofDays(4)); // app closed for days: Mon..Thu are past days
        fight.view();
        fight.view();
        fight.sync();
        assertThat(count(EventType.BOSS_ATTACK)).isEqualTo(4);
        assertThat(ledger.all().stream().filter(e -> e.type() == EventType.BOSS_ATTACK).map(e -> e.key()).distinct().count())
                .isEqualTo(4);
        assertThat(ledger.verify().valid()).isTrue();
    }

    @Test
    void attacksPayNothing() {
        checkin.checkin(3);
        double before = LedgerFacts.balance(ledger.all()).doubleValue();
        clock.advance(Duration.ofDays(3));
        fight.sync();
        assertThat(LedgerFacts.balance(ledger.all()).doubleValue()).isEqualTo(before);
        assertThat(ledger.all().stream().filter(e -> e.type() == EventType.BOSS_ATTACK)).allMatch(e -> e.xp().signum() == 0);
    }

    @Test
    void armourReducesDamage() {
        earn(150); // 165 coins
        shop.upgrade("armor", true); // level 1: -0.05 damage, +20 HP
        nextDay();
        nextDay(); // a zero-XP day
        assertThat(lastAttackDamage()).isEqualTo(19.0); // 20 x 0.95
        assertThat(fight.view().heroMaxHp()).isEqualTo(130.0);
    }

    // ---------------------------------------------------------------- falling

    @Test
    void heroFallsAtZeroHpTheBossHealsAndNothingIsLost() {
        double xp = earn(200);                // boss at 800 - 220
        double coins = LedgerFacts.balance(ledger.all()).doubleValue();
        var statsBefore = LedgerFacts.statTotals(ledger.all());
        for (int i = 0; i < 7; i++) nextDay(); // day 1 shielded, then six zero days: 6 x 20 = 120 > 110
        var v = fight.view();
        assertThat(count(EventType.HERO_FELL)).isEqualTo(1);
        assertThat(v.falls()).isEqualTo(1);
        assertThat(v.defeatsByThisBoss()).isEqualTo(1);
        assertThat(v.bossHp()).isEqualTo(800.0);   // healed to full
        assertThat(v.heroHp()).isEqualTo(110.0);   // back at full HP
        assertThat(LedgerFacts.balance(ledger.all()).doubleValue()).isEqualTo(coins); // mild: nothing lost
        assertThat(LedgerFacts.statTotals(ledger.all())).isEqualTo(statsBefore);
        assertThat(xp).isPositive();
    }

    // ---------------------------------------------------------------- ascension

    @Test
    void ascensionAfterTheLastBossFile() {
        assertThatThrownBy(() -> fight.ascend()).hasMessageContaining("Beat all");
        for (int k = 1; k <= 10; k++) {
            earn((int) fight.view().bossMaxHp());
        }
        var v = fight.view();
        assertThat(v.kills()).isEqualTo(10);
        assertThat(v.ascendReady()).isTrue();
        assertThat(v.boss()).isEqualTo(11);
        assertThat(v.variant()).isTrue();                   // loops to the first picture, tinted
        assertThat(v.bossSprite()).isEqualTo("content/enemies/boss_01.png");

        fight.ascend();
        v = fight.view();
        assertThat(v.boss()).isEqualTo(1);
        assertThat(v.ascensions()).isEqualTo(1);
        assertThat(v.bossMaxHp()).isEqualTo(880.0);         // stronger variant: 800 x (1 + 0.1 x 1 ascension)
        assertThat(v.variant()).isTrue();
        assertThat(v.ascendReady()).isFalse();
        assertThatThrownBy(() -> fight.ascend()).hasMessageContaining("Beat all");
    }

    // ---------------------------------------------------------------- pause limits

    @Test
    void pauseIsLimitedPerWindowAndOnlyForComingDays() {
        for (int i = 0; i < 7; i++) fight.pause(LocalDate.of(2026, 3, 2).plusDays(i));
        assertThatThrownBy(() -> fight.pause(LocalDate.of(2026, 3, 9))).hasMessageContaining("Already 7");
        assertThatThrownBy(() -> fight.pause(LocalDate.of(2026, 3, 1))).hasMessageContaining("today or a coming day");
        assertThat(fight.pause(LocalDate.of(2026, 3, 2)).paid()).isFalse(); // double tap
    }
}
