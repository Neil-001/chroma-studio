package dev.chromastudio.core.color;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class ColorTest {
    private static void assertOklab(double l, double a, double b, Oklab actual, double tolerance) {
        assertEquals(l, actual.l(), tolerance, "L");
        assertEquals(a, actual.a(), tolerance, "a");
        assertEquals(b, actual.b(), tolerance, "b");
    }

    /** The table of example pairs in Ottosson's post, XYZ in and OKLab out, which he rounds to three decimals. */
    @Test
    void oklabMatchesOttossonsTable() {
        assertOklab(1.000, 0.000, 0.000, Oklab.ofXyz(0.950, 1.000, 1.089), 0.0006);
        assertOklab(0.450, 1.236, -0.019, Oklab.ofXyz(1.000, 0.000, 0.000), 0.0006);
        assertOklab(0.922, -0.671, 0.263, Oklab.ofXyz(0.000, 1.000, 0.000), 0.0006);
        assertOklab(0.153, -1.415, -0.449, Oklab.ofXyz(0.000, 0.000, 1.000), 0.0006);
    }

    /** The sRGB primaries, as CSS Color 4 and Ottosson's own converter give them. */
    @Test
    void oklabOfThePrimaries() {
        assertOklab(1, 0, 0, Color.ofRgb(0xFFFFFF).oklab(), 1e-4);
        assertOklab(0, 0, 0, Color.ofRgb(0x000000).oklab(), 1e-9);
        assertOklab(0.62796, 0.22486, 0.12585, Color.ofRgb(0xFF0000).oklab(), 1e-4);
        assertOklab(0.86644, -0.23389, 0.17950, Color.ofRgb(0x00FF00).oklab(), 1e-4);
        assertOklab(0.45201, -0.03246, -0.31153, Color.ofRgb(0x0000FF).oklab(), 1e-4);
    }

    /** A grey has no a or b, and its lightness is the cube root of its luminance: 0.5 for the 12.5% grey, and so on. */
    @Test
    void oklabOfGreys() {
        // sRGB 99 is 12.5% luminance to within half a percent, 188 is 50.3%, 55 is 3.8%.
        assertOklab(Math.cbrt(Math.pow((99 / 255.0 + 0.055) / 1.055, 2.4)), 0, 0, Color.ofRgb(0x636363).oklab(), 1e-4);
        assertEquals(0.5, Color.ofRgb(0x636363).oklab().l(), 0.002);
        assertOklab(Math.cbrt(Math.pow((188 / 255.0 + 0.055) / 1.055, 2.4)), 0, 0, Color.ofRgb(0xBCBCBC).oklab(), 1e-4);
        assertEquals(0.795, Color.ofRgb(0xBCBCBC).oklab().l(), 0.002);
        assertOklab(Math.cbrt(55 / 255.0 / 12.92 > 0.0031308 ? Math.pow((55 / 255.0 + 0.055) / 1.055, 2.4) : 0), 0, 0,
                Color.ofRgb(0x373737).oklab(), 1e-4);
    }

    /** The sRGB route and the XYZ route use different published matrices and must still agree. */
    @Test
    void theTwoOklabRoutesAgree() {
        // Red in XYZ under D65, from the standard sRGB matrix.
        assertOklab(0.62796, 0.22486, 0.12585, Oklab.ofXyz(0.4124564, 0.2126729, 0.0193339), 1e-3);
    }

    /** CIELAB of the primaries under D65, from Bruce Lindbloom's tables. */
    @Test
    void labOfThePrimaries() {
        assertLab(100, 0, 0, Color.ofRgb(0xFFFFFF).lab());
        assertLab(0, 0, 0, Color.ofRgb(0x000000).lab());
        assertLab(53.2408, 80.0925, 67.2032, Color.ofRgb(0xFF0000).lab());
        assertLab(87.7347, -86.1827, 83.1793, Color.ofRgb(0x00FF00).lab());
        assertLab(32.2970, 79.1875, -107.8602, Color.ofRgb(0x0000FF).lab());
    }

    private static void assertLab(double l, double a, double b, Lab actual) {
        assertEquals(l, actual.l(), 0.01, "L");
        assertEquals(a, actual.a(), 0.01, "a");
        assertEquals(b, actual.b(), 0.01, "b");
    }

    @Test
    void oklchIsThePolarForm() {
        Oklch red = Color.ofRgb(0xFF0000).oklch();
        // CSS Color 4 gives red as oklch(62.8% 0.2577 29.23).
        assertEquals(0.628, red.l(), 0.0005);
        assertEquals(0.2577, red.c(), 0.0001);
        assertEquals(29.23, red.h(), 0.01);
        assertOklab(0.62796, 0.22486, 0.12585, red.oklab(), 1e-4);
        assertEquals(0, Color.ofRgb(0x808080).oklch().h());
        assertEquals(350, new Oklch(0.5, 0.1, -10).h(), 1e-9);
        assertEquals(10, new Oklch(0.5, 0.1, 370).h(), 1e-9);
        assertEquals(new Oklch(0.5, 0.1, 40), new Oklch(0.2, 0.3, 80).withL(0.5).withC(0.1).withHue(40));
    }

    @Test
    void everyRgbColourSurvivesOklabAndBack() {
        for (int rgb = 0; rgb <= 0xFFFFFF; rgb += 0x010305) {
            Color c = Color.ofRgb(rgb);
            assertTrue(c.inGamut());
            Color back = Color.of(c.oklab());
            assertEquals(rgb, back.rgb());
            assertTrue(back.inGamut(), c.hex());
            assertEquals(rgb, Color.ofOklch(c.oklch()).rgb());
        }
    }

    @Test
    void outOfGamutColoursLoseChromaOnly() {
        Oklch vivid = new Oklch(0.7, 0.4, 150);
        assertFalse(vivid.inGamut());
        Color clipped = Color.ofOklch(vivid);
        assertFalse(clipped.inGamut());
        Oklch got = clipped.oklch();
        assertEquals(0.7, got.l(), 0.005);
        assertEquals(150, got.h(), 1.5);
        assertTrue(got.c() < 0.4 && got.c() > 0.1, "chroma " + got.c());
        // The clipped colour sits on the boundary: a little more chroma at the same lightness and hue no longer fits.
        Oklch edge = vivid.clipped();
        assertTrue(edge.inGamut());
        assertFalse(edge.withC(edge.c() + 0.002).inGamut());
        assertEquals(0.7, edge.l());
        assertEquals(150, edge.h());

        // Lightness beyond white or black has nowhere to go but the ends.
        assertEquals(0xFFFFFF, Color.ofOklch(new Oklch(1.3, 0.1, 20)).rgb());
        assertEquals(0x000000, Color.ofOklch(new Oklch(-0.2, 0.1, 20)).rgb());
        assertFalse(Color.ofOklch(new Oklch(1.3, 0.1, 20)).inGamut());
    }

    @Test
    void hexParsesBothFormsAndRejectsTheRest() {
        assertEquals(0x1A2B3C, Color.fromHex("#1A2B3C").rgb());
        assertEquals(0x1A2B3C, Color.fromHex("1a2b3c").rgb());
        assertEquals(0x1A2B3C, Color.fromHex(" #1a2B3c ").rgb());
        assertEquals("#1A2B3C", Color.ofRgb(0x1A2B3C).hex());
        assertEquals("#00000F", Color.ofRgb(0xF).hex());
        for (String bad : new String[] {"", "#", "#12345", "1234567", "#12345G", "+12345", "##123456", "0x123456"}) {
            assertThrows(IllegalArgumentException.class, () -> Color.fromHex(bad), bad);
        }
        assertThrows(IllegalArgumentException.class, () -> Color.fromHex(null));
    }

    @Test
    void anAlphaByteIsDropped() {
        assertEquals(0x123456, Color.ofRgb(0xFF123456).rgb());
        assertEquals(Color.ofRgb(0x123456), Color.ofRgb(0xFF123456));
    }

    @Test
    void hsvAndHslByHand() {
        // Orange: 255, 128, 0.
        assertArrayEquals(new float[] {30.12f, 1f, 1f}, Color.ofRgb(0xFF8000).hsv(), 0.01f);
        assertArrayEquals(new float[] {30.12f, 1f, 0.5f}, Color.ofRgb(0xFF8000).hsl(), 0.01f);
        // A dull teal: 64, 128, 128.
        assertArrayEquals(new float[] {180f, 0.5f, 0.502f}, Color.ofRgb(0x408080).hsv(), 0.001f);
        assertArrayEquals(new float[] {180f, 0.3333f, 0.3765f}, Color.ofRgb(0x408080).hsl(), 0.001f);
        assertArrayEquals(new float[] {0f, 0f, 0.502f}, Color.ofRgb(0x808080).hsv(), 0.001f);
        assertArrayEquals(new float[] {300f, 1f, 1f}, Color.ofRgb(0xFF00FF).hsv(), 0.001f);

        assertEquals(0xFF8000, Color.fromHsv(30, 1, 1).rgb());
        assertEquals(0x408080, Color.fromHsv(180, 0.5f, 128 / 255f).rgb());
        assertEquals(0x408080, Color.fromHsl(180, 1 / 3f, 96 / 255f).rgb());
        assertEquals(0xFF0000, Color.fromHsv(360, 1, 1).rgb());
        assertEquals(0x0000FF, Color.fromHsv(-120, 1, 1).rgb());
        assertEquals(0xFFFFFF, Color.fromHsl(77, 0.3f, 1).rgb());
    }

    @Test
    void hsvAndHslRoundTrip() {
        for (int rgb = 0; rgb <= 0xFFFFFF; rgb += 0x030507) {
            float[] hsv = Color.ofRgb(rgb).hsv();
            assertEquals(rgb, Color.fromHsv(hsv[0], hsv[1], hsv[2]).rgb());
            float[] hsl = Color.ofRgb(rgb).hsl();
            assertEquals(rgb, Color.fromHsl(hsl[0], hsl[1], hsl[2]).rgb());
        }
    }

    @Test
    void tintMultipliesEachChannel() {
        assertEquals(0x804020, Color.ofRgb(0xFF8040).tinted(0x808080).rgb());
        assertEquals(0x123456, Color.ofRgb(0x123456).tinted(0xFFFFFF).rgb());
        assertEquals(0x003400, Color.ofRgb(0x123456).tinted(0x00FF00).rgb());
    }

    @Test
    void toneFollowsHueAndChroma() {
        assertEquals(Tone.WARM, Tone.of(new Oklch(0.6, 0.1, 0)));
        assertEquals(Tone.WARM, Tone.of(new Oklch(0.6, 0.1, 90)));
        assertEquals(Tone.WARM, Tone.of(new Oklch(0.6, 0.1, 330)));
        assertEquals(Tone.WARM, Tone.of(new Oklch(0.6, 0.1, 359)));
        assertEquals(Tone.NEUTRAL, Tone.of(new Oklch(0.6, 0.1, 91)));
        assertEquals(Tone.NEUTRAL, Tone.of(new Oklch(0.6, 0.1, 149)));
        assertEquals(Tone.COOL, Tone.of(new Oklch(0.6, 0.1, 150)));
        assertEquals(Tone.COOL, Tone.of(new Oklch(0.6, 0.1, 300)));
        assertEquals(Tone.NEUTRAL, Tone.of(new Oklch(0.6, 0.1, 315)));
        assertEquals(Tone.NEUTRAL, Tone.of(new Oklch(0.6, 0.029, 30)));
        assertEquals(Tone.WARM, Tone.of(new Oklch(0.6, 0.03, 30)));
        assertEquals(Tone.WARM, Tone.of(Color.ofRgb(0xD87F33).oklch()));
        assertEquals(Tone.COOL, Tone.of(Color.ofRgb(0x334CB2).oklch()));
        assertEquals(Tone.NEUTRAL, Tone.of(Color.ofRgb(0x707070).oklch()));
    }

    @Test
    void facesFollowTheGamesDirectionOrder() {
        assertArrayEquals(new Face[] {Face.DOWN, Face.UP, Face.NORTH, Face.SOUTH, Face.WEST, Face.EAST, Face.ANY}, Face.values());
    }
}
