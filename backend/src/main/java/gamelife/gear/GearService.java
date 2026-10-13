package gamelife.gear;

import gamelife.common.Json;
import gamelife.common.Money;
import gamelife.config.GameConfig;
import gamelife.content.ContentService;
import gamelife.ledger.EventType;
import gamelife.ledger.LedgerEvent;
import gamelife.ledger.LedgerService;
import gamelife.ledger.NewEvent;
import gamelife.rules.LedgerFacts;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * What the hero owns and wears, always computed from the ledger (brief 5: owned items are never
 * stored as editable data):
 *  - owned items: PURCHASE events (bought or dropped as loot) plus every free item (price 0);
 *  - equipped items: the last EQUIP event per slot (charms: up to shop.maxCharms);
 *  - weapon and armour levels: UPGRADE events;
 *  - bonuses: the sum of the bonus fields and charm effects of what is equipped.
 */
@Service
public class GearService {
    private final LedgerService ledger;
    private final ContentService content;
    private final GameConfig config;

    public GearService(LedgerService ledger, ContentService content, GameConfig config) {
        this.ledger = ledger;
        this.content = content;
        this.config = config;
    }

    /** Charm effect names the game understands (brief 9.3). Unknown effects are ignored. */
    public static final String SHIELD_BONUS = "shieldBonus";     // + fraction to every day's shield
    public static final String SHIELD_HEAL = "shieldHeal";       // + HP healed per fully shielded day
    public static final String FREEZE_CAPACITY = "freezeCapacity"; // + freezes that can be stored
    public static final String COMEBACK_BONUS = "comebackBonus"; // + to the comeback multiplier
    public static final String GOAL_REDUCTION = "goalReduction"; // - fraction of the daily goal for the shield

    public record Loadout(int weaponLevel, int armorLevel, Set<String> owned, Map<String, String> equipped,
                          List<String> charms, ContentService.Bonus bonus, Map<String, Double> effects) {
        public double effect(String name) {
            return effects.getOrDefault(name, 0.0);
        }

        public boolean isEquipped(String itemId) {
            return equipped.containsValue(itemId) || charms.contains(itemId);
        }
    }

    public Loadout loadout() {
        return loadout(ledger.all());
    }

    public Loadout loadout(List<LedgerEvent> events) {
        Set<String> owned = new LinkedHashSet<>();
        content.catalog().items().stream().filter(i -> i.price() == 0).forEach(i -> owned.add(i.id()));
        Map<String, String> equipped = new LinkedHashMap<>();
        List<String> charms = new ArrayList<>();
        for (LedgerEvent e : events) {
            if (e.type() == EventType.PURCHASE) {
                Object id = Json.toMap(e.meta()).get("itemId");
                if (id != null) owned.add(id.toString());
            } else if (e.type() == EventType.EQUIP) {
                Map<String, Object> m = Json.toMap(e.meta());
                String slot = String.valueOf(m.get("slot"));
                Object item = m.get("itemId");
                if ("charm".equals(slot)) {
                    if (Boolean.TRUE.equals(m.get("on"))) {
                        if (item != null && !charms.contains(item.toString())) charms.add(item.toString());
                    } else if (item != null) {
                        charms.remove(item.toString());
                    }
                } else if (item == null) {
                    equipped.remove(slot);
                } else {
                    equipped.put(slot, item.toString());
                }
            }
        }
        // the free default theme is worn until another one is equipped
        if (!equipped.containsKey("theme")) {
            content.catalog().items().stream().filter(i -> "theme".equals(i.kind()) && i.price() == 0).findFirst()
                    .ifPresent(t -> equipped.put("theme", t.id()));
        }
        // only items that still exist in the content folder count
        equipped.values().removeIf(id -> content.item(id) == null);
        charms.removeIf(id -> content.item(id) == null);

        double dmg = 0, hp = 0, red = 0;
        Map<String, Double> effects = new HashMap<>();
        List<String> worn = new ArrayList<>(equipped.values());
        worn.addAll(charms);
        for (String id : worn) {
            ContentService.Item item = content.item(id);
            dmg += item.bonus().damagePct();
            hp += item.bonus().maxHp();
            red += item.bonus().reduction();
            if (item.effect() != null) effects.merge(item.effect(), item.value(), Double::sum);
        }
        return new Loadout(LedgerFacts.weaponLevel(events), LedgerFacts.armorLevel(events), owned, equipped, charms,
                new ContentService.Bonus(dmg, hp, red), effects);
    }

    /**
     * Loot (brief 10): about lootRate of validations also drop a cosmetic (outfit or skin) the
     * owner does not have yet. Deterministic: decided by a hash of the event key, so a retry gives
     * the same result and nothing is random at replay. Cosmetic only: no coins, no XP.
     */
    public void maybeDropLoot(String eventKey) {
        if (fraction(eventKey + ":loot") >= config.loot().dropRate()) {
            return;
        }
        Loadout l = loadout();
        List<ContentService.Item> pool = content.catalog().items().stream()
                .filter(i -> ("outfit".equals(i.kind()) || "skin".equals(i.kind())) && i.price() > 0)
                .filter(i -> !l.owned().contains(i.id()))
                .toList();
        if (pool.isEmpty()) {
            return;
        }
        ContentService.Item drop = pool.get((int) (fraction(eventKey + ":pick") * pool.size()));
        ledger.append(new NewEvent("loot:" + eventKey, EventType.PURCHASE, "loot", "Loot: " + drop.name(),
                Money.ZERO, Money.ZERO, Map.of(), Map.of("itemId", drop.id(), "loot", true)));
    }

    /** A number in [0, 1) derived from a string (SHA-256), the same every time. */
    static double fraction(String s) {
        try {
            byte[] h = MessageDigest.getInstance("SHA-256").digest(s.getBytes(StandardCharsets.UTF_8));
            long v = ((h[0] & 0xffL) << 24) | ((h[1] & 0xffL) << 16) | ((h[2] & 0xffL) << 8) | (h[3] & 0xffL);
            return v / (double) (1L << 32);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
