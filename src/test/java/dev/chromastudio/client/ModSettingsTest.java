package dev.chromastudio.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ModSettingsTest {
    @AfterEach
    void restore() { ModSettings.reset(); }

    private static void assertDefaults() {
        assertEquals(ModSettings.CENTRED, ModSettings.windowX);
        assertEquals(ModSettings.CENTRED, ModSettings.windowY);
        assertEquals(400, ModSettings.windowW);
        assertEquals(220, ModSettings.windowH);
        assertFalse(ModSettings.fullscreen);
        assertEquals(FaceChoice.SIDE, ModSettings.defaultFace);
        assertEquals("OKLAB", ModSettings.metric);
        assertEquals(12, ModSettings.resultCount);
        assertEquals(0.8, ModSettings.pinOpacity);
        assertEquals(TooltipMode.SHIFT, ModSettings.tooltipMode);
        assertEquals("match", ModSettings.startTab);
        assertEquals("", ModSettings.exportFolder);
    }

    private static void change() {
        ModSettings.windowX = 12;
        ModSettings.windowY = 0;
        ModSettings.windowW = 640;
        ModSettings.windowH = 360;
        ModSettings.fullscreen = true;
        ModSettings.defaultFace = FaceChoice.TOP;
        ModSettings.metric = "CIEDE2000";
        ModSettings.resultCount = 30;
        ModSettings.pinOpacity = 0.5;
        ModSettings.tooltipMode = TooltipMode.NEVER;
        ModSettings.startTab = "palettes";
        ModSettings.exportFolder = "/tmp/exports";
    }

    @Test
    void aMissingFileLeavesTheDefaults(@TempDir Path dir) {
        change();
        ModSettings.load(dir.resolve("chromastudio").resolve("settings.json"));
        assertDefaults();
    }

    @Test
    void everySettingSurvivesSavingAndLoading(@TempDir Path dir) throws Exception {
        Path file = dir.resolve("chromastudio").resolve("settings.json");
        change();
        ModSettings.save(file);
        try (var files = Files.list(file.getParent())) {
            assertEquals(List.of(file), files.toList(), "the temporary file is gone");
        }

        ModSettings.reset();
        ModSettings.load(file);
        assertEquals(12, ModSettings.windowX);
        assertEquals(0, ModSettings.windowY);
        assertEquals(640, ModSettings.windowW);
        assertEquals(360, ModSettings.windowH);
        assertTrue(ModSettings.fullscreen);
        assertEquals(FaceChoice.TOP, ModSettings.defaultFace);
        assertEquals("CIEDE2000", ModSettings.metric);
        assertEquals(30, ModSettings.resultCount);
        assertEquals(0.5, ModSettings.pinOpacity);
        assertEquals(TooltipMode.NEVER, ModSettings.tooltipMode);
        assertEquals("palettes", ModSettings.startTab);
        assertEquals("/tmp/exports", ModSettings.exportFolder);
    }

    @Test
    void badValuesGetTheirDefaults() {
        assertTrue(ModSettings.read("{\"windowX\": -5, \"windowY\": 1.5, \"windowW\": 100, \"windowH\": \"tall\", \"fullscreen\": 1, \"unknown\": true}"));
        assertDefaults();
        assertTrue(ModSettings.read("{\"windowW\": 500}"), "the rest are missing");
        assertEquals(500, ModSettings.windowW);
        assertEquals(220, ModSettings.windowH);
        assertFalse(ModSettings.read("[]"));
        assertFalse(ModSettings.read("not json"));
        assertDefaults();
    }

    @Test
    void badValuesOfEveryKindGetTheirDefaults() {
        assertTrue(ModSettings.read("""
                {"defaultFace": "SIDEWAYS", "metric": "oklab", "resultCount": 2, "pinOpacity": 1.5, "tooltipMode": 3,
                 "startTab": "Not A Tab", "biome": "plains", "shading": "yes", "easing": 4, "recipeKey": "r",
                 "exportFolder": 12, "gradientSteps": 33, "viewingDistance": 0}"""));
        assertDefaults();
        assertEquals("minecraft:plains", ModSettings.biome);
        assertFalse(ModSettings.shading);
        assertEquals("SMOOTH", ModSettings.easing);
        assertEquals("key.keyboard.r", ModSettings.recipeKey);
        assertEquals(9, ModSettings.gradientSteps);
        assertEquals(24, ModSettings.viewingDistance);
    }

    @Test
    void theFileUsesTheFieldNamesAndPlainNamesForChoices() {
        ModSettings.defaultLayout = LayoutChoice.PATCH_GRID;
        var o = com.google.gson.JsonParser.parseString(ModSettings.write()).getAsJsonObject();
        assertEquals("PATCH_GRID", o.get("defaultLayout").getAsString());
        assertEquals("SHORTEST", o.get("hueArc").getAsString());
        assertEquals("HERE", o.get("biomeMode").getAsString());
        assertEquals(24, o.get("viewingDistance").getAsInt());
        int settings = ModSettings.GROUPS.values().stream().mapToInt(List::size).sum();
        assertEquals(settings, o.size(), "every setting is written once");
    }

    @Test
    void settingByNameKeepsWithinTheLimitsAndRefusesTheWrongKind() {
        assertTrue(ModSettings.set("resultCount", 99.0));
        assertEquals(60, ModSettings.resultCount);
        assertTrue(ModSettings.set("pinOpacity", 0.05));
        assertEquals(0.2, ModSettings.pinOpacity);
        assertFalse(ModSettings.set("metric", "RGB"));
        assertFalse(ModSettings.set("biome", "not an id"));
        assertFalse(ModSettings.set("shading", "true"));
        assertFalse(ModSettings.set("defaultFace", TooltipMode.ALWAYS));
        assertEquals("OKLAB", ModSettings.metric);
        assertTrue(ModSettings.set("defaultFace", FaceChoice.ANY));
        assertEquals(FaceChoice.ANY, ModSettings.get("defaultFace"));
    }

    @Test
    void anOldFilesShulkerBoxSwitchBecomesAMode() {
        assertTrue(ModSettings.read("{}"));
        assertEquals(OverflowMode.NEVER, ModSettings.hotbarOverflowMode);
        assertTrue(ModSettings.read("{\"hotbarOverflow\": true}"));
        assertEquals(OverflowMode.ALWAYS, ModSettings.hotbarOverflowMode);
        assertTrue(ModSettings.read("{\"hotbarOverflow\": false}"));
        assertEquals(OverflowMode.NEVER, ModSettings.hotbarOverflowMode);
        assertTrue(ModSettings.read("{\"hotbarOverflow\": \"yes\"}"), "not an on or off, so the default");
        assertEquals(OverflowMode.NEVER, ModSettings.hotbarOverflowMode);
        assertTrue(ModSettings.read("{\"hotbarOverflow\": true, \"hotbarOverflowMode\": \"WHEN_FULL\"}"), "the new key wins");
        assertEquals(OverflowMode.WHEN_FULL, ModSettings.hotbarOverflowMode);
        assertFalse(ModSettings.write().contains("\"hotbarOverflow\""), "the old key isn't written back");
        assertTrue(ModSettings.write().contains("\"hotbarOverflowMode\": \"WHEN_FULL\""));
        assertFalse(ModSettings.dioramaPerspective);
    }

    @Test
    void aGroupResetsOnItsOwn() {
        change();
        ModSettings.reset("matching");
        assertEquals(12, ModSettings.resultCount);
        assertEquals(FaceChoice.SIDE, ModSettings.defaultFace);
        assertEquals(640, ModSettings.windowW, "another group's setting is left alone");
        assertEquals(TooltipMode.NEVER, ModSettings.tooltipMode);
    }

    @Test
    void undoPutsAGroupBackToItsSnapshotAndLeavesTheOthers() {
        ModSettings.resultCount = 20;
        ModSettings.pinOpacity = 0.6;
        var atOpen = ModSettings.snapshot();
        assertFalse(ModSettings.changedSince(atOpen));
        assertFalse(ModSettings.changedSince("matching", atOpen));

        ModSettings.resultCount = 40;
        ModSettings.shading = true;
        ModSettings.pinOpacity = 0.3;
        assertTrue(ModSettings.changedSince(atOpen));
        assertTrue(ModSettings.changedSince("matching", atOpen));
        assertTrue(ModSettings.changedSince("window", atOpen));
        assertFalse(ModSettings.changedSince("gradient", atOpen));

        ModSettings.restore("matching", atOpen);
        assertEquals(20, ModSettings.resultCount, "back to the value at the snapshot, not the default");
        assertFalse(ModSettings.shading);
        assertEquals(0.3, ModSettings.pinOpacity, "another group's change stays");
        assertFalse(ModSettings.changedSince("matching", atOpen));
        assertTrue(ModSettings.changedSince(atOpen));

        ModSettings.restore(atOpen);
        assertEquals(0.6, ModSettings.pinOpacity);
        assertFalse(ModSettings.changedSince(atOpen));
    }

    @Test
    void aSettingChangedAndChangedBackCountsAsUnchanged() {
        var atOpen = ModSettings.snapshot();
        ModSettings.set("exportFolder", "/tmp/a");
        assertTrue(ModSettings.changedSince("palettes", atOpen));
        ModSettings.set("exportFolder", "");
        assertFalse(ModSettings.changedSince("palettes", atOpen));
    }

    @Test
    void undoAndResetCoverTheWindowsPlaceAndSize() {
        ModSettings.windowX = 30;
        ModSettings.windowW = 500;
        var atOpen = ModSettings.snapshot();
        ModSettings.windowX = 90;
        ModSettings.windowW = 700;
        assertTrue(ModSettings.changedSince("window", atOpen));
        ModSettings.restore("window", atOpen);
        assertEquals(30, ModSettings.windowX);
        assertEquals(500, ModSettings.windowW);

        ModSettings.reset("window");
        assertEquals(ModSettings.CENTRED, ModSettings.windowX);
        assertEquals(400, ModSettings.windowW);
        assertTrue(ModSettings.changedSince("window", atOpen), "a reset can be undone");
        ModSettings.restore(atOpen);
        assertEquals(500, ModSettings.windowW);
    }

    @Test
    void resetAllAfterChangesCanBeUndoneToTheSnapshot() {
        change();
        ModSettings.theme = ThemeChoice.VANILLA;
        var atOpen = ModSettings.snapshot();
        ModSettings.reset();
        assertDefaults();
        assertEquals(ThemeChoice.DEFAULT, ModSettings.theme);
        ModSettings.restore(atOpen);
        assertEquals(30, ModSettings.resultCount);
        assertEquals(ThemeChoice.VANILLA, ModSettings.theme);
        assertEquals("/tmp/exports", ModSettings.exportFolder);
    }

    @Test
    void theThemeIsSavedAndABadCustomColourGetsTheDefault() {
        ModSettings.theme = ThemeChoice.CUSTOM;
        assertTrue(ModSettings.set("customColour", "#C6C6C6"));
        assertFalse(ModSettings.set("customColour", "C6C6C6"), "needs its #");
        assertFalse(ModSettings.set("customColour", "#C6C6"));
        String saved = ModSettings.write();
        ModSettings.reset();
        assertTrue(ModSettings.read(saved));
        assertEquals(ThemeChoice.CUSTOM, ModSettings.theme);
        assertEquals("#C6C6C6", ModSettings.customColour);

        assertTrue(ModSettings.read("{\"theme\": \"PURPLE\", \"customColour\": \"grey\"}"));
        assertEquals(ThemeChoice.DEFAULT, ModSettings.theme);
        assertEquals("#243447", ModSettings.customColour);
    }
}
