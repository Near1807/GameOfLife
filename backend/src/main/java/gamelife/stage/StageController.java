package gamelife.stage;

import gamelife.payout.ActionResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class StageController {
    private final StageService service;

    public StageController(StageService service) {
        this.service = service;
    }

    @GetMapping("/api/stage")
    public StageService.StageView stage() {
        return service.current();
    }

    /** The frontend asks for a second tap before calling this (brief 9.3: confirmed acts). */
    @PostMapping("/api/ascend")
    public ActionResult ascend() {
        return service.ascend();
    }
}
