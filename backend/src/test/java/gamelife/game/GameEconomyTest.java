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
    @Autowired gamelife.config.GameConfig config;

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
        game.build(0, "archer");                 // full price: 100
        game.upgrade(0, 1, 1);                    // Rapid 1, 80
        double paidForArcher = 100 + 80;
        game.build(1, "income");                  // Market
        game.upgrade(1, 1, 1);                    // Bargain 1: everything 5% cheaper from now on
        game.upgrade(0, 1, 2);                    // Rapid 2 at the new price: 200 x 0.95 = 190
        paidForArcher += 190;
        double before = coins();
        var sale = game.sell(0, true);
        assertThat(sale.event().currencyDelta()).isEqualByComparingTo(BigDecimal.valueOf(paidForArcher));
        assertThat(coins()).isEqualTo(before + paidForArcher);
        // the price list today is different: the refund did not use it
        assertThat(GameMath.price(100, 0.05) + GameMath.price(80, 0.05) + GameMath.price(200, 0.05))
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
        earn(1200);
        assertThatThrownBy(() -> game.build(3, "archer")).hasMessageContaining("Unlock this spot first");
        assertThatThrownBy(() -> game.unlock(0)).hasMessageContaining("already open");
        double c0 = coins();
        game.unlock(3);                            // spotBaseCost x spotCostGrowth^0
        double c1 = coins();
        game.unlock(7);                            // ... ^1
        double c2 = coins();
        game.unlock(4);                            // ... ^2
        double g = config.game().spotCostGrowth();
        assertThat(c0 - c1).isEqualTo(150);
        assertThat(c1 - c2).isEqualTo(Math.round(150 * g));
        assertThat(c2 - coins()).isEqualTo(Math.round(150 * g * g));
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

    @Test
    void aMarketRaisesTheCoinsOfRealActionsButNeverTheXp() {
        earn(500);
        game.build(0, "income");                  // Market: earnings +6%
        double before = coins();
        tasks.tick(tasks.create("Study", 100, List.of("INTELLECT")).id());
        LedgerEvent paid = ledger.all().get(ledger.all().size() - 1);
        assertThat(paid.xp()).isEqualByComparingTo("110.00");             // 100 x streak 1.10, unchanged
        assertThat(paid.currencyDelta()).isEqualByComparingTo("116.60");  // x 1.06
        assertThat(paid.statSplits()).isEqualTo("{\"INTELLECT\":\"100.00\"}");
        assertThat(coins() - before).isCloseTo(116.6, org.assertj.core.data.Offset.offset(1e-6));
        // selling the Market gives back its price and the bonus stops
        game.sell(0, true);
        tasks.tick(tasks.create("Study more", 100, List.of()).id());
        LedgerEvent after = ledger.all().get(ledger.all().size() - 1);
        assertThat(after.currencyDelta()).isEqualByComparingTo(after.xp());
    }

    @Test
    void socialAndCreativityLevelsFeedIncomeAndDiscount() {
        var g = gamelife.config.GameConfig.load(java.nio.file.Path.of("../config/gamelife.json")).game();
        var cat = GameCatalog.load(java.nio.file.Path.of("../content"));
        Build market = Build.empty(3).withTower(0, new Build.Tower("income", 3, 0, 0));       // Commerce 3: +25%
        var stats = new java.util.HashMap<String, Integer>(java.util.Map.of("SOCIAL", 11, "CREATIVITY", 41));
        assertThat(GameMath.incomeMultiplier(market, cat, stats, g)).isCloseTo(1.25 * 1.30, org.assertj.core.data.Offset.offset(1e-9));
        assertThat(GameMath.discount(market, cat, stats, g)).isCloseTo(0.15, org.assertj.core.data.Offset.offset(1e-9)); // CRE capped
        Build bargains = Build.empty(3).withTower(0, new Build.Tower("income", 0, 3, 0))
                .withTower(1, new Build.Tower("income", 0, 3, 0)).withTower(2, new Build.Tower("income", 0, 3, 0));
        assertThat(GameMath.discount(bargains, cat, stats, g)).isCloseTo(0.5, org.assertj.core.data.Offset.offset(1e-9)); // 0.4 + 0.15 -> 0.5
    }
}
