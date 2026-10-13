package gamelife.checkin;

import gamelife.payout.ActionResult;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/checkin")
public class CheckinController {
    private final CheckinService service;

    public CheckinController(CheckinService service) {
        this.service = service;
    }

    public record CheckinInput(int mood, List<String> tags) {}

    @GetMapping("/today")
    public Map<String, Object> today() {
        Map<String, Object> m = new HashMap<>();
        m.put("mood", service.todayMood()); // null when not answered yet
        return m;
    }

    @PostMapping
    public ActionResult checkin(@RequestBody CheckinInput in) {
        return service.checkin(in.mood(), in.tags());
    }
}
