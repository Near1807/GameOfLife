package gamelife.ledger;

import gamelife.common.Json;
import gamelife.common.Money;
import gamelife.time.GameTime;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * The only way to write to the ledger.
 *
 * Rule (brief 3.2): every event has a unique key, and a second event with the same key is
 * refused. Here "refused" means: nothing is written and the ORIGINAL event is returned with
 * written=false, so a double tap or a network retry is harmless and the client still gets
 * a normal answer.
 */
@Service
public class LedgerService {
    private final LedgerRepository repo;
    private final GameTime time;

    public LedgerService(LedgerRepository repo, GameTime time) {
        this.repo = repo;
        this.time = time;
    }

    /** written=false means the key already existed and nothing new was paid. */
    public record AppendResult(LedgerEvent event, boolean written) {}

    /**
     * Appends one event. synchronized: only one append at a time, so two events can never
     * get the same id or the same prevHash. The UNIQUE column on event_key is a second guard.
     */
    public synchronized AppendResult append(NewEvent n) {
        var existing = repo.findByKey(n.key());
        if (existing.isPresent()) {
            return new AppendResult(existing.get(), false);
        }

        var last = repo.findLast();
        long id = last.map(e -> e.id() + 1).orElse(1L);
        String prevHash = last.map(LedgerEvent::hash).orElse(HashChain.GENESIS);
        Instant now = time.now().truncatedTo(ChronoUnit.MILLIS); // server time, never the client's

        LedgerEvent unhashed = new LedgerEvent(id, n.key(), n.type(), now, time.gameDay(now),
                n.module(), n.label(), Money.of(n.xp()), Money.of(n.currencyDelta()),
                splitsJson(n.statSplits()), Json.canonical(n.meta() == null ? Map.of() : n.meta()),
                prevHash, "");
        LedgerEvent event = withHash(unhashed);

        try {
            repo.insert(event);
        } catch (DuplicateKeyException e) {
            // Only possible if another process wrote the same key: still pay once.
            return new AppendResult(repo.findByKey(n.key()).orElseThrow(), false);
        }
        return new AppendResult(event, true);
    }

    public List<LedgerEvent> all() {
        return repo.findAll();
    }

    public List<LedgerEvent> latest(int limit) {
        return repo.findLatest(limit);
    }

    public List<LedgerEvent> byKeyPrefix(String prefix) {
        return repo.findByKeyPrefix(prefix);
    }

    public java.util.Optional<LedgerEvent> find(String key) {
        return repo.findByKey(key);
    }

    /** Checks the whole hash chain (brief 5: at startup, on import, and for the warning banner). */
    public HashChain.Check verify() {
        return HashChain.verify(repo.findAll());
    }

    /** Returns the same event with its hash filled in (the hash field itself is not hashed). */
    static LedgerEvent withHash(LedgerEvent e) {
        return new LedgerEvent(e.id(), e.key(), e.type(), e.occurredAt(), e.gameDay(), e.module(), e.label(),
                e.xp(), e.currencyDelta(), e.statSplits(), e.meta(), e.prevHash(), HashChain.hashOf(e));
    }

    /** Stat splits as canonical JSON with 2-digit decimal strings: {"INTELLECT":"7.50"}. */
    private static String splitsJson(Map<String, BigDecimal> splits) {
        Map<String, String> m = new TreeMap<>();
        if (splits != null) {
            splits.forEach((stat, xp) -> m.put(stat, Money.of(xp).toPlainString()));
        }
        return Json.canonical(m);
    }
}
