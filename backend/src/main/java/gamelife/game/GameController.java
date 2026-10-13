package gamelife.game;

import gamelife.payout.ActionResult;
import org.springframework.web.bind.annotation.*;

/** The tower-defense map (v4 brief 2-4). */
@RestController
@RequestMapping("/api/game")
public class GameController {
    private final GameService game;
    private final WaveService waves;
    private final GameViewService views;

    public GameController(GameService game, WaveService waves, GameViewService views) {
        this.game = game;
        this.waves = waves;
        this.views = views;
    }

    @GetMapping
    public GameViewService.GameView view() {
        return views.view();
    }

    public record SpotInput(int spot) {}

    public record BuildInput(int spot, String tower) {}

    /** path 0 = A, 1 = B; tier = the tier being bought (1..3), as shown to the player. */
    public record UpgradeInput(int spot, int path, int tier) {}

    /** confirm must be true: the UI sends it on the confirmation tap. */
    public record SellInput(int spot, boolean confirm) {}

    /** Moving sells every tower here for a full refund: it needs a confirmation tap. */
    public record MoveInput(String map, boolean confirm) {}

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

    @PostMapping("/move")
    public ActionResult move(@RequestBody MoveInput in) {
        return game.move(in.map(), in.confirm());
    }

    /** An extra attempt now, under the same daily cap and "stuck" rule as the automatic ones. */
    @PostMapping("/run")
    public ActionResult run() {
        return waves.runNow();
    }

    /** The latest attempt (or the one with this key) with its replay. */
    @GetMapping("/replay")
    public WaveService.Watch replay(@RequestParam(required = false) String key) {
        waves.sync();
        return waves.watch(key);
    }
}
