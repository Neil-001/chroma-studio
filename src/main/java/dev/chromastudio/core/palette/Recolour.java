package dev.chromastudio.core.palette;

import dev.chromastudio.core.color.Color;
import dev.chromastudio.core.color.Metric;
import dev.chromastudio.core.color.Oklab;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.IntPredicate;

/**
 * Decides which of a palette's blocks each block of a build becomes when the build is recoloured into the palette.
 * Axiom's Script Brush scripts and the palette brush both ask this, so the two give the same blocks.
 *
 * <p>Picking each block's closest colour alone makes most of a build one block when the palette is small or far
 * from the build's colours, and its shading is lost. {@link Mode#SHADING} keeps the shading. It sorts the build's
 * colours from darkest to lightest into shades, and hands the shades out in order to the palette's blocks, sorted the
 * same way, so a darker block never becomes a lighter one than a lighter block does. A variant that can't be told from
 * a block of its own material, such as cracked stone bricks beside stone bricks, joins that block's shade; different
 * materials of nearly one colour, such as cobblestone and andesite, which a builder mixes on purpose, stay apart.
 *
 * <p>How the shades are handed out goes by how much of each there is. Lay the build's shades along a line, each as
 * long as the share of the build it covers, and the palette's blocks along another, each as long as its weight; each
 * shade takes the block at the same place on the palette's line. So the build's main material takes one of the
 * palette's main blocks, such as a desert's sandstone, and a small accent an accent. While the palette has blocks
 * enough, every shade takes a block of its own, moved along as little as that needs. When there are more shades than
 * blocks, every block still takes at least one, and the rest go by weight.
 *
 * <p>Glass and other blocks you can see through are kept apart from solid ones: a solid block only becomes a
 * see-through one when the palette has nothing else it may become, and a see-through block only ever becomes another
 * see-through one, so windows stay windows.
 *
 * <p>What it answers never changes after it is made, so any number of threads may ask it at once.
 */
public final class Recolour {
    /** How a block's new block is picked. */
    public enum Mode {
        /** By its shade's place between the build's darkest and lightest, so the build's shading survives. */
        SHADING,
        /** The palette's block closest in colour. */
        CLOSEST
    }

    /**
     * A block as the mapping sees it: its colour, whether it can be seen through, as glass can, and its weight: for a
     * palette's block how much of a build the palette means it to cover, and for a build's how much of the build it
     * is, such as how many of it there are. {@code family} names its material with variants folded in, so cracked
     * and mossy stone bricks share stone bricks' family; null when it isn't known, and then a block that can't be told
     * from another by colour shares its shade.
     */
    public record Look(Color colour, boolean seeThrough, int weight, String family) {
        /** A look of no known family. */
        public Look(Color colour, boolean seeThrough, int weight) { this(colour, seeThrough, weight, null); }

        /** A look with a weight of 1 and no known family. */
        public Look(Color colour, boolean seeThrough) { this(colour, seeThrough, 1); }
    }

    private final Mode mode;
    private final Oklab[] palette;
    private final boolean[] paletteClear;
    private final int[] weights;
    // The palette's blocks from darkest to lightest, and in the palette's order among equals.
    private final int[] byLightness;
    private final Shades buildSolid, buildClear;
    // The block each shade of the build takes, for each class and set of blocks a shape allows.
    private final Map<List<Object>, int[]> tables = new ConcurrentHashMap<>();

    /**
     * @param palette the blocks a build may become, in the palette's order, which breaks ties
     * @param build   the blocks of the build being recoloured, which set its darkest and lightest and how much of
     *                it each shade is; ignored when picking the closest colour
     */
    public Recolour(List<Look> palette, List<Look> build, Mode mode) {
        this.mode = mode;
        int n = palette.size();
        this.palette = new Oklab[n];
        this.paletteClear = new boolean[n];
        this.weights = new int[n];
        for (int i = 0; i < n; i++) {
            this.palette[i] = palette.get(i).colour().oklab();
            this.paletteClear[i] = palette.get(i).seeThrough();
            // A weight below 1 counts as 1, so every block keeps a share.
            this.weights[i] = Math.max(1, palette.get(i).weight());
        }
        List<Integer> order = new ArrayList<>();
        for (int i = 0; i < n; i++) order.add(i);
        order.sort(Comparator.comparingDouble(i -> this.palette[i].l()));
        this.byLightness = order.stream().mapToInt(Integer::intValue).toArray();
        this.buildSolid = new Shades(build.stream().filter(l -> !l.seeThrough()).toList());
        this.buildClear = new Shades(build.stream().filter(Look::seeThrough).toList());
    }

