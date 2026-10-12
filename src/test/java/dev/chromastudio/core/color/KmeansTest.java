package dev.chromastudio.core.color;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Random;
import org.junit.jupiter.api.Test;

class KmeansTest {
    private static int[] image(int... coloursAndCounts) {
        int total = 0;
        for (int i = 1; i < coloursAndCounts.length; i += 2) total += coloursAndCounts[i];
        int[] out = new int[total];
        int n = 0;
        for (int i = 0; i < coloursAndCounts.length; i += 2) {
            for (int j = 0; j < coloursAndCounts[i + 1]; j++) out[n++] = 0xFF000000 | coloursAndCounts[i];
        }
        return out;
    }

    @Test
    void separateColoursComeBackLargestFirst() {
        int[] pixels = image(0x334CB2, 20, 0xD87F33, 50, 0xFFFFFF, 30);
        assertEquals(List.of(Color.ofRgb(0xD87F33), Color.ofRgb(0xFFFFFF), Color.ofRgb(0x334CB2)), Kmeans.cluster(pixels, 3, 16));
    }

    @Test
    void noisyGroupsAreFoundNearTheirCentres() {
        int[] centres = {0xC83232, 0x32C832, 0x3232C8, 0xE6E6E6};
        Random random = new Random(3);
        int[] pixels = new int[4000];
        for (int i = 0; i < pixels.length; i++) {
            // Group 0 gets four tenths of the pixels, then three, two and one.
            int group = i % 10 < 4 ? 0 : i % 10 < 7 ? 1 : i % 10 < 9 ? 2 : 3;
            int c = centres[group];
            int r = (c >> 16 & 0xFF) + random.nextInt(21) - 10, g = (c >> 8 & 0xFF) + random.nextInt(21) - 10, b = (c & 0xFF) + random.nextInt(21) - 10;
            pixels[i] = 0xFF000000 | r << 16 | g << 8 | b;
        }
        List<Color> found = Kmeans.cluster(pixels, 4, 16);
        assertEquals(4, found.size());
        for (int i = 0; i < 4; i++) {
            assertTrue(Metric.SRGB.distance(Color.ofRgb(centres[i]), found.get(i)) < 4, "cluster " + i + " is " + found.get(i).hex());
        }
    }

    @Test
    void fewerColoursThanClustersGiveFewer() {
        assertEquals(List.of(Color.ofRgb(0x102030), Color.ofRgb(0xF0E0D0)), Kmeans.cluster(image(0x102030, 5, 0xF0E0D0, 3), 6, 16));
    }

    @Test
    void oneClusterIsTheOklabMean() {
        // Black and white in equal parts average to OKLab lightness 0.5, the 12.5% grey that sRGB stores as 99.
        assertEquals(List.of(Color.ofRgb(0x636363)), Kmeans.cluster(image(0x000000, 8, 0xFFFFFF, 8), 1, 16));
    }

    @Test
    void transparentPixelsAreIgnored() {
        int[] pixels = {0x00FF0000, 0x0FFF0000, 0xFF00FF00, 0x10FFFFFF, 0xFF00FF00};
        assertEquals(List.of(Color.ofRgb(0x00FF00), Color.ofRgb(0xFFFFFF)), Kmeans.cluster(pixels, 4, 16));
        assertEquals(List.of(), Kmeans.cluster(new int[] {0x00FF0000, 0x0FFF0000}, 4, 16));
        assertEquals(List.of(), Kmeans.cluster(new int[0], 4, 16));
    }

    @Test
    void theSameImageAlwaysGivesTheSameClusters() {
        Random random = new Random(11);
        int[] pixels = new int[2000];
        for (int i = 0; i < pixels.length; i++) pixels[i] = 0xFF000000 | random.nextInt(0x1000000);
        assertEquals(Kmeans.cluster(pixels, 6, 16), Kmeans.cluster(pixels.clone(), 6, 16));
        assertEquals(6, Kmeans.cluster(pixels, 6, 16).size());
    }

    @Test
    void aClusterCountBelowOneIsRejected() {
        assertThrows(IllegalArgumentException.class, () -> Kmeans.cluster(new int[] {0xFF000000}, 0, 16));
    }
}
