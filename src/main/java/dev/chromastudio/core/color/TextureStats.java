package dev.chromastudio.core.color;

public final class TextureStats {
    public static final int ALPHA_CUTOFF = 16;
    private static final Scale[] SCALES = Scale.values();

    private final int[][] pixels;
    private final Distribution[] distributions;
    private final Color mean;
    private final double coverage;
    private final double spread;
    private final double[] fullAlpha;

    private TextureStats(int[][] pixels, Distribution[] distributions, Color mean, double coverage, double[] fullAlpha) {
        this.fullAlpha = fullAlpha;
        this.spread = spread(pixels[Scale.T16.ordinal()], fullAlpha);
        this.pixels = pixels;
        this.distributions = distributions;
        this.mean = mean;
        this.coverage = coverage;
    }

    public static TextureStats of(int[] argb, int w, int h) {
        if (w < 16 || h < 16 || w % 16 != 0 || h % 16 != 0) {
            throw new IllegalArgumentException("Texture sides must be multiples of 16, not " + w + " by " + h);
        }
        if (argb.length != w * h) throw new IllegalArgumentException("Expected " + w * h + " pixels, not " + argb.length);

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
        double[] fullAlpha = null;
        for (Scale s : SCALES) {
            if (s != Scale.T16) grid = halved(grid, s.texels);
            double[] alpha = new double[grid.length];
            for (int i = 0; i < alpha.length; i++) alpha[i] = grid[i][3];
            if (s == Scale.T16) fullAlpha = alpha;
            pixels[s.ordinal()] = packed(grid);
            distributions[s.ordinal()] = Distribution.ofPixels(pixels[s.ordinal()], alpha);
        }
        return new TextureStats(pixels, distributions, Color.ofRgb(pixels[Scale.T1.ordinal()][0]), grid[0][3], fullAlpha);
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
            if (a == 0) continue;
            int alpha = (int) Math.round(a * 255);
            out[i] = alpha << 24 | Color.pack(new double[] {grid[i][0] / a, grid[i][1] / a, grid[i][2] / a});
        }
        return out;
    }

    private static double spread(int[] argb, double[] weights) {
        double l = 0, a = 0, b = 0, total = 0;
        Oklab[] labs = new Oklab[argb.length];
        for (int i = 0; i < argb.length; i++) {
            if (weights[i] == 0) continue;
            labs[i] = Color.ofRgb(argb[i]).oklab();
            l += labs[i].l() * weights[i];
            a += labs[i].a() * weights[i];
            b += labs[i].b() * weights[i];
            total += weights[i];
        }
        if (total == 0) return 0;
        Oklab centre = new Oklab(l / total, a / total, b / total);
        double squares = 0;
        for (int i = 0; i < argb.length; i++) {
            if (labs[i] == null) continue;
            double d = labs[i].distance(centre);
            squares += d * d * weights[i];
        }
        return Math.sqrt(squares / total);
    }

    public Color mean(Scale s) { return mean; }

    public Distribution distribution(Scale s) { return distributions[s.ordinal()]; }

    public double coverage() { return coverage; }

    public double spread() { return spread; }

    public Badge badge() { return Badge.of(spread); }

    public int[] pixels(Scale s) { return pixels[s.ordinal()].clone(); }

    public TextureStats tinted(int rgb) {
        int[][] tintedPixels = new int[SCALES.length][];
        Distribution[] tintedDistributions = new Distribution[SCALES.length];
        for (int s = 0; s < SCALES.length; s++) {
            tintedPixels[s] = new int[pixels[s].length];
            for (int i = 0; i < tintedPixels[s].length; i++) tintedPixels[s][i] = Color.tint(pixels[s][i], rgb);
            tintedDistributions[s] = distributions[s].tinted(rgb);
        }
        return new TextureStats(tintedPixels, tintedDistributions, mean.tinted(rgb), coverage, fullAlpha);
    }
}
