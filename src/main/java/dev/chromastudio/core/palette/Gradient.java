package dev.chromastudio.core.palette;

import dev.chromastudio.core.color.Color;
import dev.chromastudio.core.color.Distribution;
import dev.chromastudio.core.color.Metric;
import dev.chromastudio.core.color.Oklch;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class Gradient {
    public static final int NEAREST = 16;

    public record Candidate(Object key, Distribution distribution, double spread) {}

    public record Result(List<Candidate> steps, List<Oklch> ideal, double meanDeltaE) {}

    public record Option(Candidate candidate, double deltaE) {}

    private Gradient() {}

    public static Result solve(List<Oklch> ideal, List<Candidate> all, Metric metric, boolean matchSpread) {
        return solve(ideal, all, metric, matchSpread, Map.of());
    }

    public static Result solve(List<Oklch> ideal, List<Candidate> all, Metric metric, boolean matchSpread, Map<Integer, ?> pinned) {
        List<Candidate> candidates = new ArrayList<>(all.size());
        Set<Object> keys = new HashSet<>();
        for (Candidate c : all) if (keys.add(c.key())) candidates.add(c);
        int n = ideal.size(), m = candidates.size();
        if (m < n) throw new IllegalArgumentException("A gradient of " + n + " steps needs at least " + n + " different blocks, and there are " + m);
        if (n == 0) return new Result(List.of(), List.of(), 0);

        double[][] colour = new double[n][m];
        for (int i = 0; i < n; i++) {
            Distribution target = Distribution.of(Color.ofOklch(ideal.get(i)));
            for (int j = 0; j < m; j++) colour[i][j] = target.emd(candidates.get(j).distribution(), metric);
        }
        double[][] cost = colour;
        if (matchSpread) {
            double target = (candidates.get(nearest(colour[0])).spread() + candidates.get(nearest(colour[n - 1])).spread()) / 2;
            cost = new double[n][m];
            for (int i = 0; i < n; i++) {
                for (int j = 0; j < m; j++) cost[i][j] = colour[i][j] + Math.abs(candidates.get(j).spread() - target);
            }
        }

        int[] column = new int[n];
        Arrays.fill(column, -1);
        boolean[] taken = new boolean[m];
        if (!pinned.isEmpty()) {
            Map<Object, Integer> byKey = new HashMap<>();
            for (int j = 0; j < m; j++) byKey.put(candidates.get(j).key(), j);
            for (int i = 0; i < n; i++) {
                Integer j = pinned.containsKey(i) ? byKey.get(pinned.get(i)) : null;
                if (j == null || taken[j]) continue;
                column[i] = j;
                taken[j] = true;
            }
        }
        int[] rows = new int[n], columns = new int[m];
        int free = 0, left = 0;
        for (int i = 0; i < n; i++) if (column[i] < 0) rows[free++] = i;
        for (int j = 0; j < m; j++) if (!taken[j]) columns[left++] = j;
        if (free > 0) {
            double[][] open = new double[free][left];
            for (int i = 0; i < free; i++) {
                for (int j = 0; j < left; j++) open[i][j] = cost[rows[i]][columns[j]];
            }
            int[] pool = pool(open);
            double[][] pooled = new double[free][pool.length];
            for (int i = 0; i < free; i++) {
                for (int j = 0; j < pool.length; j++) pooled[i][j] = open[i][pool[j]];
            }
            int[] chosen = assign(pooled);
            for (int i = 0; i < free; i++) column[rows[i]] = columns[pool[chosen[i]]];
        }
        List<Candidate> steps = new ArrayList<>(n);
        double total = 0;
        for (int i = 0; i < n; i++) {
            int j = column[i];
            steps.add(candidates.get(j));
            total += colour[i][j];
        }
        return new Result(List.copyOf(steps), List.copyOf(ideal), total / n);
    }

    public static List<Option> closest(Oklch ideal, List<Candidate> all, Metric metric, Set<?> used, int limit) {
        Distribution target = Distribution.of(Color.ofOklch(ideal));
        List<Option> options = new ArrayList<>();
        Set<Object> keys = new HashSet<>();
        for (Candidate c : all) {
            if (keys.add(c.key()) && !used.contains(c.key())) options.add(new Option(c, target.emd(c.distribution(), metric)));
        }
        options.sort((a, b) -> Double.compare(a.deltaE(), b.deltaE()));
        return List.copyOf(options.subList(0, Math.clamp(limit, 0, options.size())));
    }

    private static int nearest(double[] costs) {
        int best = 0;
        for (int j = 1; j < costs.length; j++) if (costs[j] < costs[best]) best = j;
        return best;
    }

    static int[] pool(double[][] cost) {
        int n = cost.length, m = cost[0].length;
        int keep = Math.min(m, Math.max(NEAREST, n));
        boolean[] in = new boolean[m];
        Integer[] order = new Integer[m];
        for (double[] row : cost) {
            for (int j = 0; j < m; j++) order[j] = j;
            Arrays.sort(order, (a, b) -> Double.compare(row[a], row[b]));
            for (int k = 0; k < keep; k++) in[order[k]] = true;
        }
        int size = 0;
        for (boolean b : in) if (b) size++;
        int[] pool = new int[size];
        for (int j = 0, k = 0; j < m; j++) if (in[j]) pool[k++] = j;
        return pool;
    }

    static int[] assign(double[][] cost) {
        int n = cost.length, m = cost[0].length;
        double[] rowPrice = new double[n + 1], columnPrice = new double[m + 1];
        int[] rowOf = new int[m + 1], previous = new int[m + 1];
        double[] slack = new double[m + 1];
        boolean[] used = new boolean[m + 1];
        for (int row = 1; row <= n; row++) {
            rowOf[0] = row;
            Arrays.fill(slack, Double.POSITIVE_INFINITY);
            Arrays.fill(used, false);
            int column = 0;
            do {
                used[column] = true;
                int r = rowOf[column], next = 0;
                double least = Double.POSITIVE_INFINITY;
                for (int j = 1; j <= m; j++) {
                    if (used[j]) continue;
                    double reduced = cost[r - 1][j - 1] - rowPrice[r] - columnPrice[j];
                    if (reduced < slack[j]) {
                        slack[j] = reduced;
                        previous[j] = column;
                    }
                    if (slack[j] < least) {
                        least = slack[j];
                        next = j;
                    }
                }
                for (int j = 0; j <= m; j++) {
                    if (used[j]) {
                        rowPrice[rowOf[j]] += least;
                        columnPrice[j] -= least;
                    } else {
                        slack[j] -= least;
                    }
                }
                column = next;
            } while (rowOf[column] != 0);
            do {
                int before = previous[column];
                rowOf[column] = rowOf[before];
                column = before;
            } while (column != 0);
        }
        int[] out = new int[n];
        for (int j = 1; j <= m; j++) if (rowOf[j] != 0) out[rowOf[j] - 1] = j - 1;
        return out;
    }
}
