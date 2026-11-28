package dev.chromastudio.core.palette;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class WeightedPickTest {
    private static final List<PaletteEntry> ENTRIES = List.of(new PaletteEntry("minecraft:stone", "", 3),
            new PaletteEntry("minecraft:andesite", "", 1), new PaletteEntry("minecraft:cobblestone", "", 6));

    @Test
    void eachItemHasItsShareOfTheRange() {
        WeightedPick<PaletteEntry> pick = new WeightedPick<>(ENTRIES, PaletteEntry::weight);
        assertEquals(10, pick.total());
        assertEquals("minecraft:stone", pick.at(0.0).blockId());
        assertEquals("minecraft:stone", pick.at(0.299).blockId());
        assertEquals("minecraft:andesite", pick.at(0.3).blockId());
        assertEquals("minecraft:andesite", pick.at(0.399).blockId());
        assertEquals("minecraft:cobblestone", pick.at(0.4).blockId());
        assertEquals("minecraft:cobblestone", pick.at(0.9999999).blockId());
    }

    @Test
    void positionsAreScatteredByWeight() {
        WeightedPick<PaletteEntry> pick = new WeightedPick<>(ENTRIES, PaletteEntry::weight);
        Map<String, Integer> counts = new HashMap<>();
        for (int x = 0; x < 40; x++) for (int y = 0; y < 25; y++) for (int z = 0; z < 10; z++) counts.merge(pick.at(7, x, y, z).blockId(), 1, Integer::sum);
        assertTrue(Math.abs(counts.get("minecraft:stone") - 3000) < 200, counts::toString);
        assertTrue(Math.abs(counts.get("minecraft:andesite") - 1000) < 200, counts::toString);
        assertTrue(Math.abs(counts.get("minecraft:cobblestone") - 6000) < 200, counts::toString);
    }

    @Test
    void aPositionKeepsItsPickForTheSameSeed() {
        assertEquals(WeightedPick.unit(42, 10, -64, 300), WeightedPick.unit(42, 10, -64, 300));
        assertNotEquals(WeightedPick.unit(42, 10, -64, 300), WeightedPick.unit(43, 10, -64, 300));
        assertNotEquals(WeightedPick.unit(42, 10, -64, 300), WeightedPick.unit(42, 10, -63, 300));
        for (int i = 0; i < 1000; i++) {
            double u = WeightedPick.unit(i, i, -i, i * 31);
            assertTrue(u >= 0 && u < 1);
        }
    }

    @Test
    void nothingToPickFromIsRefused() {
        assertThrows(IllegalArgumentException.class, () -> new WeightedPick<>(List.<PaletteEntry>of(), PaletteEntry::weight));
    }
}
