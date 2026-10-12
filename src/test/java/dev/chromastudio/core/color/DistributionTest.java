package dev.chromastudio.core.color;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import org.junit.jupiter.api.Test;

class DistributionTest {
    private static final Distribution WARM = Distribution.of(
            List.of(Color.ofRgb(0xD87F33), Color.ofRgb(0x993333), Color.ofRgb(0xF7E9A3), Color.ofRgb(0x664C33)),
            List.of(0.4, 0.3, 0.2, 0.1));
    private static final Distribution COOL = Distribution.of(
            List.of(Color.ofRgb(0x334CB2), Color.ofRgb(0x5CDBD5), Color.ofRgb(0x4C7F99)),
            List.of(0.5, 0.25, 0.25));

    private static Color grey(int level) {
        return Color.ofRgb(level << 16 | level << 8 | level);
    }

    @Test
    void weightsAreScaledToSumToOne() {
        Distribution d = Distribution.of(List.of(Color.ofRgb(0xFF0000), Color.ofRgb(0x0000FF)), List.of(3.0, 1.0));
        assertEquals(List.of(0.75, 0.25), d.weights());
        assertEquals(List.of(Color.ofRgb(0xFF0000), Color.ofRgb(0x0000FF)), d.colours());
        assertEquals(List.of(1.0), Distribution.of(Color.ofRgb(0x123456)).weights());
    }

    @Test
    void badInputIsRejected() {
        Color c = Color.ofRgb(0);
        assertThrows(IllegalArgumentException.class, () -> Distribution.of(List.of(), List.of()));
        assertThrows(IllegalArgumentException.class, () -> Distribution.of(List.of(c), List.of(1.0, 2.0)));
        assertThrows(IllegalArgumentException.class, () -> Distribution.of(List.of(c, c), List.of(1.0, -1.0)));
        assertThrows(IllegalArgumentException.class, () -> Distribution.of(List.of(c, c), List.of(0.0, 0.0)));
        assertThrows(IllegalArgumentException.class, () -> Distribution.of(List.of(c), List.of(Double.NaN)));
        assertThrows(IllegalArgumentException.class,
                () -> Distribution.of(Collections.nCopies(9, c), Collections.nCopies(9, 1.0)));
        assertEquals(8, Distribution.of(Collections.nCopies(8, c), Collections.nCopies(8, 1.0)).colours().size());
    }

    @Test
    void emdAgainstAFlatColourIsTheMeanDistance() {
        Color target = Color.ofRgb(0x7FA796);
        for (Metric m : Metric.values()) {
            for (Distribution d : List.of(WARM, COOL)) {
                double mean = 0;
                for (int i = 0; i < d.colours().size(); i++) mean += d.weights().get(i) * m.distance(target, d.colours().get(i));
                assertEquals(mean, Distribution.of(target).emd(d, m), 1e-9, m.toString());
                assertEquals(mean, d.emd(Distribution.of(target), m), 1e-9, m.toString());
            }
        }
    }

    @Test
    void emdIsSymmetricAndZeroOnItself() {
        for (Metric m : Metric.values()) {
            assertEquals(0, WARM.emd(WARM, m), 1e-9, m.toString());
            assertEquals(0, COOL.emd(COOL, m), 1e-9, m.toString());
            double there = WARM.emd(COOL, m);
            assertTrue(there > 0);
            assertEquals(there, COOL.emd(WARM, m), 1e-9, m.toString());
        }
    }

    /** Along the grey axis the earth mover's distance is the area between the two cumulative curves. */
    @Test
    void emdOnGreysByHand() {
        Distribution a = Distribution.of(List.of(grey(0), grey(100)), List.of(0.5, 0.5));
        Distribution b = Distribution.of(List.of(grey(20), grey(60)), List.of(0.25, 0.75));
        // 0 to 20: 0.5 · 20. 20 to 60: 0.25 · 40. 60 to 100: 0.5 · 40. That is 40 grey levels, each √3 in sRGB.
        assertEquals(40 * Math.sqrt(3), a.emd(b, Metric.SRGB), 1e-9);
    }

    @Test
    void emdMatchesTheCumulativeFormOnRandomGreys() {
        Random random = new Random(7);
        for (int round = 0; round < 200; round++) {
            double[] wa = new double[8], wb = new double[8];
            int[] la = new int[8], lb = new int[8];
            Distribution a = randomGreys(random, la, wa), b = randomGreys(random, lb, wb);
            double area = 0;
            for (int level = 0; level < 255; level++) {
                double fa = 0, fb = 0;
                for (int i = 0; i < 8; i++) {
                    if (la[i] <= level) fa += wa[i];
                    if (lb[i] <= level) fb += wb[i];
                }
                area += Math.abs(fa - fb);
            }
            assertEquals(area * Math.sqrt(3), a.emd(b, Metric.SRGB), 1e-6, "round " + round);
        }
    }

