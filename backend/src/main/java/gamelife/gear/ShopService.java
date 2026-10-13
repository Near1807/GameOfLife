package gamelife.gear;

import gamelife.character.CharacterService;
import gamelife.common.GameException;
import gamelife.common.Money;
import gamelife.content.ContentService;
import gamelife.ledger.EventType;
import gamelife.ledger.LedgerEvent;
import gamelife.ledger.LedgerService;
import gamelife.ledger.NewEvent;
import gamelife.payout.ActionResult;
import gamelife.rules.LedgerFacts;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The shop (brief 10, v4 brief 4): cosmetics only (outfits, map skins, themes). Power comes from
 * towers. Spending is an explicit act with a confirmation step: buy must carry confirm=true (the UI
 * sends it on the second tap), and is refused when the balance is too low. The key purchase:{itemId}
 * makes a double tap harmless.
 */
@Service
public class ShopService {
    private final LedgerService ledger;
    private final ContentService content;
    private final GearService gear;
    private final CharacterService character;

    public ShopService(LedgerService ledger, ContentService content, GearService gear, CharacterService character) {
        this.ledger = ledger;
        this.content = content;
        this.gear = gear;
        this.character = character;
    }

    /** status: EQUIPPED, OWNED, LOCKED (stat requirement), AFFORDABLE, TOO_EXPENSIVE. */
    public record ShopItem(ContentService.Item item, String status, String lockReason, String effectText) {}

    public record ShopView(double balance, List<ShopItem> items) {}

    public ShopView view() {
        List<LedgerEvent> events = ledger.all();
        double balance = LedgerFacts.balance(events).doubleValue();
        GearService.Loadout l = gear.loadout(events);
        var stats = character.compute(events).stats();
        List<ShopItem> items = new ArrayList<>();
        for (ContentService.Item i : content.catalog().items()) {
            String lock = lockReason(i, stats);
            String status = l.isEquipped(i.id()) ? "EQUIPPED"
                    : l.owned().contains(i.id()) ? "OWNED"
                    : lock != null ? "LOCKED"
                    : balance >= i.price() ? "AFFORDABLE" : "TOO_EXPENSIVE";
            items.add(new ShopItem(i, status, lock, switch (i.kind()) {
                case "theme" -> "Restyles the whole app. Looks only.";
                case "outfit" -> "A new look for your hero. Looks only.";
                case "skin" -> "New colours for the map. Looks only.";
                default -> "Looks only.";
            }));
        }
        return new ShopView(balance, items);
    }

    public synchronized ActionResult buy(String itemId, boolean confirm) {
        if (!confirm) {
            throw GameException.badRequest("Spending needs a confirmation");
        }
        ContentService.Item item = content.item(itemId);
        if (item == null) {
            throw GameException.notFound("No item " + itemId);
        }
        List<LedgerEvent> events = ledger.all();
        if (gear.loadout(events).owned().contains(itemId)) {
            return ledger.find("purchase:" + itemId).map(e -> new ActionResult(e, false, "Already owned"))
                    .orElse(ActionResult.nothing("Already owned"));
        }
        String lock = lockReason(item, character.compute(events).stats());
        if (lock != null) {
            throw GameException.conflict("Locked: " + lock);
        }
        double balance = LedgerFacts.balance(events).doubleValue();
        if (balance < item.price()) {
            throw GameException.conflict("Not enough coins: " + Math.round(balance) + " of " + Math.round(item.price()));
        }
        var r = ledger.append(new NewEvent("purchase:" + itemId, EventType.PURCHASE, "shop", "Bought " + item.name(),
                Money.ZERO, Money.of(-item.price()), Map.of(), Map.of("itemId", itemId, "price", item.price())));
        if (r.written()) {
            equip(itemId, true); // a new thing is worn at once; the owner can take it off with one tap
        }
        return new ActionResult(r.event(), r.written(), "Bought " + item.name());
    }

    /** Wear or take off an owned item. One tap, no coins, no XP. */
    public synchronized ActionResult equip(String itemId, boolean on) {
        ContentService.Item item = content.item(itemId);
        if (item == null) {
            throw GameException.notFound("No item " + itemId);
        }
        List<LedgerEvent> events = ledger.all();
        if (!gear.loadout(events).owned().contains(itemId)) {
            throw GameException.conflict("Buy it first");
        }
        String slot = item.slot();
        if ("theme".equals(slot) && !on) {
            throw GameException.badRequest("Pick another theme instead");
        }
        Map<String, Object> meta = new LinkedHashMap<>();
        meta.put("slot", slot);
        if (on) {
            meta.put("itemId", itemId);
        }
        long n = events.stream().filter(e -> e.type() == EventType.EQUIP).count() + 1;
        var r = ledger.append(new NewEvent("equip:" + n, EventType.EQUIP, "shop", (on ? "Equipped " : "Took off ") + item.name(),
                Money.ZERO, Money.ZERO, Map.of(), meta));
        return new ActionResult(r.event(), r.written(), on ? "Equipped" : "Taken off");
    }

    private static String lockReason(ContentService.Item i, List<CharacterService.StatView> stats) {
        if (i.requires() == null) {
            return null;
        }
        int level = stats.stream().filter(s -> s.id().equalsIgnoreCase(i.requires().stat()))
                .mapToInt(CharacterService.StatView::level).findFirst().orElse(1);
        if (level >= i.requires().level()) {
            return null;
        }
        String name = i.requires().stat().charAt(0) + i.requires().stat().substring(1).toLowerCase();
        return "needs " + name + " level " + i.requires().level() + " (you have " + level + ")";
    }
}
