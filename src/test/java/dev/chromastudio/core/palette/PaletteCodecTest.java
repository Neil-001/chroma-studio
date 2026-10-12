package dev.chromastudio.core.palette;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

class PaletteCodecTest {
    @Test
    void idWithPropertiesAddsBracketsOnlyWhenThereAreProperties() {
        assertEquals("minecraft:stone", new PaletteEntry("minecraft:stone", "").idWithProperties());
        assertEquals("minecraft:oak_log[axis=y]", new PaletteEntry("minecraft:oak_log", "axis=y", 2).idWithProperties());
        assertEquals("oak_log[axis=y]", new PaletteEntry("minecraft:oak_log", "axis=y").shortIdWithProperties());
        assertEquals("create:andesite_casing", new PaletteEntry("create:andesite_casing", "").shortIdWithProperties());
        assertThrows(IllegalArgumentException.class, () -> new PaletteEntry("minecraft:stone", "", 0));
        assertThrows(IllegalArgumentException.class, () -> new PaletteEntry(" ", ""));
    }

    @Test
    void aPaletteComesBackAsItWentIn() {
        Palette p = new Palette("Mossy <ruin> & \"friends\"");
        p.tags.addAll(List.of("stone", "overgrown"));
        p.notes = "Line one\nLine two";
        p.entries.add(new PaletteEntry("minecraft:stone", ""));
        p.entries.add(new PaletteEntry("minecraft:oak_log", "axis=y,lit=true", 3));
        p.entries.add(new PaletteEntry("create:andesite_casing", "", 12));
        p.preset = true;

        String json = PaletteCodec.toJson(p);
        Palette back = PaletteCodec.fromJson(json);
        assertEquals(p.name, back.name);
        assertEquals(p.tags, back.tags);
        assertEquals(p.notes, back.notes);
        assertEquals(p.entries, back.entries);
        assertFalse(back.preset, "the file doesn't say whether a palette is bundled");
        assertFalse(json.contains("preset"));
        assertEquals(json, PaletteCodec.toJson(back));
    }

    @Test
    void theJsonIsPrettyAndLeavesHtmlAlone() {
        Palette p = new Palette("<a> & 'b'");
        p.entries.add(new PaletteEntry("minecraft:stone", ""));
        String json = PaletteCodec.toJson(p);
        assertTrue(json.contains("\"name\": \"<a> & 'b'\""), json);
        assertTrue(json.contains("\n  \"format\": 1,\n"), json);
        assertTrue(json.contains("\"block\": \"minecraft:stone\""), json);
    }

    @Test
    void missingFieldsGetDefaults() {
        Palette p = PaletteCodec.fromJson("{\"format\": 1}");
        assertEquals("", p.name);
        assertEquals(List.of(), p.tags);
        assertEquals("", p.notes);
        assertEquals(List.of(), p.entries);

        p = PaletteCodec.fromJson("{\"format\": 1, \"entries\": [{\"block\": \"minecraft:stone\"}]}");
        assertEquals(List.of(new PaletteEntry("minecraft:stone", "", 1)), p.entries);
    }

    @Test
    void unknownAndMistypedFieldsAreTolerated() {
        Palette p = PaletteCodec.fromJson("""
                {"format": 1, "name": 7, "tags": ["a", 3, "b"], "notes": [], "colour": "#fff", "future": {"x": 1},
                 "entries": [
                   {"block": "minecraft:stone", "weight": "heavy", "properties": 4, "icon": "x"},
                   {"block": "minecraft:andesite", "weight": 0},
                   {"block": "minecraft:granite", "weight": 4},
                   {"weight": 2},
                   "minecraft:dirt"
                 ]}""");
        assertEquals("", p.name);
        assertEquals(List.of("a", "b"), p.tags);
        assertEquals("", p.notes);
        assertEquals(List.of(
                new PaletteEntry("minecraft:stone", "", 1),
                new PaletteEntry("minecraft:andesite", "", 1),
                new PaletteEntry("minecraft:granite", "", 4)), p.entries);
    }

    @Test
    void aMissingOrWrongFormatIsRefusedInPlainWords() {
        assertEquals("This palette has no format number",
                assertThrows(IllegalArgumentException.class, () -> PaletteCodec.fromJson("{\"name\": \"x\"}")).getMessage());
        assertEquals("This palette has format 2, and only format 1 can be read",
                assertThrows(IllegalArgumentException.class, () -> PaletteCodec.fromJson("{\"format\": 2}")).getMessage());
        assertThrows(IllegalArgumentException.class, () -> PaletteCodec.fromJson("{\"format\": \"1\"}"));
        assertThrows(IllegalArgumentException.class, () -> PaletteCodec.fromJson("[1]"));
        assertThrows(IllegalArgumentException.class, () -> PaletteCodec.fromJson("{\"format\": 1"));
        assertThrows(IllegalArgumentException.class, () -> PaletteCodec.fromJson(""));
    }

    @Test
    void aCopyOfAPresetIsThePlayersOwn() {
        Palette p = new Palette("Preset");
        p.tags.add("warm");
        p.entries.add(new PaletteEntry("minecraft:stone", ""));
        p.preset = true;
        Palette copy = p.copy();
        assertFalse(copy.preset);
        copy.entries.clear();
        copy.tags.clear();
        assertEquals(1, p.entries.size());
        assertEquals(List.of("warm"), p.tags);
    }
}
