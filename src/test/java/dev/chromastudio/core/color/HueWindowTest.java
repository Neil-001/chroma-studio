package dev.chromastudio.core.color;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class HueWindowTest {
    @Test
    void wrapKeepsTurnsInOneCircle() {
        assertEquals(0, HueWindow.wrap(1));
        assertEquals(0, HueWindow.wrap(0));
        assertEquals(0.75, HueWindow.wrap(-0.25), 1e-12);
        assertEquals(0.25, HueWindow.wrap(2.25), 1e-12);
        assertEquals(0, HueWindow.wrap(-1e-18));
    }

    @Test
    void theFullCirclePlacesEveryHueOnceBeforeTheRightEdge() {
        for (double start = -1; start <= 2; start += 0.0137) {
            HueWindow w = new HueWindow(start, 1);
            for (int d = 0; d <= 360; d++) {
                double p = w.position(d / 360.0);
                assertTrue(p >= 0 && p < 1, start + " " + d + " " + p);
                assertTrue(w.contains(d / 360.0));
            }
        }
    }

    @Test
    void zeroAndThreeSixtyAreTheSamePlace() {
        HueWindow w = new HueWindow(0.9, 0.25);
        assertEquals(w.position(0), w.position(1), 1e-12);
        assertEquals(w.position(0), w.position(360 / 360.0), 1e-12);
    }

    @Test
    void aWindowCannotSpanMoreThanOneCircle() {
        assertEquals(1, new HueWindow(0.3, 4).width());
        assertEquals(1, HueWindow.around(0.5, 2).width());
    }

    @Test
    void aZoomedWindowAcrossTheSeamShowsBothSides() {
        HueWindow w = HueWindow.around(0, 1 / 6.0);
        assertEquals(11 / 12.0, w.start(), 1e-12);
        assertEquals(1 / 12.0, w.end(), 1e-12);
        assertTrue(w.contains(350 / 360.0));
        assertTrue(w.contains(10 / 360.0));
        assertFalse(w.contains(0.5));
        assertTrue(w.position(350 / 360.0) < w.position(0) && w.position(0) < w.position(10 / 360.0));
        assertEquals(0.5, w.position(0), 1e-12);
    }
}