    private static Distribution randomGreys(Random random, int[] levels, double[] weights) {
        List<Color> colours = new ArrayList<>();
        List<Double> raw = new ArrayList<>();
        double total = 0;
        for (int i = 0; i < 8; i++) {
            levels[i] = random.nextInt(256);
            weights[i] = random.nextDouble();
            total += weights[i];
            colours.add(grey(levels[i]));
            raw.add(weights[i]);
        }
        for (int i = 0; i < 8; i++) weights[i] /= total;
        return Distribution.of(colours, raw);
    }

    @Test
    void transportFindsTheOptimumWhereTheCheapestCellMisleads() {
        // Filling the free cell first forces the dear one: 0 + 3 · 0.5 = 1.5. Crossing over costs 0.5 + 0.5 = 1.
        double[][] cost = {{0, 1}, {1, 3}};
        assertEquals(1.0, Distribution.transport(new double[] {0.5, 0.5}, new double[] {0.5, 0.5}, cost), 1e-12);
        // One source can only split as the sinks ask.
        assertEquals(0.3 * 2 + 0.7 * 5, Distribution.transport(new double[] {1}, new double[] {0.3, 0.7}, new double[][] {{2, 5}}), 1e-12);
    }

    @Test
    void meanBlendsInLinearLight() {
        // Half black and half white is 50% luminance, which sRGB stores as 188.
        assertEquals(0xBCBCBC, Distribution.of(List.of(grey(0), grey(255)), List.of(1.0, 1.0)).mean().rgb());
        assertEquals(0x123456, Distribution.of(Color.ofRgb(0x123456)).mean().rgb());
    }

    @Test
    void tintMultipliesEveryColour() {
        Distribution d = Distribution.of(List.of(Color.ofRgb(0xFFFFFF), Color.ofRgb(0x808080)), List.of(1.0, 3.0)).tinted(0x80FF00);
        assertEquals(List.of(Color.ofRgb(0x80FF00), Color.ofRgb(0x408000)), d.colours());
        assertEquals(List.of(0.25, 0.75), d.weights());
    }

    @Test
    void pixelsBecomeTheirDominantColours() {
        int[] pixels = new int[16];
        for (int i = 0; i < 16; i++) pixels[i] = i < 12 ? 0xFFD87F33 : 0xFF334CB2;
        Distribution d = Distribution.ofPixels(pixels, 16);
        assertEquals(List.of(Color.ofRgb(0xD87F33), Color.ofRgb(0x334CB2)), d.colours());
        assertEquals(0.75, d.weights().get(0), 1e-12);
        assertEquals(0.25, d.weights().get(1), 1e-12);
    }

    @Test
    void pixelsCloserThanAStepMerge() {
        // Four greys one level apart are about 0.004 apart in OKLab lightness, far inside a step of 0.02.
        int[] pixels = {0xFF808080, 0xFF818181, 0xFF828282, 0xFF838383, 0xFFFF0000, 0xFFFF0000};
        Distribution d = Distribution.ofPixels(pixels, 16);
        assertEquals(2, d.colours().size());
        assertEquals(4 / 6.0, d.weights().get(0), 1e-12);
        assertTrue(Metric.SRGB.distance(grey(0x81), d.colours().get(0)) <= Math.sqrt(3) + 1e-9, d.colours().get(0).hex());
        assertEquals(Color.ofRgb(0xFF0000), d.colours().get(1));
    }

    @Test
    void pixelsGiveAtMostFourColours() {
        int[] pixels = {0xFFFF0000, 0xFF00FF00, 0xFF0000FF, 0xFFFFFF00, 0xFF00FFFF, 0xFFFF00FF, 0xFF000000, 0xFFFFFFFF};
        Distribution d = Distribution.ofPixels(pixels, 16);
        assertEquals(4, d.colours().size());
        assertEquals(1, d.weights().stream().mapToDouble(Double::doubleValue).sum(), 1e-12);
    }

    @Test
    void faintPixelsAreLeftOutAndTheRestCountByAlpha() {
        // Alpha 15 is under the cutoff; alpha 51 counts a fifth of a solid pixel.
        int[] pixels = {0x0F00FF00, 0xFFFF0000, 0x330000FF};
        Distribution d = Distribution.ofPixels(pixels, 16);
        assertEquals(List.of(Color.ofRgb(0xFF0000), Color.ofRgb(0x0000FF)), d.colours());
        assertEquals(1 / 1.2, d.weights().get(0), 1e-12);
        assertEquals(List.of(Color.ofRgb(0)), Distribution.ofPixels(new int[] {0x0FFFFFFF, 0x00FFFFFF}, 16).colours());
    }
}
