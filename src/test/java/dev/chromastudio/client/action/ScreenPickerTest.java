package dev.chromastudio.client.action;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

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
        for (int scale = 1; scale <= 4; scale++) {
            int guiW = 427, guiH = 240, width = guiW * scale, height = guiH * scale;
            for (int x = 0; x < guiW; x++)
                for (int y = 0; y < guiH; y += x % 7 == 0 ? 1 : 37) {
                    int lx = ScreenPicker.loupeStart(ScreenPicker.pick(x, scale, width), width);
                    int ly = ScreenPicker.loupeStart(ScreenPicker.pick(y, scale, height), height);
                    for (int[] bar : ScreenPicker.reticle(x, y, scale, width, height)) {
                        boolean apart = (bar[2] + 1) * scale <= lx || (bar[0] - 1) * scale >= lx + LOUPE
                                || (bar[3] + 1) * scale <= ly || (bar[1] - 1) * scale >= ly + LOUPE;
                        assertTrue(apart, "scale " + scale + ", pointer at " + x + ", " + y);
                    }
                    if (x > 20 && x < guiW - 20 && y > 20 && y < guiH - 20)
                        assertEquals(4, ScreenPicker.reticle(x, y, scale, width, height).size(), "all arms show away from the edges");
                }
        }
    }
}
