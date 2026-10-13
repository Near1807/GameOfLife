package gamelife.fight;

import gamelife.character.CharacterService;
import gamelife.common.GameException;
import gamelife.common.Json;
import gamelife.common.Money;
import gamelife.config.GameConfig;
import gamelife.content.ContentService;
import gamelife.gear.GearService;
import gamelife.ledger.EventType;
import gamelife.ledger.LedgerEvent;
import gamelife.ledger.LedgerService;
import gamelife.ledger.NewEvent;
import gamelife.payout.ActionResult;
import gamelife.rules.LedgerFacts;
import gamelife.rules.StreakCalculator;
import gamelife.time.GameTime;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The losable boss fight (brief 9.1).
 *
 * sync() brings the ledger up to date, and is called before every read of the game state and
 * before and after every payment:
 *  1. a boss at 0 HP whose kill is not written yet gets its STAGE_CLEARED (coin reward, no XP);
 *  2. every past game day that the boss has not attacked yet gets one BOSS_ATTACK, in date order,
 *     with key boss-attack:{fight}:{date}, so reopening the app after many days replays each
 *     missed day exactly once;
 *  3. if an attack brings the hero to 0 HP, a HERO_FELL is written (key hero-fell:{fight}:{date}).
 * None of these pay XP. The numbers of each attack are stored in its meta, so the past never
 * changes when the config or the gear changes later.
 */
@Service
public class FightService {
    private final LedgerService ledger;
    private final GameConfig config;
    private final GameTime time;
    private final ContentService content;
    private final GearService gear;
    private final CharacterService character;

    public FightService(LedgerService ledger, GameConfig config, GameTime time, ContentService content,
                        GearService gear, CharacterService character) {
        this.ledger = ledger;
        this.config = config;
        this.time = time;
        this.content = content;
        this.gear = gear;
        this.character = character;
    }

    // ---------------------------------------------------------------- sync

    public synchronized void sync() {
        LocalDate today = time.today();
        for (int guard = 0; guard < 5000; guard++) {
            List<LedgerEvent> events = ledger.all();
            FightReplay.State s = FightReplay.replay(events, config);
            if (s.killPending()) {
                writeKill(s, events);
                continue;
            }
            LocalDate day = nextDayToAttack(events, s, today);
            if (day == null) {
                return;
            }
            writeAttack(day, s, events);
        }
    }

    /** First past day (from the first day of play) not attacked yet, or null. */
    private LocalDate nextDayToAttack(List<LedgerEvent> events, FightReplay.State s, LocalDate today) {
        LocalDate first = null;
        for (LedgerEvent e : events) {
            if (e.type() == EventType.REST_DAY || e.type() == EventType.PAUSE) {
                continue; // planning ahead does not start the game
            }
            if (first == null || e.gameDay().isBefore(first)) {
                first = e.gameDay();
            }
        }
        if (first == null) {
            return null;
        }
        for (LocalDate d = first; d.isBefore(today); d = d.plusDays(1)) {
            if (!s.attackedDays.contains(d)) {
                return d;
            }
        }
        return null;
    }

    private void writeKill(FightReplay.State s, List<LedgerEvent> events) {
        ContentService.Art art = content.bossArt(s.boss);
        boolean noReward = "medium".equals(config.fight().defeatMode()) && s.fellThisFight;
        double reward = noReward ? 0 : config.fight().killRewardCoins();
        Map<String, Object> meta = new LinkedHashMap<>();
        meta.put("boss", s.boss);
        meta.put("ascension", s.ascensions);
        meta.put("name", art.name());
        meta.put("defeats", s.defeatsByCurrentBoss());
        ledger.append(new NewEvent("boss-kill:" + s.fightId(), EventType.STAGE_CLEARED, "fight",
                "Defeated " + art.name(), Money.ZERO, Money.of(reward), Map.of(), meta));
    }

    private void writeAttack(LocalDate day, FightReplay.State s, List<LedgerEvent> events) {
        Numbers n = numbers(events, s);
        double dayXp = LedgerFacts.xpOnDay(events, day).doubleValue();
        boolean prot = n.protectedDays().contains(day) || s.pausedDays.contains(day);
        double shield = FightRules.shield(dayXp, n.goal(), n.shieldBonus(), prot);
        double damage = FightRules.attackDamage(n.atk(), shield, n.reduction());
        double heal = shield >= 1 ? config.fight().fullShieldHeal() + n.extraHeal() : 0;

        Map<String, Object> meta = new LinkedHashMap<>();
        meta.put("date", day.toString());
        meta.put("boss", s.boss);
        meta.put("atk", n.atk());
        meta.put("dayXp", FightRules.round2(dayXp));
        meta.put("shield", FightRules.round2(shield));
        meta.put("protected", prot);
        meta.put("reduction", FightRules.round2(n.reduction()));
        meta.put("damage", damage);
        meta.put("blocked", FightRules.round2(n.atk() - damage));
        meta.put("heal", heal);
        String label = content.bossArt(s.boss).name() + " attacks";
        ledger.append(new NewEvent("boss-attack:" + s.fightId() + ":" + day, EventType.BOSS_ATTACK, "fight", label,
                Money.ZERO, Money.ZERO, Map.of(), meta));

        double missing = Math.max(0, s.heroMissing + damage - heal);
        if (missing >= n.heroMax()) {
            writeFall(day, s, events);
        }
    }

