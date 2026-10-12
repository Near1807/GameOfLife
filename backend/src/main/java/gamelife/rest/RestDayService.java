package gamelife.rest;

import gamelife.common.GameException;
import gamelife.common.Money;
import gamelife.config.GameConfig;
import gamelife.ledger.EventType;
import gamelife.ledger.LedgerService;
import gamelife.ledger.NewEvent;
import gamelife.payout.ActionResult;
import gamelife.rules.LedgerFacts;
import gamelife.time.GameTime;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * Planned rest days (brief 7): marked in advance with one tap, at most maxRestDaysPerWeek per
 * game week. They keep the streak alive and do not increase it. They pay nothing.
 * Ledger key rest:{date}.
 */
@Service
public class RestDayService {
    private final LedgerService ledger;
    private final GameConfig config;
    private final GameTime time;

    public RestDayService(LedgerService ledger, GameConfig config, GameTime time) {
        this.ledger = ledger;
        this.config = config;
        this.time = time;
    }

    public synchronized ActionResult plan(LocalDate date) {
        LocalDate today = time.today();
        if (date == null) {
            throw GameException.badRequest("Pick a day");
        }
        if (date.isBefore(today)) {
            throw GameException.badRequest("Rest days are planned in advance, not afterwards");
        }
        if (date.isAfter(today.plusDays(config.streak().restDayMaxDaysAhead()))) {
            throw GameException.badRequest("That is too far ahead");
        }
        String key = "rest:" + date;
        var existing = ledger.find(key);
        if (existing.isPresent()) {
            return new ActionResult(existing.get(), false, "Already a rest day");
        }
        LocalDate week = GameTime.weekStart(date);
        long inWeek = LedgerFacts.restDays(ledger.all()).stream()
                .filter(d -> GameTime.weekStart(d).equals(week)).count();
        if (inWeek >= config.streak().maxRestDaysPerWeek()) {
            throw GameException.conflict("Already " + inWeek + " rest days that week");
        }
        var result = ledger.append(new NewEvent(key, EventType.REST_DAY, "rest", "Rest day " + date,
                Money.ZERO, Money.ZERO, Map.of(), Map.of("date", date.toString())));
        return new ActionResult(result.event(), result.written(), "Rest day planned");
    }

    /** Rest days from today on, sorted. */
    public List<LocalDate> upcoming() {
        LocalDate today = time.today();
        return LedgerFacts.restDays(ledger.all()).stream().filter(d -> !d.isBefore(today)).sorted().toList();
    }
}
