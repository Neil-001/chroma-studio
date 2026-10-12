package dev.chromastudio.core.palette;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.chromastudio.core.color.Color;
import dev.chromastudio.core.color.Distribution;
import dev.chromastudio.core.color.Metric;
import dev.chromastudio.core.color.Oklch;
import dev.chromastudio.core.palette.Gradient.Candidate;
import dev.chromastudio.core.palette.Gradient.Result;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;
import org.junit.jupiter.api.Test;

class GradientTest {
    private static Oklch grey(double l) {
        return new Oklch(l, 0, 0);
    }

    private static Candidate flat(String key, Oklch colour, double spread) {
        return new Candidate(key, Distribution.of(Color.ofOklch(colour)), spread);
    }

    private static double distance(Oklch a, Oklch b) {
        return Metric.OKLAB.distance(Color.ofOklch(a), Color.ofOklch(b));
    }

    private static List<Candidate> randomCandidates(int count, long seed) {
        Random random = new Random(seed);
        List<Candidate> out = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            // Two colours each, so the distance is a true earth mover's one and not just point to point.
            Color a = Color.ofRgb(random.nextInt(0x1000000)), b = Color.ofRgb(random.nextInt(0x1000000));
            double share = random.nextDouble(0.1, 0.9);
            out.add(new Candidate(i, Distribution.of(List.of(a, b), List.of(share, 1 - share)), random.nextDouble(0.2)));
        }
        return out;
    }

    private static List<Object> keys(Result result) {
        return result.steps().stream().map(Candidate::key).toList();
    }

    private static double[][] costs(List<Oklch> ideal, List<Candidate> candidates, Metric metric) {
        double[][] cost = new double[ideal.size()][candidates.size()];
        for (int i = 0; i < cost.length; i++) {
            Distribution target = Distribution.of(Color.ofOklch(ideal.get(i)));
            for (int j = 0; j < cost[i].length; j++) cost[i][j] = target.emd(candidates.get(j).distribution(), metric);
        }
        return cost;
    }

    private static double total(double[][] cost, int[] columns) {
        double total = 0;
        for (int i = 0; i < columns.length; i++) total += cost[i][columns[i]];
        return total;
    }

    /** The least total over every way of giving each row its own column, by trying them all. */
    private static double bruteForce(double[][] cost, int row, boolean[] taken) {
        if (row == cost.length) return 0;
        double best = Double.POSITIVE_INFINITY;
        for (int j = 0; j < cost[row].length; j++) {
            if (taken[j]) continue;
            taken[j] = true;
            best = Math.min(best, cost[row][j] + bruteForce(cost, row + 1, taken));
            taken[j] = false;
        }
        return best;
    }

    @Test
    void theAssignmentIsTheBestOnAHandSolvedMatrix() {
        // Every row's cheapest column is the first. The best total is 3 + 4 + 3 = 10 down the other diagonal;
        // giving each row in turn the cheapest column left gives 1 + 4 + 9 = 14.
        double[][] cost = {
                {1, 2, 3},
                {2, 4, 6},
                {3, 6, 9}};
        assertArrayEquals(new int[] {2, 1, 0}, Gradient.assign(cost));
        assertEquals(10, total(cost, Gradient.assign(cost)));
    }

    @Test
    void theAssignmentMatchesTryingEveryOne() {
        Random random = new Random(11);
        for (int round = 0; round < 200; round++) {
            int n = 1 + random.nextInt(5), m = n + random.nextInt(4);
            double[][] cost = new double[n][m];
            for (double[] row : cost) for (int j = 0; j < m; j++) row[j] = random.nextInt(20) / 4.0;
            int[] columns = Gradient.assign(cost);
            assertEquals(n, new HashSet<>(Arrays.stream(columns).boxed().toList()).size(), "a column was used twice");
            assertEquals(bruteForce(cost, 0, new boolean[m]), total(cost, columns), 1e-9, "round " + round);
        }
    }

    @Test
    void aStepGivesUpItsNearestBlockWhenThatIsBetterOverall() {
        // The first step is nearest to A, but the second needs A far more than the first does.
        List<Oklch> ideal = List.of(grey(0.50), grey(0.55));
        List<Candidate> candidates = List.of(flat("A", grey(0.54), 0), flat("B", grey(0.40), 0));
        Result result = Gradient.solve(ideal, candidates, Metric.OKLAB, false);
        assertEquals(List.of("B", "A"), keys(result));
        // About 0.10 and 0.01, against the 0.04 and 0.15 of taking the nearest in turn.
        assertEquals((distance(grey(0.50), grey(0.40)) + distance(grey(0.55), grey(0.54))) / 2, result.meanDeltaE(), 1e-12);
        assertEquals(0.055, result.meanDeltaE(), 5e-3);
        assertEquals(ideal, result.ideal());
    }

    @Test
    void noBlockIsUsedTwiceEvenWhenEveryStepWantsTheSameOne() {
        List<Candidate> candidates = new ArrayList<>();
        for (int i = 0; i < 20; i++) candidates.add(flat("c" + i, grey(0.30 + 0.02 * i), 0));
        List<Oklch> ideal = new ArrayList<>();
        for (int i = 0; i < 8; i++) ideal.add(grey(0.50));
        Result result = Gradient.solve(ideal, candidates, Metric.OKLAB, false);
        Set<Object> used = new HashSet<>(keys(result));
        assertEquals(8, used.size());
        // The eight nearest to 0.50 are 0.42 to 0.56. 0.58 ties with 0.42, so either may stand in for the other.
        for (int i = 7; i <= 13; i++) assertTrue(used.contains("c" + i), "c" + i);
        assertTrue(used.contains("c6") || used.contains("c14"));
    }

    @Test
    void candidatesWithTheSameKeyStillCountAsDifferent() {
        Candidate same = flat("same", grey(0.5), 0);
        Result result = Gradient.solve(List.of(grey(0.5), grey(0.5)), List.of(same, same), Metric.OKLAB, false);
        assertEquals(List.of("same", "same"), keys(result));
    }

    @Test
    void thePoolDoesNotChangeTheResult() {
        // Fewer steps than the pool keeps per step, and more: both must match solving over every candidate.
        for (int steps : new int[] {6, 24}) {
            List<Candidate> candidates = randomCandidates(600, steps);
            List<Oklch> ideal = Ramp.of(List.of(new Oklch(0.3, 0.1, 20), new Oklch(0.8, 0.12, 200)), steps, Easing.LINEAR, HueArc.SHORTEST);
            double[][] cost = costs(ideal, candidates, Metric.OKLAB);
            assertTrue(Gradient.pool(cost).length < 600, "the pool should leave candidates out");
            double best = total(cost, Gradient.assign(cost));

            Result result = Gradient.solve(ideal, candidates, Metric.OKLAB, false);
            assertEquals(best / steps, result.meanDeltaE(), 1e-12, steps + " steps");
            assertEquals(steps, new HashSet<>(keys(result)).size());
        }
    }

    @Test
    void thePoolHoldsEachStepsNearest() {
        double[][] cost = new double[2][40];
        for (int j = 0; j < 40; j++) {
            cost[0][j] = j;
            cost[1][j] = 39 - j;
        }
        int[] pool = Gradient.pool(cost);
        assertEquals(2 * Gradient.NEAREST, pool.length);
        for (int k = 0; k < Gradient.NEAREST; k++) {
            assertEquals(k, pool[k]);
            assertEquals(40 - Gradient.NEAREST + k, pool[Gradient.NEAREST + k]);
        }
    }

    @Test
    void matchingSpreadPrefersTheTextureOfTheEnds() {
        List<Oklch> ideal = List.of(grey(0.3), grey(0.5), grey(0.7));
        List<Candidate> candidates = List.of(
                flat("dark", grey(0.3), 0.10),
                flat("light", grey(0.7), 0.10),
                flat("smooth", grey(0.5), 0.00),
                flat("alike", grey(0.52), 0.10));
        Result plain = Gradient.solve(ideal, candidates, Metric.OKLAB, false);
        assertEquals(List.of("dark", "smooth", "light"), keys(plain));
        assertEquals(0, plain.meanDeltaE(), 1e-9);

        // "smooth" now costs its 0.10 of spread difference, more than the 0.02 of colour "alike" is off by.
        Result matched = Gradient.solve(ideal, candidates, Metric.OKLAB, true);
        assertEquals(List.of("dark", "alike", "light"), keys(matched));
        // The mean leaves the spread term out: only the middle step's colour is off.
        assertEquals(distance(grey(0.5), grey(0.52)) / 3, matched.meanDeltaE(), 1e-12);
    }

    @Test
    void theSpreadTargetIsTheMeanOfTheEndsBestBlocks() {
        // The ends' best blocks have spreads 0.02 and 0.10, so the target is 0.06 and "mid" beats both others.
        List<Oklch> ideal = List.of(grey(0.3), grey(0.5), grey(0.7));
        List<Candidate> candidates = List.of(
                flat("dark", grey(0.3), 0.02),
                flat("light", grey(0.7), 0.10),
                flat("low", grey(0.5), 0.02),
                flat("mid", grey(0.5), 0.06),
                flat("high", grey(0.5), 0.10));
        assertEquals(List.of("dark", "mid", "light"), keys(Gradient.solve(ideal, candidates, Metric.OKLAB, true)));
    }

    @Test
    void everyMetricWorks() {
        List<Oklch> ideal = Ramp.of(List.of(new Oklch(0.3, 0.1, 20), new Oklch(0.8, 0.12, 200)), 5, Easing.SMOOTH, HueArc.SHORTEST);
        List<Candidate> candidates = randomCandidates(50, 3);
        for (Metric metric : Metric.values()) {
            Result result = Gradient.solve(ideal, candidates, metric, true);
            assertEquals(5, new HashSet<>(keys(result)).size(), metric.toString());
            assertTrue(result.meanDeltaE() >= 0, metric.toString());
        }
    }

    @Test
    void thirtyTwoStepsFromTwoThousandCandidates() {
        List<Candidate> candidates = randomCandidates(2000, 5);
        List<Oklch> ideal = Ramp.of(List.of(new Oklch(0.25, 0.08, 30), new Oklch(0.6, 0.15, 150), new Oklch(0.9, 0.05, 260)),
                32, Easing.LINEAR, HueArc.SHORTEST);
        Result result = Gradient.solve(ideal, candidates, Metric.OKLAB, true);
        assertEquals(32, result.steps().size());
        assertEquals(32, new HashSet<>(keys(result)).size());
    }

    @Test
    void tooFewCandidatesAreRefused() {
        List<Oklch> ideal = List.of(grey(0.3), grey(0.5), grey(0.7));
        List<Candidate> two = List.of(flat("a", grey(0.3), 0), flat("b", grey(0.5), 0));
        assertThrows(IllegalArgumentException.class, () -> Gradient.solve(ideal, two, Metric.OKLAB, false));
        assertEquals(List.of(), Gradient.solve(List.of(), two, Metric.OKLAB, false).steps());
    }
}
