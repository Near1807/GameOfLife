package gamelife.timer;

import gamelife.payout.ActionResult;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * REST API of the focus timer. Note that no request carries a time: the client cannot
 * influence how long a timer lasted (brief 3.2).
 */
@RestController
@RequestMapping("/api/timers")
public class TimerController {
    private final TimerService service;

    public TimerController(TimerService service) {
        this.service = service;
    }

    public record StartInput(Integer minutes, String label, Long taskId, List<String> tags) {}

    public record AnswerInput(boolean yes) {}

    /** The timer waiting for an answer, or 204 No Content if there is none. */
    @GetMapping("/current")
    public ResponseEntity<TimerView> current() {
        return service.current().map(ResponseEntity::ok).orElse(ResponseEntity.noContent().build());
    }

    @PostMapping
    public TimerView start(@RequestBody StartInput in) {
        return service.start(in.minutes(), in.label(), in.taskId(), in.tags());
    }

    @PostMapping("/{id}/stop")
    public TimerView stop(@PathVariable long id) {
        return service.stop(id);
    }

    @PostMapping("/{id}/answer")
    public ActionResult answer(@PathVariable long id, @RequestBody AnswerInput in) {
        return service.answer(id, in.yes());
    }
}
