package dev.chromastudio.core.color;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.Arrays;
import java.util.List;
import java.util.function.IntBinaryOperator;
import org.junit.jupiter.api.Test;

class TextureStatsTest {
    private static final int RED = 0xFFFF0000, BLUE = 0xFF0000FF, BLACK = 0xFF000000, WHITE = 0xFFFFFFFF;
    // Half of full luminance, as sRGB stores it.
    private static final int HALF = 0xFFBCBCBC;

    private static TextureStats texture(int side, IntBinaryOperator pixelAt) {
        int[] argb = new int[side * side];
        for (int y = 0; y < side; y++) {
            for (int x = 0; x < side; x++) argb[y * side + x] = pixelAt.applyAsInt(x, y);
        }
        return TextureStats.of(argb, side, side);
    }

    private static int[] filled(int count, int argb) {
        int[] out = new int[count];
        Arrays.fill(out, argb);
        return out;
    }

    @Test
    void halvesStayApartUntilTheLastScale() {
        TextureStats s = texture(16, (x, y) -> x < 8 ? RED : BLUE);
        for (Scale scale : List.of(Scale.T16, Scale.T8, Scale.T4, Scale.T2)) {
            int[] pixels = s.pixels(scale);
            assertEquals(scale.texels * scale.texels, pixels.length);
            for (int i = 0; i < pixels.length; i++) assertEquals(i % scale.texels < scale.texels / 2 ? RED : BLUE, pixels[i]);
            assertEquals(List.of(0.5, 0.5), s.distribution(scale).weights());
            assertEquals(List.of(Color.ofRgb(0x0000FF), Color.ofRgb(0xFF0000)), sortedByRgb(s.distribution(scale).colours()));
        }
        // Red and blue in equal parts are half of each in linear light.
        assertArrayEquals(new int[] {0xFFBC00BC}, s.pixels(Scale.T1));
        assertEquals(List.of(Color.ofRgb(0xBC00BC)), s.distribution(Scale.T1).colours());
        for (Scale scale : Scale.values()) assertEquals(0xBC00BC, s.mean(scale).rgb());
        assertEquals(1, s.coverage());
    }

    @Test
    void aFineCheckerBlendsAfterOneHalving() {
        TextureStats s = texture(16, (x, y) -> (x + y) % 2 == 0 ? BLACK : WHITE);
        assertEquals(2, s.distribution(Scale.T16).colours().size());
        for (Scale scale : List.of(Scale.T8, Scale.T4, Scale.T2, Scale.T1)) {
            assertArrayEquals(filled(scale.texels * scale.texels, HALF), s.pixels(scale));
            assertEquals(List.of(Color.ofRgb(0xBCBCBC)), s.distribution(scale).colours());
        }
        // Black and white are 1 apart in OKLab, so each pixel is 0.5 from the mean.
        assertEquals(0.5, s.spread(), 1e-4);
        assertEquals(Badge.BUSY, s.badge());
    }

    @Test
    void largerTexturesAreReducedToSixteenFirst() {
        TextureStats checker = texture(32, (x, y) -> (x + y) % 2 == 0 ? BLACK : WHITE);
        assertArrayEquals(filled(256, HALF), checker.pixels(Scale.T16));
        // Spread is measured after the reduction, where the checker has gone.
        assertEquals(0, checker.spread(), 1e-12);
        assertEquals(Badge.FLAT, checker.badge());

        TextureStats quarters = texture(64, (x, y) -> x < 32 == y < 32 ? RED : BLUE);
        int[] expected = new int[256];
        for (int i = 0; i < 256; i++) expected[i] = i % 16 < 8 == i / 16 < 8 ? RED : BLUE;
        assertArrayEquals(expected, quarters.pixels(Scale.T16));
        assertArrayEquals(new int[] {RED, BLUE, BLUE, RED}, quarters.pixels(Scale.T2));
    }

    @Test
    void sidesMayDiffer() {
        int[] argb = new int[32 * 16];
        for (int i = 0; i < argb.length; i++) argb[i] = i % 32 < 16 ? RED : BLUE;
        assertArrayEquals(new int[] {RED, BLUE, RED, BLUE}, TextureStats.of(argb, 32, 16).pixels(Scale.T2));
    }

    @Test
    void oddSizesAreRejected() {
        assertThrows(IllegalArgumentException.class, () -> TextureStats.of(new int[24 * 24], 24, 24));
        assertThrows(IllegalArgumentException.class, () -> TextureStats.of(new int[20 * 16], 20, 16));
        assertThrows(IllegalArgumentException.class, () -> TextureStats.of(new int[8 * 8], 8, 8));
        assertThrows(IllegalArgumentException.class, () -> TextureStats.of(new int[0], 0, 0));
        assertThrows(IllegalArgumentException.class, () -> TextureStats.of(new int[255], 16, 16));
    }

