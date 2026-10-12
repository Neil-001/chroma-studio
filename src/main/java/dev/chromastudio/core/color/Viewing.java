package dev.chromastudio.core.color;

/** How much of a block's texture the eye makes out at a distance. */
public final class Viewing {
    public static final double FAR = Double.POSITIVE_INFINITY;

    /**
     * A texel counts as made out when it spans this many physical screen pixels. At a normal distance from a
     * monitor that is about where the eye's contrast sensitivity stops telling neighbouring texels apart.
     */
    public static final double PIXELS_PER_RESOLVED_TEXEL = 4;

    private Viewing() {}

    /** The scale a block face shows at, {@code distanceBlocks} away in a window that many physical pixels high. */
    public static Scale scaleFor(double distanceBlocks, int windowHeightPx, double verticalFovDegrees) {
        double texels = pixelsPerBlock(distanceBlocks, windowHeightPx, verticalFovDegrees) / PIXELS_PER_RESOLVED_TEXEL;
        for (Scale s : Scale.values()) {
            if (texels >= s.texels) return s;
        }
        return Scale.T1;
    }

    /**
     * The farthest distance in blocks at which T16, T8, T4 and T2 each still show, in that order. A slider marks
     * them; past the last one a face shows at T1.
     */
    public static double[] blockMarks(int windowHeightPx, double verticalFovDegrees) {
        double[] marks = new double[Scale.values().length - 1];
        double atOneBlock = pixelsPerBlock(1, windowHeightPx, verticalFovDegrees);
        for (int i = 0; i < marks.length; i++) {
            marks[i] = atOneBlock / (PIXELS_PER_RESOLVED_TEXEL * Scale.values()[i].texels);
        }
        return marks;
    }

    /** A block's height on screen in physical pixels, facing the camera at the centre of the view. */
    private static double pixelsPerBlock(double distanceBlocks, int windowHeightPx, double verticalFovDegrees) {
        // Standing in the block counts as right up against it.
        if (distanceBlocks <= 0) return Double.POSITIVE_INFINITY;
        return windowHeightPx / (2 * distanceBlocks * Math.tan(Math.toRadians(verticalFovDegrees) / 2));
    }
}
