package dev.chromastudio.core.color;

/** A colour in OKLab, by Björn Ottosson's published matrices (https://bottosson.github.io/posts/oklab/). Lightness runs 0 to 1. */
public record Oklab(double l, double a, double b) {
    // Below this chroma the hue is rounding noise, so it reads as 0.
    private static final double ACHROMATIC = 1e-6;

    public Oklch oklch() {
        double c = Math.hypot(a, b);
        if (c < ACHROMATIC) return new Oklch(l, c, 0);
        return new Oklch(l, c, Math.toDegrees(Math.atan2(b, a)));
    }

    public double distance(Oklab o) {
        double dl = l - o.l, da = a - o.a, db = b - o.b;
        return Math.sqrt(dl * dl + da * da + db * db);
    }

    /** From CIE XYZ with a D65 white of Y = 1, the form Ottosson's reference table is given in. */
    public static Oklab ofXyz(double x, double y, double z) {
        return ofCones(
                0.8189330101 * x + 0.3618667424 * y - 0.1288597137 * z,
                0.0329845436 * x + 0.9293118715 * y + 0.0361456387 * z,
                0.0482003018 * x + 0.2643662691 * y + 0.6338517070 * z);
    }

    /** From linear-light sRGB, each channel 0 to 1. */
    static Oklab ofLinear(double r, double g, double b) {
        return ofCones(
                0.4122214708 * r + 0.5363325363 * g + 0.0514459929 * b,
                0.2119034982 * r + 0.6806995451 * g + 0.1073969566 * b,
                0.0883024619 * r + 0.2817188376 * g + 0.6299787005 * b);
    }

    private static Oklab ofCones(double l, double m, double s) {
        double l3 = Math.cbrt(l), m3 = Math.cbrt(m), s3 = Math.cbrt(s);
        return new Oklab(
                0.2104542553 * l3 + 0.7936177850 * m3 - 0.0040720468 * s3,
                1.9779984951 * l3 - 2.4285922050 * m3 + 0.4505937099 * s3,
                0.0259040371 * l3 + 0.7827717662 * m3 - 0.8086757660 * s3);
    }

    /** To linear-light sRGB. Channels fall outside 0 to 1 when the colour is out of gamut. */
    double[] toLinear() {
        double l3 = l + 0.3963377774 * a + 0.2158037573 * b;
        double m3 = l - 0.1055613458 * a - 0.0638541728 * b;
        double s3 = l - 0.0894841775 * a - 1.2914855480 * b;
        double lc = l3 * l3 * l3, mc = m3 * m3 * m3, sc = s3 * s3 * s3;
        return new double[] {
                4.0767416621 * lc - 3.3077115913 * mc + 0.2309699292 * sc,
                -1.2684380046 * lc + 2.6097574011 * mc - 0.3413193965 * sc,
                -0.0041960863 * lc - 0.7034186147 * mc + 1.7076147010 * sc};
    }

    /** Whether the colour can be shown in sRGB without clipping. */
    public boolean inGamut() {
        return Color.inGamut(toLinear());
    }
}