    /** The hero falls (brief 9.1, defeat rule from config). Stat XP is never taken. */
    private void writeFall(LocalDate day, FightReplay.State s, List<LedgerEvent> events) {
        double coins = 0;
        if ("hard".equals(config.fight().defeatMode())) {
            BigDecimal balance = LedgerFacts.balance(events);
            coins = -Math.max(0, Math.floor(balance.doubleValue() * config.fight().hardModeCoinLoss()));
        }
        Map<String, Object> meta = new LinkedHashMap<>();
        meta.put("date", day.toString());
        meta.put("boss", s.boss);
        meta.put("mode", config.fight().defeatMode());
        ledger.append(new NewEvent("hero-fell:" + s.fightId() + ":" + day, EventType.HERO_FELL, "fight",
                "Fell to " + content.bossArt(s.boss).name(), Money.ZERO, Money.of(coins), Map.of(), meta));
    }

    // ---------------------------------------------------------------- numbers of the current fight

    /** Everything an attack needs, from the current gear, stats and boss. */
    record Numbers(double atk, double reduction, double heroMax, double goal, double shieldBonus, double extraHeal,
                   Set<LocalDate> protectedDays) {}

    Numbers numbers(List<LedgerEvent> events, FightReplay.State s) {
        GearService.Loadout l = gear.loadout(events);
        int vitality = character.compute(events).stats().stream()
                .filter(st -> st.id().equals("VITALITY")).mapToInt(CharacterService.StatView::level).findFirst().orElse(1);
        double atk = FightRules.bossAtk(s.boss, s.ascensions, config.fight());
        double reduction = FightRules.armorReduction(l.armorLevel(), l.bonus().reduction(), config.armor());
        double heroMax = FightRules.heroMaxHp(vitality, l.armorLevel(), l.bonus().maxHp(), config);
        double goal = config.home().dailyXpGoal() * (1 - Math.min(0.9, l.effect(GearService.GOAL_REDUCTION)));
        Set<LocalDate> prot = LedgerFacts.restDays(events);
        prot.addAll(StreakCalculator.compute(LedgerFacts.countedDays(events), LedgerFacts.restOrPausedDays(events),
                time.today(), config.streak(), (int) l.effect(GearService.FREEZE_CAPACITY)).frozenDays());
        return new Numbers(atk, reduction, heroMax, goal, l.effect(GearService.SHIELD_BONUS),
                l.effect(GearService.SHIELD_HEAL), prot);
    }

    // ---------------------------------------------------------------- pause and ascension

    /**
     * Pause a day (illness, travel): fully shielded and keeps the streak. One tap, at most
     * pauseMaxDays within any pauseWindowDays. Today or a later day only.
     */
    public synchronized ActionResult pause(LocalDate date) {
        LocalDate today = time.today();
        LocalDate d = date == null ? today : date;
        if (d.isBefore(today) || d.isAfter(today.plusDays(config.fight().pauseWindowDays()))) {
            throw GameException.badRequest("A pause is for today or a coming day");
        }
        String key = "pause:" + d;
        var existing = ledger.find(key);
        if (existing.isPresent()) {
            return new ActionResult(existing.get(), false, "Already paused");
        }
        int window = config.fight().pauseWindowDays();
        long used = LedgerFacts.pausedDays(ledger.all()).stream()
                .filter(p -> !p.isBefore(d.minusDays(window - 1)) && !p.isAfter(d.plusDays(window - 1))).count();
        if (used >= config.fight().pauseMaxDays()) {
            throw GameException.conflict("Already " + used + " paused days within " + window + " days");
        }
        var r = ledger.append(new NewEvent(key, EventType.PAUSE, "fight", "Paused " + d, Money.ZERO, Money.ZERO,
                Map.of(), Map.of("date", d.toString())));
        return new ActionResult(r.event(), r.written(), "Paused: the boss cannot hurt you that day");
    }

