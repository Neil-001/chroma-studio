package dev.chromastudio.core.color;

/**
 * An sRGB colour, packed 0xRRGGBB with no alpha. Hues are in degrees from 0 up to 360; saturation, value and
 * HSL lightness run 0 to 1. Two colours are equal when their channels are.
 */
public final class Color {
    // Linear light this far outside 0 to 1 still counts as in gamut. It covers the rounding in the published
    // matrices, and is under half an 8-bit step everywhere.
    private static final double GAMUT_SLACK = 1e-4;
    private static final double[] LINEAR = new double[256];
    // The D65 white the sRGB matrix below gives for (1, 1, 1).
    private static final double WHITE_X = 0.95047, WHITE_Y = 1.0, WHITE_Z = 1.08883;

    static {
        for (int i = 0; i < 256; i++) {
            double v = i / 255.0;
            LINEAR[i] = v <= 0.04045 ? v / 12.92 : Math.pow((v + 0.055) / 1.055, 2.4);
        }
    }

    private final int rgb;
    private final boolean inGamut;
    // Worked out on first use. A race only repeats the work, since both are immutable.
    private Oklab oklab;
    private Lab lab;

    private Color(int rgb, boolean inGamut) {
        this.rgb = rgb & 0xFFFFFF;
        this.inGamut = inGamut;
    }

    public static Color ofRgb(int rgb) {
        return new Color(rgb, true);
    }

    /** The colour for an OKLab value, clipped to sRGB by lowering chroma at the same lightness and hue. */
    public static Color of(Oklab lab) {
        double[] linear = lab.toLinear();
        if (inGamut(linear)) return new Color(pack(linear), true);
        return new Color(pack(lab.oklch().clipped().oklab().toLinear()), false);
    }

    public static Color ofOklch(Oklch lch) {
        return of(lch.oklab());
    }

    /** Takes {@code #RRGGBB} or {@code RRGGBB}, with spaces around it allowed. */
    public static Color fromHex(String s) {
        if (s == null) throw new IllegalArgumentException("No colour given");
        String digits = s.strip();
        if (digits.startsWith("#")) digits = digits.substring(1);
        if (digits.length() != 6) throw new IllegalArgumentException("Not a hex colour: " + s);
        int rgb = 0;
        for (int i = 0; i < 6; i++) {
            int d = Character.digit(digits.charAt(i), 16);
            if (d < 0) throw new IllegalArgumentException("Not a hex colour: " + s);
            rgb = rgb << 4 | d;
        }
        return ofRgb(rgb);
    }

    public static Color fromHsv(float h, float s, float v) {
        float c = v * s;
        return ofHueChroma(h, c, v - c);
    }

    public static Color fromHsl(float h, float s, float l) {
        float c = (1 - Math.abs(2 * l - 1)) * s;
        return ofHueChroma(h, c, l - c / 2);
    }

    /** The colour with hue {@code h}, the largest channel {@code c} above the smallest, and the smallest at {@code m}. */
    private static Color ofHueChroma(float h, float c, float m) {
        float sector = ((h % 360 + 360) % 360) / 60;
        float x = c * (1 - Math.abs(sector % 2 - 1));
        float r, g, b;
        switch ((int) sector) {
            case 0 -> { r = c; g = x; b = 0; }
            case 1 -> { r = x; g = c; b = 0; }
            case 2 -> { r = 0; g = c; b = x; }
            case 3 -> { r = 0; g = x; b = c; }
            case 4 -> { r = x; g = 0; b = c; }
            default -> { r = c; g = 0; b = x; }
        }
        return ofRgb(channel(r + m) << 16 | channel(g + m) << 8 | channel(b + m));
    }

    private static int channel(double encoded) {
        return (int) Math.round(Math.clamp(encoded, 0.0, 1.0) * 255);
    }

    public int rgb() { return rgb; }

    public int red() { return rgb >> 16; }

    public int green() { return rgb >> 8 & 0xFF; }

    public int blue() { return rgb & 0xFF; }

    public Oklab oklab() {
        Oklab o = oklab;
        if (o == null) {
            o = Oklab.ofLinear(LINEAR[red()], LINEAR[green()], LINEAR[blue()]);
            oklab = o;
        }
        return o;
    }

