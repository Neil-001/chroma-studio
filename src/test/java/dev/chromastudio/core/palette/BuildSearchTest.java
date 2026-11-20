package dev.chromastudio.core.palette;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

class BuildSearchTest {
    private static final int GRASS = 1, STONE = 2;

    /** A wall of stone 20 long and 8 high on a flat field of grass, which is what a gradient must not spill into. */
    private static int world(int x, int y, int z) {
        if (y == 0 && Math.abs(x) <= 40 && Math.abs(z) <= 40) return GRASS;
        if (y >= 1 && y <= 8 && x >= 0 && x < 20 && z == 0) return STONE;
        return 0;
    }

    /** What the gradient tool counts as the build: blocks like the first, within reach of the line between its ends. */
    private static BuildSearch.Found build(GradientLine line, int x, int y, int z, int reach, int cap) {
        int first = world(x, y, z);
        return BuildSearch.from(x, y, z, (bx, by, bz) -> world(bx, by, bz) == first && line.reaches(bx, by, bz, reach), cap);
    }

    private static Set<Integer> blocksIn(BuildSearch.Found found) {
        Set<Integer> kinds = new HashSet<>();
        for (long p : found.blocks()) kinds.add(world(BuildSearch.x(p), BuildSearch.y(p), BuildSearch.z(p)));
        return kinds;
    }

    @Test
    void aGradientAlongAWallLeavesTheGroundAlone() {
        GradientLine line = new GradientLine(0, 4, 0, 19, 4, 0);
        BuildSearch.Found found = build(line, 0, 4, 0, 32, 100_000);
        assertEquals(20 * 8, found.blocks().length);
        assertEquals(Set.of(STONE), blocksIn(found));
        assertFalse(found.capped());
    }

    @Test
    void aDiagonalGradientCoversTheWholeWallAndNoGround() {
        GradientLine line = new GradientLine(0, 1, 0, 19, 8, 0);
        BuildSearch.Found found = build(line, 0, 1, 0, 32, 100_000);
        assertEquals(20 * 8, found.blocks().length);
        assertEquals(Set.of(STONE), blocksIn(found));
    }

    @Test
    void theEndsAndReachBoundTheBuild() {
        // From x 5 to 14 along the wall's middle, at most two blocks from it: rows 2 to 6 of ten columns.
        GradientLine line = new GradientLine(5, 4, 0, 14, 4, 0);
        BuildSearch.Found found = build(line, 5, 4, 0, 2, 100_000);
        assertEquals(10 * 5, found.blocks().length);
        for (long p : found.blocks()) assertTrue(line.reaches(BuildSearch.x(p), BuildSearch.y(p), BuildSearch.z(p), 2));
    }

    @Test
    void theCapStopsTheSearchAndSaysSo() {
        BuildSearch.Found found = BuildSearch.from(0, 0, 0, (x, y, z) -> world(x, y, z) == GRASS, 500);
        assertEquals(500, found.blocks().length);
        assertTrue(found.capped());
        for (long p : found.blocks()) assertEquals(GRASS, world(BuildSearch.x(p), BuildSearch.y(p), BuildSearch.z(p)));
    }

    @Test
    void aBuildOfExactlyTheCapIsNotCapped() {
        BuildSearch.Found found = BuildSearch.from(0, 1, 0, (x, y, z) -> world(x, y, z) == STONE, 160);
        assertEquals(160, found.blocks().length);
        assertFalse(found.capped());
    }

    @Test
    void blocksTouchingAtACornerAreJoined() {
        // A staircase of single blocks, each one up and one along from the last.
        BuildSearch.Found found = BuildSearch.from(0, 0, 0, (x, y, z) -> x == y && z == 0 && x >= 0 && x < 10, 100);
        assertEquals(10, found.blocks().length);
    }

    @Test
    void aFirstBlockOutsideTheBuildFindsNothing() {
        assertEquals(0, BuildSearch.from(0, 50, 0, (x, y, z) -> world(x, y, z) == STONE, 100).blocks().length);
    }

    @Test
    void positionsPackAndUnpack() {
        int[][] cases = {{0, 0, 0}, {-1, -64, -1}, {30_000_000, 319, -30_000_000}, {-123, 2047, 456}, {5, -2048, 7}};
        for (int[] c : cases) {
            long p = BuildSearch.pack(c[0], c[1], c[2]);
            assertEquals(c[0], BuildSearch.x(p));
            assertEquals(c[1], BuildSearch.y(p));
            assertEquals(c[2], BuildSearch.z(p));
        }
    }

    /**
     * How long the search takes over a build as big as the cap, with a test that costs nothing, so only the search
     * itself is timed. Off by default, as timings depend on the machine; run it with {@code -Dchroma.timing=true}.
     */
    @Test
    @EnabledIfSystemProperty(named = "chroma.timing", matches = "true")
    void aSearchUpToTheCapTakesAFewMilliseconds() {
        // A solid box of 50 by 40 by 50, exactly 100,000 blocks, and the same box searched with a cap it overruns.
        BuildSearch.Member box = (x, y, z) -> x >= 0 && x < 50 && y >= 0 && y < 40 && z >= 0 && z < 50;
        for (int cap : new int[] {100_000, 50_000}) {
            double ms = Double.POSITIVE_INFINITY;
            for (int run = 0; run < 8; run++) {
                long start = System.nanoTime();
                BuildSearch.Found found = BuildSearch.from(0, 0, 0, box, cap);
                ms = Math.min(ms, (System.nanoTime() - start) / 1e6);
                assertEquals(cap, found.blocks().length);
                assertEquals(cap < 100_000, found.capped());
            }
            System.out.printf("build search: %d blocks: %.1f ms%n", cap, ms);
            assertTrue(ms < 15, cap + " blocks took " + ms + " ms");
        }
    }
}
