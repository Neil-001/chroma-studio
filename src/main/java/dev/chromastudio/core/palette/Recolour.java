package dev.chromastudio.core.palette;

import dev.chromastudio.core.color.Color;
import dev.chromastudio.core.color.Metric;
import dev.chromastudio.core.color.Oklab;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.IntPredicate;

/**
 * Decides which of a palette's blocks each block of a build becomes when the build is recoloured into the palette.
 * Axiom's Script Brush scripts and the palette brush both ask this, so the two give the same blocks.
 *
 * <p>Picking each block's closest colour alone makes most of a build one block when the palette is small or far
 * from the build's colours, and its shading is lost. {@link Mode#SHADING} keeps the shading, by this rule:
 * <ol>
 * <li>A block the palette already has stays as it is.
 * <li>The build's main material becomes the palette's main block: the heaviest that leaves the materials lighter and
 *     darker than it a block each, unless the palette already has it. The main material is the one there is most of,
 *     but the build's darkest or lightest only when it is most of the build, so the walls, not a dark roof, are a
 *     house's main material.
 * <li>When the palette has the main material, every other material keeps its difference from it: as much lighter or
 *     darker, and as much warmer or cooler, landing on the palette's block closest to that, so a grey trim on a
 *     stone brick wall takes the palette's next grey rather than its yellow accent.
 * <li>Otherwise every other material keeps its place from darkest to lightest: lay the build's shades along a line,
 *     each as long as the share of the build it covers, and the palette's blocks along another, each as long as its
 *     weight; each shade takes the block at the same place on the palette's line, and the build's darkest and
 *     lightest take the palette's, so the build's shading spans the palette. While the palette has blocks enough,
 *     every shade takes a block of its own; when there are more shades than blocks, every block still takes one.
 * <li>The order from darkest to lightest never turns round, and materials you can tell apart keep blocks of their
 *     own unless the palette has nothing near enough for one of them.
 * </ol>
 * Without counts to say which material is the main one, as when every block in the game may change, the largest
 * shade between the darkest and lightest is taken for it.
 *
 * <p>A variant that can't be told from a block of its own material, such as cracked stone bricks beside stone bricks,
 * joins that block's shade; different materials of nearly one colour, such as cobblestone and andesite, which a
 * builder mixes on purpose, stay apart.
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
        /** By the rule above, so the build's shading survives. */
        SHADING,
        /** The palette's block closest in colour. */
        CLOSEST
    }

    /**
     * A block as the mapping sees it: its colour, whether it can be seen through, as glass can, and its weight: for a
     * palette's block how much of a build the palette means it to cover, and for a build's how much of the build it
     * is, such as how many of it there are. {@code family} names its material with variants folded in, so cracked
     * and mossy stone bricks share stone bricks' family; null when it isn't known, and then a block that can't be told
     * from another by colour shares its shade. {@code block} says which block it is, by its full block's id for a
     * shape, so a build's block the palette has is known; null when it isn't known.
     */
    public record Look(Color colour, boolean seeThrough, int weight, String family, String block) {
        /** A look of no known block. */
        public Look(Color colour, boolean seeThrough, int weight, String family) { this(colour, seeThrough, weight, family, null); }

        /** A look of no known family or block. */
        public Look(Color colour, boolean seeThrough, int weight) { this(colour, seeThrough, weight, null, null); }

        /** A look with a weight of 1 and no known family or block. */
        public Look(Color colour, boolean seeThrough) { this(colour, seeThrough, 1); }
    }

    /**
     * What two materials sharing one block costs beyond the difference between them, in OKLab, times the smaller one's
     * amount: one just-noticeable step, so materials a builder mixed on purpose keep blocks of their own while the
     * palette has a near one for each, and share rather than take a colour of the palette's that sits between them in
     * lightness, as cobblestone beside polished andesite takes a palette's andesite with it rather than its copper.
     */
    private static final double SHARE = 0.02;

    private final Mode mode;
    private final Oklab[] palette;
    private final boolean[] paletteClear;
    private final int[] weights;
    private final String[] blocks;
    // The palette's blocks from darkest to lightest, and in the palette's order among equals.
    private final int[] byLightness;
    private final Shades buildSolid, buildClear;
    // The block each shade of the build takes, for each class and set of blocks a shape allows.
    private final Map<List<Object>, int[]> tables = new ConcurrentHashMap<>();

    /**
     * @param palette the blocks a build may become, in the palette's order, which breaks ties
     * @param build   the blocks of the build being recoloured, with how much of the build each is; ignored when
     *                picking the closest colour
     */
    public Recolour(List<Look> palette, List<Look> build, Mode mode) {
        this.mode = mode;
        int n = palette.size();
        this.palette = new Oklab[n];
        this.paletteClear = new boolean[n];
        this.weights = new int[n];
        this.blocks = new String[n];
        for (int i = 0; i < n; i++) {
            this.palette[i] = palette.get(i).colour().oklab();
            this.paletteClear[i] = palette.get(i).seeThrough();
            // A weight below 1 counts as 1, so every block keeps a share.
            this.weights[i] = Math.max(1, palette.get(i).weight());
            this.blocks[i] = palette.get(i).block();
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
        // A block the palette has stays itself.
        int own = own(block.block(), clear, allowed);
        if (own >= 0) return own;
        // Only the blocks this one may become count, so one it may not, such as a block without its shape, takes
        // nothing from the others.
        int[] eligible = Arrays.stream(byLightness).filter(i -> paletteClear[i] == clear && allowed.test(i)).toArray();
        Shades build = block.seeThrough() ? buildClear : buildSolid;
        // Blocks of one shape and class share the same table, so it is worked out once for them all.
        int[] table = tables.computeIfAbsent(List.of(block.seeThrough(), Arrays.toString(eligible)), _ -> table(eligible, build));
        return table[build.of(c, block.family())];
    }

    /** The palette's index of a block by its id, among those of a class that are allowed, or -1. */
    private int own(String block, boolean clear, IntPredicate allowed) {
        if (block == null) return -1;
        for (int i = 0; i < palette.length; i++) if (block.equals(blocks[i]) && paletteClear[i] == clear && allowed.test(i)) return i;
        return -1;
    }

    /**
     * Which of {@code eligible}, sorted from darkest to lightest, each of a build's shades takes, by the rule at the
     * top. Of all the ways to hand out blocks in order from darkest to lightest, with the blocks the palette has and
     * the main material pinned, this takes the one that lands each shade closest to its difference from the main
     * material, by how much of the build it is, counting what two shades lose by sharing a block.
     */
    private int[] table(int[] eligible, Shades build) {
        int n = eligible.length, shades = build.count();
        int main = build.main();
        // The blocks the palette has, each pinned to its shade, kept only while they stay in order.
        int[] pin = new int[shades];
        Arrays.fill(pin, -1);
        for (int k = 0, last = -1; k < shades; k++) {
            for (String id : build.blocks(k)) {
                for (int j = last + 1; j < n; j++) {
                    if (!id.equals(blocks[eligible[j]])) continue;
                    pin[k] = j;
                    break;
                }
                if (pin[k] >= 0) break;
            }
            if (pin[k] >= 0) last = pin[k];
        }
        // When the palette lacks the main material, there's nothing to measure differences from that the builder
        // chose, so each shade keeps its place from darkest to lightest instead, the main one on the palette's main block.
        if (main < 0 || pin[main] < 0) return byPlace(eligible, build, main, pin);
        Oklab m = palette[eligible[pin[main]]];
        Oklab c = build.colour(main);
        Oklab shift = new Oklab(m.l() - c.l(), m.a() - c.a(), m.b() - c.b());

        // best[k][j]: the least cost for shades 0..k with shade k on block j; back[k][j] the block shade k-1 took. The
        // earlier block wins a tie, and a block of its own wins a tie with sharing.
        double[][] best = new double[shades][n];
        int[][] back = new int[shades][n];
        for (int k = 0; k < shades; k++) {
            Oklab colour = build.colour(k);
            Oklab target = new Oklab(colour.l() + shift.l(), colour.a() + shift.a(), colour.b() + shift.b());
            double share = k == 0 ? 0 : Math.min(build.amount(k), build.amount(k - 1)) * (colour.distance(build.colour(k - 1)) + SHARE);
            // A shade that adds nothing to the build, as one of only shapes, still weighs a little, so where nothing
            // else decides, the block nearest its target wins a tie rather than the darkest.
            double amount = Math.max(build.amount(k), Double.MIN_NORMAL);
            int least = -1;
            for (int j = 0; j < n; j++) {
                if (k > 0 && j > 0 && best[k - 1][j - 1] < Double.POSITIVE_INFINITY && (least < 0 || best[k - 1][j - 1] < best[k - 1][least])) least = j - 1;
                best[k][j] = Double.POSITIVE_INFINITY;
                if (pin[k] >= 0 && j != pin[k]) continue;
                double fit = amount * target.distance(palette[eligible[j]]);
                if (k == 0) {
                    best[k][j] = fit;
                    continue;
                }
                if (least >= 0) {
                    best[k][j] = best[k - 1][least] + fit;
                    back[k][j] = least;
                }
                if (best[k - 1][j] + share + fit < best[k][j]) {
                    best[k][j] = best[k - 1][j] + share + fit;
                    back[k][j] = j;
                }
            }
        }
        int last = 0;
        for (int j = 1; j < n; j++) if (best[shades - 1][j] < best[shades - 1][last]) last = j;
        int[] out = new int[shades];
        for (int k = shades - 1, j = last; k >= 0; k--) {
            out[k] = eligible[j];
            j = back[k][j];
        }
        return out;
    }

    /**
     * Which of {@code eligible}, sorted from darkest to lightest, each of a build's shades takes when the build's
     * palette lacks its main material, or its weights don't say which that is, as when every block in the game may
     * change: each shade takes the block at its place from darkest to lightest, and the main one, given or else the
     * largest between the ends, one of the palette's main blocks. The shades {@code pin} gives a block the palette
     * has keep it, and bound the others.
     */
    private int[] byPlace(int[] eligible, Shades build, int main, int[] pin) {
        int n = eligible.length, shades = build.count();
        // A build with no shades of this class: a block of it the build lacks takes the palette's middle.
        if (shades == 0) return new int[] {eligible[(n - 1) / 2]};
        int[] at = new int[shades];
        if (shades <= n) {
            int[] picked = matched(eligible, build, main);
            for (int k = 0; k < shades; k++) for (int j = 0; j < n; j++) if (eligible[j] == picked[k]) at[k] = j;
        } else if (main < 0) {
            shareOut(eligible, 0, shades, 0, n, at);
        } else {
            // The main shade takes the heaviest block, the nearest its place among equals, and the shades either side
            // share out the blocks either side of it, so a build of many kinds still puts its bulk on a main block.
            double place = (main + 0.5) / shades, total = 0;
            for (int i : eligible) total += weights[i];
            int heaviest = 0;
            double upTo = 0, nearest = Double.POSITIVE_INFINITY;
            for (int j = 0; j < n; j++) {
                double middle = (upTo + weights[eligible[j]] / 2.0) / total, off = Math.abs(middle - place);
                upTo += weights[eligible[j]];
                if (weights[eligible[j]] > weights[eligible[heaviest]] || weights[eligible[j]] == weights[eligible[heaviest]] && off < nearest) {
                    heaviest = j;
                    nearest = off;
                }
            }
            at[main] = heaviest;
            if (heaviest > 0) shareOut(eligible, 0, main, 0, heaviest, at);
            else Arrays.fill(at, 0, main, heaviest);
            if (heaviest < n - 1) shareOut(eligible, main + 1, shades, heaviest + 1, n, at);
            else Arrays.fill(at, main + 1, shades, heaviest);
        }
        // A block the palette has stays itself, so the shades either side keep to their side of it: the order from
        // darkest to lightest never turns round, though a neighbour may share it.
        for (int k = 0; k < shades; k++) {
            if (pin[k] < 0) continue;
            at[k] = pin[k];
            for (int i = 0; i < k; i++) at[i] = Math.min(at[i], pin[k]);
            for (int i = k + 1; i < shades; i++) at[i] = Math.max(at[i], pin[k]);
        }
        int[] out = new int[shades];
        for (int k = 0; k < shades; k++) out[k] = eligible[at[k]];
        return out;
    }

    /**
     * Hands shades {@code fromShade} to {@code toShade} out, in order, among the blocks at places {@code fromBlock} to
     * {@code toBlock} of {@code eligible}, writing each shade's place to {@code at}. With at least as many shades as
     * blocks, every block takes one shade and the rest go by weight, by largest remainder. Weights count in full once
     * there are twice as many shades as blocks, and barely at all with only a few more, so one extra shade doesn't
     * hand the bulk of the build to the heaviest. With fewer, each shade takes the block at its place along the line
     * of the blocks' weights.
     */
    private void shareOut(int[] eligible, int fromShade, int toShade, int fromBlock, int toBlock, int[] at) {
        int shades = toShade - fromShade, n = toBlock - fromBlock;
        if (shades <= 0) return;
        double total = 0;
        if (shades < n) {
            for (int j = fromBlock; j < toBlock; j++) total += weights[eligible[j]];
            for (int k = 0; k < shades; k++) {
                double place = (k + 0.5) / shades * total, upTo = 0;
                int j = fromBlock;
                while (j < toBlock - 1 && upTo + weights[eligible[j]] <= place) upTo += weights[eligible[j++]];
                at[fromShade + k] = j;
            }
            return;
        }
        double strength = Math.min(1, (shades - n) / (double) n);
        double[] share = new double[n];
        for (int j = 0; j < n; j++) total += share[j] = Math.pow(weights[eligible[fromBlock + j]], strength);
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
        for (int j = 0, k = fromShade; j < n; j++) for (int c = 0; c < count[j]; c++) at[k++] = fromBlock + j;
    }

    /**
     * A block for each of a build's shades, when there are no more shades than blocks, in order from darkest to
     * lightest. The build's darkest shade takes the palette's darkest block and its lightest the lightest, so the
     * shading spans the palette, and the main shade, given or else the largest between them, such as a house's walls,
     * takes the palette's heaviest block between its ends, such as a desert's sandstone. A main shade that is the
     * build's darkest or lightest may take any block instead of the palette's end. Each other shade has a place from
     * 0 to 1, the middle of its share of the build, and each block a stretch of the same line, as long as its weight.
     * A shade fits a block whose stretch holds its place, and less well the further outside it, by how much of the
     * build the shade is. Of all the ways to hand out blocks in order, each shade its own, this takes the best fit.
     * Two neighbouring shades share a block only where the main shade's pin, below, makes room for it.
     */
    private int[] matched(int[] eligible, Shades build, int main) {
        int n = eligible.length, shades = build.count();
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
        // The main shade, given or else the largest between the ends, and the heaviest block for it, nearest its place
        // among equals: between the palette's ends for a shade between the build's, anywhere for an end shade.
        int heaviest = -1, shares = 0;
        if (main < 0 && shades >= 3) for (int k = 1; k < shades - 1; k++) if (main < 0 || build.amount(k) > build.amount(main)) main = k;
        boolean end = main == 0 || main == shades - 1;
        if (main >= 0 && n >= (end ? 2 : 3)) {
            // A block that leaves room for every shade on either side of it, so no two shades share. Only when the
            // palette has blocks to spare and a heavier block lies one place beyond that room may two neighbouring
            // shades share to put the walls on it, as a builder would: stone walls on stone bricks, not on planks.
            int roomy = heaviest(eligible, at[main], from, to, main, shades, 0);
            int crowded = n > shades ? heaviest(eligible, at[main], from, to, main, shades, 1) : -1;
            if (crowded >= 0 && crowded != roomy && (roomy < 0 || weights[eligible[crowded]] > weights[eligible[roomy]])) {
                heaviest = crowded;
                shares = 1;
            } else {
                heaviest = roomy;
            }
            if (heaviest < 0) main = -1;
        }
        // best[s][k][j]: the least cost for shades 0..k with shade k on block j and s pairs of shades sharing a block;
        // back[s][k][j] the block shade k-1 took: the first earlier block whose cost, with this shade's fit added, is
        // the least, or block j itself, shared, only when that costs less still.
        //
        // Rather than trying every earlier block for every cell, each row keeps the places where its running minimum
        // drops, cheapest last. Adding the same fit to each can round two costs to the same total, which makes the
        // earlier block win, so the first place whose total rounds to the least is found by halving.
        double[][][] best = new double[shares + 1][shades][n];
        int[][][] back = new int[shares + 1][shades][n];
        int[] dropAt = new int[n];
        double[] dropTo = new double[n];
        for (int k = 0; k < shades; k++) {
            double share = build.amount(k) / build.total();
            for (int s = 0; s <= shares; s++) {
                int drops = 0;
                for (int j = 0; j < n; j++) {
                    if (k > 0 && j > 0) {
                        double cost = best[s][k - 1][j - 1];
                        if (cost != Double.POSITIVE_INFINITY && (drops == 0 || cost < dropTo[drops - 1])) {
                            dropAt[drops] = j - 1;
                            dropTo[drops++] = cost;
                        }
                    }
                    best[s][k][j] = Double.POSITIVE_INFINITY;
                    boolean pinned = shades > 1 && k != main && (k == 0 && j != 0 || k == shades - 1 && j != n - 1) || k == main && j != heaviest;
                    if (pinned) continue;
                    double fit = share * away(at[k], from[j], to[j]);
                    if (k == 0) {
                        if (s == 0) best[s][k][j] = fit;
                        continue;
                    }
                    if (drops > 0) {
                        double least = dropTo[drops - 1] + fit;
                        int lo = 0, hi = drops - 1;
                        while (lo < hi) {
                            int mid = (lo + hi) >>> 1;
                            if (dropTo[mid] + fit <= least) hi = mid;
                            else lo = mid + 1;
                        }
                        best[s][k][j] = least;
                        back[s][k][j] = dropAt[lo];
                    }
                    if (s > 0 && best[s - 1][k - 1][j] != Double.POSITIVE_INFINITY && best[s - 1][k - 1][j] + fit < best[s][k][j]) {
                        best[s][k][j] = best[s - 1][k - 1][j] + fit;
                        back[s][k][j] = j;
                    }
                }
            }
        }
        int last = 0;
        for (int j = 1; j < n; j++) if (best[shares][shades - 1][j] < best[shares][shades - 1][last]) last = j;
        int[] out = new int[shades];
        for (int k = shades - 1, j = last, s = shares; k >= 0; k--) {
            out[k] = eligible[j];
            int i = back[s][k][j];
            if (k > 0 && i == j) s--;
            j = i;
        }
        return out;
    }

    /**
     * The heaviest of {@code eligible} that the main shade may take, between the ends unless it is an end shade
     * itself, leaving all but {@code crowded} of the other shades a block of their own, and the nearest its place
     * among equals; -1 when there's none.
     */
    private int heaviest(int[] eligible, double at, double[] from, double[] to, int main, int shades, int crowded) {
        int n = eligible.length, best = -1;
        boolean end = main == 0 || main == shades - 1;
        for (int j = end ? 0 : 1; j < (end ? n : n - 1); j++) {
            if (Math.max(0, main - j) + Math.max(0, shades - 1 - main - (n - 1 - j)) > crowded) continue;
            if (best < 0 || weights[eligible[j]] > weights[eligible[best]]
                    || weights[eligible[j]] == weights[eligible[best]] && away(at, from[j], to[j]) < away(at, from[best], to[best])) {
                best = j;
            }
        }
        return best;
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
     * the build it is: the square root of its looks' weights together; its colour: that of its heaviest look; and
     * the blocks it holds, heaviest first.
     */
    static final class Shades {
        /** How many times further than it can be told apart a block of the same family may be and still share a shade. */
        private static final double VARIANT = 3;

        private record Key(Oklab colour, String family) {}

        // The colour and family each shade starts at, darkest first, how much of the build each shade is, its
        // heaviest look's colour and weight, the blocks it holds by weight, and the shade each of the build's colours
        // belongs to.
        private final List<Oklab> starts = new ArrayList<>();
        private final List<String> families = new ArrayList<>();
        private final List<Double> amounts = new ArrayList<>();
        private final List<Oklab> colours = new ArrayList<>();
        private final List<Integer> heaviest = new ArrayList<>();
        private final List<Map<String, Integer>> blocks = new ArrayList<>();
        private final Map<Key, Integer> shadeOf = new HashMap<>();
        private double total;
        private final int main;

        Shades(List<Look> looks) {
            List<Look> sorted = new ArrayList<>(looks);
            sorted.sort(Comparator.comparingDouble(l -> l.colour().oklab().l()));
            int fewest = Integer.MAX_VALUE, most = 0;
            // When no look weighs anything, as when every block of a class is a shape another block counts, each
            // counts as one, so the build still has an amount to spread.
            boolean weighed = looks.stream().anyMatch(l -> l.weight() > 0);
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
                    colours.add(c);
                    heaviest.add(-1);
                    blocks.add(new LinkedHashMap<>());
                }
                shadeOf.putIfAbsent(key, shade);
                // A weight of 0 says the look is in the build without adding to how much there is, as a stair is
                // when its material's full block already counts it.
                int w = weighed ? Math.max(0, l.weight()) : 1;
                if (w > 0) {
                    fewest = Math.min(fewest, w);
                    most = Math.max(most, w);
                }
                amounts.set(shade, amounts.get(shade) + w);
                if (w > heaviest.get(shade)) {
                    heaviest.set(shade, w);
                    colours.set(shade, c);
                }
                if (l.block() != null) blocks.get(shade).merge(l.block(), w, Integer::sum);
            }
            // The largest shade, and the largest between the darkest and the lightest, by how many blocks.
            int shades = starts.size(), largest = -1, inner = -1;
            boolean tied = false;
            double all = 0;
            for (int s = 0; s < shades; s++) {
                double a = amounts.get(s);
                all += a;
                if (s > 0 && s < shades - 1 && (inner < 0 || a > amounts.get(inner))) inner = s;
                if (largest < 0 || a > amounts.get(largest)) {
                    largest = s;
                    tied = false;
                } else if (a == amounts.get(largest)) {
                    tied = true;
                }
            }
            // A main material only when the weights are counts that tell one apart: with every block weighed alike,
            // as when every block in the game may change, there is none, unless the build is all one shade. The
            // darkest or lightest shade is the main one only when it's most of the build, as a wall of one stone is;
            // a dark roof that is merely the largest part leaves the walls, the largest between the ends, the main one.
            int counted = most > fewest && !tied ? largest : -1;
            if (counted >= 0 && (counted == 0 || counted == shades - 1)) counted = amounts.get(counted) * 2 > all ? counted : inner;
            // Counted as square roots, so a build's bulk material counts for more without the rest counting for nothing.
            for (int s = 0; s < shades; s++) {
                amounts.set(s, Math.sqrt(amounts.get(s)));
                total += amounts.get(s);
            }
            main = shades == 1 ? 0 : counted;
        }

        /** How many shades the build holds, so a variant that shares one adds none. */
        int count() { return starts.size(); }

        /** How much of the build a shade is. */
        double amount(int shade) { return amounts.get(shade); }

        /** How much there is of the whole build. */
        double total() { return total; }

        /** A shade's colour: its heaviest look's. */
        Oklab colour(int shade) { return colours.get(shade); }

        /** The blocks a shade holds, heaviest first. */
        List<String> blocks(int shade) {
            List<Map.Entry<String, Integer>> out = new ArrayList<>(blocks.get(shade).entrySet());
            out.sort(Map.Entry.<String, Integer>comparingByValue().reversed());
            return out.stream().map(Map.Entry::getKey).toList();
        }

        /** The shade of the build's main material, the one there is most of, or -1 when the weights don't say. */
        int main() { return main; }

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
