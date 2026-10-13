package gamelife.stage;

import gamelife.common.GameException;
import gamelife.common.Money;
import gamelife.config.GameConfig;
import gamelife.ledger.EventType;
import gamelife.ledger.LedgerEvent;
import gamelife.ledger.LedgerService;
import gamelife.ledger.NewEvent;
import gamelife.payout.ActionResult;
import gamelife.time.GameTime;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Weekly stage and boss (brief 9.1): the view for the screens, the kill check after every
 * payment, and ascension (New Game+).
 */
@Service
public class StageService {
    private final LedgerService ledger;
    private final GameConfig config;
    private final GameTime time;
    private final BossContent bosses;

    public StageService(LedgerService ledger, GameConfig config, GameTime time, BossContent bosses) {
        this.ledger = ledger;
        this.config = config;
        this.time = time;
        this.bosses = bosses;
    }

    /** One node of the campaign map. status: CLEARED, CURRENT or LOCKED. */
    public record Node(int stage, String name, String sprite, double target, String status) {}

    public record StageView(int stage, int stageCount, double target, double damage, double hp, double overkill,
                            boolean cleared, boolean ascendReady, int ascensions, double ascensionMultiplier,
                            LocalDate weekStart, Instant endsAt, BossContent.Boss boss, double rewardCoins,
                            List<Node> map) {}

    public StageView current() {
        return view(ledger.all());
    }

    public StageView view(List<LedgerEvent> events) {
        LocalDate week = GameTime.weekStart(time.today());
        StageRules.Progress p = StageRules.compute(events, week, config.stages());
        List<Node> map = new ArrayList<>();
        for (int s = 1; s <= config.stages().count(); s++) {
            boolean done = s < p.stage() || (s == p.stage() && (p.cleared() || p.ascendReady()));
            String status = done ? "CLEARED" : s == p.stage() ? "CURRENT" : "LOCKED";
            BossContent.Boss b = bosses.forStage(s);
            map.add(new Node(s, b.name(), b.sprite(), config.stages().target(s), status));
        }
        return new StageView(p.stage(), p.stageCount(), p.target(), p.damage(), p.hp(), p.overkill(), p.cleared(),
                p.ascendReady(), p.ascensions(), Math.pow(config.ascension().perAscension(), p.ascensions()),
                week, time.endOfWeek(week), bosses.forStage(p.stage()), config.stages().clearRewardCoins(), map);
    }

    /**
     * Called right after a payment (same lock). If this week's damage reached the boss's HP and
     * it is not dead yet, writes STAGE_CLEARED once: no XP, the configured reward in coins.
     * The kill is a consequence of the owner's explicit act, and its unique key means it can
     * only happen once per week.
     */
    public void checkClear() {
        StageRules.Progress p = StageRules.compute(ledger.all(), GameTime.weekStart(time.today()), config.stages());
        if (!p.bossAlive() || p.damage() < p.target()) {
            return;
        }
        BossContent.Boss boss = bosses.forStage(p.stage());
        Map<String, Object> meta = new LinkedHashMap<>();
        meta.put("stage", p.stage());
        meta.put("target", p.target());
        meta.put("damage", p.damage());
        meta.put("boss", boss.name());
        ledger.append(new NewEvent(StageRules.clearKey(p.ascensions(), p.weekStart()), EventType.STAGE_CLEARED,
                "stage", "Defeated " + boss.name(), Money.ZERO, Money.of(config.stages().clearRewardCoins()),
                Map.of(), meta));
    }

    /**
     * New Game+ (brief 9.1), only once the last stage is cleared. The stage returns to 1, the
     * weapon to level 0 (computed from the ledger), and every later payment gets x1.05 more.
     * Stats, achievements and cosmetics are kept.
     */
    public synchronized ActionResult ascend() {
        StageRules.Progress p = StageRules.compute(ledger.all(), GameTime.weekStart(time.today()), config.stages());
        if (!p.ascendReady()) {
            throw GameException.conflict("Clear stage " + config.stages().count() + " first");
        }
        int n = p.ascensions() + 1;
        var r = ledger.append(new NewEvent("ascend:" + n, EventType.ASCEND, "stage", "Ascension " + n,
                Money.ZERO, Money.ZERO, Map.of(), Map.of("ascension", n)));
        return new ActionResult(r.event(), r.written(), "Ascended");
    }
}
