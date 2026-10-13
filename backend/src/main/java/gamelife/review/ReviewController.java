package gamelife.review;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

@RestController
public class ReviewController {
    private final ReviewService service;

    public ReviewController(ReviewService service) {
        this.service = service;
    }

    /** GET /api/review?week=2026-03-02 (any day of the week; default this week). */
    @GetMapping("/api/review")
    public ReviewService.WeekReview review(@RequestParam(required = false) LocalDate week) {
        return service.week(week);
    }

    /** GET /api/dashboard?days=30 */
    @GetMapping("/api/dashboard")
    public ReviewService.Dashboard dashboard(@RequestParam(defaultValue = "30") int days) {
        return service.dashboard(days);
    }
}
