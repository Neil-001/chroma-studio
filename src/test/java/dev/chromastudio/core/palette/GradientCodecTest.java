package dev.chromastudio.core.palette;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

class GradientCodecTest {
    static StoredGradient sunset() {
        return new StoredGradient(" Sunset ", List.of(StoredGradient.Stop.colour(0xFF1F2A44), new StoredGradient.Stop(0xE9D8A6, new PaletteEntry("minecraft:oak_log", "axis=x"))),
                3, "SMOOTH", "LONGEST", true, "FULL_BLOCK", List.of(
                new StoredGradient.Step(0x1F2A44, new PaletteEntry("minecraft:blue_wool", ""), false),
                new StoredGradient.Step(0x808080, new PaletteEntry("create:andesite_casing", ""), true),
                new StoredGradient.Step(0xE9D8A6, new PaletteEntry("minecraft:oak_log", "axis=x"), false)));
    }

    @Test
    void gradientsComeBackAsTheyWentIn() {
        StoredGradient g = sunset();
        assertEquals("Sunset", g.name(), "the name is trimmed");
        assertEquals(0x1F2A44, g.stops().getFirst().rgb(), "alpha is dropped");
        String json = GradientCodec.toJson(List.of(g, new StoredGradient("Two", List.of(StoredGradient.Stop.colour(0), StoredGradient.Stop.colour(0xFFFFFF)),
                2, "", "", false, "", List.of())));
        List<StoredGradient> back = GradientCodec.fromJson(json);
        assertEquals(2, back.size());
        assertEquals(g, back.getFirst());
        assertTrue(back.getFirst().stops().get(1).isBlock());
        assertEquals(json, GradientCodec.toJson(back));
    }

    @Test
    void stepsStayInRange() {
        assertEquals(StoredGradient.MIN_STEPS, new StoredGradient("a", sunset().stops(), 0, "", "", false, "", List.of()).steps());
        assertEquals(StoredGradient.MAX_STEPS, new StoredGradient("a", sunset().stops(), 99, "", "", false, "", List.of()).steps());
        assertThrows(IllegalArgumentException.class, () -> new StoredGradient(" ", sunset().stops(), 5, "", "", false, "", List.of()));
        assertThrows(IllegalArgumentException.class, () -> new StoredGradient("a", sunset().stops().subList(0, 1), 5, "", "", false, "", List.of()));
    }

    @Test
    void aFileEditedByHandLoadsWhatItCan() {
        String json = """
                {"format": 1, "extra": true, "gradients": [
                  {"name": "Kept", "stops": [{"colour": "#ff0000"}, {"colour": "nonsense", "block": "minecraft:stone"}, {"colour": "bad"}, 3],
                   "steps": "many", "spread": "yes",
                   "blocks": [{"ideal": "#00ff00", "block": "minecraft:stone", "by_hand": true}, {"ideal": "#0000ff"}, {"block": "minecraft:dirt"}]},
                  {"name": "", "stops": [{"colour": "#000000"}, {"colour": "#ffffff"}]},
                  {"name": "One stop", "stops": [{"colour": "#000000"}]},
                  "not a gradient"
                ]}""";
        List<StoredGradient> read = GradientCodec.fromJson(json);
        assertEquals(1, read.size());
        StoredGradient g = read.getFirst();
        assertEquals("Kept", g.name());
        assertEquals(List.of(StoredGradient.Stop.colour(0xFF0000), new StoredGradient.Stop(0, new PaletteEntry("minecraft:stone", ""))), g.stops());
        assertEquals(List.of(new StoredGradient.Step(0x00FF00, new PaletteEntry("minecraft:stone", ""), true),
                new StoredGradient.Step(0, new PaletteEntry("minecraft:dirt", ""), false)), g.blocks());
        assertEquals(2, g.steps(), "with no step count, the blocks' count stands in for it");
        assertEquals(false, g.spread());
        assertEquals("", g.easing());
    }

    @Test
    void anotherFormatIsRefused() {
        assertThrows(IllegalArgumentException.class, () -> GradientCodec.fromJson("{\"gradients\": []}"));
        assertThrows(IllegalArgumentException.class, () -> GradientCodec.fromJson("{\"format\": 2, \"gradients\": []}"));
        assertThrows(IllegalArgumentException.class, () -> GradientCodec.fromJson("[1, 2]"));
        assertThrows(IllegalArgumentException.class, () -> GradientCodec.fromJson("{"));
        assertEquals(List.of(), GradientCodec.fromJson("{\"format\": 1}"));
    }
}
