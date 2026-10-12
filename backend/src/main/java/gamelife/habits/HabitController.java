package gamelife.habits;

import gamelife.payout.ActionResult;
import org.springframework.web.bind.annotation.*;

import java.time.DayOfWeek;
import java.util.List;
import java.util.Set;

/** REST API of habits. done takes no date: it is always today on the server. */
@RestController
@RequestMapping("/api/habits")
public class HabitController {
    private final HabitService service;

    public HabitController(HabitService service) {
        this.service = service;
    }

    public record HabitInput(String name, Set<DayOfWeek> days, List<String> tags) {}

    @GetMapping
    public List<HabitService.HabitView> list() {
        return service.list();
    }

    @PostMapping
    public Habit create(@RequestBody HabitInput in) {
        return service.create(in.name(), in.days(), in.tags());
    }

    @PostMapping("/{id}/done")
    public ActionResult done(@PathVariable long id) {
        return service.done(id);
    }

    @PostMapping("/{id}/archive")
    public void archive(@PathVariable long id) {
        service.archive(id);
    }
}
