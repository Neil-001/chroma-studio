package dev.chromastudio.core.color;

public final class Viewing {
    public static final double FAR = Double.POSITIVE_INFINITY;

    public static final double PIXELS_PER_RESOLVED_TEXEL = 4;

    private Viewing() {}

    public static Scale scaleFor(double distanceBlocks, int windowHeightPx, double verticalFovDegrees) {
        double texels = pixelsPerBlock(distanceBlocks, windowHeightPx, verticalFovDegrees) / PIXELS_PER_RESOLVED_TEXEL;
        for (Scale s : Scale.values()) {
            if (texels >= s.texels) return s;
        }
        return Scale.T1;
    }

    public static double[] blockMarks(int windowHeightPx, double verticalFovDegrees) {
        double[] marks = new double[Scale.values().length - 1];
        double atOneBlock = pixelsPerBlock(1, windowHeightPx, verticalFovDegrees);
        for (int i = 0; i < marks.length; i++) {
            marks[i] = atOneBlock / (PIXELS_PER_RESOLVED_TEXEL * Scale.values()[i].texels);
        }
        return marks;
    }

    private static double pixelsPerBlock(double distanceBlocks, int windowHeightPx, double verticalFovDegrees) {
        if (distanceBlocks <= 0) return Double.POSITIVE_INFINITY;
        return windowHeightPx / (2 * distanceBlocks * Math.tan(Math.toRadians(verticalFovDegrees) / 2));
    }
}
