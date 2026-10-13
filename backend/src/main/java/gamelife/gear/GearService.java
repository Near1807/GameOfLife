package gamelife.gear;

import gamelife.common.Json;
import gamelife.common.Money;
import gamelife.config.GameConfig;
import gamelife.content.ContentService;
import gamelife.ledger.EventType;
import gamelife.ledger.LedgerEvent;
import gamelife.ledger.LedgerService;
import gamelife.ledger.NewEvent;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The cosmetics the owner has and wears, always computed from the ledger (brief 5: owned items are
 * never stored as editable data). v4: looks only (outfits, map skins, themes), no power.
 *  - owned items: PURCHASE events (bought or dropped as loot) plus every free item (price 0);
 *  - equipped items: the last EQUIP event per slot.
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

    public record Loadout(Set<String> owned, Map<String, String> equipped) {
        public boolean isEquipped(String itemId) {
            return equipped.containsValue(itemId);
        }
    }

    public Loadout loadout() {
        return loadout(ledger.all());
    }

    public Loadout loadout(List<LedgerEvent> events) {
        Set<String> owned = new LinkedHashSet<>();
        content.catalog().items().stream().filter(i -> i.price() == 0).forEach(i -> owned.add(i.id()));
        Map<String, String> equipped = new LinkedHashMap<>();
        for (LedgerEvent e : events) {
            if (e.type() == EventType.PURCHASE) {
                Object id = Json.toMap(e.meta()).get("itemId");
                if (id != null) owned.add(id.toString());
            } else if (e.type() == EventType.EQUIP) {
                Map<String, Object> m = Json.toMap(e.meta());
                String slot = String.valueOf(m.get("slot"));
                Object item = m.get("itemId");
                if (item == null) {
                    equipped.remove(slot);
                } else if (!"charm".equals(slot)) { // v3 charms no longer exist
                    equipped.put(slot, item.toString());
                }
            }
        }
        // the free default theme is worn until another one is equipped
        if (!equipped.containsKey("theme")) {
            content.catalog().items().stream().filter(i -> "theme".equals(i.kind()) && i.price() == 0).findFirst()
                    .ifPresent(t -> equipped.put("theme", t.id()));
        }
        // only items that still exist in the content folder count (v3 weapons and armour are gone)
        equipped.values().removeIf(id -> content.item(id) == null);
        owned.removeIf(id -> content.item(id) == null);
        return new Loadout(owned, equipped);
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
