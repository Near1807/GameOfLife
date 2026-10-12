package gamelife.common;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

import java.util.List;
import java.util.Map;

/**
 * Small JSON helpers. "Canonical" JSON means: map keys sorted alphabetically, no spaces.
 * The same data therefore always gives the same text, which the hash chain needs.
 */
public final class Json {
    private Json() {}

    private static final ObjectMapper CANONICAL = new ObjectMapper()
            .configure(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS, true);

    public static String canonical(Object value) {
        try {
            return CANONICAL.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(e);
        }
    }

    public static Map<String, Object> toMap(String json) {
        try {
            return CANONICAL.readValue(json, new TypeReference<>() {});
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Bad JSON: " + json, e);
        }
    }

    public static List<String> toStringList(String json) {
        try {
            return CANONICAL.readValue(json, new TypeReference<>() {});
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Bad JSON: " + json, e);
        }
    }
}
