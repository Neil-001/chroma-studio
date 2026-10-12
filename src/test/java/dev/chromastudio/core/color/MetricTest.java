package dev.chromastudio.core.color;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class MetricTest {
    /**
     * The test pairs published with Sharma, Wu and Dalal, "The CIEDE2000 color-difference formula: implementation
     * notes, supplementary test data, and mathematical observations" (2005): L, a, b of each colour, then ΔE00.
     */
    private static final double[][] SHARMA = {
            {50.0000, 2.6772, -79.7751, 50.0000, 0.0000, -82.7485, 2.0425},
            {50.0000, 3.1571, -77.2803, 50.0000, 0.0000, -82.7485, 2.8615},
            {50.0000, 2.8361, -74.0200, 50.0000, 0.0000, -82.7485, 3.4412},
            {50.0000, -1.3802, -84.2814, 50.0000, 0.0000, -82.7485, 1.0000},
            {50.0000, -1.1848, -84.8006, 50.0000, 0.0000, -82.7485, 1.0000},
            {50.0000, -0.9009, -85.5211, 50.0000, 0.0000, -82.7485, 1.0000},
            {50.0000, 0.0000, 0.0000, 50.0000, -1.0000, 2.0000, 2.3669},
            {50.0000, -1.0000, 2.0000, 50.0000, 0.0000, 0.0000, 2.3669},
            {50.0000, 2.4900, -0.0010, 50.0000, -2.4900, 0.0009, 7.1792},
            {50.0000, 2.4900, -0.0010, 50.0000, -2.4900, 0.0010, 7.1792},
            {50.0000, 2.4900, -0.0010, 50.0000, -2.4900, 0.0011, 7.2195},
            {50.0000, 2.4900, -0.0010, 50.0000, -2.4900, 0.0012, 7.2195},
            {50.0000, -0.0010, 2.4900, 50.0000, 0.0009, -2.4900, 4.8045},
            {50.0000, -0.0010, 2.4900, 50.0000, 0.0010, -2.4900, 4.8045},
            {50.0000, -0.0010, 2.4900, 50.0000, 0.0011, -2.4900, 4.7461},
            {50.0000, 2.5000, 0.0000, 50.0000, 0.0000, -2.5000, 4.3065},
            {50.0000, 2.5000, 0.0000, 73.0000, 25.0000, -18.0000, 27.1492},
            {50.0000, 2.5000, 0.0000, 61.0000, -5.0000, 29.0000, 22.8977},
            {50.0000, 2.5000, 0.0000, 56.0000, -27.0000, -3.0000, 31.9030},
            {50.0000, 2.5000, 0.0000, 58.0000, 24.0000, 15.0000, 19.4535},
            {50.0000, 2.5000, 0.0000, 50.0000, 3.1736, 0.5854, 1.0000},
            {50.0000, 2.5000, 0.0000, 50.0000, 3.2972, 0.0000, 1.0000},
            {50.0000, 2.5000, 0.0000, 50.0000, 1.8634, 0.5757, 1.0000},
            {50.0000, 2.5000, 0.0000, 50.0000, 3.2592, 0.3350, 1.0000},
            {60.2574, -34.0099, 36.2677, 60.4626, -34.1751, 39.4387, 1.2644},
            {63.0109, -31.0961, -5.8663, 62.8187, -29.7946, -4.0864, 1.2630},
            {61.2901, 3.7196, -5.3901, 61.4292, 2.2480, -4.9620, 1.8731},
            {35.0831, -44.1164, 3.7933, 35.0232, -40.0716, 1.5901, 1.8645},
            {22.7233, 20.0904, -46.6940, 23.0331, 14.9730, -42.5619, 2.0373},
            {36.4612, 47.8580, 18.3852, 36.2715, 50.5065, 21.2231, 1.4146},
            {90.8027, -2.0831, 1.4410, 91.1528, -1.6435, 0.0447, 1.4441},
            {90.9257, -0.5406, -0.9208, 88.6381, -0.8985, -0.7239, 1.5381},
            {6.7747, -0.2908, -2.4247, 5.8714, -0.0985, -2.2286, 0.6377},
            {2.0776, 0.0795, -1.1350, 0.9033, -0.0636, -0.5514, 0.9082}};

    @Test
    void ciede2000MatchesSharmasPairs() {
        for (int i = 0; i < SHARMA.length; i++) {
            double[] p = SHARMA[i];
            Lab x = new Lab(p[0], p[1], p[2]), y = new Lab(p[3], p[4], p[5]);
            assertEquals(p[6], Metric.ciede2000(x, y), 0.00006, "pair " + (i + 1));
            assertEquals(p[6], Metric.ciede2000(y, x), 0.00006, "pair " + (i + 1) + " reversed");
        }
    }

    @Test
    void ciede2000OnColours() {
        Color a = Color.ofRgb(0xFF0000), b = Color.ofRgb(0x00FF00);
        assertEquals(Metric.ciede2000(a.lab(), b.lab()), Metric.CIEDE2000.distance(a, b));
        assertEquals(0, Metric.CIEDE2000.distance(a, a));
        // Black to white differ only in L: 100 over S_L, with S_L = 1 + 0.015 · 0² / √20 = 1 at the mean of 50.
        assertEquals(100, Metric.CIEDE2000.distance(Color.ofRgb(0), Color.ofRgb(0xFFFFFF)), 0.01);
    }

    @Test
    void cie76ByHand() {
        assertEquals(100, Metric.CIE76.distance(Color.ofRgb(0), Color.ofRgb(0xFFFFFF)), 0.01);
        // Red (53.2408, 80.0925, 67.2032) to green (87.7347, -86.1827, 83.1793), from Lindbloom's tables:
        // √(34.4939² + 166.2752² + 15.9761²) = √(1189.83 + 27647.44 + 255.24) = 170.565.
        assertEquals(170.565, Metric.CIE76.distance(Color.ofRgb(0xFF0000), Color.ofRgb(0x00FF00)), 0.01);
        assertEquals(5, Metric.cie76(new Lab(50, 0, 0), new Lab(50, 3, -4)));
    }

    @Test
    void srgbByHand() {
        assertEquals(Math.sqrt(3) * 255, Metric.SRGB.distance(Color.ofRgb(0), Color.ofRgb(0xFFFFFF)), 1e-9);
        // (16, 32, 48) to (19, 36, 60): √(9 + 16 + 144) = 13.
        assertEquals(13, Metric.SRGB.distance(Color.ofRgb(0x102030), Color.ofRgb(0x13243C)), 1e-9);
    }

    @Test
    void oklabIsEuclidean() {
        assertEquals(1, Metric.OKLAB.distance(Color.ofRgb(0), Color.ofRgb(0xFFFFFF)), 1e-4);
        // Red and green from the published values: √(0.23848² + 0.45875² + 0.05365²) = 0.5198.
        assertEquals(0.5198, Metric.OKLAB.distance(Color.ofRgb(0xFF0000), Color.ofRgb(0x00FF00)), 1e-4);
        assertEquals(0.5, new Oklab(0.2, 0.1, 0.1).distance(new Oklab(0.2, 0.4, -0.3)), 1e-12);
    }

    @Test
    void theMapColourTableIsTheGames() {
        assertEquals(62, MapColors.count());
        assertEquals(0x000000, MapColors.rgb(0));  // NONE
        assertEquals(0x7FB238, MapColors.rgb(1));  // GRASS
        assertEquals(0x707070, MapColors.rgb(11)); // STONE
        assertEquals(0x4040FF, MapColors.rgb(12)); // WATER
        assertEquals(0x191919, MapColors.rgb(29)); // COLOR_BLACK
        assertEquals(0x7FA796, MapColors.rgb(61)); // GLOW_LICHEN
    }

    @Test
    void mapColourSnapsToTheNearestBaseColour() {
        assertEquals(11, MapColors.nearest(Color.ofRgb(0x707070)));
        assertEquals(11, MapColors.nearest(Color.ofRgb(0x727071)));
        assertEquals(4, MapColors.nearest(Color.ofRgb(0xFA0505)));
        assertEquals(8, MapColors.nearest(Color.ofRgb(0xFFFFFF)));
        // Pure black goes to the black base colour, not to NONE, which a map leaves empty.
        assertEquals(29, MapColors.nearest(Color.ofRgb(0x000000)));

        // Two colours that snap to the same base colour are no distance apart.
        assertEquals(0, Metric.MAP_COLOR.distance(Color.ofRgb(0x707070), Color.ofRgb(0x727071)));
        // Otherwise it is the OKLab distance between the two base colours, not between the colours themselves.
        double stoneToFire = Metric.OKLAB.distance(Color.ofRgb(0x707070), Color.ofRgb(0xFF0000));
        assertEquals(stoneToFire, Metric.MAP_COLOR.distance(Color.ofRgb(0x727071), Color.ofRgb(0xFA0505)), 1e-12);
    }

    @Test
    void justNoticeableSteps() {
        assertEquals(0.02, Metric.OKLAB.justNoticeable());
        assertEquals(1.0, Metric.CIEDE2000.justNoticeable());
        assertEquals(2.3, Metric.CIE76.justNoticeable());
        assertEquals(8, Metric.SRGB.justNoticeable());
        assertEquals(0.02, Metric.MAP_COLOR.justNoticeable());
    }

    @Test
    void everyMetricIsSymmetricAndZeroOnItself() {
        int[] samples = {0x000000, 0xFFFFFF, 0x7F7F7F, 0xD87F33, 0x334CB2, 0x14B485, 0xF27FA5};
        for (Metric m : Metric.values()) {
            for (int a : samples) {
                assertEquals(0, m.distance(Color.ofRgb(a), Color.ofRgb(a)), 1e-9, m + " self");
                for (int b : samples) {
                    double ab = m.distance(Color.ofRgb(a), Color.ofRgb(b));
                    assertEquals(ab, m.distance(Color.ofRgb(b), Color.ofRgb(a)), 1e-9, m + " symmetry");
                    assertTrue(a == b || ab > 0, m + " apart");
                }
            }
        }
    }
}
