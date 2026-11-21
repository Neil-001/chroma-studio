package dev.chromastudio.core.color;

public record Oklch(double l, double c, double h) {
    public Oklch {
        h %= 360;
        if (h < 0) h += 360;
        if (h >= 360) h = 0;
    }

    public Oklab oklab() {
        double r = Math.toRadians(h);
        return new Oklab(l, c * Math.cos(r), c * Math.sin(r));
    }

    public Oklch withHue(double h) { return new Oklch(l, c, h); }

    public Oklch withL(double l) { return new Oklch(l, c, h); }

    public Oklch withC(double c) { return new Oklch(l, c, h); }

    public boolean inGamut() {
        return oklab().inGamut();
    }

    public Oklch clipped() {
        if (inGamut()) return this;
        Oklch grey = new Oklch(Math.clamp(l, 0.0, 1.0), 0, h);
        double low = 0, high = c;
        for (int i = 0; i < 40; i++) {
            double mid = (low + high) / 2;
            if (grey.withC(mid).inGamut()) low = mid; else high = mid;
        }
        return grey.withC(low);
    }
}
