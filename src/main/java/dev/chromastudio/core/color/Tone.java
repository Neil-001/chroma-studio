package dev.chromastudio.core.color;

public enum Tone {
    WARM, COOL, NEUTRAL;

    public static final double MIN_CHROMA = 0.03;

    public static Tone of(Oklch c) {
        if (c.c() < MIN_CHROMA) return NEUTRAL;
        double h = c.h();
        if (h >= 330 || h <= 90) return WARM;
        if (h >= 150 && h <= 300) return COOL;
        return NEUTRAL;
    }
}
