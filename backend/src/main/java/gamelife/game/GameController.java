package gamelife.game;

import gamelife.payout.ActionResult;
import org.springframework.web.bind.annotation.*;

/** The tower-defense map (v4 brief 2-4). */
@RestController
@RequestMapping("/api/game")
public class GameController {
    private final GameService game;

    public GameController(GameService game) {
        this.game = game;
    }

    public record SpotInput(int spot) {}

    public record BuildInput(int spot, String tower) {}

    /** path 0 = A, 1 = B; tier = the tier being bought (1..3), as shown to the player. */
    public record UpgradeInput(int spot, int path, int tier) {}

    /** confirm must be true: the UI sends it on the confirmation tap. */
    public record SellInput(int spot, boolean confirm) {}

    @PostMapping("/unlock")
    public ActionResult unlock(@RequestBody SpotInput in) {
        return game.unlock(in.spot());
    }

    @PostMapping("/build")
    public ActionResult build(@RequestBody BuildInput in) {
        return game.build(in.spot(), in.tower());
    }

    @PostMapping("/upgrade")
    public ActionResult upgrade(@RequestBody UpgradeInput in) {
        return game.upgrade(in.spot(), in.path(), in.tier());
    }

    @PostMapping("/sell")
    public ActionResult sell(@RequestBody SellInput in) {
        return game.sell(in.spot(), in.confirm());
    }
}
