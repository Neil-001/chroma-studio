package dev.chromastudio.client.ui.tabs;

import java.util.List;
import java.util.Locale;

final class PaletteSearch {
    private PaletteSearch() {}

    static boolean matches(String query, String name, List<String> tags) {
        String in = name.toLowerCase(Locale.ROOT);
        List<String> labels = tags.stream().map(t -> t.toLowerCase(Locale.ROOT)).toList();
        for (String word : query.toLowerCase(Locale.ROOT).strip().split("\\s+")) {
            if (!in.contains(word) && labels.stream().noneMatch(t -> t.contains(word))) return false;
        }
        return true;
    }
}
