package gamelife.gear;

import gamelife.character.CharacterService;
import gamelife.common.GameException;
import gamelife.common.Money;
import gamelife.config.GameConfig;
import gamelife.content.ContentService;
import gamelife.fight.FightRules;
import gamelife.fight.FightService;
import gamelife.ledger.EventType;
import gamelife.ledger.LedgerEvent;
import gamelife.ledger.LedgerService;
import gamelife.ledger.NewEvent;
import gamelife.payout.ActionResult;
import gamelife.rules.LedgerFacts;
import gamelife.time.GameTime;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The shop (brief 9.2, 9.3, 10). Spending is always an explicit act with a confirmation step:
 * every buying call must carry confirm=true (the UI sends it on the second tap), and is refused
 * when the balance is too low. Keys make a double tap harmless:
 *   purchase:{itemId}                one purchase per item
 *   upgrade:{slot}:{cycle}:{level}   one per level (weapon cycle = ascensions, armour cycle 0)
 */
@Service
public class ShopService {
    private final LedgerService ledger;
    private final ContentService content;
    private final GearService gear;
    private final FightService fight;
    private final CharacterService character;
    private final GameConfig config;
    private final GameTime time;

    public ShopService(LedgerService ledger, ContentService content, GearService gear, FightService fight,
                       CharacterService character, GameConfig config, GameTime time) {
        this.ledger = ledger;
        this.content = content;
        this.gear = gear;
        this.fight = fight;
        this.character = character;
        this.config = config;
        this.time = time;
    }

    /** status: EQUIPPED, OWNED, LOCKED (stat requirement), AFFORDABLE, TOO_EXPENSIVE. */
    public record ShopItem(ContentService.Item item, String status, String lockReason, String effectText) {}

    public record Upgrade(String slot, int level, double cost, String status, String effectText) {}

    public record ShopView(double balance, double averageDailyXp, Upgrade weapon, Upgrade armor,
                           List<ShopItem> items, int maxCharms) {}

    public ShopView view() {
        fight.sync();
        List<LedgerEvent> events = ledger.all();
        double balance = LedgerFacts.balance(events).doubleValue();
        GearService.Loadout l = gear.loadout(events);
        FightService.FightView f = fight.view();
        double avg = averageDailyXp(events);

        int wl = l.weaponLevel();
        double wCost = FightRules.weaponCost(wl, config.weapon());
        double pct = (config.weapon().bonusPerLevel() - 1) * 100;
        String wText = String.format("+%.0f%% damage. %s", pct,
                daysText(f.bossHp(), avg, avg * config.weapon().bonusPerLevel(), f.bossName()));
        Upgrade weapon = new Upgrade("weapon", wl, wCost, balance >= wCost ? "AFFORDABLE" : "TOO_EXPENSIVE", wText);

        int al = l.armorLevel();
        double aCost = FightRules.armorCost(al, config.armor());
        double redNow = f.armorReduction();
        double redNext = Math.min(config.armor().maxReduction(), redNow + config.armor().reductionPerLevel());
        double hpNext = f.heroMaxHp() + config.armor().hpPerLevel();
        String aText = String.format("+%.0f max HP and %.0f%% less damage. A zero-XP day now costs %.0f HP instead of %.0f; "
                        + "you survive about %d lazy days in a row instead of %d.",
                config.armor().hpPerLevel(), (redNext - redNow) * 100,
                f.bossAtk() * (1 - redNext), f.bossAtk() * (1 - redNow),
                survivable(hpNext, f.bossAtk() * (1 - redNext)), survivable(f.heroMaxHp(), f.bossAtk() * (1 - redNow)));
        Upgrade armor = new Upgrade("armor", al, aCost, balance >= aCost ? "AFFORDABLE" : "TOO_EXPENSIVE", aText);

        var stats = character.compute(events).stats();
        List<ShopItem> items = new ArrayList<>();
        for (ContentService.Item i : content.catalog().items()) {
            String lock = lockReason(i, stats);
            String status = l.isEquipped(i.id()) ? "EQUIPPED"
                    : l.owned().contains(i.id()) ? "OWNED"
                    : lock != null ? "LOCKED"
                    : balance >= i.price() ? "AFFORDABLE" : "TOO_EXPENSIVE";
            items.add(new ShopItem(i, status, lock, itemEffect(i, f, avg)));
        }
        return new ShopView(balance, Math.round(avg), weapon, armor, items, config.shop().maxCharms());
    }

