package dev.chromastudio.core.palette;

import dev.chromastudio.core.color.Color;
import dev.chromastudio.core.color.Distribution;
import dev.chromastudio.core.color.Metric;
import dev.chromastudio.core.color.Oklch;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/** Picks the blocks of a gradient: one for each ideal colour, none used twice, as close overall as can be. */
public final class Gradient {
    /** The fewest of each step's nearest candidates that go into the pool the assignment is solved over. */
    public static final int NEAREST = 16;

    /** Something that can fill a step: {@code key} is the caller's own handle for it, and {@code spread} its texture spread. */
    public record Candidate(Object key, Distribution distribution, double spread) {}

    /** The candidate chosen for each ideal colour, and the mean distance between the two, in the metric's units. */
    public record Result(List<Candidate> steps, List<Oklch> ideal, double meanDeltaE) {}

    private Gradient() {}

    /**
     * Assigns a different candidate to each ideal colour so that the total cost is the least possible. A step's
     * cost for a candidate is the earth mover's distance from the ideal colour, which is the candidate's mean
     * per-pixel ΔE from it. With {@code matchSpread}, how far the candidate's spread is from a target is added,
     * the target being the mean spread of the closest candidates to the first and last colours, so that the
     * steps in between look as busy as the ends.
     *
     * <p>Candidates count as different by their place in the list, whatever their keys.
     */
    public static Result solve(List<Oklch> ideal, List<Candidate> candidates, Metric metric, boolean matchSpread) {
        int n = ideal.size(), m = candidates.size();
        if (m < n) throw new IllegalArgumentException("A gradient of " + n + " steps needs at least " + n + " blocks, and there are " + m);
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

        int[] pool = pool(cost);
        double[][] pooled = new double[n][pool.length];
        for (int i = 0; i < n; i++) {
            for (int j = 0; j < pool.length; j++) pooled[i][j] = cost[i][pool[j]];
        }
        int[] chosen = assign(pooled);
        List<Candidate> steps = new ArrayList<>(n);
        double total = 0;
        for (int i = 0; i < n; i++) {
            int j = pool[chosen[i]];
            steps.add(candidates.get(j));
            total += colour[i][j];
        }
        return new Result(List.copyOf(steps), List.copyOf(ideal), total / n);
    }

    private static int nearest(double[] costs) {
        int best = 0;
        for (int j = 1; j < costs.length; j++) if (costs[j] < costs[best]) best = j;
        return best;
    }

    /**
     * The candidates worth solving over: each step's nearest few, in list order. Some best assignment gives every
     * step one of its n nearest, since a step given anything further always has one of those n going spare to
     * swap for. So taking at least as many as there are steps loses nothing, and keeps thousands of candidates
     * down to a few hundred.
     */
    static int[] pool(double[][] cost) {
        int n = cost.length, m = cost[0].length;
        int keep = Math.min(m, Math.max(NEAREST, n));
        boolean[] in = new boolean[m];
        Integer[] order = new Integer[m];
        for (double[] row : cost) {
            for (int j = 0; j < m; j++) order[j] = j;
            // The sort is stable, so equal costs keep list order and the pool is the same on every run.
            Arrays.sort(order, (a, b) -> Double.compare(row[a], row[b]));
            for (int k = 0; k < keep; k++) in[order[k]] = true;
        }
        int size = 0;
        for (boolean b : in) if (b) size++;
        int[] pool = new int[size];
        for (int j = 0, k = 0; j < m; j++) if (in[j]) pool[k++] = j;
        return pool;
    }

    /**
     * The Hungarian method for a cost matrix with no more rows than columns: gives each row its own column at the
     * least total cost. It adds the rows one at a time, each along the cheapest path that makes room for it, and
     * keeps a price on every row and column so that later paths stay cheapest. Exact, in rows² × columns steps.
     */
    static int[] assign(double[][] cost) {
        int n = cost.length, m = cost[0].length;
        // Rows and columns are numbered from 1 here, and column 0 holds the row being added.
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
            // Walk the path back, moving each row on it to the column it reached.
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
