package dev.chromastudio.core.color;

public enum Badge {
    FLAT, GRAINY, BUSY;

    public static final double GRAINY_FROM = 0.02, BUSY_FROM = 0.13;

    public static Badge of(double spread) {
        if (spread < GRAINY_FROM) return FLAT;
        return spread < BUSY_FROM ? GRAINY : BUSY;
    }
}
