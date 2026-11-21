package dev.chromastudio.core.color;

import java.util.ArrayList;
import java.util.List;

public final class Distribution {
    public static final int MAX_ENTRIES = 8;
    private static final int PIXEL_CLUSTERS = 4;
    private static final double EMPTY = 1e-15;
    private static final double OPTIMAL_SLACK = 1e-9;

    private final Color[] colours;
    private final double[] weights;

    private Distribution(Color[] colours, double[] weights) {
        this.colours = colours;
        this.weights = weights;
    }

    public static Distribution of(Color c) {
        return new Distribution(new Color[] {c}, new double[] {1});
    }

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
        double total = 0;
        for (int i = 0; i < n; i++) total += w[i] /= largest;
        for (int i = 0; i < n; i++) w[i] /= total;
        return new Distribution(colours.toArray(new Color[0]), w);
    }

    public static Distribution ofPixels(int[] argb, int alphaCutoff) {
        return ofClusters(Kmeans.clusters(argb, PIXEL_CLUSTERS, alphaCutoff));
    }

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

    public Color mean() {
        double[] sum = new double[3];
        for (int i = 0; i < colours.length; i++) {
            sum[0] += Color.linear(colours[i].red()) * weights[i];
            sum[1] += Color.linear(colours[i].green()) * weights[i];
            sum[2] += Color.linear(colours[i].blue()) * weights[i];
        }
        return Color.ofRgb(Color.pack(sum));
    }

    public double emd(Distribution other, Metric metric) {
        double[][] cost = new double[colours.length][other.colours.length];
        for (int i = 0; i < colours.length; i++) {
            for (int j = 0; j < other.colours.length; j++) cost[i][j] = metric.distance(colours[i], other.colours[j]);
        }
        return transport(weights, other.weights, cost);
    }

    public Distribution tinted(int rgbMultiplier) {
        Color[] out = new Color[colours.length];
        for (int i = 0; i < out.length; i++) out[i] = colours[i].tinted(rgbMultiplier);
        return new Distribution(out, weights);
    }

    static double transport(double[] supply, double[] demand, double[][] cost) {
        int n = supply.length, m = demand.length;
        double[] left = supply.clone(), wanted = demand.clone();
        double[][] flow = new double[n][m];
        double[] potential = new double[n + m];
        double[] dist = new double[n + m];
        int[] from = new int[n + m];
        boolean[] settled = new boolean[n + m];
        double sinkPotential = 0;
        while (true) {
            double highest = Double.NEGATIVE_INFINITY;
            for (int i = 0; i < n; i++) {
                if (left[i] > 0) highest = Math.max(highest, potential[i]);
            }
            if (highest == Double.NEGATIVE_INFINITY) break;
            for (int v = 0; v < n + m; v++) {
                dist[v] = v < n && left[v] > 0 ? highest - potential[v] : Double.POSITIVE_INFINITY;
                from[v] = -1;
                settled[v] = false;
            }
            int end = -1;
            double reach = Double.POSITIVE_INFINITY;
            while (true) {
                int u = -1;
                for (int v = 0; v < n + m; v++) {
                    if (!settled[v] && dist[v] < reach && (u < 0 || dist[v] < dist[u])) u = v;
                }
                if (u < 0) break;
                settled[u] = true;
                if (u >= n && wanted[u - n] > 0) {
                    double toSink = dist[u] + Math.max(0, potential[u] - sinkPotential);
                    if (toSink < reach) {
                        reach = toSink;
                        end = u;
                    }
                }
                if (u < n) {
                    for (int j = 0; j < m; j++) {
                        int v = n + j;
                        double through = dist[u] + Math.max(0, cost[u][j] + potential[u] - potential[v]);
                        if (!settled[v] && through < dist[v]) {
                            dist[v] = through;
                            from[v] = u;
                        }
                    }
                } else {
                    for (int i = 0; i < n; i++) {
                        double through = dist[u] + Math.max(0, potential[u] - cost[i][u - n] - potential[i]);
                        if (flow[i][u - n] > EMPTY && !settled[i] && through < dist[i]) {
                            dist[i] = through;
                            from[i] = u;
                        }
                    }
                }
            }
            if (end < 0) break;

            for (int v = 0; v < n + m; v++) potential[v] += Math.min(dist[v], reach);
            sinkPotential += reach;

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
        checkOptimal(flow, cost, potential);
        double total = 0;
        for (int i = 0; i < n; i++) {
            for (int j = 0; j < m; j++) total += flow[i][j] * cost[i][j];
        }
        return total;
    }

    private static void checkOptimal(double[][] flow, double[][] cost, double[] potential) {
        int n = flow.length;
        double scale = 1;
        for (double[] row : cost) {
            for (double c : row) scale = Math.max(scale, Math.abs(c));
        }
        double tolerance = scale * OPTIMAL_SLACK;
        for (int i = 0; i < n; i++) {
            for (int j = 0; j < flow[i].length; j++) {
                double reduced = cost[i][j] + potential[i] - potential[n + j];
                if (reduced < -tolerance || flow[i][j] > EMPTY && reduced > tolerance) {
                    throw new IllegalStateException("The transport plan is not optimal: cell " + i + ", " + j + " has reduced cost " + reduced);
                }
            }
        }
    }
}
