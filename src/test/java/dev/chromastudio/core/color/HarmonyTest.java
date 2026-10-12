package dev.chromastudio.core.color;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

class HarmonyTest {
    private static final Oklch SEED = new Oklch(0.6, 0.1, 40);

    private static void assertHues(Harmony harmony, double... hues) {
        List<Oklch> colours = harmony.colours(SEED, hues.length);
        assertEquals(hues.length, colours.size(), harmony.toString());
        for (int i = 0; i < hues.length; i++) {
            assertEquals(hues[i], colours.get(i).h(), 1e-9, harmony + " hue " + i);
            assertEquals(SEED.l(), colours.get(i).l(), harmony + " lightness " + i);
            assertEquals(SEED.c(), colours.get(i).c(), harmony + " chroma " + i);
        }
    }

    @Test
    void everyModelTurnsTheHueByItsOffsets() {
        assertHues(Harmony.SIMILAR, 40, 55, 25);
        assertHues(Harmony.ANALOGOUS, 40, 70, 10);
        assertHues(Harmony.COMPLEMENTARY, 40, 220);
        assertHues(Harmony.SPLIT_COMPLEMENTARY, 40, 190, 250);
        assertHues(Harmony.TRIADIC, 40, 160, 280);
        assertHues(Harmony.TETRADIC, 40, 100, 220, 280);
        assertHues(Harmony.SQUARE, 40, 130, 220, 310);
    }

    @Test
    void offsetsAreAsSpecified() {
        assertArrayEquals(new double[] {0, 15, -15}, Harmony.SIMILAR.offsets());
        assertArrayEquals(new double[] {0, 30, -30}, Harmony.ANALOGOUS.offsets());
        assertArrayEquals(new double[] {0, 180}, Harmony.COMPLEMENTARY.offsets());
        assertArrayEquals(new double[] {0, 150, 210}, Harmony.SPLIT_COMPLEMENTARY.offsets());
        assertArrayEquals(new double[] {0, 120, 240}, Harmony.TRIADIC.offsets());
        assertArrayEquals(new double[] {0, 60, 180, 240}, Harmony.TETRADIC.offsets());
        assertArrayEquals(new double[] {0, 90, 180, 270}, Harmony.SQUARE.offsets());
        assertArrayEquals(new double[] {0}, Harmony.MONOCHROMATIC.offsets());
    }

    @Test
    void huesWrapAndCycle() {
        // 300° + 120° wraps to 60°, and a fourth triadic colour starts the cycle again.
        List<Oklch> colours = Harmony.TRIADIC.colours(SEED.withHue(300), 5);
        assertEquals(List.of(300.0, 60.0, 180.0, 300.0, 60.0), colours.stream().map(Oklch::h).toList());
        for (Harmony h : Harmony.values()) {
            for (int count = 1; count <= 9; count++) assertEquals(count, h.colours(SEED, count).size());
            assertEquals(SEED, h.colours(SEED, 1).get(0));
            assertThrows(IllegalArgumentException.class, () -> h.colours(SEED, 0));
        }
    }

    @Test
    void monochromaticStepsLightnessEvenly() {
        List<Oklch> colours = Harmony.MONOCHROMATIC.colours(SEED, 4);
        double[] lightness = {0.2, 0.45, 0.7, 0.95};
        for (int i = 0; i < 4; i++) {
            assertEquals(lightness[i], colours.get(i).l(), 1e-9);
            assertEquals(40, colours.get(i).h(), 1e-9);
            assertTrue(colours.get(i).inGamut());
            assertTrue(colours.get(i).c() <= SEED.c());
        }
        // The middle steps have room for the seed's chroma; the ends are too near black and white to hold it.
        assertEquals(0.1, colours.get(1).c());
        assertEquals(0.1, colours.get(2).c());
        assertTrue(colours.get(0).c() < 0.1);
        assertTrue(colours.get(3).c() < 0.1);
    }
}
