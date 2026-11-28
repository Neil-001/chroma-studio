package dev.chromastudio.client.ui;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;

final class SettingsFilter {
    private SettingsFilter() {}

    static boolean matches(String search, String text) {
        String in = text.toLowerCase(Locale.ROOT);
        for (String word : search.toLowerCase(Locale.ROOT).trim().split("\\s+"))
            if (!in.contains(word)) return false;
        return true;
    }

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
