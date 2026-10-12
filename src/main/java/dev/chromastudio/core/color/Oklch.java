package dev.chromastudio.core.color;

/** OKLab in polar form: lightness 0 to 1, chroma as OKLab gives it, hue in degrees from 0 up to 360. */
public record Oklch(double l, double c, double h) {
    public Oklch {
        h %= 360;
        if (h < 0) h += 360;
        // A tiny negative hue rounds up to 360 itself.
        if (h >= 360) h = 0;
    }

    public Oklab oklab() {
        double r = Math.toRadians(h);
        return new Oklab(l, c * Math.cos(r), c * Math.sin(r));
    }

    public Oklch withHue(double h) { return new Oklch(l, c, h); }

    public Oklch withL(double l) { return new Oklch(l, c, h); }

    public Oklch withC(double c) { return new Oklch(l, c, h); }

    /** Whether the colour can be shown in sRGB without clipping. */
    public boolean inGamut() {
        return oklab().inGamut();
    }

    /** The nearest colour sRGB can show, found by lowering chroma at the same lightness and hue. Lightness is held within 0 to 1. */
    public Oklch clipped() {
        if (inGamut()) return this;
        Oklch grey = new Oklch(Math.clamp(l, 0.0, 1.0), 0, h);
        // The in-gamut chromas at one lightness and hue form a range from zero, so a bisection finds its end.
        double low = 0, high = c;
        for (int i = 0; i < 40; i++) {
            double mid = (low + high) / 2;
            if (grey.withC(mid).inGamut()) low = mid; else high = mid;
        }
        return grey.withC(low);
    }
}
