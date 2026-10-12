package gamelife.tasks;

import gamelife.payout.ActionResult;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/** REST API of the to-do list. */
@RestController
@RequestMapping("/api/tasks")
public class TaskController {
    private final TaskService service;

    public TaskController(TaskService service) {
        this.service = service;
    }

    /** Body of create and edit. Only name is required on create. */
    public record TaskInput(String name, Integer minutes, List<String> tags) {}

    @GetMapping
    public List<TaskView> list() {
        return service.list();
    }

    @GetMapping("/suggestions")
    public List<Suggestion> suggestions(@RequestParam(defaultValue = "") String q) {
        return service.suggestions(q);
    }

    @PostMapping
    public Task create(@RequestBody TaskInput in) {
        return service.create(in.name(), in.minutes(), in.tags());
    }

    @PutMapping("/{id}")
    public Task edit(@PathVariable long id, @RequestBody TaskInput in) {
        return service.edit(id, in.name(), in.minutes(), in.tags());
    }

    @PostMapping("/{id}/done")
    public ActionResult tick(@PathVariable long id) {
        return service.tick(id);
    }

    @PostMapping("/{id}/undo")
    public ActionResult untick(@PathVariable long id) {
        return service.untick(id);
    }

    @PostMapping("/{id}/archive")
    public void archive(@PathVariable long id) {
        service.archive(id);
    }
}
