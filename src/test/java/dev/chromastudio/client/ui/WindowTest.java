package dev.chromastudio.client.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.chromastudio.client.ModSettings;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class WindowTest {
    private static final int SW = 640, SH = 360;

    @AfterEach
    void restore() { ModSettings.reset(); }

    @Test
    void aFreshWindowIsCentredAtItsSmallestSize() {
        Window w = new Window();
        assertEquals(new Bounds(120, 70, 400, 220), w.frame(SW, SH));
        assertEquals(new Bounds(121, 86, 398, 203), w.content(SW, SH), "under the title bar and inside the border");
    }

    @Test
    void aSmallScreenForcesFullscreen() {
        Window w = new Window();
        assertFalse(w.fullscreen(427, 240));
        assertTrue(w.fullscreen(426, 240), "1280 by 720 at GUI scale 3");
        assertTrue(w.fullscreen(427, 239));
        assertEquals(new Bounds(0, 0, 426, 240), w.frame(426, 240));
        assertEquals(new Bounds(0, 16, 426, 224), w.content(426, 240));
        assertEquals(Window.Grip.NONE, w.gripAt(200, 5, 426, 240), "nothing to drag in fullscreen");
    }

    @Test
    void theTitleBarMovesItAndTheScreenKeepsItIn() {
        Window w = new Window();
        assertEquals(Window.Grip.MOVE, w.gripAt(300, 78, SW, SH));
        w.grab(Window.Grip.MOVE, 300, 78, SW, SH);
        assertTrue(w.dragTo(310, 98, SW, SH));
        assertEquals(new Bounds(130, 90, 400, 220), w.frame(SW, SH));
        assertFalse(w.dragTo(310, 98, SW, SH), "the same place again changes nothing");
        w.dragTo(-500, 900, SW, SH);
        assertEquals(new Bounds(0, 140, 400, 220), w.frame(SW, SH));
        w.release();
        assertFalse(w.dragging());
    }

    @Test
    void edgesAndCornersResizeDownToTheMinimum() {
        ModSettings.windowX = 100;
        ModSettings.windowY = 50;
        ModSettings.windowW = 500;
        ModSettings.windowH = 300;
        Window w = new Window();
        assertEquals(Window.Grip.E, w.gripAt(598, 200, SW, SH));
        assertEquals(Window.Grip.S, w.gripAt(300, 348, SW, SH));
        assertEquals(Window.Grip.W, w.gripAt(101, 200, SW, SH));
        assertEquals(Window.Grip.N, w.gripAt(300, 51, SW, SH));
        assertEquals(Window.Grip.SE, w.gripAt(594, 348, SW, SH));
        assertEquals(Window.Grip.NW, w.gripAt(101, 56, SW, SH));
        assertEquals(Window.Grip.NONE, w.gripAt(300, 200, SW, SH));
        assertEquals(Window.Grip.NONE, w.gripAt(99, 200, SW, SH));

        w.grab(Window.Grip.E, 598, 200, SW, SH);
        w.dragTo(628, 200, SW, SH);
        assertEquals(new Bounds(100, 50, 530, 300), w.frame(SW, SH));
        w.dragTo(900, 200, SW, SH);
        assertEquals(new Bounds(100, 50, 540, 300), w.frame(SW, SH), "no wider than the screen allows");
        w.dragTo(0, 200, SW, SH);
        assertEquals(new Bounds(100, 50, 400, 300), w.frame(SW, SH), "no narrower than the minimum");
        w.release();

        // The top-left corner moves that corner and leaves the opposite one where it is.
        w.grab(Window.Grip.NW, 101, 51, SW, SH);
        w.dragTo(81, 41, SW, SH);
        assertEquals(new Bounds(80, 40, 420, 310), w.frame(SW, SH));
        w.dragTo(600, 340, SW, SH);
        assertEquals(new Bounds(100, 130, 400, 220), w.frame(SW, SH));
        w.release();
    }

    @Test
    void theSettingsRememberItUnlessToldNotTo() {
        Window w = new Window();
        w.grab(Window.Grip.MOVE, 300, 78, SW, SH);
        w.dragTo(310, 98, SW, SH);
        w.toggleFullscreen();
        w.store();
        assertEquals(130, ModSettings.windowX);
        assertEquals(90, ModSettings.windowY);
        assertTrue(ModSettings.fullscreen);
        assertEquals(new Bounds(0, 0, SW, SH), new Window().frame(SW, SH));

        ModSettings.rememberWindow = false;
        Window fresh = new Window();
        assertEquals(new Bounds(120, 70, 400, 220), fresh.frame(SW, SH));
        fresh.grab(Window.Grip.MOVE, 300, 78, SW, SH);
        fresh.dragTo(0, 0, SW, SH);
        fresh.store();
        assertEquals(130, ModSettings.windowX, "left as it was");
    }

    @Test
    void aWindowTooBigForTheScreenIsDrawnSmallerButKeepsItsSize() {
        ModSettings.windowX = 300;
        ModSettings.windowY = 200;
        ModSettings.windowW = 900;
        ModSettings.windowH = 500;
        Window w = new Window();
        assertEquals(new Bounds(0, 0, SW, SH), w.frame(SW, SH));
        assertEquals(new Bounds(300, 200, 900, 500), w.frame(1920, 1080));
    }
}
