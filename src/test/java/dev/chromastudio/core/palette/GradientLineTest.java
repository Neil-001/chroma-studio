package dev.chromastudio.core.palette;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class GradientLineTest {
    @Test
    void bandsShareTheBlocksFromEndToEndEqually() {
        GradientLine line = new GradientLine(0, 0, 0, 0, 31, 0);
        for (int y = 0; y < 32; y++) assertEquals(y / 4, line.step(0, y, 0, 8, 0), "y " + y);
    }

    @Test
    void beyondTheEndsTheEndStepsHold() {
        GradientLine line = new GradientLine(0, 0, 0, 0, 31, 0);
        assertEquals(0, line.step(0, -5, 0, 8, 1));
        assertEquals(7, line.step(0, 40, 0, 8, 1));
    }

    @Test
    void aPlaneAcrossTheLineHasOneStep() {
        GradientLine line = new GradientLine(0, 0, 0, 20, 20, 0);
        for (int i = -5; i <= 5; i++) assertEquals(line.step(10, 10, 0, 6, 0), line.step(10 + i, 10 - i, 3, 6, 0));
    }

    @Test
    void stepsNeverGoBackwardsAlongTheLine() {
        GradientLine line = new GradientLine(3, -2, 7, 40, 30, -9);
        int last = 0;
        for (int i = 0; i <= 100; i++) {
            int x = 3 + 37 * i / 100, y = -2 + 32 * i / 100, z = 7 - 16 * i / 100;
            int step = line.step(x, y, z, 12, 0);
            assertTrue(step >= last);
            last = step;
        }
        assertEquals(11, last);
    }

    @Test
    void noBlendIsHardBands() {
        GradientLine line = new GradientLine(2, -7, 5, 31, 12, -20);
        for (int x = 0; x < 40; x++) {
            for (int y = -10; y < 20; y++) {
                int z = (x * 7 + y * 3) % 30 - 20;
                double along = ((x - 2) * 29 + (y + 7) * 19 + (z - 5) * -25) / Math.sqrt(29 * 29 + 19 * 19 + 25 * 25);
                int band = (int) Math.clamp(Math.floor((along + 0.5) / (Math.sqrt(29 * 29 + 19 * 19 + 25 * 25) + 1) * 9), 0, 8);
                assertEquals(band, line.step(x, y, z, 9, 0), x + " " + y + " " + z);
            }
        }
    }

    @Test
    void mixingTakesOnlyANeighbourAndOnlyInsideTheZone() {
        GradientLine line = new GradientLine(0, 0, 0, 0, 79, 0);
        for (double w : new double[] {0.2, 0.5, 0.75, 1}) {
            for (int y = 0; y < 80; y++) {
                int band = y / 10;
                double fromEdge = Math.min(y % 10 + 0.5, 10 - (y % 10 + 0.5));
                for (int x = -40; x < 40; x++) {
                    int step = line.step(x, y, x * 7, 8, w);
                    if (fromEdge > w * 5) assertEquals(band, step, "w " + w + " y " + y);
                    else assertTrue(Math.abs(step - band) <= 1, "w " + w + " y " + y);
                }
            }
        }
    }

    @Test
    void theMixRisesEvenlyAcrossTheZone() {
        GradientLine line = new GradientLine(0, 0, 0, 0, 79, 0);
        for (double w : new double[] {0.5, 1}) {
            for (int y = 0; y < 20; y++) {
                int upper = 0, tries = 0;
                for (int x = -32; x < 32; x++) {
                    for (int z = -32; z < 32; z++, tries++) upper += line.step(x, y, z, 8, w) >= 1 ? 1 : 0;
                }
                double u = (y + 0.5) / 10;
                double expected = Math.clamp((u - 1) / w + 0.5, 0, 1);
                assertEquals(expected, (double) upper / tries, 0.02, "w " + w + " y " + y);
            }
        }
    }

    @Test
    void aWholeStepBlendLeavesOnlyEachStepsMiddlePure() {
        GradientLine line = new GradientLine(0, 0, 0, 79, 0, 0);
        int pure = 0;
        for (int x = 0; x < 80; x++) {
            boolean mixed = false;
            for (int y = -20; y < 20; y++) for (int z = -20; z < 20; z++) mixed |= line.step(x, y, z, 8, 1) != x / 10;
            if (!mixed) pure++;
        }
        assertTrue(pure <= 10, "pure " + pure);
        for (int x = 0; x < 80; x++) assertEquals(line.step(x, 3, -4, 8, 1), line.step(x, 3, -4, 8, 5));
    }

    @Test
    void neighboursAreSpreadEvenlyNotClumped() {
        for (int axis = 0; axis < 3; axis++) {
            int same = 0, pairs = 0;
            for (int a = -40; a < 40; a++) {
                for (int b = -40; b < 40; b++) {
                    boolean here = cut(axis, a, b), right = cut(axis, a + 1, b), up = cut(axis, a, b + 1);
                    same += (here == right ? 1 : 0) + (here == up ? 1 : 0);
                    pairs += 2;
                }
            }
            assertTrue((double) same / pairs < 0.45, "axis " + axis + ": " + (double) same / pairs);
        }
    }

    private static boolean cut(int axis, int a, int b) {
        return switch (axis) {
            case 0 -> GradientLine.dither(5, a, b) >= 0.5;
            case 1 -> GradientLine.dither(a, 5, b) >= 0.5;
            default -> GradientLine.dither(a, b, 5) >= 0.5;
        };
    }

    @Test
    void theDitherIsTheSameEveryTimeAndCoversEveryShareOnce() {
        GradientLine a = new GradientLine(0, 0, 0, 10, 30, 5), b = new GradientLine(0, 0, 0, 10, 30, 5);
        for (int y = -30; y < 30; y++) assertEquals(a.step(4, y, -2, 9, 0.75), b.step(4, y, -2, 9, 0.75));
        boolean[] seen = new boolean[4096];
        for (int x = 0; x < 16; x++) {
            for (int y = 0; y < 16; y++) {
                for (int z = 0; z < 16; z++) {
                    int i = (int) (GradientLine.dither(x - 32, y + 16, z) * 4096);
                    assertFalse(seen[i]);
                    seen[i] = true;
                }
            }
        }
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
        assertEquals(0, line.step(0, 0, 0, 32, 0));
        assertEquals(10, line.step(0, 1, 0, 32, 0));
        assertEquals(21, line.step(0, 2, 0, 32, 0));
        assertEquals(31, line.step(0, 3, 0, 32, 0));
        for (int x = -20; x < 20; x++) {
            for (int z = -20; z < 20; z++) {
                assertEquals(0, line.step(x, 0, z, 32, 1));
                assertEquals(31, line.step(x, 3, z, 32, 1));
            }
        }
    }

    @Test
    void aLineShorterThanTheGradientStillBlends() {
        GradientLine line = new GradientLine(0, 0, 0, 0, 6, 0);
        int changed = 0;
        for (int y = 0; y <= 6; y++) {
            for (int x = 0; x < 33; x++) {
                int hard = line.step(x, y, 0, 8, 0), mixed = line.step(x, y, 0, 8, 1);
                assertTrue(Math.abs(hard - mixed) <= 1);
                if (hard != mixed) changed++;
            }
        }
        assertTrue(changed > 33, "changed " + changed);
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
    void aBlockExactlyAtTheReachCountsWhicheverWayTheLineRuns() {
        assertTrue(new GradientLine(0, 0, 0, 5, 12, 0).reaches(-2, 3, 0, 3));
        assertTrue(new GradientLine(5, 12, 0, 0, 0, 0).reaches(-2, 3, 0, 3));
    }

    @Test
    void endsClickedInDifferentColumnsOfATowerGiveLevelBands() {
        GradientLine line = GradientLine.between(0, 0, 0, 2, 30, 1);
        assertTrue(line.straightened());
        for (int x = -2; x <= 4; x++) {
            for (int z = -2; z <= 4; z++) {
                assertTrue(line.reaches(x, 0, z, 8), x + " 0 " + z);
                assertTrue(line.reaches(x, 30, z, 8), x + " 30 " + z);
                assertEquals(line.step(0, 15, 0, 8, 0), line.step(x, 15, z, 8, 0));
            }
        }
        assertFalse(line.reaches(0, 31, 0, 8));
        assertFalse(line.reaches(0, -1, 0, 8));
    }

    @Test
    void aDiagonalThePlayerMeantIsKept() {
        GradientLine wall = GradientLine.between(0, 0, 0, 20, 8, 0);
        assertFalse(wall.straightened());
        assertTrue(wall.slanted());
        assertFalse(GradientLine.between(0, 0, 0, 20, 20, 0).straightened());
        GradientLine tower = GradientLine.between(0, 0, 0, 4, 15, 0);
        assertTrue(tower.straightened());
        assertFalse(tower.slanted());
        GradientLine along = GradientLine.between(0, 0, 0, 0, 20, 0);
        assertFalse(along.straightened());
        assertFalse(along.slanted());
    }

    @Test
    void swappedEndsCoverTheSameLayersRunningTheOtherWay() {
        GradientLine up = GradientLine.between(0, 0, 0, 2, 30, 1), down = GradientLine.between(2, 30, 1, 0, 0, 0);
        assertTrue(down.straightened());
        for (int y = -1; y <= 31; y++) assertEquals(up.reaches(1, y, 1, 8), down.reaches(1, y, 1, 8), "y " + y);
        assertEquals(0, up.step(1, 0, 1, 8, 0));
        assertEquals(7, down.step(1, 0, 1, 8, 0));
    }

    @Test
    void twoEndsMustDiffer() {
        assertThrows(IllegalArgumentException.class, () -> new GradientLine(1, 2, 3, 1, 2, 3));
    }
}
