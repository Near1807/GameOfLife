package gamelife.gear;

import gamelife.payout.ActionResult;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/shop")
public class ShopController {
    private final ShopService shop;
    private final GearService gear;

    public ShopController(ShopService shop, GearService gear) {
        this.shop = shop;
        this.gear = gear;
    }

    /** confirm must be true: the UI sends it on the second tap (brief 9.3). */
    public record BuyInput(String itemId, boolean confirm) {}

    public record UpgradeInput(String slot, boolean confirm) {}

    public record EquipInput(String itemId, boolean on) {}

    @GetMapping
    public ShopService.ShopView view() {
        return shop.view();
    }

    @GetMapping("/loadout")
    public GearService.Loadout loadout() {
        return gear.loadout();
    }

    @PostMapping("/buy")
    public ActionResult buy(@RequestBody BuyInput in) {
        return shop.buy(in.itemId(), in.confirm());
    }

    @PostMapping("/upgrade")
    public ActionResult upgrade(@RequestBody UpgradeInput in) {
        return shop.upgrade(in.slot(), in.confirm());
    }

    @PostMapping("/equip")
    public ActionResult equip(@RequestBody EquipInput in) {
        return shop.equip(in.itemId(), in.on());
    }
}
