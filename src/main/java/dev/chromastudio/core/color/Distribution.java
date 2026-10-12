package dev.chromastudio.core.color;

import java.util.ArrayList;
import java.util.List;

/** A texture or target summed up as at most eight colours, each with the share of the area it covers. The shares sum to 1. */
public final class Distribution {
    public static final int MAX_ENTRIES = 8;
    // What ofPixels asks k-means for. A block face at any scale rarely holds more distinct colour groups.
    private static final int PIXEL_CLUSTERS = 4;
    // Shares below this are rounding left over from the transport, not mass still to move.
    private static final double EMPTY = 1e-12;

    private final Color[] colours;
    private final double[] weights;

    private Distribution(Color[] colours, double[] weights) {
        this.colours = colours;
        this.weights = weights;
    }

    public static Distribution of(Color c) {
        return new Distribution(new Color[] {c}, new double[] {1});
    }

    /** One to eight colours with weights that are not negative and not all zero. The weights are scaled to sum to 1. */
    public static Distribution of(List<Color> colours, List<Double> weights) {
        int n = colours.size();
        if (n != weights.size()) throw new IllegalArgumentException("Each colour needs one weight");
        if (n < 1 || n > MAX_ENTRIES) throw new IllegalArgumentException("A distribution holds 1 to " + MAX_ENTRIES + " colours");
        double[] w = new double[n];
        double largest = 0;
        for (int i = 0; i < n; i++) {
            w[i] = weights.get(i);
            if (!(w[i] >= 0) || Double.isInfinite(w[i])) throw new IllegalArgumentException("Bad weight: " + w[i]);
            largest = Math.max(largest, w[i]);
        }
        if (largest <= 0) throw new IllegalArgumentException("The weights are all zero");
        // Dividing by the largest first keeps the sum of very large weights from overflowing.
        double total = 0;
        for (int i = 0; i < n; i++) total += w[i] /= largest;
        for (int i = 0; i < n; i++) w[i] /= total;
        return new Distribution(colours.toArray(new Color[0]), w);
    }

    /**
     * The dominant colours of some pixels: k-means in OKLab with up to four clusters, with clusters less than a
     * just-noticeable step apart merged. Pixels with alpha under {@code alphaCutoff} are left out and the rest
     * count by their alpha. With nothing opaque enough the result is black alone, so check coverage first.
     */
    public static Distribution ofPixels(int[] argb, int alphaCutoff) {
        return ofClusters(Kmeans.clusters(argb, PIXEL_CLUSTERS, alphaCutoff));
    }

    /** As {@link #ofPixels}, with each pixel counting by its weight in place of its alpha. */
    static Distribution ofPixels(int[] argb, double[] weights) {
        return ofClusters(Kmeans.clusters(argb, weights, PIXEL_CLUSTERS));
    }

    private static Distribution ofClusters(Kmeans.Clusters clusters) {
        List<Oklab> means = new ArrayList<>(List.of(clusters.means()));
        List<Double> shares = new ArrayList<>();
        for (double w : clusters.weights()) shares.add(w);
        if (means.isEmpty()) return of(Color.ofRgb(0));

        double step = Metric.OKLAB.justNoticeable();
        while (true) {
            int x = -1, y = -1;
            double closest = step;
            for (int i = 0; i < means.size(); i++) {
                for (int j = i + 1; j < means.size(); j++) {
                    double d = means.get(i).distance(means.get(j));
                    if (d < closest) {
                        closest = d;
                        x = i;
                        y = j;
                    }
                }
            }
            if (x < 0) break;
            double wx = shares.get(x), wy = shares.get(y), w = wx + wy;
            Oklab p = means.get(x), q = means.get(y);
            means.set(x, new Oklab((p.l() * wx + q.l() * wy) / w, (p.a() * wx + q.a() * wy) / w, (p.b() * wx + q.b() * wy) / w));
            shares.set(x, w);
            means.remove(y);
            shares.remove(y);
        }

        Oklab[] m = means.toArray(new Oklab[0]);
        double[] s = new double[m.length];
        for (int i = 0; i < s.length; i++) s[i] = shares.get(i);
        Kmeans.Clusters merged = Kmeans.sorted(m, s);
        Color[] out = new Color[merged.means().length];
        for (int i = 0; i < out.length; i++) out[i] = Color.of(merged.means()[i]);
        return new Distribution(out, merged.weights());
    }

    public List<Color> colours() {
        return List.of(colours);
    }

