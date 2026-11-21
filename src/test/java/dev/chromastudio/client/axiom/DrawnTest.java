package dev.chromastudio.client.axiom;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class DrawnTest {
    @Test
    void aToolDrawnEveryFrameIsKeptHoweverLongTheFramesTake() throws Exception {
        Drawn d = new Drawn();
        d.start(10);
        assertTrue(d.draw(10), "drawn in the frame it started in");
        assertTrue(d.draw(11));
        Thread.sleep(300);
        assertFalse(d.missed(11));
        assertFalse(d.missed(12), "the next frame may still draw it");
        assertTrue(d.draw(12));
    }

    @Test
    void aFrameWithoutTheToolIsNoticed() {
        Drawn d = new Drawn();
        d.start(10);
        assertTrue(d.missed(12), "between frames, a whole frame went by without it");
        Drawn back = new Drawn();
        back.start(10);
        assertFalse(back.draw(12), "drawn again after a frame without it, as after picking another tool and back");
    }

    @Test
    void nothingIsMissedOnceStopped() {
        Drawn d = new Drawn();
        d.start(10);
        d.stop();
        assertFalse(d.missed(100));
    }
}
