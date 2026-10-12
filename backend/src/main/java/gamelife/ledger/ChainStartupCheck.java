package gamelife.ledger;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

/**
 * Verifies the hash chain once at startup (brief 5). A broken chain only logs a warning:
 * it never blocks use. The frontend shows the banner from /api/state.
 */
@Component
public class ChainStartupCheck implements ApplicationRunner {
    private static final Logger log = LoggerFactory.getLogger(ChainStartupCheck.class);
    private final LedgerService ledger;

    public ChainStartupCheck(LedgerService ledger) {
        this.ledger = ledger;
    }

    @Override
    public void run(ApplicationArguments args) {
        HashChain.Check check = ledger.verify();
        if (check.valid()) {
            log.info("Ledger chain verified: intact");
        } else {
            log.warn("LEDGER CHAIN BROKEN: {}", check.message());
        }
    }
}
