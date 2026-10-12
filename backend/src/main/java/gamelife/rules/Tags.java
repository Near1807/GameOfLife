package gamelife.rules;

import gamelife.common.GameException;
import gamelife.config.GameConfig;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;

/** Checks stat tags: each must be a configured stat; duplicates are removed, order kept. */
public final class Tags {
    private Tags() {}

    public static List<String> clean(List<String> tags, GameConfig config) {
        if (tags == null) {
            return List.of();
        }
        LinkedHashSet<String> unique = new LinkedHashSet<>();
        for (String tag : tags) {
            if (!config.isStat(tag)) {
                throw GameException.badRequest("Unknown stat tag: " + tag);
            }
            unique.add(tag);
        }
        return new ArrayList<>(unique);
    }
}
