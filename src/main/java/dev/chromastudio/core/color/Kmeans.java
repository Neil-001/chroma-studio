package dev.chromastudio.core.color;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

/** K-means clustering of pixels in OKLab, where distance follows how different colours look. */
public final class Kmeans {
    // A fixed seed, so one image always gives the same clusters and a cached index matches a rebuilt one.
    private static final long SEED = 0x43484F4D41L;
    private static final int MAX_ROUNDS = 64;

    private Kmeans() {}

    /** Cluster means with the share of the weight each holds, largest first. */
    record Clusters(Oklab[] means, double[] weights) {}

    /**
     * The means of up to {@code k} clusters as colours, largest cluster first. Pixels with alpha under
     * {@code alphaCutoff} are left out and the rest count by their alpha. Fewer than {@code k} come back when
     * the image has fewer distinct colours, and none when nothing is opaque enough.
     */
    public static List<Color> cluster(int[] argb, int k, int alphaCutoff) {
        Clusters found = clusters(argb, k, alphaCutoff);
        List<Color> out = new ArrayList<>(found.means.length);
        for (Oklab mean : found.means) out.add(Color.of(mean));
        return out;
    }

    static Clusters clusters(int[] argb, int k, int alphaCutoff) {
        double[] weights = new double[argb.length];
        for (int i = 0; i < argb.length; i++) {
            int alpha = argb[i] >>> 24;
            if (alpha >= alphaCutoff) weights[i] = alpha / 255.0;
        }
        return clusters(argb, weights, k);
    }

    /** Clusters the colour channels of {@code argb}, each pixel counting by its weight. Pixels of no weight are left out. */
    static Clusters clusters(int[] argb, double[] weights, int k) {
        if (k < 1) throw new IllegalArgumentException("k must be at least 1");
        // Equal pixels are pooled first: textures and pixel art have few distinct colours.
        Map<Integer, double[]> pooled = new LinkedHashMap<>();
        for (int i = 0; i < argb.length; i++) {
            if (weights[i] > 0) pooled.computeIfAbsent(argb[i] & 0xFFFFFF, key -> new double[1])[0] += weights[i];
        }
        Oklab[] points = new Oklab[pooled.size()];
        double[] pooledWeights = new double[pooled.size()];
        int n = 0;
        for (Map.Entry<Integer, double[]> e : pooled.entrySet()) {
            points[n] = Color.ofRgb(e.getKey()).oklab();
            pooledWeights[n++] = e.getValue()[0];
        }
        return run(points, pooledWeights, k, new Random(SEED));
    }

    private static Clusters run(Oklab[] points, double[] weights, int k, Random random) {
        int n = points.length;
        if (n == 0) return new Clusters(new Oklab[0], new double[0]);

        // k-means++ seeding: each new centre is drawn with odds in proportion to its squared distance from the
        // centres so far, which spreads them out.
        List<Oklab> centres = new ArrayList<>();
        double[] odds = weights.clone();
        double[] nearest = new double[n];
        Arrays.fill(nearest, Double.MAX_VALUE);
        while (centres.size() < k) {
            double total = 0;
            for (double o : odds) total += o;
            // Every point already sits on a centre.
            if (total <= 1e-12) break;
            double pick = random.nextDouble() * total;
            int chosen = n - 1;
            for (int i = 0; i < n; i++) {
                pick -= odds[i];
                if (pick < 0) {
                    chosen = i;
                    break;
                }
            }
            centres.add(points[chosen]);
            for (int i = 0; i < n; i++) {
                double d = points[i].distance(points[chosen]);
                nearest[i] = Math.min(nearest[i], d * d);
                odds[i] = weights[i] * nearest[i];
            }
        }

        Oklab[] means = centres.toArray(new Oklab[0]);
        int[] owner = new int[n];
        Arrays.fill(owner, -1);
        double[] held = new double[means.length];
        for (int round = 0; round < MAX_ROUNDS; round++) {
            boolean moved = false;
            for (int i = 0; i < n; i++) {
                int best = 0;
                for (int c = 1; c < means.length; c++) {
                    if (points[i].distance(means[c]) < points[i].distance(means[best])) best = c;
                }
                if (owner[i] != best) {
                    owner[i] = best;
                    moved = true;
                }
            }
            if (!moved) break;
            double[] l = new double[means.length], a = new double[means.length], b = new double[means.length];
            held = new double[means.length];
            for (int i = 0; i < n; i++) {
                int c = owner[i];
                l[c] += points[i].l() * weights[i];
                a[c] += points[i].a() * weights[i];
                b[c] += points[i].b() * weights[i];
                held[c] += weights[i];
            }
            for (int c = 0; c < means.length; c++) {
                // A cluster left with nothing keeps its place and is dropped at the end.
                if (held[c] > 0) means[c] = new Oklab(l[c] / held[c], a[c] / held[c], b[c] / held[c]);
            }
        }
        return sorted(means, held);
    }

    /** Drops empty clusters, puts the largest first and scales the weights to sum to 1. */
    static Clusters sorted(Oklab[] means, double[] held) {
        List<Integer> order = new ArrayList<>();
        double total = 0;
        for (int c = 0; c < means.length; c++) {
            if (held[c] > 0) {
                order.add(c);
                total += held[c];
            }
        }
        order.sort((x, y) -> Double.compare(held[y], held[x]));
        Oklab[] outMeans = new Oklab[order.size()];
        double[] outWeights = new double[order.size()];
        for (int i = 0; i < outMeans.length; i++) {
            outMeans[i] = means[order.get(i)];
            outWeights[i] = held[order.get(i)] / total;
        }
        return new Clusters(outMeans, outWeights);
    }
}
