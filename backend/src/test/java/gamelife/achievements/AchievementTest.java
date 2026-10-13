package gamelife.achievements;

import gamelife.IntegrationTest;
import gamelife.tasks.TaskService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.HashSet;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** Brief 8: at least 30 data-driven achievements, unlocked by rules computed from the ledger. */
class AchievementTest extends IntegrationTest {
    @Autowired AchievementService achievements;
    @Autowired TaskService tasks;
    @Autowired gamelife.game.GameService game;

    private boolean unlocked(String id) {
        return achievements.statuses().stream().filter(s -> s.id().equals(id)).findFirst().orElseThrow().unlocked();
    }

    @Test
    void atLeastThirtyValidUniqueAchievements() {
        var defs = achievements.definitions();
        assertThat(defs.size()).isGreaterThanOrEqualTo(30);
        assertThat(new HashSet<>(defs.stream().map(AchievementService.Achievement::id).toList())).hasSize(defs.size());
        assertThat(defs).allMatch(a -> AchievementService.FACTS.contains(a.rule().fact()));
    }

    @Test
    void nothingUnlockedOnAFreshGame() {
        assertThat(achievements.statuses()).noneMatch(AchievementService.Status::unlocked);
    }

    @Test
    void unlocksFollowTheLedger() {
        var t = tasks.create("Big day", 800, List.of("STRENGTH"));
        tasks.tick(t.id()); // 880 XP: first quest, big day
        game.build(0, "archer");
        assertThat(unlocked("first_quest")).isTrue();
        assertThat(unlocked("xp_day_300")).isTrue();
        assertThat(unlocked("first_tower")).isTrue();
        assertThat(unlocked("wave_10")).isFalse();
        assertThat(unlocked("quest_10")).isFalse();
        var q10 = achievements.statuses().stream().filter(s -> s.id().equals("quest_10")).findFirst().orElseThrow();
        assertThat(q10.current()).isEqualTo(1.0);
        assertThat(q10.target()).isEqualTo(10.0);

        tasks.untick(t.id()); // un-ticking takes the quest back; the tower stays built
        assertThat(unlocked("first_quest")).isFalse();
        assertThat(unlocked("first_tower")).isTrue();
    }
}
