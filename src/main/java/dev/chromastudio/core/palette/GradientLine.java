package dev.chromastudio.core.palette;

public final class GradientLine {
    private static final double EDGE = 1e-6;

    static final double STRAIGHTEN = 0.36;

    private final int ax, ay, az;
    private final double dx, dy, dz, length;
    private final boolean straightened;

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

    public boolean straightened() { return straightened; }

    public boolean slanted() { return (dx != 0 ? 1 : 0) + (dy != 0 ? 1 : 0) + (dz != 0 ? 1 : 0) > 1; }

    private double along(int x, int y, int z) { return (x - ax) * dx + (y - ay) * dy + (z - az) * dz; }

    public boolean reaches(int x, int y, int z, double reach) {
        double s = along(x, y, z);
        return s >= -EDGE && s <= length + EDGE && distance(x, y, z) <= reach + EDGE;
    }

    public double distance(int x, int y, int z) {
        double s = along(x, y, z);
        double px = x - ax - s * dx, py = y - ay - s * dy, pz = z - az - s * dz;
        return Math.sqrt(px * px + py * py + pz * pz);
    }

    public int step(int x, int y, int z, int steps, double blend) {
        boolean shortLine = length + 1 < steps;
        double u = shortLine ? along(x, y, z) / length * (steps - 1) + 0.5 : (along(x, y, z) + 0.5) / (length + 1) * steps;
        if (blend > 0) u += (dither(x, y, z) - 0.5) * Math.min(blend, 1);
        return Math.clamp((long) Math.floor(u), 0, steps - 1);
    }

    static double dither(int x, int y, int z) {
        long h = (x >> Dither.BITS) * 0x9E3779B97F4A7C15L + (y >> Dither.BITS) * 0xC2B2AE3D27D4EB4FL + (z >> Dither.BITS) * 0x165667B19E3779F9L;
        h = (h ^ h >>> 30) * 0xBF58476D1CE4E5B9L;
        h = (h ^ h >>> 27) * 0x94D049BB133111EBL;
        int sx = (int) h, sy = (int) (h >>> 16), sz = (int) (h >>> 32);
        return Dither.TABLE[(x + sx & Dither.MASK) << 2 * Dither.BITS | (y + sy & Dither.MASK) << Dither.BITS | z + sz & Dither.MASK];
    }

    private static final class Dither {
        static final int BITS = 4, SIZE = 1 << BITS, MASK = SIZE - 1, CELLS = SIZE * SIZE * SIZE;
        static final double SPREAD = 1.5;
        static final double[] TABLE = make();

        private static double[] make() {
            double[] kernel = new double[CELLS];
            for (int i = 0; i < CELLS; i++) {
                int dx = wrap(i >> 2 * BITS), dy = wrap(i >> BITS & MASK), dz = wrap(i & MASK);
                kernel[i] = StrictMath.exp(-(dx * dx + dy * dy + dz * dz) / (2 * SPREAD * SPREAD));
            }
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

        private static int wrap(int d) { return Math.min(d, SIZE - d); }
    }
}