    /**
     * The index of the palette block that a block becomes, chosen among those {@code allowed} says it may become,
     * such as those of a material that has its shape. -1 leaves the block as it is: a see-through block when none
     * allowed is see-through, or any block when none is allowed at all.
     */
    public int pick(Look block, IntPredicate allowed) {
        boolean clear = block.seeThrough() || !any(allowed, false);
        if (!any(allowed, clear)) return -1;
        Oklab c = block.colour().oklab();
        if (mode == Mode.CLOSEST) {
            int best = -1;
            double bestDistance = Double.POSITIVE_INFINITY;
            for (int i = 0; i < palette.length; i++) {
                if (paletteClear[i] != clear || !allowed.test(i)) continue;
                double d = c.distance(palette[i]);
                if (d < bestDistance) {
                    best = i;
                    bestDistance = d;
                }
            }
            return best;
        }
        // Only the blocks this one may become count, so one it may not, such as a block without its shape, takes
        // nothing from the others.
        int[] eligible = Arrays.stream(byLightness).filter(i -> paletteClear[i] == clear && allowed.test(i)).toArray();
        Shades build = block.seeThrough() ? buildClear : buildSolid;
        // Blocks of one shape and class share the same table, so it is worked out once for them all.
        int[] table = tables.computeIfAbsent(List.of(block.seeThrough(), Arrays.toString(eligible)), _ -> table(eligible, build));
        return table[build.of(c, block.family())];
    }

    /** Which of {@code eligible}, sorted from darkest to lightest, each of a build's shades takes. */
    private int[] table(int[] eligible, Shades build) {
        int n = eligible.length, shades = build.count();
        if (shades <= n) return matched(eligible, build);
        int[] out = new int[shades];
        // More shades than blocks, so some must share. Every block takes one shade, and the rest go by weight, by
        // largest remainder. Weights count in full once the build has twice as many shades as there are blocks, and
        // barely at all with only a few more, so one extra shade doesn't hand the bulk of the build to the heaviest.
        double strength = Math.min(1, (shades - n) / (double) n), total = 0;
        double[] share = new double[n];
        for (int j = 0; j < n; j++) total += share[j] = Math.pow(weights[eligible[j]], strength);
        int[] count = new int[n];
        int left = shades - n;
        Integer[] byRemainder = new Integer[n];
        double[] remainder = new double[n];
        for (int j = 0; j < n; j++) {
            double exact = (shades - n) * share[j] / total;
            count[j] = 1 + (int) exact;
            remainder[j] = exact - (int) exact;
            left -= (int) exact;
            byRemainder[j] = j;
        }
        // Largest remainders first, and the darker block among equals.
        Arrays.sort(byRemainder, Comparator.comparingDouble((Integer j) -> -remainder[j]).thenComparingInt(j -> j));
        for (int r = 0; r < left; r++) count[byRemainder[r]]++;
        for (int j = 0, k = 0; j < n; j++) for (int c = 0; c < count[j]; c++) out[k++] = eligible[j];
        return out;
    }

    /**
     * What two neighbouring shades sharing a block costs, as if the smaller sat this far along the line from its
     * place: enough that shades stay apart wherever the palette has room, not so much that the build's main material
     * is pushed off the palette's main block to make room for its accents.
     */
    private static final double SHARING = 2;

    /**
     * A block for each of a build's shades, when there are no more shades than blocks, in order from darkest to
     * lightest. The build's darkest shade takes the palette's darkest block and its lightest the lightest, so the
     * shading spans the palette, and the largest shade between them, such as a house's walls, takes the palette's
     * heaviest block between its ends, such as a desert's sandstone. Each other shade has a place from 0 to 1, the
     * middle of its share of the build, and each block a stretch of the same line, as long as its weight. A shade fits
     * a block whose stretch holds its place, and less well the further outside it, by how much of the build the shade
     * is. Of all the ways to hand out blocks in order, this takes the best fit, where two neighbouring shades share a
     * block only when keeping them apart would put a larger one far from its place.
     */
    private int[] matched(int[] eligible, Shades build) {
        int n = eligible.length, shades = build.count();
        if (shades == 0) return new int[] {eligible[(n - 1) / 2]};
        double[] at = new double[shades];
        double before = 0;
        for (int k = 0; k < shades; k++) {
            at[k] = (before + build.amount(k) / 2) / build.total();
            before += build.amount(k);
        }
        double weight = 0;
        for (int i : eligible) weight += weights[i];
        double[] from = new double[n], to = new double[n];
        double upTo = 0;
        for (int j = 0; j < n; j++) {
            from[j] = upTo / weight;
            upTo += weights[eligible[j]];
            to[j] = upTo / weight;
        }
        // The largest shade between the ends, and the heaviest block between them, nearest its place among equals.
        int main = -1, heaviest = -1;
        if (shades >= 3 && n >= 3) {
            for (int k = 1; k < shades - 1; k++) if (main < 0 || build.amount(k) > build.amount(main)) main = k;
            // Only a block that leaves room for the shades on either side of it, all but one at most, so pinning the
            // main shade doesn't crowd the rest onto a few blocks.
            for (int j = 1; j < n - 1; j++) {
                int crowded = Math.max(0, main - j) + Math.max(0, shades - 1 - main - (n - 1 - j));
                if (crowded > 1) continue;
                if (heaviest < 0 || weights[eligible[j]] > weights[eligible[heaviest]]
                        || weights[eligible[j]] == weights[eligible[heaviest]] && away(at[main], from[j], to[j]) < away(at[main], from[heaviest], to[heaviest])) {
                    heaviest = j;
                }
            }
            if (heaviest < 0) main = -1;
        }
        // best[k][j]: the least cost for shades 0..k with shade k on block j; back[k][j] the block shade k-1 took.
        double[][] best = new double[shades][n];
        int[][] back = new int[shades][n];
        for (int k = 0; k < shades; k++) {
            double share = build.amount(k) / build.total();
            for (int j = 0; j < n; j++) {
                boolean pinned = shades > 1 && (k == 0 && j != 0 || k == shades - 1 && j != n - 1) || k == main && j != heaviest;
                best[k][j] = Double.POSITIVE_INFINITY;
                if (pinned) continue;
                double fit = share * away(at[k], from[j], to[j]);
                if (k == 0) {
                    best[k][j] = fit;
                    continue;
                }
                for (int i = 0; i <= j; i++) {
                    if (best[k - 1][i] == Double.POSITIVE_INFINITY) continue;
                    double shared = i == j ? SHARING * Math.min(share, build.amount(k - 1) / build.total()) : 0;
                    double cost = best[k - 1][i] + fit + shared;
                    if (cost < best[k][j]) {
                        best[k][j] = cost;
                        back[k][j] = i;
                    }
                }
            }
        }
        int last = 0;
        for (int j = 1; j < n; j++) if (best[shades - 1][j] < best[shades - 1][last]) last = j;
        int[] out = new int[shades];
        for (int k = shades - 1, j = last; k >= 0; j = back[k][j], k--) out[k] = eligible[j];
        return out;
    }

