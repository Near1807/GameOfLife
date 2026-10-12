package gamelife.ledger;

import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Replaces the whole ledger with events from a backup file. The caller must have verified
 * the chain of the new events first and must run inside a transaction.
 * This is the only code path that removes ledger rows (brief 5: restore from backup).
 */
@Component
public class LedgerRestore {
    private final LedgerRepository repo;

    public LedgerRestore(LedgerRepository repo) {
        this.repo = repo;
    }

    public void replaceAll(List<LedgerEvent> events) {
        HashChain.Check check = HashChain.verify(events);
        if (!check.valid()) {
            throw new IllegalArgumentException("Refusing to restore a broken chain: " + check.message());
        }
        repo.deleteAllForRestore();
        events.forEach(repo::insert);
    }
}