    // ---------------------------------------------------------------- acts

    public synchronized ActionResult buy(String itemId, boolean confirm) {
        requireConfirm(confirm);
        ContentService.Item item = content.item(itemId);
        if (item == null) {
            throw GameException.notFound("No item " + itemId);
        }
        List<LedgerEvent> events = ledger.all();
        GearService.Loadout l = gear.loadout(events);
        if (l.owned().contains(itemId)) {
            return ledger.find("purchase:" + itemId).map(e -> new ActionResult(e, false, "Already owned"))
                    .orElse(ActionResult.nothing("Already owned"));
        }
        String lock = lockReason(item, character.compute(events).stats());
        if (lock != null) {
            throw GameException.conflict("Locked: " + lock);
        }
        requireBalance(events, item.price());
        var r = ledger.append(new NewEvent("purchase:" + itemId, EventType.PURCHASE, "shop", "Bought " + item.name(),
                Money.ZERO, Money.of(-item.price()), Map.of(), Map.of("itemId", itemId, "price", item.price())));
        if (r.written()) {
            equip(itemId, true); // a new thing is worn at once; the owner can take it off with one tap
        }
        return new ActionResult(r.event(), r.written(), "Bought " + item.name());
    }

    public synchronized ActionResult upgrade(String slot, boolean confirm) {
        requireConfirm(confirm);
        List<LedgerEvent> events = ledger.all();
        GearService.Loadout l = gear.loadout(events);
        int level;
        double cost;
        String key;
        if ("weapon".equals(slot)) {
            level = l.weaponLevel();
            cost = FightRules.weaponCost(level, config.weapon());
            key = "upgrade:weapon:" + LedgerFacts.ascensions(events) + ":" + (level + 1);
        } else if ("armor".equals(slot)) {
            level = l.armorLevel();
            cost = FightRules.armorCost(level, config.armor());
            key = "upgrade:armor:0:" + (level + 1);
        } else {
            throw GameException.badRequest("Only weapon and armor can be upgraded");
        }
        var existing = ledger.find(key);
        if (existing.isPresent()) {
            return new ActionResult(existing.get(), false, "Already upgraded");
        }
        requireBalance(events, cost);
        Map<String, Object> meta = new LinkedHashMap<>();
        meta.put("slot", slot);
        meta.put("level", level + 1);
        meta.put("price", cost);
        String label = ("weapon".equals(slot) ? "Weapon" : "Armour") + " level " + (level + 1);
        var r = ledger.append(new NewEvent(key, EventType.UPGRADE, "shop", label, Money.ZERO, Money.of(-cost),
                Map.of(), meta));
        return new ActionResult(r.event(), r.written(), label);
    }

    /** Wear or take off an owned item. One tap, no coins, no XP. */
    public synchronized ActionResult equip(String itemId, boolean on) {
        ContentService.Item item = content.item(itemId);
        if (item == null) {
            throw GameException.notFound("No item " + itemId);
        }
        List<LedgerEvent> events = ledger.all();
        GearService.Loadout l = gear.loadout(events);
        if (!l.owned().contains(itemId)) {
            throw GameException.conflict("Buy it first");
        }
        String slot = item.slot();
        if ("charm".equals(slot) && on && !l.charms().contains(itemId) && l.charms().size() >= config.shop().maxCharms()) {
            throw GameException.conflict("Only " + config.shop().maxCharms() + " charms at once. Take one off first.");
        }
        if ("theme".equals(slot) && !on) {
            throw GameException.badRequest("Pick another theme instead");
        }
        Map<String, Object> meta = new LinkedHashMap<>();
        meta.put("slot", slot);
        if ("charm".equals(slot)) {
            meta.put("itemId", itemId);
            meta.put("on", on);
        } else if (on) {
            meta.put("itemId", itemId);
        }
        long n = events.stream().filter(e -> e.type() == EventType.EQUIP).count() + 1;
        var r = ledger.append(new NewEvent("equip:" + n, EventType.EQUIP, "shop", (on ? "Equipped " : "Took off ") + item.name(),
                Money.ZERO, Money.ZERO, Map.of(), meta));
        return new ActionResult(r.event(), r.written(), on ? "Equipped" : "Taken off");
    }

