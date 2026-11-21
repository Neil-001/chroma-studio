package dev.chromastudio.core.color;

public final class Shading {
    public static final double TOP = 1.0, NORTH_SOUTH = 0.8, EAST_WEST = 0.6, BOTTOM = 0.5;

    private Shading() {}

    public static double factor(Face f) {
        return switch (f) {
            case UP, ANY -> TOP;
            case NORTH, SOUTH -> NORTH_SOUTH;
            case WEST, EAST -> EAST_WEST;
            case DOWN -> BOTTOM;
        };
    }

    public static Color apply(Color c, Face f) {
        return scale(c, factor(f));
    }

    public static Color remove(Color c, Face f) {
        return scale(c, 1 / factor(f));
    }

    private static Color scale(Color c, double by) {
        return Color.ofRgb(channel(c.red() * by) << 16 | channel(c.green() * by) << 8 | channel(c.blue() * by));
    }

    private static int channel(double v) {
        return (int) Math.min(255, Math.round(v));
    }
}