    @Test
    void holesAreLeftOutOfAveragesAndCoverage() {
        // The left half is green at alpha 15, just under the cutoff, so nothing of it may show.
        TextureStats s = texture(16, (x, y) -> x < 8 ? 0x0F00FF00 : RED);
        assertEquals(0.5, s.coverage(), 1e-12);
        assertEquals(0xFF0000, s.mean(Scale.T16).rgb());
        assertEquals(List.of(Color.ofRgb(0xFF0000)), s.distribution(Scale.T16).colours());
        assertEquals(List.of(Color.ofRgb(0xFF0000)), s.distribution(Scale.T1).colours());
        assertEquals(0, s.pixels(Scale.T16)[0]);
        assertEquals(RED, s.pixels(Scale.T16)[8]);
        assertArrayEquals(new int[] {0x80FF0000}, s.pixels(Scale.T1));
        assertEquals(0, s.spread(), 1e-12);

        // Alpha 16 is in.
        assertEquals(16 / 255.0, texture(16, (x, y) -> 0x1000FF00).coverage(), 1e-12);
        assertEquals(0x00FF00, texture(16, (x, y) -> 0x1000FF00).mean(Scale.T1).rgb());
    }

    @Test
    void averagesAreWeightedByAlpha() {
        // Solid red beside blue at a third of the alpha: 0.75 red and 0.25 blue in linear light,
        // which sRGB stores as 225 and 137.
        TextureStats s = texture(16, (x, y) -> x < 8 ? RED : 0x550000FF);
        assertEquals(0xE10089, s.mean(Scale.T1).rgb());
        assertEquals(2 / 3.0, s.coverage(), 1e-12);
        assertEquals(0.75, s.distribution(Scale.T16).weights().get(0), 1e-12);
        assertEquals(0.25, s.distribution(Scale.T16).weights().get(1), 1e-12);
    }

    @Test
    void oneFaintPixelStillGivesItsColour() {
        // Alpha 16 on one pixel of 256 is far below one 8-bit step of alpha once averaged, but it is all there is.
        TextureStats s = texture(16, (x, y) -> x == 0 && y == 0 ? 0x10FF0000 : 0);
        assertEquals(16 / 255.0 / 256, s.coverage(), 1e-15);
        for (Scale scale : Scale.values()) {
            assertEquals(0xFF0000, s.mean(scale).rgb());
            assertEquals(List.of(Color.ofRgb(0xFF0000)), s.distribution(scale).colours());
        }
        assertEquals(0x10FF0000, s.pixels(Scale.T16)[0]);
        // The thumbnail has no alpha left to show it with.
        assertEquals(0, s.pixels(Scale.T1)[0] >>> 24);
        assertEquals(0, s.spread());
        assertEquals(List.of(Color.ofRgb(0x800000)), s.tinted(0x808080).distribution(Scale.T1).colours());
        assertEquals(0x800000, s.tinted(0x808080).mean(Scale.T1).rgb());
    }

    @Test
    void nothingOpaqueGivesEmptyStats() {
        TextureStats s = texture(16, (x, y) -> 0x00FFFFFF);
        assertEquals(0, s.coverage());
        assertEquals(0, s.spread());
        assertEquals(0x000000, s.mean(Scale.T16).rgb());
        assertArrayEquals(new int[256], s.pixels(Scale.T16));
        assertEquals(List.of(Color.ofRgb(0)), s.distribution(Scale.T8).colours());
    }

    @Test
    void theDistributionAtEachScaleIsOfItsPixels() {
        TextureStats s = texture(16, (x, y) -> 0xFF000000 | (x * 16) << 16 | (y * 16) << 8 | (x * y) & 0xFF);
        for (Scale scale : Scale.values()) {
            Distribution direct = Distribution.ofPixels(s.pixels(scale), 16);
            assertEquals(direct.colours(), s.distribution(scale).colours());
            assertEquals(direct.weights(), s.distribution(scale).weights());
        }
    }

    @Test
    void pixelsAreACopy() {
        TextureStats s = texture(16, (x, y) -> RED);
        s.pixels(Scale.T16)[0] = BLUE;
        assertEquals(RED, s.pixels(Scale.T16)[0]);
    }