    /** How far a place on a line is from a stretch of it, or 0 inside it. */
    private static double away(double at, double from, double to) { return Math.max(0, Math.max(from - at, at - to)); }

    /** {@link #pick} with every palette block allowed. */
    public int pick(Look block) { return pick(block, _ -> true); }

    private boolean any(IntPredicate allowed, boolean clear) {
        for (int i = 0; i < palette.length; i++) if (paletteClear[i] == clear && allowed.test(i)) return true;
        return false;
    }

    /**
     * A build's colours sorted into shades, from darkest to lightest. A colour joins the latest shade when it can't be
     * told from where that shade starts, unless both families are known and differ, or when it is of that shade's
     * family and only a little further off; otherwise it starts a shade of its own. Each shade also knows how much of
     * the build it is: the sum of the square roots of its looks' weights.
     */
    static final class Shades {
        /** How many times further than it can be told apart a block of the same family may be and still share a shade. */
        private static final double VARIANT = 3;

        private record Key(Oklab colour, String family) {}

        // The colour and family each shade starts at, darkest first, how much of the build each shade is, and the
        // shade each of the build's colours belongs to.
        private final List<Oklab> starts = new ArrayList<>();
        private final List<String> families = new ArrayList<>();
        private final List<Double> amounts = new ArrayList<>();
        private final Map<Key, Integer> shadeOf = new HashMap<>();
        private double total;

        Shades(List<Look> looks) {
            List<Look> sorted = new ArrayList<>(looks);
            sorted.sort(Comparator.comparingDouble(l -> l.colour().oklab().l()));
            for (Look l : sorted) {
                Oklab c = l.colour().oklab();
                Key key = new Key(c, l.family());
                Integer shade = shadeOf.get(key);
                // Only the latest shade may take it, so shades stay in order from darkest to lightest: when it can't be
                // told from where that shade starts, unless both families are known and differ, or when it's a worn or
                // overgrown variant of that shade's family a little further off.
                if (shade == null && !starts.isEmpty()) {
                    int s = starts.size() - 1;
                    double d = c.distance(starts.get(s)), jnd = Metric.OKLAB.justNoticeable();
                    String f = families.get(s);
                    boolean same = f != null && f.equals(l.family()), unknown = f == null || l.family() == null;
                    if (d < jnd && (same || unknown) || same && d < VARIANT * jnd) shade = s;
                }
                if (shade == null) {
                    shade = starts.size();
                    starts.add(c);
                    families.add(l.family());
                    amounts.add(0.0);
                }
                shadeOf.putIfAbsent(key, shade);
                // Counted as its square root, so a build's bulk material pulls its shade toward the palette's main
                // block without squeezing the rest of the build's shades into a sliver of the palette.
                double weight = Math.sqrt(Math.max(1, l.weight()));
                amounts.set(shade, amounts.get(shade) + weight);
                total += weight;
            }
        }

        /** How many shades the build holds, so a variant that shares one adds none. */
        int count() { return starts.size(); }

        /** How much of the build a shade is. */
        double amount(int shade) { return amounts.get(shade); }

        /** How much there is of the whole build. */
        double total() { return total; }

        /**
         * Which shade a colour belongs to, from 0 for the darkest: its own for one of the build's looks, else the
         * shade whose start is nearest it, or 0 for a build with none.
         */
        int of(Oklab c, String family) {
            Integer known = shadeOf.get(new Key(c, family));
            if (known == null && family != null) known = shadeOf.get(new Key(c, null));
            if (known != null) return known;
            int best = 0;
            for (int s = 1; s < starts.size(); s++) if (c.distance(starts.get(s)) < c.distance(starts.get(best))) best = s;
            return best;
        }
    }
}
