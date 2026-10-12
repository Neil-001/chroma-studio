package dev.chromastudio.core.color;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class ViewingTest {
    @Test
    void theTableAt1080pAnd70Degrees() {
        assertEquals(Scale.T16, Viewing.scaleFor(12, 1080, 70));
        assertEquals(Scale.T8, Viewing.scaleFor(24, 1080, 70));
        assertEquals(Scale.T4, Viewing.scaleFor(48, 1080, 70));
        assertEquals(Scale.T2, Viewing.scaleFor(96, 1080, 70));
        assertEquals(Scale.T1, Viewing.scaleFor(200, 1080, 70));
        assertEquals(Scale.T1, Viewing.scaleFor(Viewing.FAR, 1080, 70));
        assertEquals(Scale.T16, Viewing.scaleFor(1, 1080, 70));
        assertEquals(Scale.T16, Viewing.scaleFor(0, 1080, 70));
    }

    @Test
    void marksAreWhereEachScaleEnds() {
        double[] marks = Viewing.blockMarks(1080, 70);
        assertEquals(4, marks.length);
        // 1080 / (2 · tan 35° · 4 · 16) = 1080 / 89.627 = 12.05, and each coarser scale reaches twice as far.
        assertEquals(12.05, marks[0], 0.005);
        assertEquals(24.10, marks[1], 0.01);
        assertEquals(48.20, marks[2], 0.02);
        assertEquals(96.40, marks[3], 0.04);
        Scale[] scales = Scale.values();
        for (int i = 0; i < marks.length; i++) {
            assertEquals(scales[i], Viewing.scaleFor(marks[i] * 0.999, 1080, 70));
            assertEquals(scales[i + 1], Viewing.scaleFor(marks[i] * 1.001, 1080, 70));
        }
    }

    @Test
    void aTallerWindowOrNarrowerViewResolvesMore() {
        // Twice the pixels show the same detail from twice as far.
        assertEquals(Scale.T16, Viewing.scaleFor(24, 2160, 70));
        assertEquals(Viewing.blockMarks(1080, 70)[0] * 2, Viewing.blockMarks(2160, 70)[0], 1e-9);
        // Zoomed in to 30°, 24 blocks is as good as 12 at 70°. At 110° a block at 24 is
        // 1080 / (2 · 24 · tan 55°) = 15.8 pixels high, under four texels' worth.
        assertEquals(Scale.T16, Viewing.scaleFor(24, 1080, 30));
        assertEquals(Scale.T2, Viewing.scaleFor(24, 1080, 110));
    }
}
