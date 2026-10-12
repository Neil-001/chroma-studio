package dev.chromastudio.core.palette;

/** Which way round the hue circle a gradient goes between two stops. */
public enum HueArc {
    SHORTEST, LONGEST;

    /**
     * The degrees to turn from one hue to the other, negative for clockwise. The shortest way is from -180 up to
     * 180; the longest is the rest of the circle, and a whole turn between two equal hues.
     */
    public double turn(double from, double to) {
        double shortest = ((to - from) % 360 + 540) % 360 - 180;
        if (this == SHORTEST) return shortest;
        return shortest > 0 ? shortest - 360 : shortest + 360;
    }
}