    // ---------------------------------------------------------------- helpers

    private static void requireConfirm(boolean confirm) {
        if (!confirm) {
            throw GameException.badRequest("Spending needs a confirmation");
        }
    }

    private static void requireBalance(List<LedgerEvent> events, double price) {
        double balance = LedgerFacts.balance(events).doubleValue();
        if (balance < price) {
            throw GameException.conflict("Not enough coins: " + Math.round(balance) + " of " + Math.round(price));
        }
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

    /** Final XP per day over the last forecastDays days (brief 9.2: the shop's predictions). */
    double averageDailyXp(List<LedgerEvent> events) {
        LocalDate today = time.today();
        int n = config.shop().forecastDays();
        double sum = 0;
        for (int i = 1; i <= n; i++) {
            sum += LedgerFacts.xpOnDay(events, today.minusDays(i)).doubleValue();
        }
        double avg = sum / n;
        return avg > 0 ? avg : config.home().dailyXpGoal(); // no history yet: assume a normal day
    }

    private static String daysText(double bossHp, double avgNow, double avgNext, String boss) {
        int now = (int) Math.ceil(bossHp / avgNow);
        int next = (int) Math.ceil(bossHp / avgNext);
        return String.format("%s now takes about %d day%s instead of %d.", boss, next, next == 1 ? "" : "s", now);
    }

    private static int survivable(double hp, double perDay) {
        return perDay <= 0 ? 99 : (int) Math.floor(hp / perDay);
    }

    private String itemEffect(ContentService.Item i, FightService.FightView f, double avg) {
        List<String> parts = new ArrayList<>();
        if (i.bonus().damagePct() != 0) {
            parts.add(String.format("+%.0f%% damage. %s", i.bonus().damagePct() * 100,
                    daysText(f.bossHp(), avg, avg * (1 + i.bonus().damagePct()), f.bossName())));
        }
        if (i.bonus().maxHp() != 0) parts.add(String.format("+%.0f max HP", i.bonus().maxHp()));
        if (i.bonus().reduction() != 0) parts.add(String.format("%.0f%% less damage taken", i.bonus().reduction() * 100));
        if (i.effect() != null) {
            parts.add(switch (i.effect()) {
                case GearService.SHIELD_BONUS -> String.format("+%.0f%% shield every day", i.value() * 100);
                case GearService.SHIELD_HEAL -> String.format("+%.0f HP healed on every fully shielded day", i.value());
                case GearService.FREEZE_CAPACITY -> String.format("store %.0f more streak freeze%s", i.value(), i.value() == 1 ? "" : "s");
                case GearService.COMEBACK_BONUS -> String.format("comeback bonus x%.1f instead of x%.1f",
                        config.streak().comebackMultiplier() + i.value(), config.streak().comebackMultiplier());
                case GearService.GOAL_REDUCTION -> String.format("full shield with %.0f%% less XP", i.value() * 100);
                default -> i.effect();
            });
        }
        if (parts.isEmpty()) {
            parts.add(switch (i.kind()) {
                case "theme" -> "Restyles the whole app. Looks only.";
                case "outfit" -> "A new look for your hero. Looks only.";
                case "skin" -> "A new arena backdrop. Looks only.";
                default -> "Looks only.";
            });
        }
        return String.join(". ", parts);
    }
}
