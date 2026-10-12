package dev.chromastudio.core.color;

/** Whether a colour reads as warm, cool or neither. */
public enum Tone {
    WARM, COOL, NEUTRAL;

    /** Below this chroma a colour is a grey whatever its hue. */
    public static final double MIN_CHROMA = 0.03;

    /** Warm for hues from 330° through 0° to 90°, cool from 150° to 300°, neutral in between and for greys. */
    public static Tone of(Oklch c) {
        if (c.c() < MIN_CHROMA) return NEUTRAL;
        double h = c.h();
        if (h >= 330 || h <= 90) return WARM;
        if (h >= 150 && h <= 300) return COOL;
        return NEUTRAL;
    }
}
