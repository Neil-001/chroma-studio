package dev.chromastudio.core.export;

import java.util.Arrays;
import java.util.List;
import java.util.Random;

/**
 * Which palette entry sits at each cell of an exported schematic, by its index in the palette, with {@link #AIR}
 * for none. Every layout stands upright and is one block deep: x runs along the entries, y is up.
 *
 * <ul>
 * <li>Strip: one block per entry in a row.</li>
 * <li>Wall: one column per entry, {@code height} tall.</li>
 * <li>Patch grid: a {@code patch} by {@code patch} square per entry, in reading order from the top left, wrapped
 * to a grid about as wide as it is tall.</li>
 * <li>Noise clusters, height gradient and dither fill {@code width} by {@code height}, giving each entry its
 * weight's share of the cells: as patches of smooth noise, as bands from the first entry at the bottom to the last
 * at the top with ragged edges, or cell by cell at random.</li>
 * </ul>
 *
 * No side goes past {@link #MAX_SIZE}. The diorama layouts' sizes are brought into range; the other three leave
 * out the entries past the last one that fits, and {@link #placed} says how many made it.
 */
public final class Arrangement {
    public static final int AIR = -1, MAX_SIZE = 256;

    /** How far, in rows, a cell of the height gradient may be nudged up or down before its band is read off. */
    private static final double RAGGED_ROWS = 1.0;

    private final int sizeX, sizeY, sizeZ, placed;
    private final int[] cells;

    private Arrangement(int sizeX, int sizeY, int placed) {
        this.sizeX = sizeX; this.sizeY = sizeY; this.sizeZ = 1; this.placed = placed;
        cells = new int[sizeX * sizeY];
        Arrays.fill(cells, AIR);
    }

    /** Arranges one entry per weight. The weights only matter to the diorama layouts. There must be at least one. */
    public static Arrangement of(List<Integer> weights, LayoutOptions o) {
        int n = weights.size();
        if (n == 0) throw new IllegalArgumentException("There are no entries to arrange");
        int step = o.gap() ? 2 : 1, spare = o.gap() ? 1 : 0;
        switch (o.layout()) {
            case STRIP, WALL -> {
                int fit = Math.min(n, (MAX_SIZE + spare) / step);
                int height = o.layout() == Layout.WALL ? clamp(o.height()) : 1;
                Arrangement a = new Arrangement(fit * step - spare, height, fit);
                for (int i = 0; i < fit; i++) a.fill(i * step, 0, 1, height, i);
                return a;
            }
            case PATCH_GRID -> {
                int patch = clamp(o.patch()), pitch = patch + spare, most = (MAX_SIZE + spare) / pitch;
                int cols = Math.min(most, (int) Math.ceil(Math.sqrt(n)));
                int rows = Math.min(most, (n + cols - 1) / cols);
                int fit = Math.min(n, cols * rows);
                Arrangement a = new Arrangement(cols * pitch - spare, rows * pitch - spare, fit);
                // Reading order starts at the top, and y counts up from the bottom.
                for (int i = 0; i < fit; i++) a.fill(i % cols * pitch, (rows - 1 - i / cols) * pitch, patch, patch, i);
                return a;
            }
            default -> {
                Arrangement a = new Arrangement(clamp(o.width()), clamp(o.height()), n);
                double[] ends = cumulative(weights);
                switch (o.layout()) {
                    case NOISE_CLUSTERS -> a.noiseClusters(ends, o.seed());
                    case HEIGHT_GRADIENT -> a.heightGradient(ends, o.seed());
                    default -> a.dither(ends, o.seed());
                }
                return a;
            }
        }
    }

    public int sizeX() { return sizeX; }

    public int sizeY() { return sizeY; }

    public int sizeZ() { return sizeZ; }

    /** How many entries, counted from the first, the layout had room for. Fewer than were given only past {@link #MAX_SIZE}. */
    public int placed() { return placed; }

    /** The entry at a cell, or {@link #AIR}. */
    public int at(int x, int y, int z) {
        if (x < 0 || x >= sizeX || y < 0 || y >= sizeY || z != 0) throw new IndexOutOfBoundsException("(" + x + ", " + y + ", " + z + ")");
        return cells[y * sizeX + x];
    }

