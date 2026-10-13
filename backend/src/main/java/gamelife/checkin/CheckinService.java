package gamelife.checkin;

import gamelife.common.GameException;
import gamelife.common.Json;
import gamelife.common.Money;
import gamelife.config.GameConfig;
import gamelife.ledger.EventType;
import gamelife.ledger.LedgerService;
import gamelife.payout.ActionResult;
import gamelife.payout.PayRequest;
import gamelife.payout.PayoutService;
import gamelife.rules.Tags;
import gamelife.time.GameTime;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

/**
 * Mood check-in (brief 6): one tap on a 5-level scale, once per game day.
 * Ledger key checkin:{gameDay}. The first answer of the day is kept.
 */
@Service
public class CheckinService {
    private final LedgerService ledger;
    private final PayoutService payout;
    private final GameConfig config;
    private final GameTime time;

    public CheckinService(LedgerService ledger, PayoutService payout, GameConfig config, GameTime time) {
        this.ledger = ledger;
        this.payout = payout;
        this.config = config;
        this.time = time;
    }

    /** Today's mood, or null if not answered yet. */
    public Integer todayMood() {
        return ledger.find(key()).map(e -> ((Number) Json.toMap(e.meta()).get("mood")).intValue()).orElse(null);
    }

    /**
     * The one tap. tags: the stats this check-in feeds (0 to 7). null = the tags of the last
     * check-in, or the configured default (Spirit) the first time.
     */
    public ActionResult checkin(int mood, List<String> tags) {
        if (mood < 1 || mood > config.checkin().moodLevels()) {
            throw GameException.badRequest("Mood must be between 1 and " + config.checkin().moodLevels());
        }
        List<String> t = tags != null ? Tags.clean(tags, config) : lastTags();
        return payout.pay(new PayRequest(key(), EventType.CHECKIN, "checkin", "Mood check-in",
                Money.of(config.checkin().xp()), t, Map.of("mood", mood)));
    }

    public ActionResult checkin(int mood) {
        return checkin(mood, null);
    }

    /** Tags of the most recent check-in, else the configured default. */
    @SuppressWarnings("unchecked")
    public List<String> lastTags() {
        return ledger.byKeyPrefix("checkin:").stream()
                .reduce((a, b) -> b)
                .map(e -> (List<String>) Json.toMap(e.meta()).get("tags"))
                .orElse(Tags.clean(config.checkin().defaultTags(), config));
    }

    private String key() {
        return "checkin:" + time.today();
    }
}
