package dev.chromastudio.core.palette;

public enum HueArc {
    SHORTEST, LONGEST;

    public double turn(double from, double to) {
        double shortest = ((to - from) % 360 + 540) % 360 - 180;
        if (this == SHORTEST) return shortest;
        return shortest > 0 ? shortest - 360 : shortest + 360;
    }
}
