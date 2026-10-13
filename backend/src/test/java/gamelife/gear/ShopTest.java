package gamelife.gear;

import gamelife.IntegrationTest;
import gamelife.payout.PayoutService;
import gamelife.rules.LedgerFacts;
import gamelife.tasks.TaskService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Brief 10 and v4 brief 4: a cosmetic-only shop, two taps to buy, purchases change no number. */
class ShopTest extends IntegrationTest {
    @Autowired ShopService shop;
    @Autowired GearService gear;
    @Autowired TaskService tasks;
    @Autowired PayoutService payout;

    private void earn(int minutes) {
        tasks.tick(tasks.create("Earn " + minutes + clock.instant(), minutes, List.of()).id());
    }

    private double coins() {
        return LedgerFacts.balance(ledger.all()).doubleValue();
    }

    @Test
    void purchaseRefusedWhenTooPoorAndNeedsConfirmation() {
        earn(100); // 110 coins
        assertThatThrownBy(() -> shop.buy("skin_aurora", true)).hasMessageContaining("Not enough coins");
        assertThatThrownBy(() -> shop.buy("skin_aurora", false)).hasMessageContaining("confirmation");
        assertThat(coins()).isEqualTo(110.0);
    }

    @Test
    void confirmedPurchasePaysOnceAndEquips() {
        earn(300); // 330 coins
        var r = shop.buy("skin_aurora", true); // 300
        assertThat(r.paid()).isTrue();
        assertThat(shop.buy("skin_aurora", true).paid()).isFalse(); // double tap pays once
        assertThat(coins()).isEqualTo(30.0);
        assertThat(gear.loadout().owned()).contains("skin_aurora");
        assertThat(gear.loadout().equipped().get("skin")).isEqualTo("skin_aurora"); // worn at once
        shop.equip("skin_aurora", false);
        assertThat(gear.loadout().equipped()).doesNotContainKey("skin");
    }

    @Test
    void onlyLooksAreSoldAndTheyChangeNoNumber() {
        assertThat(shop.view().items()).extracting(i -> i.item().kind()).containsOnly("outfit", "skin", "theme");
        earn(1000);
        var before = payout.multipliersNow(ledger.all());
        shop.buy("outfit_neon_ronin", true);
        assertThat(payout.multipliersNow(ledger.all())).isEqualTo(before);
    }

    @Test
    void themesSwap() {
        earn(400);
        assertThat(gear.loadout().equipped().get("theme")).isEqualTo("theme_nebula"); // free default
        shop.buy("theme_aurora", true);
        assertThat(gear.loadout().equipped().get("theme")).isEqualTo("theme_aurora");
        shop.equip("theme_nebula", true);
        assertThat(gear.loadout().equipped().get("theme")).isEqualTo("theme_nebula");
        assertThatThrownBy(() -> shop.equip("theme_nebula", false)).hasMessageContaining("another theme");
    }
}
