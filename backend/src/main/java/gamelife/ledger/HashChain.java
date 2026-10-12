package gamelife.ledger;

import gamelife.common.Json;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Hash chain of the ledger (brief section 5):
 * hash = SHA-256( prevHash + canonical JSON of the event ).
 * Changing any stored field of any row breaks every hash from that row on.
 */
public final class HashChain {
    private HashChain() {}

    /** prevHash of the very first event. */
    public static final String GENESIS = "0".repeat(64);

    /** Result of checking a whole chain. brokenAtId is 0 when the chain is valid. */
    public record Check(boolean valid, long brokenAtId, String message) {
        public static Check ok() {
            return new Check(true, 0, "Ledger chain is intact");
        }
    }

    public static String hashOf(LedgerEvent e) {
        return sha256(e.prevHash() + canonicalJson(e));
    }

    /** The fields covered by the hash, in a fixed form. */
    static String canonicalJson(LedgerEvent e) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", e.id());
        m.put("key", e.key());
        m.put("type", e.type().name());
        m.put("occurredAt", e.occurredAt().toEpochMilli());
        m.put("gameDay", e.gameDay().toString());
        m.put("module", e.module());
        m.put("label", e.label());
        m.put("xp", e.xp().toPlainString());
        m.put("currencyDelta", e.currencyDelta().toPlainString());
        m.put("statSplits", e.statSplits()); // already canonical JSON text
        m.put("meta", e.meta());             // already canonical JSON text
        return Json.canonical(m);
    }

    /** Verifies ids are 1,2,3..., each prevHash links to the previous hash and every hash is right. */
    public static Check verify(List<LedgerEvent> events) {
        String expectedPrev = GENESIS;
        long expectedId = 1;
        for (LedgerEvent e : events) {
            if (e.id() != expectedId) {
                return new Check(false, e.id(), "Event ids are not sequential at id " + e.id());
            }
            if (!e.prevHash().equals(expectedPrev)) {
                return new Check(false, e.id(), "Chain link broken at event " + e.id());
            }
            if (!hashOf(e).equals(e.hash())) {
                return new Check(false, e.id(), "Event " + e.id() + " was modified after it was written");
            }
            expectedPrev = e.hash();
            expectedId++;
        }
        return Check.ok();
    }

    private static String sha256(String text) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(text.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
