package dev.chromastudio.client.action;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;

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
}
