package dev.chromastudio.core.palette;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class BuildSampleTest {
    private static final String BRICKS = "minecraft:stone_bricks", PLANKS = "minecraft:spruce_planks", GLASS = "minecraft:glass",
            LANTERN = "minecraft:lantern";

    private static void fill(BuildSample s, String id, int n, int y) {
        for (int x = 0; x < n; x++) s.add(x, y, 0, id);
    }

    @Test
    void theSameBlockSampledTwiceCountsOnce() {
        BuildSample s = new BuildSample();
        assertTrue(s.add(1, 2, 3, BRICKS));
        assertFalse(s.add(1, 2, 3, BRICKS));
        assertEquals(List.of(new BuildSample.Row(BRICKS, 1)), s.rows());
        assertEquals(1, s.total());
    }

    @Test
    void aBlockThatChangedMovesToItsNewKindOrLeaves() {
        BuildSample s = new BuildSample();
        s.add(0, 0, 0, BRICKS);
        s.add(1, 0, 0, BRICKS);
        assertTrue(s.add(0, 0, 0, PLANKS));
        assertEquals(List.of(new BuildSample.Row(PLANKS, 1), new BuildSample.Row(BRICKS, 1)), s.rows());
        assertTrue(s.add(1, 0, 0, null));
        assertEquals(List.of(new BuildSample.Row(PLANKS, 1)), s.rows());
        assertFalse(s.add(5, 5, 5, null));
    }

    @Test
    void rowsAreMostCommonFirstAndTiesByIdSoTheyDontJitter() {
        BuildSample s = new BuildSample();
        fill(s, GLASS, 3, 0);
        fill(s, BRICKS, 10, 1);
        fill(s, PLANKS, 3, 2);
        assertEquals(List.of(new BuildSample.Row(BRICKS, 10), new BuildSample.Row(GLASS, 3), new BuildSample.Row(PLANKS, 3)), s.rows());
        assertEquals(16, s.total());
    }

    @Test
    void aRemovedKindStaysOutWhileSamplingGoesOnUntilPutBack() {
        BuildSample s = new BuildSample();
        fill(s, BRICKS, 4, 0);
        fill(s, GLASS, 2, 1);
        s.remove(GLASS);
        assertEquals(List.of(new BuildSample.Row(BRICKS, 4)), s.rows());
        assertEquals(List.of(GLASS), s.removed());
        assertFalse(s.counts(0, 1, 0));
        assertFalse(s.add(9, 1, 0, GLASS));
        assertEquals(4, s.total());
        s.putBack(GLASS);
        assertEquals(List.of(new BuildSample.Row(BRICKS, 4), new BuildSample.Row(GLASS, 3)), s.rows());
        assertTrue(s.removed().isEmpty());
    }

    @Test
    void kindsArePutBackOneAtATimeAndASampleOfOnlyLeftOutKindsIsntEmpty() {
        BuildSample s = new BuildSample();
        fill(s, BRICKS, 4, 0);
        fill(s, GLASS, 2, 1);
        s.remove(GLASS);
        s.remove(BRICKS);
        assertTrue(s.rows().isEmpty());
        assertFalse(s.isEmpty());
        s.putBack(BRICKS);
        assertEquals(List.of(new BuildSample.Row(BRICKS, 4)), s.rows());
        assertEquals(List.of(GLASS), s.removed());
    }

    @Test
    void aLeftOutKindWhoseLastBlockIsGoneIsForgotten() {
        BuildSample s = new BuildSample();
        s.add(0, 0, 0, GLASS);
        s.remove(GLASS);
        assertFalse(s.add(0, 0, 0, null));
        assertTrue(s.isEmpty());
        assertTrue(s.removed().isEmpty());
        s.add(1, 0, 0, GLASS);
        assertEquals(List.of(new BuildSample.Row(GLASS, 1)), s.rows());
    }

    @Test
    void clearEmptiesEverythingRemovedKindsIncluded() {
        BuildSample s = new BuildSample();
        fill(s, BRICKS, 4, 0);
        s.remove(BRICKS);
        s.clear();
        assertTrue(s.isEmpty());
        assertTrue(s.removed().isEmpty());
        s.add(0, 0, 0, BRICKS);
        assertEquals(List.of(new BuildSample.Row(BRICKS, 1)), s.rows());
    }

    @Test
    void forEachVisitsOnlyCountedPositions() {
        BuildSample s = new BuildSample();
        s.add(-3, -60, 7, BRICKS);
        s.add(4, 100, -2, GLASS);
        s.remove(GLASS);
        StringBuilder seen = new StringBuilder();
        s.forEach((x, y, z) -> seen.append(x).append(',').append(y).append(',').append(z));
        assertEquals("-3,-60,7", seen.toString());
    }

    @Test
    void onlyAPositionHoldingALeftOutKindIsLeftOut() {
        BuildSample s = new BuildSample();
        s.add(0, 0, 0, GLASS);
        s.add(1, 0, 0, BRICKS);
        s.remove(GLASS);
        assertTrue(s.leftOut(0, 0, 0));
        assertFalse(s.leftOut(1, 0, 0));
        assertFalse(s.leftOut(2, 0, 0));
        s.add(0, 0, 0, null);
        assertFalse(s.leftOut(0, 0, 0));
    }

    @Test
    void forEachLeftOutVisitsEveryLeftOutKindOnce() {
        BuildSample s = new BuildSample();
        s.add(0, 0, 0, GLASS);
        s.add(1, 0, 0, LANTERN);
        s.add(2, 0, 0, BRICKS);
        s.remove(GLASS);
        s.remove(LANTERN);
        List<String> seen = new ArrayList<>();
        s.forEachLeftOut((x, y, z) -> seen.add(s.kindAt(x, y, z)));
        assertEquals(2, seen.size());
        assertTrue(seen.contains(GLASS) && seen.contains(LANTERN), seen.toString());
    }

    @Test
    void aLeftOutKindKeepsItsCount() {
        BuildSample s = new BuildSample();
        fill(s, GLASS, 3, 0);
        s.remove(GLASS);
        assertEquals(3, s.count(GLASS));
        assertEquals(0, s.count(PLANKS));
    }

    @Test
    void forEachOfVisitsOneKindEvenWhenLeftOut() {
        BuildSample s = new BuildSample();
        s.add(1, 2, 3, BRICKS);
        s.add(4, 5, 6, GLASS);
        s.add(7, 8, 9, GLASS);
        s.remove(GLASS);
        StringBuilder seen = new StringBuilder();
        s.forEachOf(GLASS, (x, y, z) -> seen.append(x).append(',').append(y).append(',').append(z).append(' '));
        assertEquals(2, seen.toString().split(" ").length);
        assertTrue(seen.indexOf("4,5,6") >= 0 && seen.indexOf("7,8,9") >= 0, seen.toString());
    }

    @Test
    void weightsAreTheCountsWhenTheyFit() {
        BuildSample s = new BuildSample();
        fill(s, BRICKS, 412, 0);
        fill(s, PLANKS, 90, 1);
        fill(s, LANTERN, 3, 2);
        assertEquals(List.of(new PaletteEntry(BRICKS, "", 412), new PaletteEntry(PLANKS, "", 90), new PaletteEntry(LANTERN, "", 3)), s.entries());
    }

    @Test
    void weightsScaleToFitAndAnAccentKeepsOne() {
        BuildSample s = new BuildSample();
        fill(s, BRICKS, 5000, 0);
        fill(s, PLANKS, 2500, 1);
        fill(s, LANTERN, 2, 2);
        List<PaletteEntry> e = s.entries();
        assertEquals(BuildSample.MAX_WEIGHT, e.get(0).weight());
        assertEquals(500, e.get(1).weight());
        assertEquals(new PaletteEntry(LANTERN, "", 1), e.get(2));
    }

    @Test
    void nothingSampledMakesNoEntries() {
        assertTrue(new BuildSample().entries().isEmpty());
    }
}
