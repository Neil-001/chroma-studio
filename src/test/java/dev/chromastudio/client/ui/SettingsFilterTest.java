package dev.chromastudio.client.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class SettingsFilterTest {
    private static final Map<String, List<String>> GROUPS = new LinkedHashMap<>();
    private static final Map<String, String> TEXT = Map.of(
            "pinOpacity", "Pinned window opacity How solid a pinned window looks, from 0.2 to 1.",
            "theme", "Window colour The colours of the window and of this screen.",
            "resultCount", "Results How many blocks a match lists, from 3 to 60.",
            "shading", "Shading Compares faces as the game lights them.");

    static {
        GROUPS.put("window", List.of("pinOpacity", "theme"));
        GROUPS.put("matching", List.of("resultCount", "shading"));
    }

    private static Map<String, List<String>> filter(String search) { return SettingsFilter.filter(GROUPS, search, TEXT::get); }

    @Test
    void anEmptySearchShowsEverything() {
        assertEquals(GROUPS, filter(""));
        assertEquals(GROUPS, filter("   "));
    }

    @Test
    void aSearchLooksInLabelsAndTooltipsInEitherCase() {
        assertEquals(Map.of("window", List.of("theme")), filter("COLOUR"), "in the label");
        assertEquals(Map.of("matching", List.of("shading")), filter("lights"), "only in the tooltip");
    }

    @Test
    void aGroupStaysWhileAnyOfItsRowsMatchAndGoesWhenNoneDo() {
        Map<String, List<String>> shown = filter("window");
        assertEquals(List.of("window"), List.copyOf(shown.keySet()));
        assertEquals(List.of("pinOpacity", "theme"), shown.get("window"));
        assertEquals(List.of("window", "matching"), List.copyOf(filter("from").keySet()), "groups keep their order");
        assertEquals(List.of("pinOpacity"), filter("from").get("window"));
    }

    @Test
    void nothingMatchingLeavesNothing() { assertTrue(filter("litematica").isEmpty()); }

    @Test
    void everyWordHasToBeThereInAnyOrder() {
        assertTrue(SettingsFilter.matches("window pinned", TEXT.get("pinOpacity")));
        assertTrue(SettingsFilter.matches("  opacity   solid ", TEXT.get("pinOpacity")));
        assertFalse(SettingsFilter.matches("window blocks", TEXT.get("pinOpacity")));
    }
}
