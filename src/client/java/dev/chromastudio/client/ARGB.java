package dev.chromastudio.client;

/** Packed 0xAARRGGBB colours. 1.21.2 gave the game a class of this name; 1.21.1 has only {@code FastColor}, so this is the part of it the mod uses. */
public final class ARGB {
    private ARGB() {}

    public static int alpha(int argb) { return argb >>> 24; }

    public static int red(int argb) { return argb >> 16 & 0xFF; }

    public static int green(int argb) { return argb >> 8 & 0xFF; }

    public static int blue(int argb) { return argb & 0xFF; }

    public static int color(int alpha, int red, int green, int blue) { return alpha << 24 | red << 16 | green << 8 | blue; }

    /** The colour with full alpha. */
    public static int opaque(int argb) { return argb | 0xFF000000; }

    /** White with an alpha from 0 to 1. */
    public static int white(float alpha) { return color(Math.round(Math.clamp(alpha, 0, 1) * 255), 255, 255, 255); }

    /** The colour with its alpha scaled by {@code factor}, 0 to 1. */
    public static int multiplyAlpha(int argb, float factor) {
        return color(Math.round(alpha(argb) * Math.clamp(factor, 0, 1)), red(argb), green(argb), blue(argb));
    }

    /** A colour {@code t} of the way from one to the other, channel by channel as stored. */
    public static int srgbLerp(float t, int from, int to) {
        return color(lerp(t, alpha(from), alpha(to)), lerp(t, red(from), red(to)), lerp(t, green(from), green(to)), lerp(t, blue(from), blue(to)));
    }

    private static int lerp(float t, int a, int b) { return Math.round(a + (b - a) * t); }
}
