package dev.chromastudio.core.palette;

/**
 * A gradient laid across a build between two blocks, as the Axiom gradient tool lays one. A block's step comes from
 * how far along the line from the first end it sits, measured square to the line, so every block in a plane across
 * the line gets the same step, inside the build as on its face. The ends are the centres of their blocks.
 */
public final class GradientLine {
    /**
     * How far past an end plane, or past the reach, a block's centre may be and still count as on it, so rounding
     * doesn't decide a block that sits exactly there, and the ends give the same answer either way round.
     */
    private static final double EDGE = 1e-6;

    /**
     * How far off an axis a line may lean, as the sideways distance over the distance along the axis, and still be
     * straightened onto it: about 20 degrees. Clicking the top and bottom of a tower, or the two ends of a wall, rarely
     * hits the same column or row: on a tower 16 high, clicking the top at one corner and the bottom at the other leans
     * a quarter. A leaning line tilts the planes between steps, which cuts the build's corners off at the ends. A line
     * leaning further than this is a diagonal the player meant, as builders draw them at 30 degrees or more.
     */
    static final double STRAIGHTEN = 0.36;

    private final int ax, ay, az;
    private final double dx, dy, dz, length;
    private final boolean straightened;

    /**
     * The line from the first end to the second, straightened onto the axis it runs nearly along, so that its steps
     * lie square to the build: it keeps the first end, and the second moves sideways onto the axis through it. Its
     * end planes then pass through both ends' blocks along that axis, as the player clicked them.
     */
    public static GradientLine between(int ax, int ay, int az, int bx, int by, int bz) {
        long x = Math.abs((long) bx - ax), y = Math.abs((long) by - ay), z = Math.abs((long) bz - az);
        long main = Math.max(x, Math.max(y, z));
        double sideways = Math.sqrt((double) x * x + (double) y * y + (double) z * z - (double) main * main);
        if (main == 0 || sideways == 0 || sideways > STRAIGHTEN * main) return new GradientLine(ax, ay, az, bx, by, bz, false);
        if (main == y) return new GradientLine(ax, ay, az, ax, by, az, true);
        if (main == x) return new GradientLine(ax, ay, az, bx, ay, az, true);
        return new GradientLine(ax, ay, az, ax, ay, bz, true);
    }

    public GradientLine(int ax, int ay, int az, int bx, int by, int bz) { this(ax, ay, az, bx, by, bz, false); }

    private GradientLine(int ax, int ay, int az, int bx, int by, int bz, boolean straightened) {
        this.straightened = straightened;
        if (ax == bx && ay == by && az == bz) throw new IllegalArgumentException("A gradient needs two different ends");
        this.ax = ax;
        this.ay = ay;
        this.az = az;
        length = Math.sqrt((double) (bx - ax) * (bx - ax) + (double) (by - ay) * (by - ay) + (double) (bz - az) * (bz - az));
        dx = (bx - ax) / length;
        dy = (by - ay) / length;
        dz = (bz - az) / length;
    }

    /** Whether {@link #between} straightened the line onto an axis. */
    public boolean straightened() { return straightened; }

    /** Whether the line runs on a slant, rather than along an axis, so its end planes cut across the build. */
    public boolean slanted() { return (dx != 0 ? 1 : 0) + (dy != 0 ? 1 : 0) + (dz != 0 ? 1 : 0) > 1; }

    /** How far along the line from the first end a block sits, in blocks: 0 at the first end and {@code length} at the second. */
    private double along(int x, int y, int z) { return (x - ax) * dx + (y - ay) * dy + (z - az) * dz; }

    /**
     * Whether a block's centre lies between the planes across the line through the two ends' centres, or on one, and
     * no further than {@code reach} blocks from the line. The ends' own blocks are on those planes, so they count.
     */
    public boolean reaches(int x, int y, int z, double reach) {
        double s = along(x, y, z);
        return s >= -EDGE && s <= length + EDGE && distance(x, y, z) <= reach + EDGE;
    }

    /** How far a block's centre is from the line, square to it, in blocks. */
    public double distance(int x, int y, int z) {
        double s = along(x, y, z);
        double px = x - ax - s * dx, py = y - ay - s * dy, pz = z - az - s * dz;
        return Math.sqrt(px * px + py * py + pz * pz);
    }

