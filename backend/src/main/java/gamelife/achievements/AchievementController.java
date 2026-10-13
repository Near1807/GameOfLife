package gamelife.achievements;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
public class AchievementController {
    private final AchievementService service;

    public AchievementController(AchievementService service) {
        this.service = service;
    }

    @GetMapping("/api/achievements")
    public List<AchievementService.Status> achievements() {
        return service.statuses();
    }
}