    @Test
    void tintReachesEveryScale() {
        TextureStats plain = texture(16, (x, y) -> (x + y) % 2 == 0 ? BLACK : WHITE);
        TextureStats s = plain.tinted(0x80C040);
        for (int i = 0; i < 256; i++) assertEquals(i / 16 % 2 == i % 2 ? BLACK : 0xFF80C040, s.pixels(Scale.T16)[i]);
        // 188 · 128 / 255 = 94, 188 · 192 / 255 = 142, 188 · 64 / 255 = 47.
        for (Scale scale : List.of(Scale.T8, Scale.T4, Scale.T2, Scale.T1)) {
            assertArrayEquals(filled(scale.texels * scale.texels, 0xFF5E8E2F), s.pixels(scale));
            assertEquals(List.of(Color.ofRgb(0x5E8E2F)), s.distribution(scale).colours());
        }
        assertEquals(List.of(Color.ofRgb(0x000000), Color.ofRgb(0x80C040)), sortedByRgb(s.distribution(Scale.T16).colours()));
        assertEquals(0x5E8E2F, s.mean(Scale.T16).rgb());
        assertEquals(1, s.coverage());
        // Half the pixels black and half the tint, so each is half their distance from the mean.
        assertEquals(Metric.OKLAB.distance(Color.ofRgb(0), Color.ofRgb(0x80C040)) / 2, s.spread(), 1e-9);
        // The original is untouched.
        assertEquals(0.5, plain.spread(), 1e-4);
        assertEquals(WHITE, plain.pixels(Scale.T16)[1]);
    }

    private static List<Color> sortedByRgb(List<Color> colours) {
        return colours.stream().sorted((a, b) -> Integer.compare(a.rgb(), b.rgb())).toList();
    }

    @Test
    void aGreyTintHalvesSpread() {
        // Tinting white by mid grey leaves black against #808080, whose OKLab lightness is 0.5999.
        TextureStats s = texture(16, (x, y) -> (x + y) % 2 == 0 ? BLACK : WHITE).tinted(0x808080);
        assertEquals(0.5999 / 2, s.spread(), 1e-4);
    }

    /** Two greys in a fine checker are half their OKLab distance from the mean, which puts each texture well inside its band. */
    @Test
    void badgeBands() {
        // One level apart: spread about 0.002.
        assertEquals(Badge.FLAT, texture(16, (x, y) -> (x + y) % 2 == 0 ? 0xFF808080 : 0xFF818181).badge());
        assertEquals(Badge.FLAT, texture(16, (x, y) -> 0xFF5EA919).badge());
        // 0x70 and 0x90: lightness 0.545 and 0.653, spread about 0.054.
        TextureStats grainy = texture(16, (x, y) -> (x + y) % 2 == 0 ? 0xFF707070 : 0xFF909090);
        assertEquals(0.054, grainy.spread(), 0.002);
        assertEquals(Badge.GRAINY, grainy.badge());
        // 0x30 and 0xD0: lightness 0.309 and 0.857, spread about 0.27.
        TextureStats busy = texture(16, (x, y) -> (x + y) % 2 == 0 ? 0xFF303030 : 0xFFD0D0D0);
        assertEquals(0.274, busy.spread(), 0.002);
        assertEquals(Badge.BUSY, busy.badge());
    }

    @Test
    void badgeThresholds() {
        assertEquals(Badge.FLAT, Badge.of(0));
        assertEquals(Badge.FLAT, Badge.of(Badge.GRAINY_FROM - 1e-9));
        assertEquals(Badge.GRAINY, Badge.of(Badge.GRAINY_FROM));
        assertEquals(Badge.GRAINY, Badge.of(Badge.BUSY_FROM - 1e-9));
        assertEquals(Badge.BUSY, Badge.of(Badge.BUSY_FROM));
        assertEquals(Badge.BUSY, Badge.of(1));
        // The spreads measured for the vanilla anchors that spread can order.
        assertEquals(Badge.FLAT, Badge.of(0.0049));   // black concrete, the roughest concrete
        assertEquals(Badge.GRAINY, Badge.of(0.0366)); // stone
        assertEquals(Badge.GRAINY, Badge.of(0.0858)); // oak planks
        assertEquals(Badge.GRAINY, Badge.of(0.0989)); // cobblestone
        assertEquals(Badge.BUSY, Badge.of(0.1665));   // bookshelf
    }

    @Test
    void scalesHalveDownToOne() {
        assertArrayEquals(new int[] {16, 8, 4, 2, 1}, Arrays.stream(Scale.values()).mapToInt(s -> s.texels).toArray());
        assertEquals(Scale.T8, Scale.T16.next());
        assertEquals(Scale.T1, Scale.T2.next());
        assertEquals(Scale.T1, Scale.T1.next());
    }
}
