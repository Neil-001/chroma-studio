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
 * colours from darkest to lightest into shades, where a variant that can't be told from the shade before it, such as
 * cracked stone bricks beside stone bricks, joins that shade, and hands the shades out in order to the palette's
 * blocks, sorted the same way. So the build's darkest shade becomes the palette's darkest block and its lightest the
 * lightest, and shades stay apart as far as the palette has blocks for them. When there are more shades than blocks,
 * every block still takes at least one, and the rest go by weight: the blocks a palette means for the bulk of a
 * build, such as a desert's sandstone, take most of them, and an accent such as gilded blackstone few.
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
     * A block as the mapping sees it: its colour, whether it can be seen through, as glass can, and for a palette's
     * block its weight, how much of a build the palette means it to cover. A build's blocks have no weight.
     */
    public record Look(Color colour, boolean seeThrough, int weight) {
        /** A look with a weight of 1. */
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
     * @param build   the blocks of the build being recoloured, which set its darkest and lightest; ignored when
     *                picking the closest colour
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
        this.buildSolid = new Shades(colours(build, false));
        this.buildClear = new Shades(colours(build, true));
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
        int[] table = tables.computeIfAbsent(List.of(block.seeThrough(), Arrays.toString(eligible)), _ -> table(eligible, build.count()));
        return table[build.of(c)];
    }

    /** Which of {@code eligible}, sorted from darkest to lightest, each of {@code shades} shades of a build takes. */
    private int[] table(int[] eligible, int shades) {
        int n = eligible.length;
        int[] out = new int[Math.max(1, shades)];
        if (shades <= n) {
            // A block for every shade: each takes its own, spread in order from the darkest block to the lightest.
            for (int k = 0; k < out.length; k++) out[k] = eligible[shades < 2 ? (n - 1) / 2 : (int) Math.round(k * (n - 1) / (double) (shades - 1))];
            return out;
        }
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

    /** {@link #pick} with every palette block allowed. */
    public int pick(Look block) { return pick(block, _ -> true); }

    private boolean any(IntPredicate allowed, boolean clear) {
        for (int i = 0; i < palette.length; i++) if (paletteClear[i] == clear && allowed.test(i)) return true;
        return false;
    }

    /** The OKLab colours of the looks that are, or aren't, see-through, from darkest to lightest. */
    private static Oklab[] colours(List<Look> looks, boolean clear) {
        return looks.stream().filter(l -> l.seeThrough() == clear).map(l -> l.colour().oklab())
                .sorted(Comparator.comparingDouble(Oklab::l)).toArray(Oklab[]::new);
    }

    /**
     * A build's colours sorted into shades, from darkest to lightest: a colour starts a new shade unless it can't be
     * told from where the latest shade starts, so a block and a variant of it that looks the same are one shade, and
     * red and green concrete of the same lightness are two.
     */
    static final class Shades {
        // The colour each shade starts at, darkest first, and the shade each of the build's colours belongs to.
        private final List<Oklab> starts = new ArrayList<>();
        private final Map<Oklab, Integer> shadeOf = new HashMap<>();

        /** {@code colours} from darkest to lightest. */
        Shades(Oklab[] colours) {
            for (Oklab c : colours) {
                // Only the latest shade may take it, so shades stay in order from darkest to lightest.
                if (starts.isEmpty() || c.distance(starts.getLast()) >= Metric.OKLAB.justNoticeable()) starts.add(c);
                shadeOf.putIfAbsent(c, starts.size() - 1);
            }
        }

        /** How many shades the build holds that can be told apart, so a near-identical variant adds none. */
        int count() { return starts.size(); }

        /**
         * Which shade a colour belongs to, from 0 for the darkest: its own for one of the build's colours, else the
         * shade whose start is nearest it, or 0 for a build with none.
         */
        int of(Oklab c) {
            Integer known = shadeOf.get(c);
            if (known != null) return known;
            int best = 0;
            for (int s = 1; s < starts.size(); s++) if (c.distance(starts.get(s)) < c.distance(starts.get(best))) best = s;
            return best;
        }
    }
}
