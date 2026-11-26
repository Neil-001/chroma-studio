package dev.chromastudio.client.action;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

class ScreenPickerTest {
    private static final int W = 20, H = 14, SIDE = ScreenPicker.SIDE, LOUPE = ScreenPicker.LOUPE;

    @Test
    void theLoupeCentreIsTheThreeByThreeUnderThePointerEvenAtTheEdges() {
        int[] screen = new int[W * H];
        for (int i = 0; i < screen.length; i++) screen[i] = i;
        for (int pickX = 0; pickX <= W - SIDE; pickX++)
            for (int pickY = 0; pickY <= H - SIDE; pickY++) {
                int lx = ScreenPicker.loupeStart(pickX, W), ly = ScreenPicker.loupeStart(pickY, H);
                int[] loupe = new int[LOUPE * LOUPE];
                for (int i = 0; i < loupe.length; i++) loupe[i] = screen[lx + i % LOUPE + (ly + i / LOUPE) * W];
                int[] picked = new int[SIDE * SIDE];
                for (int i = 0; i < picked.length; i++) picked[i] = screen[pickX + i % SIDE + (pickY + i / SIDE) * W];
                int at = pickX - lx + (pickY - ly) * LOUPE;
                assertArrayEquals(picked, ScreenPicker.centre(loupe, at), "pointer at " + pickX + ", " + pickY);
            }
    }

    @Test
    void theReticleNeverDrawsWhereTheLoupeReadsEvenAtTheEdges() {
        for (int guiScale = 1; guiScale <= 4; guiScale++) {
            for (int width : new int[] {854 * guiScale / 2, 1366}) {
                int height = width * 9 / 16, guiW = (width + guiScale - 1) / guiScale, guiH = (height + guiScale - 1) / guiScale;
                double scale = (double) width / guiW;
                for (int x = 0; x < guiW; x++)
                    for (int y = 0; y < guiH; y += x % 7 == 0 ? 1 : 37) {
                        int lx = ScreenPicker.loupeStart(ScreenPicker.pick(x, scale, width), width);
                        int ly = ScreenPicker.loupeStart(ScreenPicker.pick(y, scale, height), height);
                        List<int[]> bars = ScreenPicker.reticle(x, y, scale, guiScale, width, height);
                        for (int[] bar : bars) {
                            int[] shadow = {bar[0] - 1, bar[1] - 1, bar[2] + 1, bar[3] + 1};
                            boolean apart = shadow[2] * guiScale <= lx || shadow[0] * guiScale >= lx + LOUPE
                                    || shadow[3] * guiScale <= ly || shadow[1] * guiScale >= ly + LOUPE;
                            assertTrue(apart, "scale " + guiScale + ", width " + width + ", pointer at " + x + ", " + y);
                        }
                        if (x > 20 && x < guiW - 20 && y > 20 && y < guiH - 20)
                            assertEquals(4, bars.size(), "all arms show away from the edges at scale " + guiScale + ", width " + width);
                    }
            }
        }
    }

    @Test
    void aPreviewThatWouldReadLastFramesReticleIsSkipped() {
        int width = 854, height = 480;
        List<int[]> drawn = ScreenPicker.reticle(200, 100, 1, 1, width, height).stream()
                .map(b -> ScreenPicker.physical(new int[] {b[0] - 1, b[1] - 1, b[2] + 1, b[3] + 1}, 1)).toList();
        assertTrue(ScreenPicker.touches(drawn, 209, 100, 1, width, height), "the pointer moved onto the old right arm");
        assertFalse(ScreenPicker.touches(drawn, 200, 100, 1, width, height), "a still pointer reads clear of its own reticle");
    }

    @Test
    void aGuiScaleChangeStillSkipsTheOldReticle() {
        List<int[]> drawn = ScreenPicker.reticle(200, 100, 3, 3, 960, 720).stream()
                .map(b -> ScreenPicker.physical(new int[] {b[0] - 1, b[1] - 1, b[2] + 1, b[3] + 1}, 3)).toList();
        assertTrue(ScreenPicker.touches(drawn, 304, 150, 2, 940, 720), "the same framebuffer point, now at GUI scale 2, reads the old right arm");
    }
}
