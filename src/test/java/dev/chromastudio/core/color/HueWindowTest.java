package dev.chromastudio.core.color;

import static org.junit.jupiter.api.Assertions.assertEquals;
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
                double p = w.offset(d / 360.0);
                assertTrue(p >= 0 && p < 1, start + " " + d + " " + p);
            }
        }
    }

    @Test
    void zeroAndThreeSixtyAreTheSamePlace() {
        HueWindow w = new HueWindow(0.9, 0.25);
        assertEquals(w.offset(0), w.offset(1), 1e-12);
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
        assertTrue(w.offset(350 / 360.0) < w.offset(0) && w.offset(0) < w.offset(10 / 360.0));
        assertTrue(w.offset(10 / 360.0) < w.width());
        assertTrue(w.offset(0.5) > w.width());
        assertEquals(0.5, w.offset(0) / w.width(), 1e-12);
    }
}