    /** Ascension (brief 9.1): once every boss picture of this cycle has been beaten. */
    public synchronized ActionResult ascend() {
        sync();
        FightReplay.State s = FightReplay.replay(ledger.all(), config);
        if (!ascendReady(s)) {
            throw GameException.conflict("Beat all " + bossFiles() + " bosses first");
        }
        int n = s.ascensions + 1;
        var r = ledger.append(new NewEvent("ascend:" + n, EventType.ASCEND, "fight", "Ascension " + n,
                Money.ZERO, Money.ZERO, Map.of(), Map.of("ascension", n)));
        return new ActionResult(r.event(), r.written(), "Ascended");
    }

    boolean ascendReady(FightReplay.State s) {
        return bossFiles() > 0 && s.killsThisCycle >= bossFiles();
    }

    int bossFiles() {
        return content.catalog().bosses().size();
    }

    // ---------------------------------------------------------------- view

    public record Attack(long id, String date, double atk, double damage, double blocked, double heal,
                         double shield, boolean protectedDay) {}

    public record Node(int number, String name, String sprite, int frames, String status, int defeats) {}

    public record FightView(String fightId, int ascensions, int boss, String bossName, String bossTitle,
                            String bossSprite, int bossFrames, boolean variant, double bossHp, double bossMaxHp,
                            double bossAtk, double heroHp, double heroMaxHp, double armorReduction, double todayXp,
                            double dailyGoal, double shieldToday, boolean protectedToday, double expectedHit,
                            int defeatsByThisBoss, int kills, int falls, boolean ascendReady, int bossFiles,
                            String region, String heroSprite, int heroFrames, String defeatMode, double killReward,
                            boolean pausedToday, int pausesLeft, List<Attack> recentAttacks, List<Long> recentFalls,
                            List<Node> map) {}

    public FightView view() {
        sync();
        List<LedgerEvent> events = ledger.all();
        FightReplay.State s = FightReplay.replay(events, config);
        Numbers n = numbers(events, s);
        LocalDate today = time.today();
        double todayXp = LedgerFacts.xpOnDay(events, today).doubleValue();
        boolean prot = n.protectedDays().contains(today) || s.pausedDays.contains(today);
        double shield = FightRules.shield(todayXp, n.goal(), n.shieldBonus(), prot);
        double heroHp = Math.max(0, n.heroMax() - s.heroMissing);

        ContentService.Art art = content.bossArt(s.boss);
        boolean variant = s.ascensions > 0 || (bossFiles() > 0 && s.boss > bossFiles());
        ContentService.Art region = content.regionArt(s.boss, config.fight().regionEveryNBosses());
        int overall = character.compute(events).overallLevel();
        ContentService.Art hero = content.heroArt(overall, config.fight().heroEveryNLevels());

        List<Attack> attacks = new ArrayList<>();
        List<Long> falls = new ArrayList<>();
        for (int i = events.size() - 1; i >= 0 && attacks.size() < 14; i--) {
            LedgerEvent e = events.get(i);
            if (e.type() == EventType.BOSS_ATTACK) {
                Map<String, Object> m = Json.toMap(e.meta());
                attacks.add(new Attack(e.id(), String.valueOf(m.get("date")), d(m.get("atk")), d(m.get("damage")),
                        d(m.get("blocked")), d(m.get("heal")), d(m.get("shield")), Boolean.TRUE.equals(m.get("protected"))));
            } else if (e.type() == EventType.HERO_FELL && falls.size() < 5) {
                falls.add(e.id());
            }
        }

        int window = config.fight().pauseWindowDays();
        long pausesUsed = s.pausedDays.stream().filter(p -> !p.isBefore(today.minusDays(window - 1))).count();

        List<Node> map = new ArrayList<>();
        int shown = Math.max(bossFiles(), s.boss);
        for (int k = 1; k <= shown; k++) {
            ContentService.Art a = content.bossArt(k);
            String status = k < s.boss ? "CLEARED" : k == s.boss ? "CURRENT" : "LOCKED";
            map.add(new Node(k, a.name(), a.file(), a.frames(), status, s.defeatsByBoss.getOrDefault(s.ascensions + "-" + k, 0)));
        }

        return new FightView(s.fightId(), s.ascensions, s.boss, art.name(), art.title(), art.file(), art.frames(), variant,
                FightRules.round2(s.bossHp), s.bossMaxHp, n.atk(), FightRules.round2(heroHp), n.heroMax(),
                n.reduction(), FightRules.round2(todayXp), n.goal(), FightRules.round2(shield), prot,
                FightRules.attackDamage(n.atk(), shield, n.reduction()), s.defeatsByCurrentBoss(), s.kills, s.falls,
                ascendReady(s), bossFiles(), region == null ? null : region.file(), hero.file(), hero.frames(),
                config.fight().defeatMode(), config.fight().killRewardCoins(), s.pausedDays.contains(today),
                (int) Math.max(0, config.fight().pauseMaxDays() - pausesUsed), attacks, falls, map);
    }

    private static double d(Object o) {
        return o == null ? 0 : Double.parseDouble(o.toString());
    }
}
