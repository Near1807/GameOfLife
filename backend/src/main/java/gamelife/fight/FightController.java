package gamelife.fight;

import gamelife.payout.ActionResult;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;

@RestController
@RequestMapping("/api/fight")
public class FightController {
    private final FightService service;

    public FightController(FightService service) {
        this.service = service;
    }

    public record PauseInput(LocalDate date) {}

    @GetMapping
    public FightService.FightView fight() {
        return service.view();
    }

    /** One tap: pause today (or a given coming day). */
    @PostMapping("/pause")
    public ActionResult pause(@RequestBody(required = false) PauseInput in) {
        return service.pause(in == null ? null : in.date());
    }

    /** The frontend asks for a second tap before calling this. */
    @PostMapping("/ascend")
    public ActionResult ascend() {
        return service.ascend();
    }
}
