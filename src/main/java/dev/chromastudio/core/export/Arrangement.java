package dev.chromastudio.core.export;

import java.util.Arrays;
import java.util.List;
import java.util.Random;

public final class Arrangement {
    public static final int AIR = -1, MAX_SIZE = 256;

    private static final double RAGGED_ROWS = 1.0;

    private final int sizeX, sizeY, sizeZ, placed;
    private final int[] cells;

    private Arrangement(int sizeX, int sizeY, int placed) {
        this.sizeX = sizeX; this.sizeY = sizeY; this.sizeZ = 1; this.placed = placed;
        cells = new int[sizeX * sizeY];
        Arrays.fill(cells, AIR);
    }

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

    public int placed() { return placed; }

    public int at(int x, int y, int z) {
        if (x < 0 || x >= sizeX || y < 0 || y >= sizeY || z != 0) throw new IndexOutOfBoundsException("(" + x + ", " + y + ", " + z + ")");
        return cells[y * sizeX + x];
    }

    private void fill(int x0, int y0, int w, int h, int entry) {
        for (int y = y0; y < y0 + h; y++) Arrays.fill(cells, y * sizeX + x0, y * sizeX + x0 + w, entry);
    }

    private void noiseClusters(double[] ends, long seed) {
        int count = cells.length;
        double feature = Math.max(3.0, Math.min(sizeX, sizeY) / 3.0);
        double[] field = new double[count];
        Integer[] order = new Integer[count];
        for (int i = 0; i < count; i++) {
            int column = i % sizeX, line = i / sizeX;
            double x = column / feature, y = line / feature;
            field[i] = valueNoise(x, y, seed) + 0.3 * valueNoise(2 * x, 2 * y, seed + 1) + 1e-9 * unit(seed + 2, i, 0);
            order[i] = i;
        }
        Arrays.sort(order, (p, q) -> Double.compare(field[p], field[q]));
        for (int rank = 0; rank < count; rank++) cells[order[rank]] = entryAt(ends, (rank + 0.5) / count);
    }

    private void heightGradient(double[] ends, long seed) {
        Random random = new Random(seed);
        for (int i = 0; i < cells.length; i++) {
            int line = i / sizeX;
            double row = line + 0.5 + (random.nextDouble() * 2 - 1) * RAGGED_ROWS;
            cells[i] = entryAt(ends, row / sizeY);
        }
    }

    private void dither(double[] ends, long seed) {
        Random random = new Random(seed);
        for (int i = 0; i < cells.length; i++) cells[i] = entryAt(ends, random.nextDouble());
    }

    private static double[] cumulative(List<Integer> weights) {
        double[] ends = new double[weights.size()];
        double total = 0;
        for (int i = 0; i < ends.length; i++) ends[i] = total += Math.max(1, weights.get(i));
        for (int i = 0; i < ends.length; i++) ends[i] /= total;
        return ends;
    }

    private static int entryAt(double[] ends, double t) {
        for (int i = 0; i < ends.length - 1; i++) if (t < ends[i]) return i;
        return ends.length - 1;
    }

    private static int clamp(int size) { return Math.max(1, Math.min(MAX_SIZE, size)); }

    private static double valueNoise(double x, double y, long seed) {
        long x0 = (long) Math.floor(x), y0 = (long) Math.floor(y);
        double fx = smooth(x - x0), fy = smooth(y - y0);
        double bottom = lerp(unit(seed, x0, y0), unit(seed, x0 + 1, y0), fx);
        double top = lerp(unit(seed, x0, y0 + 1), unit(seed, x0 + 1, y0 + 1), fx);
        return lerp(bottom, top, fy);
    }

    private static double smooth(double t) { return t * t * (3 - 2 * t); }

    private static double lerp(double a, double b, double t) { return a + (b - a) * t; }

    private static double unit(long seed, long x, long y) {
        long z = seed + x * 0x9E3779B97F4A7C15L + y * 0xC2B2AE3D27D4EB4FL;
        z = (z ^ z >>> 30) * 0xBF58476D1CE4E5B9L;
        z = (z ^ z >>> 27) * 0x94D049BB133111EBL;
        z ^= z >>> 31;
        return (z >>> 11) * 0x1.0p-53;
    }
}
