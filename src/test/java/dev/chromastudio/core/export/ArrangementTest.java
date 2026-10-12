package dev.chromastudio.core.export;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.Test;

class ArrangementTest {
    private static final List<Layout> DIORAMAS = List.of(Layout.NOISE_CLUSTERS, Layout.HEIGHT_GRADIENT, Layout.DITHER);

    private static List<Integer> ones(int n) { return Collections.nCopies(n, 1); }

    private static LayoutOptions options(Layout layout, boolean gap) { return new LayoutOptions(layout, 3, 3, 16, gap, 7); }

    private static int[] counts(Arrangement a, int entries) {
        int[] counts = new int[entries + 1];
        for (int y = 0; y < a.sizeY(); y++)
            for (int x = 0; x < a.sizeX(); x++) counts[a.at(x, y, 0) + 1]++;
        return counts;
    }

    private static List<Integer> cells(Arrangement a) {
        List<Integer> out = new ArrayList<>();
        for (int y = 0; y < a.sizeY(); y++)
            for (int x = 0; x < a.sizeX(); x++) out.add(a.at(x, y, 0));
        return out;
    }

    @Test
    void aStripIsOneRowInOrder() {
        Arrangement a = Arrangement.of(ones(6), options(Layout.STRIP, false));
        assertEquals(List.of(6, 1, 1), List.of(a.sizeX(), a.sizeY(), a.sizeZ()));
        assertEquals(List.of(0, 1, 2, 3, 4, 5), cells(a));
    }

    @Test
    void aGapPutsAirBetweenAStripsEntries() {
        Arrangement a = Arrangement.of(ones(4), options(Layout.STRIP, true));
        assertEquals(7, a.sizeX());
        assertEquals(List.of(0, -1, 1, -1, 2, -1, 3), cells(a));
    }

    @Test
    void aWallIsOneColumnPerEntry() {
        Arrangement a = Arrangement.of(ones(5), new LayoutOptions(Layout.WALL, 4, 3, 16, false, 0));
        assertEquals(List.of(5, 4, 1), List.of(a.sizeX(), a.sizeY(), a.sizeZ()));
        for (int y = 0; y < 4; y++)
            for (int x = 0; x < 5; x++) assertEquals(x, a.at(x, y, 0));

        Arrangement gapped = Arrangement.of(ones(5), new LayoutOptions(Layout.WALL, 4, 3, 16, true, 0));
        assertEquals(9, gapped.sizeX());
        for (int y = 0; y < 4; y++)
            for (int x = 0; x < 9; x++) assertEquals(x % 2 == 0 ? x / 2 : -1, gapped.at(x, y, 0));
    }

    @Test
    void aPatchGridReadsFromTheTopLeft() {
        // Seven entries make a grid three across and three down, with two patches left empty at the end.
        Arrangement a = Arrangement.of(ones(7), options(Layout.PATCH_GRID, false));
        assertEquals(List.of(9, 9, 1), List.of(a.sizeX(), a.sizeY(), a.sizeZ()));
        for (int y = 0; y < 9; y++)
            for (int x = 0; x < 9; x++) {
                int entry = (2 - y / 3) * 3 + x / 3;
                assertEquals(entry < 7 ? entry : -1, a.at(x, y, 0), x + ", " + y);
            }
        assertEquals(List.of(4, 4), sizeOf(Arrangement.of(ones(4), new LayoutOptions(Layout.PATCH_GRID, 3, 2, 16, false, 0))));
        assertEquals(List.of(5, 5), sizeOf(Arrangement.of(ones(1), new LayoutOptions(Layout.PATCH_GRID, 3, 5, 16, false, 0))));
    }

    @Test
    void aGapPutsAirBetweenPatches() {
        Arrangement a = Arrangement.of(ones(4), options(Layout.PATCH_GRID, true));
        assertEquals(List.of(7, 7), sizeOf(a));
        for (int i = 0; i < 7; i++) {
            assertEquals(-1, a.at(3, i, 0));
            assertEquals(-1, a.at(i, 3, 0));
        }
        assertEquals(0, a.at(0, 6, 0));
        assertEquals(1, a.at(6, 6, 0));
        assertEquals(2, a.at(0, 0, 0));
        assertEquals(3, a.at(6, 0, 0));
        assertEquals(9, counts(a, 4)[1]);
    }

    @Test
    void theDioramasFillTheirSizeWithEveryEntry() {
        for (Layout layout : DIORAMAS) {
            Arrangement a = Arrangement.of(ones(6), new LayoutOptions(layout, 12, 3, 20, true, 3));
            assertEquals(List.of(20, 12, 1), List.of(a.sizeX(), a.sizeY(), a.sizeZ()), layout.name());
            int[] counts = counts(a, 6);
            assertEquals(0, counts[0], layout + " has no air, gap or not");
            for (int entry = 0; entry < 6; entry++) assertTrue(counts[entry + 1] > 0, layout + " has entry " + entry);
        }
    }

    @Test
    void theRegularLayoutsHoldEveryEntry() {
        for (Layout layout : List.of(Layout.STRIP, Layout.WALL, Layout.PATCH_GRID))
            for (boolean gap : new boolean[] {false, true}) {
                Arrangement a = Arrangement.of(ones(11), options(layout, gap));
                assertEquals(11, a.placed());
                int[] counts = counts(a, 11);
                for (int entry = 0; entry < 11; entry++) assertTrue(counts[entry + 1] > 0, layout + " has entry " + entry);
            }
    }

