package dev.chromastudio.core.palette;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.chromastudio.core.palette.GradientLine.Blend;
import org.junit.jupiter.api.Test;

class GradientLineTest {
    @Test
    void bandsShareTheBlocksFromEndToEndEqually() {
        // 32 blocks from y 0 to y 31 and 8 steps: four blocks a step, the ends included.
        GradientLine line = new GradientLine(0, 0, 0, 0, 31, 0);
        for (int y = 0; y < 32; y++) assertEquals(y / 4, line.step(0, y, 0, 8, Blend.BANDS), "y " + y);
    }

    @Test
    void beyondTheEndsTheEndStepsHold() {
        GradientLine line = new GradientLine(0, 0, 0, 0, 31, 0);
        assertEquals(0, line.step(0, -5, 0, 8, Blend.MIXED));
        assertEquals(7, line.step(0, 40, 0, 8, Blend.MIXED));
    }

    @Test
    void aPlaneAcrossTheLineHasOneStep() {
        // A diagonal line: blocks off it take the step of where they meet it square on.
        GradientLine line = new GradientLine(0, 0, 0, 20, 20, 0);
        for (int i = -5; i <= 5; i++) assertEquals(line.step(10, 10, 0, 6, Blend.BANDS), line.step(10 + i, 10 - i, 3, 6, Blend.BANDS));
    }

    @Test
    void stepsNeverGoBackwardsAlongTheLine() {
        GradientLine line = new GradientLine(3, -2, 7, 40, 30, -9);
        int last = 0;
        for (int i = 0; i <= 100; i++) {
            int x = 3 + 37 * i / 100, y = -2 + 32 * i / 100, z = 7 - 16 * i / 100;
            int step = line.step(x, y, z, 12, Blend.BANDS);
            assertTrue(step >= last);
            last = step;
        }
        assertEquals(11, last);
    }

    @Test
    void mixingTakesOnlyANeighbourAndLeavesTheMiddleOfEachBandPure() {
        GradientLine line = new GradientLine(0, 0, 0, 0, 79, 0);
        // Ten blocks a step: the middle five of each band are pure, and the rest are the band or the next one over.
        for (int y = 0; y < 80; y++) {
            int band = y / 10;
            for (int x = 0; x < 50; x++) {
                int step = line.step(x, y, x * 7, 8, Blend.MIXED);
                if (y % 10 >= 3 && y % 10 <= 6) assertEquals(band, step, "y " + y);
                else assertTrue(Math.abs(step - band) <= 1, "y " + y);
            }
        }
    }

    @Test
    void mixingIsHalfAndHalfAtABandsEdge() {
        GradientLine line = new GradientLine(0, 0, 0, 0, 79, 0);
        int upper = 0, tries = 0;
        for (int x = 0; x < 100; x++) {
            for (int z = 0; z < 100; z++) {
                // Blocks 9 and 10 sit either side of the edge between steps 0 and 1.
                for (int y = 9; y <= 10; y++, tries++) upper += line.step(x, y, z, 8, Blend.MIXED);
            }
        }
        assertEquals(0.5, (double) upper / tries, 0.02);
    }

    @Test
    void theSameBlockAlwaysGetsTheSameStep() {
        GradientLine a = new GradientLine(0, 0, 0, 10, 30, 5), b = new GradientLine(0, 0, 0, 10, 30, 5);
        for (int y = 0; y < 30; y++) assertEquals(a.step(4, y, 2, 9, Blend.MIXED), b.step(4, y, 2, 9, Blend.MIXED));
    }

    @Test
    void reachIsBetweenTheEndsAndCloseToTheLine() {
        GradientLine line = new GradientLine(0, 0, 0, 10, 0, 0);
        assertTrue(line.reaches(0, 0, 0, 0));
        assertTrue(line.reaches(10, 3, 0, 3));
        assertFalse(line.reaches(10, 4, 0, 3));
        assertFalse(line.reaches(-1, 0, 0, 3));
        assertFalse(line.reaches(11, 0, 0, 3));
        assertEquals(5, line.distance(4, 3, 4), 1e-9);
    }

    @Test
    void aLineShorterThanTheGradientStillEndsOnItsFirstAndLastSteps() {
        GradientLine line = new GradientLine(0, 0, 0, 0, 3, 0);
        for (Blend blend : Blend.values()) {
            assertEquals(0, line.step(0, 0, 0, 32, blend));
            assertEquals(10, line.step(0, 1, 0, 32, blend));
            assertEquals(21, line.step(0, 2, 0, 32, blend));
            assertEquals(31, line.step(0, 3, 0, 32, blend));
        }
    }

    @Test
    void aDiagonalLineReachesNothingBehindItsEnds() {
        GradientLine line = new GradientLine(0, 0, 0, 4, 1, 0);
        assertFalse(line.reaches(0, -1, 0, 2));
        assertFalse(line.reaches(4, 2, 0, 2));
        assertTrue(line.reaches(0, 0, 0, 2));
        assertTrue(line.reaches(4, 1, 0, 2));
    }

    @Test
    void twoEndsMustDiffer() {
        assertThrows(IllegalArgumentException.class, () -> new GradientLine(1, 2, 3, 1, 2, 3));
    }
}
