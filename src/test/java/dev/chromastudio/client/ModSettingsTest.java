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
    }

    private static void change() {
        ModSettings.windowX = 12;
        ModSettings.windowY = 0;
        ModSettings.windowW = 640;
        ModSettings.windowH = 360;
        ModSettings.fullscreen = true;
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
}
