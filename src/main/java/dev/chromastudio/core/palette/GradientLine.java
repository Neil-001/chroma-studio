package dev.chromastudio.core.palette;

/**
 * A gradient laid across a build between two blocks, as the Axiom gradient tool lays one. A block's step comes from
 * how far along the line from the first end it sits, measured square to the line, so every block in a plane across
 * the line gets the same step, inside the build as on its face. The ends are the centres of their blocks.
 */
public final class GradientLine {
    /** How neighbouring steps meet. */
    public enum Blend {
        /** Each step a solid band, with a sharp edge between two. */
        BANDS,
        /** Neighbouring steps mixed block by block near their shared edge, each step pure only at its middle. */
        MIXED
    }

    /**
     * How far into each band, in steps, mixing reaches from the edge on either side: half a step's width in all, so the
     * middle half of every band stays pure and the two quarters at its edges mix with the next band.
     */
    private static final double MIX = 0.5;

    private final int ax, ay, az;
    private final double dx, dy, dz, length;

    public GradientLine(int ax, int ay, int az, int bx, int by, int bz) {
        if (ax == bx && ay == by && az == bz) throw new IllegalArgumentException("A gradient needs two different ends");
        this.ax = ax;
        this.ay = ay;
        this.az = az;
        length = Math.sqrt((double) (bx - ax) * (bx - ax) + (double) (by - ay) * (by - ay) + (double) (bz - az) * (bz - az));
        dx = (bx - ax) / length;
        dy = (by - ay) / length;
        dz = (bz - az) / length;
    }

    /** How far along the line from the first end a block sits, in blocks: 0 at the first end and {@code length} at the second. */
    private double along(int x, int y, int z) { return (x - ax) * dx + (y - ay) * dy + (z - az) * dz; }

    /**
     * Whether a block lies between the planes across the line through the two ends, the ends' own blocks included, and
     * no further than {@code reach} blocks from the line.
     */
    public boolean reaches(int x, int y, int z, double reach) {
        double s = along(x, y, z);
        return s >= -0.5 && s <= length + 0.5 && distance(x, y, z) <= reach;
    }

    /** How far a block's centre is from the line, square to it, in blocks. */
    public double distance(int x, int y, int z) {
        double s = along(x, y, z);
        double px = x - ax - s * dx, py = y - ay - s * dy, pz = z - az - s * dz;
        return Math.sqrt(px * px + py * py + pz * pz);
    }

    /**
     * The step, from 0 to {@code steps - 1}, that a block gets. The {@code length + 1} blocks' worth from end to end
     * are shared equally among the steps, so the first and last steps are as wide as the others. With
     * {@link Blend#MIXED} the step may be the next one up or down near a band's edge, chosen by the block's position
     * alone, so the same block always gets the same step.
     */
    public int step(int x, int y, int z, int steps, Blend blend) {
        double u = (along(x, y, z) + 0.5) / (length + 1) * steps;
        if (blend == Blend.MIXED) u += (noise(x, y, z) - 0.5) * MIX;
        return Math.clamp((long) Math.floor(u), 0, steps - 1);
    }

    /** A number from 0 up to 1 that looks random but depends only on the position. */
    private static double noise(int x, int y, int z) {
        // SplitMix64's finaliser over the three coordinates.
        long h = x * 0x9E3779B97F4A7C15L ^ y * 0xC2B2AE3D27D4EB4FL ^ z * 0x165667B19E3779F9L;
        h = (h ^ h >>> 30) * 0xBF58476D1CE4E5B9L;
        h = (h ^ h >>> 27) * 0x94D049BB133111EBL;
        h ^= h >>> 31;
        return (h >>> 11) * 0x1.0p-53;
    }
}