    public List<Double> weights() {
        List<Double> out = new ArrayList<>(weights.length);
        for (double w : weights) out.add(w);
        return List.copyOf(out);
    }

    /** The colour the whole blends to from far away: the weighted mean in linear light. */
    public Color mean() {
        double[] sum = new double[3];
        for (int i = 0; i < colours.length; i++) {
            sum[0] += Color.linear(colours[i].red()) * weights[i];
            sum[1] += Color.linear(colours[i].green()) * weights[i];
            sum[2] += Color.linear(colours[i].blue()) * weights[i];
        }
        return Color.ofRgb(Color.pack(sum));
    }

    /**
     * The earth mover's distance to another distribution: the least total of share moved times distance, to turn
     * one into the other. Against a single colour that is the weighted mean distance from it.
     */
    public double emd(Distribution other, Metric metric) {
        double[][] cost = new double[colours.length][other.colours.length];
        for (int i = 0; i < colours.length; i++) {
            for (int j = 0; j < other.colours.length; j++) cost[i][j] = metric.distance(colours[i], other.colours[j]);
        }
        return transport(weights, other.weights, cost);
    }

    /** Each colour multiplied by the tint's channels over 255, as the game tints a texture. */
    public Distribution tinted(int rgbMultiplier) {
        Color[] out = new Color[colours.length];
        for (int i = 0; i < out.length; i++) out[i] = colours[i].tinted(rgbMultiplier);
        return new Distribution(out, weights);
    }

    /**
     * The cheapest way to move {@code supply} onto {@code demand}, which must sum to the same, at {@code cost[i][j]}
     * a unit from i to j. It sends the mass a path at a time, each time down the cheapest path left, letting a
     * later path undo part of an earlier one. That gives the exact optimum.
     */
    static double transport(double[] supply, double[] demand, double[][] cost) {
        int n = supply.length, m = demand.length;
        double[] left = supply.clone(), wanted = demand.clone();
        double[][] flow = new double[n][m];
        // More than enough paths for eight by eight; a guard against rounding keeping the loop alive.
        for (int path = 0; path < 64 * (n + m); path++) {
            // Bellman-Ford from every source with mass left. Sources are nodes 0 to n-1, sinks n to n+m-1.
            double[] dist = new double[n + m];
            int[] from = new int[n + m];
            boolean any = false;
            for (int i = 0; i < n; i++) {
                dist[i] = left[i] > EMPTY ? 0 : Double.POSITIVE_INFINITY;
                any |= left[i] > EMPTY;
                from[i] = -1;
            }
            if (!any) break;
            for (int j = 0; j < m; j++) dist[n + j] = Double.POSITIVE_INFINITY;
            for (int round = 0; round < n + m; round++) {
                boolean changed = false;
                for (int i = 0; i < n; i++) {
                    for (int j = 0; j < m; j++) {
                        if (dist[i] + cost[i][j] < dist[n + j] - EMPTY) {
                            dist[n + j] = dist[i] + cost[i][j];
                            from[n + j] = i;
                            changed = true;
                        }
                        // Going back along a used cell takes its flow away again and refunds the cost.
                        if (flow[i][j] > EMPTY && dist[n + j] - cost[i][j] < dist[i] - EMPTY) {
                            dist[i] = dist[n + j] - cost[i][j];
                            from[i] = n + j;
                            changed = true;
                        }
                    }
                }
                if (!changed) break;
            }
            int end = -1;
            for (int j = 0; j < m; j++) {
                if (wanted[j] > EMPTY && (end < 0 || dist[n + j] < dist[end])) end = n + j;
            }
            if (end < 0 || Double.isInfinite(dist[end])) break;

            // The most the path can carry: what its source has, what its sink wants, and every flow it undoes.
            double amount = wanted[end - n];
            int node = end;
            while (from[node] >= 0) {
                int prev = from[node];
                if (node < n) amount = Math.min(amount, flow[node][prev - n]);
                node = prev;
            }
            amount = Math.min(amount, left[node]);
            left[node] -= amount;
            wanted[end - n] -= amount;
            node = end;
            while (from[node] >= 0) {
                int prev = from[node];
                if (node < n) flow[node][prev - n] -= amount;
                else flow[prev][node - n] += amount;
                node = prev;
            }
        }
        double total = 0;
        for (int i = 0; i < n; i++) {
            for (int j = 0; j < m; j++) total += flow[i][j] * cost[i][j];
        }
        return total;
    }
}
