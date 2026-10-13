package gamelife.game;

import gamelife.IntegrationTest;
import gamelife.ledger.EventType;
import gamelife.ledger.LedgerEvent;
import gamelife.rules.LedgerFacts;
import gamelife.tasks.TaskService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** v4 brief 5: spending on the map, refunds, path lock, spot costs, requirements, integrity. */
class GameEconomyTest extends IntegrationTest {
    @Autowired GameService game;
    @Autowired TaskService tasks;

    private void earn(int minutes, String... tags) {
        tasks.tick(tasks.create("Earn " + minutes + " " + clock.instant() + List.of(tags), minutes, List.of(tags)).id());
    }

    private double coins() {
        return LedgerFacts.balance(ledger.all()).doubleValue();
    }

    @Test
    void buildThenSellLeavesTheBalanceUnchanged() {
        earn(500);
        double before = coins();
        game.build(0, "archer");
        assertThat(coins()).isLessThan(before);
        game.sell(0, true);
        assertThat(coins()).isEqualTo(before);
    }

    @Test
    void saleRefundsThePricePaidEvenAfterTheDiscountChanged() {
        earn(1500);
        game.build(0, "archer");                 // full price (Creativity 1 gives 0.5%: still 100)
        game.upgrade(0, 1, 1);                    // Rapid 1, 80
        double paidForArcher = 100 + 80;
        game.build(1, "income");                  // Market
        game.upgrade(1, 1, 1);                    // Bargain 1: everything 5% cheaper from now on
        game.upgrade(0, 1, 2);                    // Rapid 2 at the new price: ceil(200 x 0.945) = 189
        paidForArcher += 189;
        double before = coins();
        var sale = game.sell(0, true);
        assertThat(sale.event().currencyDelta()).isEqualByComparingTo(BigDecimal.valueOf(paidForArcher));
        assertThat(coins()).isEqualTo(before + paidForArcher);
        // the price list today is different: the refund did not use it
        assertThat(GameMath.price(100, 0.055) + GameMath.price(80, 0.055) + GameMath.price(200, 0.055))
                .isNotEqualTo((int) paidForArcher);
    }

    @Test
    void choosingAPathLocksTheOther() {
        earn(1000);
        game.build(0, "archer");
        assertThat(game.upgrade(0, 0, 1).paid()).isTrue();
        assertThat(game.upgrade(0, 0, 1).paid()).isFalse();           // double tap: once
        assertThatThrownBy(() -> game.upgrade(0, 1, 1)).hasMessageContaining("Locked");
        assertThatThrownBy(() -> game.upgrade(0, 0, 3)).hasMessageContaining("in order");
        assertThat(game.upgrade(0, 0, 2).paid()).isTrue();
        // selling and rebuilding frees the choice
        game.sell(0, true);
        game.build(0, "archer");
        assertThat(game.upgrade(0, 1, 1).paid()).isTrue();
    }

    @Test
    void spotCostsFollowTheFormula() {
        earn(1000);
        assertThatThrownBy(() -> game.build(3, "archer")).hasMessageContaining("Unlock this spot first");
        assertThatThrownBy(() -> game.unlock(0)).hasMessageContaining("already open");
        double c0 = coins();
        game.unlock(3);                            // 150 x 1.45^0
        double c1 = coins();
        game.unlock(7);                            // 150 x 1.45^1 = 217.5 -> 218
        double c2 = coins();
        game.unlock(4);                            // 150 x 1.45^2 = 315.4 -> 315
        assertThat(c0 - c1).isEqualTo(150);
        assertThat(c1 - c2).isEqualTo(218);
        assertThat(c2 - coins()).isEqualTo(315);
        assertThat(game.unlock(3).paid()).isFalse();
        assertThat(game.build(3, "archer").paid()).isTrue();
    }

    @Test
    void towersWithAStatRequirementAreRefusedBelowIt() {
        earn(100);
        assertThatThrownBy(() -> game.build(0, "cannon")).hasMessageContaining("Strength level 3 (you have 1)");
        earn(220, "STRENGTH");                     // 220 Strength XP: level 3 needs 100 + 115
        assertThat(game.build(0, "cannon").paid()).isTrue();
    }

    @Test
    void refusedWhenTooPoorOrSellWithoutConfirmation() {
        earn(50);
        assertThatThrownBy(() -> game.build(0, "archer")).hasMessageContaining("Not enough coins");
        earn(100);
        game.build(0, "archer");
        assertThatThrownBy(() -> game.sell(0, false)).hasMessageContaining("confirmation");
        assertThat(game.sell(0, true).paid()).isTrue();
        assertThat(game.sell(0, true).paid()).isFalse();             // double tap: once
    }

    @Test
    void noGameEventCreatesCurrencyOrXpExceptARefundOfWhatWasPaid() {
        earn(3000, "INTELLECT");
        game.unlock(3);
        game.build(0, "archer");
        game.upgrade(0, 0, 1);
        game.build(1, "income");
        game.upgrade(1, 0, 1);
        game.build(3, "mage");
        game.sell(0, true);
        game.sell(1, true);
        double paidOut = 0;
        for (LedgerEvent e : ledger.all()) {
            if (!GameLedger.BUILD_TYPES.contains(e.type()) && e.type() != EventType.WAVE_CLEARED
                    && e.type() != EventType.WAVE_FAILED) {
                continue;
            }
            assertThat(e.xp().signum()).isZero();
            assertThat(e.statSplits()).isEqualTo("{}");
            if (e.type() == EventType.TOWER_SOLD) {
                paidOut += e.currencyDelta().doubleValue();
            } else {
                assertThat(e.currencyDelta().signum()).isLessThanOrEqualTo(0);
            }
        }
        // the refunds equal what was spent on the two sold towers
        double spentOnSold = ledger.all().stream()
                .filter(e -> (e.type() == EventType.TOWER_BUILT || e.type() == EventType.TOWER_UPGRADED)
                        && !e.key().startsWith("tower:map_01:3:"))
                .mapToDouble(e -> -e.currencyDelta().doubleValue()).sum();
        assertThat(paidOut).isEqualTo(spentOnSold);
    }
}
