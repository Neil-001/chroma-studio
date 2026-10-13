package dev.chromastudio.client.ui;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;

/** Which rows of the settings screen a search leaves showing. */
final class SettingsFilter {
    private SettingsFilter() {}

    /** Whether every word of the search is somewhere in the text, in any order and either case. An empty search matches everything. */
    static boolean matches(String search, String text) {
        String in = text.toLowerCase(Locale.ROOT);
        for (String word : search.toLowerCase(Locale.ROOT).trim().split("\\s+"))
            if (!in.contains(word)) return false;
        return true;
    }

    /**
     * The groups with only the settings whose text matches the search, in the same order. A group none of whose
     * settings match is left out, so its heading goes too. {@code text} gives what a setting's row says: its label
     * and its tooltip.
     */
    static Map<String, List<String>> filter(Map<String, List<String>> groups, String search, Function<String, String> text) {
        Map<String, List<String>> shown = new LinkedHashMap<>();
        for (var group : groups.entrySet()) {
            List<String> rows = new ArrayList<>();
            for (String name : group.getValue()) if (matches(search, text.apply(name))) rows.add(name);
            if (!rows.isEmpty()) shown.put(group.getKey(), rows);
        }
        return shown;
    }
}
