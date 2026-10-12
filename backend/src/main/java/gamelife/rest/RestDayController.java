package gamelife.rest;

import gamelife.payout.ActionResult;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/rest-days")
public class RestDayController {
    private final RestDayService service;

    public RestDayController(RestDayService service) {
        this.service = service;
    }

    public record RestInput(LocalDate date) {}

    @GetMapping
    public List<LocalDate> upcoming() {
        return service.upcoming();
    }

    @PostMapping
    public ActionResult plan(@RequestBody RestInput in) {
        return service.plan(in.date());
    }
}
