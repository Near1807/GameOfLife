package gamelife.gear;

import gamelife.IntegrationTest;
import gamelife.fight.FightService;
import gamelife.payout.PayoutService;
import gamelife.rules.LedgerFacts;
import gamelife.tasks.TaskService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Brief 9.2, 9.3, 10 and 14: purchases, upgrades, gear effects, requirements. */
class ShopTest extends IntegrationTest {
    @Autowired ShopService shop;
    @Autowired GearService gear;
    @Autowired FightService fight;
    @Autowired TaskService tasks;
    @Autowired PayoutService payout;
    @Autowired gamelife.config.GameConfig config;

    private void earn(int minutes, String... tags) {
        tasks.tick(tasks.create("Earn " + minutes + clock.instant(), minutes, List.of(tags)).id());
    }

    private double coins() {
        return LedgerFacts.balance(ledger.all()).doubleValue();
    }

    @Test
    void purchaseRefusedWhenTooPoorAndNeedsConfirmation() {
        earn(100); // 110 coins
        assertThatThrownBy(() -> shop.buy("charm_ice_crystal", true)).hasMessageContaining("Not enough coins");
        assertThatThrownBy(() -> shop.upgrade("weapon", true)).hasMessageContaining("Not enough coins");
        assertThatThrownBy(() -> shop.buy("charm_ice_crystal", false)).hasMessageContaining("confirmation");
        assertThat(coins()).isEqualTo(110.0);
    }

    @Test
    void confirmedPurchasePaysOnceAndEquips() {
        earn(400); // 440 coins
        var r = shop.buy("charm_aegis_rune", true); // 400
        assertThat(r.paid()).isTrue();
        assertThat(shop.buy("charm_aegis_rune", true).paid()).isFalse(); // double tap pays once
        assertThat(coins()).isEqualTo(40.0);
        assertThat(gear.loadout().owned()).contains("charm_aegis_rune");
        assertThat(gear.loadout().charms()).containsExactly("charm_aegis_rune"); // worn at once
    }

    @Test
    void weaponUpgradeRaisesLaterPayouts() {
        earn(200); // 220 coins
        shop.upgrade("weapon", true); // 150
        assertThat(coins()).isEqualTo(70.0);
        assertThat(gear.loadout().weaponLevel()).isEqualTo(1);
        assertThat(payout.multipliersNow(ledger.all()).weapon()).isEqualTo(config.weapon().bonusPerLevel());
        assertThat(shop.view().weapon().cost())
                .isEqualTo((double) Math.round(150 * config.weapon().costGrowth())); // level 1 -> 2
    }

    @Test
    void armourAndCharmEffectsApplyToTheFight() {
        earn(700); // 770 coins
        shop.upgrade("armor", true);       // 120: +20 HP, -5% damage
        shop.buy("charm_aegis_rune", true); // 400: +5% shield every day
        clock.advance(Duration.ofDays(2));  // day 2 is a zero day
        var v = fight.view();
        assertThat(v.heroMaxHp()).isEqualTo(130.0);
        var a = v.recentAttacks().get(0);
        assertThat(a.shield()).isEqualTo(0.05);
        assertThat(a.damage()).isEqualTo(18.05); // 20 x 0.95 shield x 0.95 armour
    }

    @Test
    void itemsWithAStatRequirementAreLocked() {
        earn(600); // 660 coins, no Strength
        var view = shop.view();
        var blade = view.items().stream().filter(i -> i.item().id().equals("weapon_arc_blade")).findFirst().orElseThrow();
        assertThat(blade.status()).isEqualTo("LOCKED");
        assertThat(blade.lockReason()).contains("Strength level 3");
        assertThatThrownBy(() -> shop.buy("weapon_arc_blade", true)).hasMessageContaining("Locked");

        earn(300, "STRENGTH"); // Strength 300 stat XP -> level 3
        assertThat(shop.buy("weapon_arc_blade", true).paid()).isTrue();
        assertThat(payout.multipliersNow(ledger.all()).weapon()).isEqualTo(1.08);
    }

    @Test
    void onlyTwoCharmsAtOnceAndThemesSwap() {
        earn(1200);
        shop.buy("charm_aegis_rune", true);
        shop.buy("charm_ice_crystal", true);
        assertThatThrownBy(() -> shop.buy("charm_moon_amulet", true)).hasMessageContaining("Only 2 charms");
        assertThat(gear.loadout().charms()).containsExactly("charm_aegis_rune", "charm_ice_crystal");
        assertThat(gear.loadout().equipped().get("theme")).isEqualTo("theme_nebula"); // free default
        shop.buy("theme_aurora", true);
        assertThat(gear.loadout().equipped().get("theme")).isEqualTo("theme_aurora");
        shop.equip("theme_nebula", true);
        assertThat(gear.loadout().equipped().get("theme")).isEqualTo("theme_nebula");
    }
}
