package dev.chromastudio.core.palette;

/** How a gradient's steps are paced between its ends. */
public enum Easing {
    LINEAR, SMOOTH, EASE_IN, EASE_OUT;

    /** Maps 0 to 1 onto 0 to 1: a straight line, smoothstep, t squared, or its mirror image. */
    public double apply(double t) {
        return switch (this) {
            case LINEAR -> t;
            case SMOOTH -> t * t * (3 - 2 * t);
            case EASE_IN -> t * t;
            case EASE_OUT -> 1 - (1 - t) * (1 - t);
        };
    }
}
