package dev.chromastudio.client.ui.tabs;

import java.util.List;
import java.util.Locale;

/** The Palettes tab's search: which palettes a typed line keeps in the list. */
final class PaletteSearch {
    private PaletteSearch() {}

    /**
     * Whether a palette is kept. Every word typed has to be part of its name or of one of its tags, whatever the
     * case, and a blank line keeps everything.
     */
    static boolean matches(String query, String name, List<String> tags) {
        String in = name.toLowerCase(Locale.ROOT);
        List<String> labels = tags.stream().map(t -> t.toLowerCase(Locale.ROOT)).toList();
        for (String word : query.toLowerCase(Locale.ROOT).strip().split("\\s+")) {
            if (!in.contains(word) && labels.stream().noneMatch(t -> t.contains(word))) return false;
        }
        return true;
    }
}
