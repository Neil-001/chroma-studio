package dev.chromastudio.client.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.chromastudio.client.ModSettings;
import dev.chromastudio.client.ThemeChoice;
import dev.chromastudio.core.color.Color;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class ThemeTest {
    @AfterEach
    void restore() {
        ModSettings.reset();
        Theme.sync();
    }

    private static void use(ThemeChoice theme) {
        ModSettings.theme = theme;
        Theme.sync();
    }

    /** The backgrounds that carry the main text. */
    private static Map<String, Integer> surfaces() {
        Map<String, Integer> m = new LinkedHashMap<>();
        m.put("WINDOW", Theme.WINDOW); m.put("WELL", Theme.WELL); m.put("PANEL", Theme.PANEL); m.put("BAR", Theme.BAR);
        m.put("HOVER", Theme.HOVER); m.put("SELECTED", Theme.SELECTED); m.put("CHIP", Theme.CHIP); m.put("TOOL", Theme.TOOL);
        m.put("TOOL_LIT", Theme.TOOL_LIT); m.put("CONTROL", Theme.CONTROL); m.put("CONTROL_HOVER", Theme.CONTROL_HOVER); m.put("DANGER", Theme.DANGER);
        return m;
    }

    /** The text and marks drawn straight on the window or a panel. */
    private static Map<String, Integer> marks() {
        Map<String, Integer> m = new LinkedHashMap<>();
        m.put("SOFT", Theme.SOFT); m.put("DIM", Theme.DIM); m.put("ACCENT", Theme.ACCENT); m.put("HIGHLIGHT", Theme.HIGHLIGHT);
        m.put("GOOD", Theme.GOOD); m.put("FAIR", Theme.FAIR); m.put("BAD", Theme.BAD);
        m.put("INFO", Theme.INFO);
        return m;
    }

    private static Map<String, Integer> all() {
        Map<String, Integer> m = surfaces();
        m.putAll(marks());
        m.put("TEXT", Theme.TEXT); m.put("STRONG", Theme.STRONG); m.put("BORDER", Theme.BORDER); m.put("GREYED", Theme.GREYED);
        m.put("CHIP_GREYED", Theme.CHIP_GREYED); m.put("CHIP_OFF", Theme.CHIP_OFF); m.put("TOOL_OFF", Theme.TOOL_OFF); m.put("CONTROL_OFF", Theme.CONTROL_OFF);
        return m;
    }

    @Test
    void theDefaultThemeIsThePaletteTheWindowAlwaysHad() {
        use(ThemeChoice.DEFAULT);
        assertEquals(0xF0101317, Theme.WINDOW);
        assertEquals(0xFF161A20, Theme.PANEL);
        assertEquals(0xFF1B2027, Theme.BAR);
        assertEquals(0xFF3A424D, Theme.BORDER);
        assertEquals(0xFF2C343E, Theme.SELECTED);
        assertEquals(0xFF0B0D10, Theme.WELL);
        assertEquals(0xFFE4E9EF, Theme.TEXT);
        assertEquals(0xFF9AA5B3, Theme.DIM);
        assertEquals(0xFF4E5560, Theme.GREYED);
        assertEquals(0xFFFFB84D, Theme.ACCENT);
        assertEquals(0xFF8EC2F2, Theme.HIGHLIGHT);
        assertEquals(0xFFFFFFFF, Theme.STRONG);
        assertEquals(0x20FFFFFF, Theme.veil(0x20));
        assertEquals(0x60000000, Theme.sink(0x60));
    }

    /** The default's colours are written out, not worked out. Worked out from its base, each lands within a just noticeable step of the written one. */
    @Test
    void workingTheDefaultOutGivesNearlyWhatIsWritten() {
        use(ThemeChoice.DEFAULT);
        Map<String, Integer> written = all();
        Theme.derive(Theme.DEFAULT_BASE, -1, false);
        all().forEach((name, worked) -> {
            double step = Color.ofRgb(worked & 0xFFFFFF).oklab().distance(Color.ofRgb(written.get(name) & 0xFFFFFF).oklab());
            assertTrue(step < 0.02, name + " is " + step + " from the written colour");
        });
    }

    @Test
    void textIsDarkOnALightBaseAndLightOnADarkOne() {
        use(ThemeChoice.VANILLA);
        assertEquals(0xF0C6C6C6, Theme.WINDOW);
        assertEquals(0xFF555555, Theme.BORDER, "the inventory's own border");
        assertTrue(Color.ofRgb(Theme.TEXT & 0xFFFFFF).oklab().l() < 0.3, "near-black text");
        assertEquals(0xFF000000, Theme.STRONG);
        use(ThemeChoice.DARK);
        assertEquals(0xF0535353, Theme.WINDOW);
        assertTrue(Color.ofRgb(Theme.TEXT & 0xFFFFFF).oklab().l() > 0.9, "near-white text");
        use(ThemeChoice.WARM);
        assertTrue(Color.ofRgb(Theme.TEXT & 0xFFFFFF).oklab().l() > 0.9);
    }

    /** The fainter text with each background it is drawn on: a chip's label, plain and under the cursor, and the muted text of rows and the title bar. */
    private static void assertFaintTextReads(String theme) {
        Map<String, Integer> soft = Map.of("WINDOW", Theme.WINDOW, "PANEL", Theme.PANEL, "CHIP", Theme.CHIP, "CONTROL", Theme.CONTROL);
        soft.forEach((name, c) -> assertTrue(Theme.contrast(Theme.SOFT, c) >= Theme.MARK_CONTRAST, theme + " SOFT on " + name + ": " + Theme.contrast(Theme.SOFT, c)));
        Map<String, Integer> dim = Map.of("WINDOW", Theme.WINDOW, "WELL", Theme.WELL, "PANEL", Theme.PANEL, "BAR", Theme.BAR, "HOVER", Theme.HOVER, "SELECTED", Theme.SELECTED);
        dim.forEach((name, c) -> assertTrue(Theme.contrast(Theme.DIM, c) >= Theme.MARK_CONTRAST, theme + " DIM on " + name + ": " + Theme.contrast(Theme.DIM, c)));
    }

    @Test
    void everyReadyMadeThemeReads() {
        for (ThemeChoice theme : ThemeChoice.values()) {
            use(theme);
            assertTrue(Theme.contrast(Theme.TEXT, Theme.WINDOW) >= Theme.TEXT_CONTRAST, theme + " text on the window");
            surfaces().forEach((name, c) -> assertTrue(Theme.contrast(Theme.TEXT, c) >= Theme.MARK_CONTRAST,
                    theme + " text on " + name + ": " + Theme.contrast(Theme.TEXT, c)));
            marks().forEach((name, c) -> assertTrue(Theme.contrast(c, Theme.WINDOW) >= Theme.MARK_CONTRAST,
                    theme + " " + name + " on the window: " + Theme.contrast(c, Theme.WINDOW)));
            assertFaintTextReads(theme.toString());
            assertTrue(Theme.contrast(Theme.ON_LIT, Theme.LIT_HOVER) >= Theme.MARK_CONTRAST);
            // Greyed text is meant to be faint, but it still has to show.
            assertTrue(Theme.contrast(Theme.GREYED, Theme.WINDOW) >= 1.8, theme + " greyed text: " + Theme.contrast(Theme.GREYED, Theme.WINDOW));
            assertTrue(Theme.contrast(Theme.BORDER, Theme.WINDOW) >= 1.5, theme + " border: " + Theme.contrast(Theme.BORDER, Theme.WINDOW));
        }
    }

    /** A player's own colour can be anything, and a middling one leaves less room: black and white both manage only 4.6 on the worst grey. */
    @Test
    void anyCustomColourReads() {
        ModSettings.theme = ThemeChoice.CUSTOM;
        double worst = 99;
        for (int i = 0; i < 4096; i++) {
            int rgb = (i >> 8 & 15) * 17 << 16 | (i >> 4 & 15) * 17 << 8 | (i & 15) * 17;
            ModSettings.customColour = String.format("#%06X", rgb);
            Theme.sync();
            String on = " on " + ModSettings.customColour;
            double text = Theme.contrast(Theme.TEXT, Theme.WINDOW);
            worst = Math.min(worst, text);
            assertTrue(text >= Theme.MARK_CONTRAST, "text" + on + ": " + text);
            surfaces().forEach((name, c) -> assertTrue(Theme.contrast(Theme.TEXT, c) >= Theme.MARK_CONTRAST, "text on " + name + on + ": " + Theme.contrast(Theme.TEXT, c)));
            assertFaintTextReads(ModSettings.customColour);
            marks().forEach((name, c) -> assertTrue(Theme.contrast(c, Theme.WINDOW) >= Theme.MARK_CONTRAST, name + on + ": " + Theme.contrast(c, Theme.WINDOW)));
        }
        assertTrue(worst < 5, "the sweep reaches the middling colours");
    }

    @Test
    void aBadCustomColourFallsBackToTheDefaultBase() {
        assertEquals(Theme.DEFAULT_BASE, Theme.base(ThemeChoice.CUSTOM, "blue"));
        assertEquals(0x243447, Theme.base(ThemeChoice.CUSTOM, "#243447"));
    }
}