    private void fill(int x0, int y0, int w, int h, int entry) {
        for (int y = y0; y < y0 + h; y++) Arrays.fill(cells, y * sizeX + x0, y * sizeX + x0 + w, entry);
    }

    /**
     * Ranks the cells by a smooth noise field and deals them out in that order, so each entry gets exactly its share
     * of the cells however the noise happens to be spread. Neighbouring entries in the palette end up neighbours here.
     */
    private void noiseClusters(double[] ends, long seed) {
        int count = cells.length;
        // About three blobs along the shorter side, and never so fine that it reads as dither.
        double feature = Math.max(3.0, Math.min(sizeX, sizeY) / 3.0);
        double[] field = new double[count];
        Integer[] order = new Integer[count];
        for (int i = 0; i < count; i++) {
            double x = i % sizeX / feature, y = i / sizeX / feature;
            // The second, finer layer roughens the edges. The last term only settles ties.
            field[i] = valueNoise(x, y, seed) + 0.3 * valueNoise(2 * x, 2 * y, seed + 1) + 1e-9 * unit(seed + 2, i, 0);
            order[i] = i;
        }
        Arrays.sort(order, (p, q) -> Double.compare(field[p], field[q]));
        for (int rank = 0; rank < count; rank++) cells[order[rank]] = entryAt(ends, (rank + 0.5) / count);
    }

    private void heightGradient(double[] ends, long seed) {
        Random random = new Random(seed);
        for (int i = 0; i < cells.length; i++) {
            double row = i / sizeX + 0.5 + (random.nextDouble() * 2 - 1) * RAGGED_ROWS;
            cells[i] = entryAt(ends, row / sizeY);
        }
    }

    private void dither(double[] ends, long seed) {
        Random random = new Random(seed);
        for (int i = 0; i < cells.length; i++) cells[i] = entryAt(ends, random.nextDouble());
    }

    /** Where each entry's share ends, from 0 to 1. A weight under 1 counts as 1, as a palette's weights can't be lower. */
    private static double[] cumulative(List<Integer> weights) {
        double[] ends = new double[weights.size()];
        double total = 0;
        for (int i = 0; i < ends.length; i++) ends[i] = total += Math.max(1, weights.get(i));
        for (int i = 0; i < ends.length; i++) ends[i] /= total;
        return ends;
    }

    /** The entry whose share holds {@code t}. Anything below 0 is the first entry and anything from 1 the last. */
    private static int entryAt(double[] ends, double t) {
        for (int i = 0; i < ends.length - 1; i++) if (t < ends[i]) return i;
        return ends.length - 1;
    }

    private static int clamp(int size) { return Math.max(1, Math.min(MAX_SIZE, size)); }

    /** Random values on the whole-number grid, blended smoothly between. From 0 to 1. */
    private static double valueNoise(double x, double y, long seed) {
        long x0 = (long) Math.floor(x), y0 = (long) Math.floor(y);
        double fx = smooth(x - x0), fy = smooth(y - y0);
        double bottom = lerp(unit(seed, x0, y0), unit(seed, x0 + 1, y0), fx);
        double top = lerp(unit(seed, x0, y0 + 1), unit(seed, x0 + 1, y0 + 1), fx);
        return lerp(bottom, top, fy);
    }

    private static double smooth(double t) { return t * t * (3 - 2 * t); }

    private static double lerp(double a, double b, double t) { return a + (b - a) * t; }

    /** A repeatable value from 0 to 1 for a grid point, by the SplitMix64 mixer. */
    private static double unit(long seed, long x, long y) {
        long z = seed + x * 0x9E3779B97F4A7C15L + y * 0xC2B2AE3D27D4EB4FL;
        z = (z ^ z >>> 30) * 0xBF58476D1CE4E5B9L;
        z = (z ^ z >>> 27) * 0x94D049BB133111EBL;
        z ^= z >>> 31;
        return (z >>> 11) * 0x1.0p-53;
    }
}