    public Oklch oklch() {
        return oklab().oklch();
    }

    /** CIELAB under D65. */
    public Lab lab() {
        Lab v = lab;
        if (v == null) {
            double r = LINEAR[red()], g = LINEAR[green()], b = LINEAR[blue()];
            double fx = labCurve((0.4124564 * r + 0.3575761 * g + 0.1804375 * b) / WHITE_X);
            double fy = labCurve((0.2126729 * r + 0.7151522 * g + 0.0721750 * b) / WHITE_Y);
            double fz = labCurve((0.0193339 * r + 0.1191920 * g + 0.9503041 * b) / WHITE_Z);
            v = new Lab(116 * fy - 16, 500 * (fx - fy), 200 * (fy - fz));
            lab = v;
        }
        return v;
    }

    private static double labCurve(double t) {
        return t > 216.0 / 24389 ? Math.cbrt(t) : (24389.0 / 27 * t + 16) / 116;
    }

    /** Hue, saturation and value. */
    public float[] hsv() {
        float r = red() / 255f, g = green() / 255f, b = blue() / 255f;
        float max = Math.max(r, Math.max(g, b)), min = Math.min(r, Math.min(g, b));
        return new float[] {hue(r, g, b, max, min), max == 0 ? 0 : (max - min) / max, max};
    }

    /** Hue, saturation and lightness. */
    public float[] hsl() {
        float r = red() / 255f, g = green() / 255f, b = blue() / 255f;
        float max = Math.max(r, Math.max(g, b)), min = Math.min(r, Math.min(g, b));
        float l = (max + min) / 2;
        float s = max == min ? 0 : (max - min) / (1 - Math.abs(2 * l - 1));
        return new float[] {hue(r, g, b, max, min), s, l};
    }

    private static float hue(float r, float g, float b, float max, float min) {
        if (max == min) return 0;
        float d = max - min;
        float h;
        if (max == r) h = (g - b) / d;
        else if (max == g) h = (b - r) / d + 2;
        else h = (r - g) / d + 4;
        h *= 60;
        return h < 0 ? h + 360 : h;
    }

    /** {@code #RRGGBB}, in capitals. */
    public String hex() {
        return String.format("#%06X", rgb);
    }

    /** True for every colour that came from RGB. False only when an OKLab or OKLCH value had to be clipped to fit. */
    public boolean inGamut() { return inGamut; }

    /** The colour with each channel multiplied by the tint's channel over 255, as the game tints a texture. */
    public Color tinted(int tint) {
        return ofRgb(tint(rgb, tint));
    }

    /** Tints the colour channels of an RGB or ARGB value, leaving any alpha as it is. */
    static int tint(int argb, int tint) {
        int r = ((argb >> 16 & 0xFF) * (tint >> 16 & 0xFF) + 127) / 255;
        int g = ((argb >> 8 & 0xFF) * (tint >> 8 & 0xFF) + 127) / 255;
        int b = ((argb & 0xFF) * (tint & 0xFF) + 127) / 255;
        return argb & 0xFF000000 | r << 16 | g << 8 | b;
    }

    /** The linear-light value of an 8-bit channel. */
    static double linear(int channel) {
        return LINEAR[channel];
    }

    static boolean inGamut(double[] linear) {
        for (double v : linear) {
            if (!(v >= -GAMUT_SLACK && v <= 1 + GAMUT_SLACK)) return false;
        }
        return true;
    }

    /** Packs linear-light channels as 0xRRGGBB, holding each within range. */
    static int pack(double[] linear) {
        return encode(linear[0]) << 16 | encode(linear[1]) << 8 | encode(linear[2]);
    }

    private static int encode(double linear) {
        double v = Math.clamp(linear, 0.0, 1.0);
        return channel(v <= 0.0031308 ? v * 12.92 : 1.055 * Math.pow(v, 1 / 2.4) - 0.055);
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof Color c && c.rgb == rgb;
    }

    @Override
    public int hashCode() { return rgb; }

    @Override
    public String toString() { return hex(); }
}
