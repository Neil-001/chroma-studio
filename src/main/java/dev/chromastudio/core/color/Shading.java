package dev.chromastudio.core.color;

/** The fixed darkening the game gives each face of a block. */
public final class Shading {
    public static final double TOP = 1.0, NORTH_SOUTH = 0.8, EAST_WEST = 0.6, BOTTOM = 0.5;

    private Shading() {}

    /** The factor for a face. ANY has none of its own and gives 1. */
    public static double factor(Face f) {
        return switch (f) {
            case UP, ANY -> TOP;
            case NORTH, SOUTH -> NORTH_SOUTH;
            case WEST, EAST -> EAST_WEST;
            case DOWN -> BOTTOM;
        };
    }

    /** The colour as the game draws it on that face. */
    public static Color apply(Color c, Face f) {
        return scale(c, factor(f));
    }

    /** Undoes {@link #apply}, for a colour read off the screen. Channels that would pass 255 stop there. */
    public static Color remove(Color c, Face f) {
        return scale(c, 1 / factor(f));
    }

    // The game multiplies the stored channel values, not linear light, so this does the same. Otherwise a shaded
    // colour wouldn't match what is on screen.
    private static Color scale(Color c, double by) {
        return Color.ofRgb(channel(c.red() * by) << 16 | channel(c.green() * by) << 8 | channel(c.blue() * by));
    }

    private static int channel(double v) {
        return (int) Math.min(255, Math.round(v));
    }
}