    /**
     * The step, from 0 to {@code steps - 1}, that a block gets. The {@code length + 1} blocks' worth from end to end
     * are shared equally among the steps, so the first and last steps are as wide as the others.
     *
     * <p>{@code blend} is how wide the zone where two neighbouring steps mix is, as a share of a step: 0 gives hard
     * bands, and 1 mixes across the whole step, so a step is pure only along its middle. Across the zone the share of
     * the next step's blocks rises evenly from none to all. Which blocks take it is set by {@link #dither}, by the
     * block's position alone, so the same block always gets the same step.
     *
     * <p>When the line is shorter than the gradient has steps, sharing would skip both ends' steps, so instead each
     * block takes the step nearest its place along the line, mixed with the next nearest as the blend allows, and the
     * two ends get the first and last steps.
     */
    public int step(int x, int y, int z, int steps, double blend) {
        boolean shortLine = length + 1 < steps;
        // On a short line the steps sit at the blocks rather than between them, so rounding is flooring half a step on.
        double u = shortLine ? along(x, y, z) / length * (steps - 1) + 0.5 : (along(x, y, z) + 0.5) / (length + 1) * steps;
        if (blend > 0) u += (dither(x, y, z) - 0.5) * Math.min(blend, 1);
        return Math.clamp((long) Math.floor(u), 0, steps - 1);
    }

    /**
     * A number from 0 up to 1 for each block, which decides where in a mixing zone it switches to the next step.
     * Random numbers clump, leaving blots of one block and holes in the other; these come from a blue noise table, in
     * which each number's neighbours are as unlike it as can be, so at every mix the blocks of each step are spread
     * evenly, as a builder would scatter them by hand. The table is a cube that the world is tiled with, each tile
     * shifted by its own fixed amount, so a long wall doesn't show the same pattern over and over.
     */
    static double dither(int x, int y, int z) {
        long h = (x >> Dither.BITS) * 0x9E3779B97F4A7C15L + (y >> Dither.BITS) * 0xC2B2AE3D27D4EB4FL + (z >> Dither.BITS) * 0x165667B19E3779F9L;
        h = (h ^ h >>> 30) * 0xBF58476D1CE4E5B9L;
        h = (h ^ h >>> 27) * 0x94D049BB133111EBL;
        int sx = (int) h, sy = (int) (h >>> 16), sz = (int) (h >>> 32);
        return Dither.TABLE[(x + sx & Dither.MASK) << 2 * Dither.BITS | (y + sy & Dither.MASK) << Dither.BITS | z + sz & Dither.MASK];
    }

    /**
     * The blue noise table, a cube {@link #SIZE} blocks on a side whose pattern runs on across its wrapped edges.
     * It's made the same way on every machine when first needed, rather than shipped, by filling the cube one cell at
     * a time, each time the cell farthest from those already filled, and numbering the cells in that order. Farthest means least crowded, with
     * nearby filled cells counting most, as a Gaussian falls off.
     */
    private static final class Dither {
        static final int BITS = 4, SIZE = 1 << BITS, MASK = SIZE - 1, CELLS = SIZE * SIZE * SIZE;
        /** How fast crowding falls off with distance, in blocks: about one and a half, so a cell feels its near neighbours. */
        static final double SPREAD = 1.5;
        static final double[] TABLE = make();

        private static double[] make() {
            // How much a filled cell crowds another a given offset away, round the cube's wrapped edges.
            double[] kernel = new double[CELLS];
            for (int i = 0; i < CELLS; i++) {
                int dx = wrap(i >> 2 * BITS), dy = wrap(i >> BITS & MASK), dz = wrap(i & MASK);
                kernel[i] = StrictMath.exp(-(dx * dx + dy * dy + dz * dz) / (2 * SPREAD * SPREAD));
            }
            // A crumb of fixed noise on every cell, far below any real crowding, so ties don't fill the cube as a grid.
            double[] crowding = new double[CELLS];
            long h = 0x2545F4914F6CDD1DL;
            for (int i = 0; i < CELLS; i++) {
                h = h * 6364136223846793005L + 1442695040888963407L;
                crowding[i] = (h >>> 11) * 0x1.0p-53 * 1e-3;
            }
            boolean[] filled = new boolean[CELLS];
            double[] table = new double[CELLS];
            for (int rank = 0; rank < CELLS; rank++) {
                int best = -1;
                for (int i = 0; i < CELLS; i++) if (!filled[i] && (best < 0 || crowding[i] < crowding[best])) best = i;
                filled[best] = true;
                table[best] = (rank + 0.5) / CELLS;
                int bx = best >> 2 * BITS, by = best >> BITS & MASK, bz = best & MASK;
                for (int i = 0; i < CELLS; i++) {
                    int dx = (i >> 2 * BITS) - bx & MASK, dy = (i >> BITS & MASK) - by & MASK, dz = (i & MASK) - bz & MASK;
                    crowding[i] += kernel[dx << 2 * BITS | dy << BITS | dz];
                }
            }
            return table;
        }

        /** An offset along one axis taken the short way round the wrapped cube. */
        private static int wrap(int d) { return Math.min(d, SIZE - d); }
    }
}
