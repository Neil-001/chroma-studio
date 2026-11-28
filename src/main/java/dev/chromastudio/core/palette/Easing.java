package dev.chromastudio.core.palette;

public enum Easing {
    LINEAR, SMOOTH, EASE_IN, EASE_OUT;

    public double apply(double t) {
        return switch (this) {
            case LINEAR -> t;
            case SMOOTH -> t * t * (3 - 2 * t);
            case EASE_IN -> t * t;
            case EASE_OUT -> 1 - (1 - t) * (1 - t);
        };
    }
}
