package dev.chromastudio.client.ui.tabs;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

class PaletteSearchTest {
    private static final List<String> TAGS = List.of("Stone", "dark fantasy");

    @Test
    void aBlankLineKeepsEverything() {
        assertTrue(PaletteSearch.matches("", "Gothic", TAGS));
        assertTrue(PaletteSearch.matches("   ", "Gothic", List.of()));
    }

    @Test
    void aWordMatchesPartOfTheNameOrOfATagWhateverTheCase() {
        assertTrue(PaletteSearch.matches("goth", "Gothic", List.of()));
        assertTrue(PaletteSearch.matches("THIC", "Gothic", List.of()));
        assertTrue(PaletteSearch.matches("stone", "Gothic", TAGS));
        assertTrue(PaletteSearch.matches("fantasy", "Gothic", TAGS));
        assertFalse(PaletteSearch.matches("wood", "Gothic", TAGS));
    }

    @Test
    void everyWordHasToBeFoundSomewhere() {
        assertTrue(PaletteSearch.matches(" dark  goth ", "Gothic", TAGS));
        assertTrue(PaletteSearch.matches("fantasy dark", "Gothic", TAGS));
        assertFalse(PaletteSearch.matches("dark wood", "Gothic", TAGS));
        assertFalse(PaletteSearch.matches("gothic stonework", "Gothic", TAGS));
    }
}
