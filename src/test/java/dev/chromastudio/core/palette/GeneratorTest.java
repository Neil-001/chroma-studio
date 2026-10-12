package dev.chromastudio.core.palette;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.chromastudio.core.color.Harmony;
import dev.chromastudio.core.color.Oklch;
import java.util.List;
import java.util.Random;
import org.junit.jupiter.api.Test;

class GeneratorTest {
    private static final Oklch SEED = new Oklch(0.6, 0.05, 40);

    private static double[] sortedLightness(List<Oklch> colours) {
        return colours.stream().mapToDouble(Oklch::l).sorted().toArray();
    }

    @Test
    void givesTheCountAskedForWithEveryHarmony() {
        for (Harmony h : Harmony.values()) {
            for (int count : new int[] {1, 2, 6, 13}) {
                assertEquals(count, Generator.targets(SEED, h, count, new Random(1)).size(), h + " " + count);
            }
        }
        assertThrows(IllegalArgumentException.class, () -> Generator.targets(SEED, Harmony.TRIADIC, 0, new Random(1)));
    }

    @Test
    void lightnessIsSpreadEvenlyOverHalfTheRangeAroundTheSeed() {
        double[] l = sortedLightness(Generator.targets(SEED, Harmony.TRIADIC, 6, new Random(7)));
        for (int i = 0; i < 6; i++) assertEquals(0.35 + 0.1 * i, l[i], 1e-9);
        assertEquals(0.6, Generator.targets(SEED, Harmony.TRIADIC, 1, new Random(7)).getFirst().l(), 1e-9);
    }

    @Test
    void lightnessIsHeldToTheUsableRange() {
        double[] dark = sortedLightness(Generator.targets(SEED.withL(0.3), Harmony.ANALOGOUS, 5, new Random(7)));
        assertEquals(0.2, dark[0], 1e-9);
        assertEquals(0.55, dark[4], 1e-9);
        assertEquals(0.375, dark[2], 1e-9);

        double[] light = sortedLightness(Generator.targets(SEED.withL(0.9), Harmony.ANALOGOUS, 5, new Random(7)));
        assertEquals(0.65, light[0], 1e-9);
        assertEquals(0.95, light[4], 1e-9);

        double[] white = sortedLightness(Generator.targets(SEED.withL(1.5), Harmony.ANALOGOUS, 3, new Random(7)));
        assertEquals(0.95, white[0], 1e-9);
        assertEquals(0.95, white[2], 1e-9);
    }

    @Test
    void huesGoRoundTheHarmonyWithALittleJitter() {
        double[] expected = {40, 160, 280, 40, 160, 280};
        boolean jittered = false;
        for (int roll = 0; roll < 20; roll++) {
            List<Oklch> targets = Generator.targets(SEED, Harmony.TRIADIC, 6, new Random(roll));
            for (int i = 0; i < 6; i++) {
                double off = Math.abs(HueArc.SHORTEST.turn(expected[i], targets.get(i).h()));
                assertTrue(off <= Generator.HUE_JITTER, "hue " + targets.get(i).h() + " for " + expected[i]);
                jittered |= off > 0.5;
            }
        }
        assertTrue(jittered);
    }

    @Test
    void chromaIsKeptWhereItFitsAndClippedWhereItDoesNot() {
        for (Oklch c : Generator.targets(SEED, Harmony.SQUARE, 8, new Random(3))) assertEquals(0.05, c.c(), 1e-9);

        List<Oklch> vivid = Generator.targets(new Oklch(0.6, 0.4, 40), Harmony.SQUARE, 8, new Random(3));
        for (Oklch c : vivid) {
            assertTrue(c.inGamut(), c.toString());
            assertTrue(c.c() < 0.4, c.toString());
        }
    }

    @Test
    void theSameSeedValueGivesTheSameTargets() {
        assertEquals(Generator.targets(SEED, Harmony.TETRADIC, 9, new Random(42)), Generator.targets(SEED, Harmony.TETRADIC, 9, new Random(42)));
        assertNotEquals(Generator.targets(SEED, Harmony.TETRADIC, 9, new Random(42)), Generator.targets(SEED, Harmony.TETRADIC, 9, new Random(43)));
    }

    @Test
    void theRandomGeneratorPairsHuesWithLightnesses() {
        // Over many rolls the first hue meets every lightness, not always the darkest.
        boolean[] seen = new boolean[4];
        Random random = new Random(9);
        for (int roll = 0; roll < 200; roll++) {
            double l = Generator.targets(SEED, Harmony.SQUARE, 4, random).getFirst().l();
            seen[(int) Math.round((l - 0.35) / (0.5 / 3))] = true;
        }
        for (boolean b : seen) assertTrue(b);
    }

    @Test
    void shadesKeepHueAndChromaAndStepLightnessEvenly() {
        // Little enough chroma to fit sRGB even at the dark end.
        List<Oklch> shades = Lightness.shades(SEED.withC(0.02), 4);
        assertEquals(4, shades.size());
        for (int i = 0; i < 4; i++) {
            assertEquals(0.2 + 0.25 * i, shades.get(i).l(), 1e-9);
            assertEquals(0.02, shades.get(i).c(), 1e-9);
            assertEquals(40, shades.get(i).h(), 1e-9);
        }
    }

    @Test
    void shadesAreClippedToGamut() {
        Oklch vivid = new Oklch(0.6, 0.3, 140);
        for (Oklch c : Lightness.shades(vivid, 7)) {
            assertTrue(c.inGamut(), c.toString());
            assertEquals(140, c.h(), 1e-9);
            assertTrue(c.c() <= 0.3);
        }
        assertEquals(List.of(vivid.clipped()), Lightness.shades(vivid, 1));
        assertEquals(List.of(SEED), Lightness.shades(SEED, 1));
        assertThrows(IllegalArgumentException.class, () -> Lightness.shades(SEED, 0));
    }
}
