package dev.chromastudio.core.palette;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class WorldEditPatternTest {
    private static Palette palette(PaletteEntry... entries) {
        Palette p = new Palette("Test");
        p.entries.addAll(List.of(entries));
        return p;
    }

    private static PaletteEntry block(String name, int weight) {
        return new PaletteEntry("minecraft:" + name, "", weight);
    }

    private static final Palette WEIGHTED = palette(block("stone", 7), block("andesite", 3));

    @Test
    void equalWeightsNeedNoPercentages() {
        assertEquals("stone,andesite", WorldEditPattern.of(palette(block("stone", 1), block("andesite", 1))));
        assertEquals("stone", WorldEditPattern.of(palette(block("stone", 1))));
        assertEquals("", WorldEditPattern.of(palette()));
    }

    @Test
    void weightsBecomePercentages() {
        assertEquals("70%stone,30%andesite", WorldEditPattern.of(WEIGHTED));
        assertEquals("100%stone", WorldEditPattern.of(palette(block("stone", 5))));
        assertEquals("50%stone,50%andesite", WorldEditPattern.of(palette(block("stone", 2), block("andesite", 2))));
    }

    @Test
    void namespacesAndPropertiesAreWrittenAsWorldEditReadsThem() {
        assertEquals("75%oak_log[axis=y],25%create:andesite_casing", WorldEditPattern.of(palette(
                new PaletteEntry("minecraft:oak_log", "axis=y", 3), new PaletteEntry("create:andesite_casing", "", 1))));
        assertEquals("oak_stairs[facing=north,half=top],stone", WorldEditPattern.of(palette(
                new PaletteEntry("minecraft:oak_stairs", "facing=north,half=top"), block("stone", 1))));
    }

    @Test
    void percentagesSumToAHundredByLargestRemainder() {
        // Thirds are 33.33 each; the one point left over goes to the first of the equal remainders.
        assertEquals("34%a,33%b,33%c", WorldEditPattern.of(palette(block("a", 2), block("b", 2), block("c", 2))));
        // 1/7, 2/7 and 4/7 are 14.29, 28.57 and 57.14: the middle one has the largest remainder.
        assertEquals("14%a,29%b,57%c", WorldEditPattern.of(palette(block("a", 1), block("b", 2), block("c", 4))));
        // 1/6 and 5/6 are 16.67 and 83.33.
        assertEquals("17%a,83%b", WorldEditPattern.of(palette(block("a", 1), block("b", 5))));
    }

    @Test
    void aRareEntryStillGetsOnePercent() {
        assertEquals("1%a,99%b", WorldEditPattern.of(palette(block("a", 1), block("b", 999))));
    }

    @Test
    void pastAHundredEntriesTheWeightsStandIn() {
        List<PaletteEntry> many = new ArrayList<>();
        for (int i = 0; i < 101; i++) many.add(block("b" + i, i == 0 ? 2 : 1));
        String pattern = WorldEditPattern.of(palette(many.toArray(new PaletteEntry[0])));
        assertTrue(pattern.startsWith("2%b0,1%b1,1%b2,"), pattern);
        assertTrue(pattern.endsWith(",1%b100"), pattern);
    }

    @Test
    void everyCommand() {
        assertEquals("//set 70%stone,30%andesite", WorldEditPattern.command("set", WEIGHTED, null));
        assertEquals("//replace dirt,grass_block 70%stone,30%andesite", WorldEditPattern.command("replace", WEIGHTED, " dirt,grass_block "));
        assertEquals("//walls 70%stone,30%andesite", WorldEditPattern.command("walls", WEIGHTED, ""));
        assertEquals("//outline 70%stone,30%andesite", WorldEditPattern.command("outline", WEIGHTED, "ignored"));
        assertEquals("//overlay 70%stone,30%andesite", WorldEditPattern.command("overlay", WEIGHTED, null));
        assertEquals(List.of("set", "replace", "walls", "outline", "overlay"), WorldEditPattern.COMMANDS);
    }

    @Test
    void whatCannotBeBuiltIsRefused() {
        assertThrows(IllegalArgumentException.class, () -> WorldEditPattern.command("stack", WEIGHTED, null));
        assertThrows(IllegalArgumentException.class, () -> WorldEditPattern.command("Set", WEIGHTED, null));
        assertThrows(IllegalArgumentException.class, () -> WorldEditPattern.command(null, WEIGHTED, null));
        assertThrows(IllegalArgumentException.class, () -> WorldEditPattern.command("replace", WEIGHTED, " "));
        assertThrows(IllegalArgumentException.class, () -> WorldEditPattern.command("set", palette(), null));
    }
}