    @Test
    void theDioramasGiveEachEntryItsWeightsShare() {
        List<Integer> weights = List.of(1, 3, 6);
        for (Layout layout : DIORAMAS) {
            Arrangement a = Arrangement.of(weights, new LayoutOptions(layout, 100, 3, 100, false, 11));
            int[] counts = counts(a, 3);
            for (int entry = 0; entry < 3; entry++)
                assertEquals(weights.get(entry) / 10.0, counts[entry + 1] / 10000.0, 0.02, layout + " entry " + entry);
        }
    }

    @Test
    void theHeightGradientRunsFromTheFirstEntryAtTheBottomToTheLastAtTheTop() {
        Arrangement a = Arrangement.of(ones(4), new LayoutOptions(Layout.HEIGHT_GRADIENT, 40, 3, 30, false, 5));
        double[] meanRow = new double[4];
        int[] counts = counts(a, 4);
        for (int y = 0; y < 40; y++)
            for (int x = 0; x < 30; x++) meanRow[a.at(x, y, 0)] += (double) y / counts[a.at(x, y, 0) + 1];
        for (int entry = 1; entry < 4; entry++) assertTrue(meanRow[entry] > meanRow[entry - 1] + 5);
        for (int x = 0; x < 30; x++) {
            assertEquals(0, a.at(x, 0, 0));
            assertEquals(3, a.at(x, 39, 0));
        }
        // The edges between bands are ragged, so some row holds two entries.
        boolean mixed = false;
        for (int y = 0; y < 40 && !mixed; y++)
            for (int x = 1; x < 30 && !mixed; x++) mixed = a.at(x, y, 0) != a.at(0, y, 0);
        assertTrue(mixed);
    }

    @Test
    void noiseClustersClumpWhereDitherDoesNot() {
        LayoutOptions noise = new LayoutOptions(Layout.NOISE_CLUSTERS, 48, 3, 48, false, 2);
        LayoutOptions dither = new LayoutOptions(Layout.DITHER, 48, 3, 48, false, 2);
        double clustered = sameAsNeighbour(Arrangement.of(ones(4), noise)), scattered = sameAsNeighbour(Arrangement.of(ones(4), dither));
        assertTrue(clustered > 0.7, "noise: " + clustered);
        assertTrue(scattered < 0.35, "dither: " + scattered);
    }

    private static double sameAsNeighbour(Arrangement a) {
        int same = 0, pairs = 0;
        for (int y = 0; y < a.sizeY(); y++)
            for (int x = 1; x < a.sizeX(); x++, pairs++) if (a.at(x, y, 0) == a.at(x - 1, y, 0)) same++;
        return (double) same / pairs;
    }

    @Test
    void aSeedRepeatsAndAnotherSeedDiffers() {
        for (Layout layout : DIORAMAS) {
            List<Integer> weights = List.of(2, 1, 1, 3);
            List<Integer> first = cells(Arrangement.of(weights, new LayoutOptions(layout, 16, 3, 24, false, 42)));
            assertEquals(first, cells(Arrangement.of(weights, new LayoutOptions(layout, 16, 3, 24, false, 42))), layout.name());
            assertNotEquals(first, cells(Arrangement.of(weights, new LayoutOptions(layout, 16, 3, 24, false, 43))), layout.name());
        }
    }

    @Test
    void noSideGoesPast256() {
        for (Layout layout : DIORAMAS) {
            Arrangement a = Arrangement.of(ones(3), new LayoutOptions(layout, 1000, 3, 1000, false, 0));
            assertEquals(List.of(256, 256), sizeOf(a));
            assertEquals(List.of(1, 1), sizeOf(Arrangement.of(ones(3), new LayoutOptions(layout, 0, 3, -4, false, 0))));
        }
        Arrangement strip = Arrangement.of(ones(300), options(Layout.STRIP, false));
        assertEquals(List.of(256, 256), List.of(strip.sizeX(), strip.placed()));
        Arrangement gapped = Arrangement.of(ones(300), options(Layout.STRIP, true));
        assertEquals(List.of(255, 128), List.of(gapped.sizeX(), gapped.placed()));
        assertEquals(127, gapped.at(254, 0, 0));
        Arrangement wall = Arrangement.of(ones(300), new LayoutOptions(Layout.WALL, 999, 3, 16, false, 0));
        assertEquals(List.of(256, 256), sizeOf(wall));
        // Patches 16 across with a gap fit fifteen to a side.
        Arrangement grid = Arrangement.of(ones(300), new LayoutOptions(Layout.PATCH_GRID, 3, 16, 16, true, 0));
        assertEquals(List.of(254, 254), sizeOf(grid));
        assertEquals(225, grid.placed());
        assertEquals(224, grid.at(253, 0, 0));
        Arrangement huge = Arrangement.of(ones(2), new LayoutOptions(Layout.PATCH_GRID, 3, 999, 16, false, 0));
        assertEquals(List.of(256, 256), sizeOf(huge));
        assertEquals(1, huge.placed());
    }

    @Test
    void thereMustBeAnEntryAndACellMustBeInside() {
        assertThrows(IllegalArgumentException.class, () -> Arrangement.of(List.of(), options(Layout.STRIP, false)));
        Arrangement a = Arrangement.of(ones(2), options(Layout.STRIP, false));
        assertThrows(IndexOutOfBoundsException.class, () -> a.at(2, 0, 0));
        assertThrows(IndexOutOfBoundsException.class, () -> a.at(0, 0, 1));
    }

    private static List<Integer> sizeOf(Arrangement a) { return List.of(a.sizeX(), a.sizeY()); }
}
