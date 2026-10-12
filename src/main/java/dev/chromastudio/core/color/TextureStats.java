package dev.chromastudio.core.color;

/**
 * One face of one texture, summed up at every scale. Pixels are ARGB. Those with alpha under {@link #ALPHA_CUTOFF}
 * count as holes: they add nothing to the averages or to coverage. The rest count by their alpha, and averages are
 * taken in linear light, as colours blend when seen from far away.
 */
public final class TextureStats {
    public static final int ALPHA_CUTOFF = 16;
    private static final Scale[] SCALES = Scale.values();

    private final int[][] pixels;
    private final Distribution[] distributions;
    private final Color mean;
    private final double coverage;
    private final double spread;

    private TextureStats(int[][] pixels, Distribution[] distributions, Color mean, double coverage, double spread) {
        this.pixels = pixels;
        this.distributions = distributions;
        this.mean = mean;
        this.coverage = coverage;
        this.spread = spread;
    }

    /**
     * Stats for a {@code w} by {@code h} texture in rows from the top. Both sides must be multiples of 16; a larger
     * texture is box-reduced to 16 by 16 first. An animated strip must be averaged to one frame before this.
     */
    public static TextureStats of(int[] argb, int w, int h) {
        if (w < 16 || h < 16 || w % 16 != 0 || h % 16 != 0) {
            throw new IllegalArgumentException("Texture sides must be multiples of 16, not " + w + " by " + h);
        }
        if (argb.length != w * h) throw new IllegalArgumentException("Expected " + w * h + " pixels, not " + argb.length);

        // Each cell holds red, green and blue in linear light, already multiplied by alpha, then alpha. Summed that
        // way, halving a grid is a plain average of four cells and stays exact down to one.
        int bw = w / 16, bh = h / 16;
        double[][] grid = new double[256][4];
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int p = argb[y * w + x];
                int alpha = p >>> 24;
                if (alpha < ALPHA_CUTOFF) continue;
                double a = alpha / 255.0 / (bw * bh);
                double[] cell = grid[y / bh * 16 + x / bw];
                cell[0] += Color.linear(p >> 16 & 0xFF) * a;
                cell[1] += Color.linear(p >> 8 & 0xFF) * a;
                cell[2] += Color.linear(p & 0xFF) * a;
                cell[3] += a;
            }
        }

        int[][] pixels = new int[SCALES.length][];
        Distribution[] distributions = new Distribution[SCALES.length];
        for (Scale s : SCALES) {
            if (s != Scale.T16) grid = halved(grid, s.texels);
            pixels[s.ordinal()] = packed(grid);
            // The cutoff has been applied already; what is left counts however faint.
            distributions[s.ordinal()] = Distribution.ofPixels(pixels[s.ordinal()], 1);
        }
        int[] full = pixels[Scale.T16.ordinal()];
        return new TextureStats(pixels, distributions, Color.ofRgb(pixels[Scale.T1.ordinal()][0]), grid[0][3], spread(full));
    }

    private static double[][] halved(double[][] grid, int side) {
        double[][] out = new double[side * side][4];
        for (int y = 0; y < side * 2; y++) {
            for (int x = 0; x < side * 2; x++) {
                double[] cell = out[y / 2 * side + x / 2];
                for (int i = 0; i < 4; i++) cell[i] += grid[y * side * 2 + x][i] / 4;
            }
        }
        return out;
    }

    private static int[] packed(double[][] grid) {
        int[] out = new int[grid.length];
        for (int i = 0; i < out.length; i++) {
            double a = grid[i][3];
            int alpha = (int) Math.round(a * 255);
            if (alpha == 0) continue;
            out[i] = alpha << 24 | Color.pack(new double[] {grid[i][0] / a, grid[i][1] / a, grid[i][2] / a});
        }
        return out;
    }

    /** The root mean square OKLab distance of the pixels from their mean, counting each by its alpha. */
    private static double spread(int[] argb) {
        double l = 0, a = 0, b = 0, total = 0;
        Oklab[] labs = new Oklab[argb.length];
        for (int i = 0; i < argb.length; i++) {
            double w = (argb[i] >>> 24) / 255.0;
            if (w == 0) continue;
            labs[i] = Color.ofRgb(argb[i]).oklab();
            l += labs[i].l() * w;
            a += labs[i].a() * w;
            b += labs[i].b() * w;
            total += w;
        }
        if (total == 0) return 0;
        Oklab centre = new Oklab(l / total, a / total, b / total);
        double squares = 0;
        for (int i = 0; i < argb.length; i++) {
            if (labs[i] == null) continue;
            double d = labs[i].distance(centre);
            squares += d * d * (argb[i] >>> 24) / 255.0;
        }
        return Math.sqrt(squares / total);
    }

    /** The average colour, black when nothing is opaque. Box averaging keeps it, so it is the same at every scale. */
    public Color mean(Scale s) { return mean; }

    /** The dominant colours at a scale, black alone when nothing is opaque. */
    public Distribution distribution(Scale s) { return distributions[s.ordinal()]; }

    /** How much of the face is filled, 0 to 1: the mean alpha, with holes as 0. */
    public double coverage() { return coverage; }

    /** The root mean square OKLab distance of the full-scale pixels from their mean. */
    public double spread() { return spread; }

    public Badge badge() { return Badge.of(spread); }

    /** The texture at a scale as ARGB in rows from the top, {@code s.texels} to a side, for thumbnails. */
    public int[] pixels(Scale s) { return pixels[s.ordinal()].clone(); }

    /**
     * The stats with every scale multiplied by a tint, each channel by the tint's over 255. The game also tints
     * after it has scaled a texture down, so this matches what it draws.
     */
    public TextureStats tinted(int rgb) {
        int[][] tintedPixels = new int[SCALES.length][];
        Distribution[] tintedDistributions = new Distribution[SCALES.length];
        for (int s = 0; s < SCALES.length; s++) {
            tintedPixels[s] = new int[pixels[s].length];
            for (int i = 0; i < tintedPixels[s].length; i++) tintedPixels[s][i] = Color.tint(pixels[s][i], rgb);
            tintedDistributions[s] = distributions[s].tinted(rgb);
        }
        return new TextureStats(tintedPixels, tintedDistributions, mean.tinted(rgb), coverage, spread(tintedPixels[Scale.T16.ordinal()]));
    }
}
