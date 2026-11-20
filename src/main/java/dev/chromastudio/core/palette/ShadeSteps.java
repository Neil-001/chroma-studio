package dev.chromastudio.core.palette;

import dev.chromastudio.core.color.Color;
import dev.chromastudio.core.color.Oklch;
import dev.chromastudio.core.color.Tone;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.function.IntPredicate;

/**
 * Which block a builder would reach for as a block's next shade, a step lighter or darker: one close to it in hue,
 * chroma and texture, about one step away in lightness. Axiom's shade brush asks this for each block it touches.
 *
 * <p>Blocks are paired into ladders, from darkest to lightest, each block with at most one lighter partner and one
 * darker one. The closest pairs are made first, so stone bricks pair with tuff bricks before a block that suits tuff
 * bricks less does. A step lighter then a step darker therefore returns to where it started. A block whose partner is
 * taken, or isn't allowed, steps to the closest block that is instead, so it isn't left alone while it has a good
 * neighbour, though that step may not undo exactly. A block with no block close enough in that direction stays.
 *
 * <p>A shade is a plainer or darker material, not a decorated one: a mossy, cracked, chiseled or infested block is
 * only ever a step from another such block, so a wall brushed lighter never grows moss. A decorated block may still
 * step to a plain one.
 *
 * <p>Steps among a palette's blocks reach further. Choosing a palette tells the brush which blocks to use, and a
 * palette rarely has a block one small step from each of its others: Desert's next block darker than sandstone is
 * yellow terracotta, a long step that is flatter and more orange. So among a palette's blocks a step may be as long as
 * it takes, as short as can be seen, of any texture or family, and stray twice as far in hue and chroma. It still never turns a
 * grey to a colour, stone to wood, or a plain block to a decorated one.
 *
 * <p>What it answers never changes after it is made, so any number of threads may ask it at once.
 */
public final class ShadeSteps {
    /** The smallest and largest step in OKLab lightness that the brush offers. */
    public static final double MIN_STEP = 0.03, MAX_STEP = 0.12;

    // How far a pick may stray across the step, in OKLab. A hue change counts by how far it moves the colour, so
    // greys, whose hue means little, are held by chroma alone: orange to red terracotta is about 0.035 in hue. Chroma
    // may change by a share of the more colourful block's, so red concrete may dull to red terracotta, 0.03 apart, but
    // a grey may not take on colour: stone bricks to tuff bricks is 0.01, while polished blackstone bricks to nether
    // bricks, a grey to a dark red, is 0.021 and white terracotta to smooth stone, a tan to a grey, 0.044. A flat
    // block such as concrete and a textured one such as planks are more than 0.04 apart in spread.
    private static final double MAX_HUE = 0.04, MIN_CHROMA_CHANGE = 0.02, CHROMA_SHARE = 0.3, MAX_SPREAD = 0.04;
    // What counts as the same in each, which weighs it against the others: one step's error in lightness costs 1.
    private static final double HUE_SCALE = 0.025, CHROMA_SCALE = 0.04, SPREAD_SCALE = 0.03;
    // What a block of another family costs, as tuff bricks are a nearer step from stone bricks than tuff is, and
    // what a block costs that comes in shapes when the other doesn't, as cracked stone bricks are a worn stone brick
    // rather than the next material along.
    private static final double OTHER_FAMILY = 2.0, OTHER_SHAPES = 0.5;
    // How short and how long a step may be, as a share of the step asked for. Below the shortest the change can
    // hardly be seen; past the longest it's a jump, not a shade.
    private static final double SHORTEST = 0.5, LONGEST = 2.5, VISIBLE = 0.025;
    // How much worse than the closest step a partner may be and still be taken, so that the steps undo each other.
    private static final double PARTNER_SLACK = 0.75;
    // How far a step among a palette's blocks may stray in hue and chroma. Sandstone to yellow terracotta is 0.025 in
    // hue and 0.063 in chroma, and to red sandstone 0.064 and 0.075, while sandstone to prismarine, a tan to a teal,
    // is 0.088 in hue.
    private static final double PALETTE_MAX_HUE = 0.08, PALETTE_CHROMA_SHARE = 0.6;

    /** Which way a step goes. */
    public enum Direction {
        LIGHTER, DARKER;

        public Direction opposite() { return this == LIGHTER ? DARKER : LIGHTER; }
    }

    /**
     * A block as the steps see it: its colour, how busy its texture is, as {@code TextureStats.spread} gives it, and
     * its family, such as "bricks" for stone and tuff bricks, which a step keeps to where it can, what it's made of,
     * such as stone or wood, which a step always keeps to, and whether its material comes in shapes, such as stairs
     * and slabs, which a step also keeps to where it can. An empty family matches none. {@code decorated} marks a worn,
     * overgrown, carved or infested variant, such as mossy stone bricks, which is no shade of a plain block.
     */
    public record Look(Color colour, double spread, String family, String material, boolean shaped, boolean decorated) {
        /** A plain block's look. */
        public Look(Color colour, double spread, String family, String material, boolean shaped) {
            this(colour, spread, family, material, shaped, false);
        }
    }

    /** Prefixes of the variants a step never reaches from a plain block. */
    private static final List<String> DECORATIONS = List.of("mossy_", "cracked_", "chiseled_", "infested_");

    /**
     * Whether a block id names a decorated variant: mossy, cracked, chiseled or infested, such as
     * {@code minecraft:mossy_stone_bricks}. A shade is a plainer or darker material, so stone bricks brushed lighter
     * never grow moss, and brushed darker then lighter come back to stone bricks.
     */
    public static boolean decorated(String id) {
        String path = id.substring(id.indexOf(':') + 1);
        for (String d : DECORATIONS) if (path.startsWith(d)) return true;
        return false;
    }

    private final List<Look> blocks;
    private final Oklch[] lch;
    private final double step;
    /** Whether the blocks are a palette's, whose steps reach further. */
    private final boolean palette;
    // Each block's lighter and darker partner, or -1.
    private final int[] lighter, darker;

    /** Steps among any building blocks, as {@link #ShadeSteps(List, double, boolean)} with no palette. */
    public ShadeSteps(List<Look> blocks, double step) { this(blocks, step, false); }

    /**
     * @param step the lightness one step aims for, held within {@link #MIN_STEP} and {@link #MAX_STEP}
     * @param palette whether the blocks are a palette's the player chose, among which a step reaches further
     */
    public ShadeSteps(List<Look> blocks, double step, boolean palette) {
        this.blocks = List.copyOf(blocks);
        this.step = Math.clamp(step, MIN_STEP, MAX_STEP);
        this.palette = palette;
        int n = blocks.size();
        lch = new Oklch[n];
        for (int i = 0; i < n; i++) lch[i] = blocks.get(i).colour().oklch();
        lighter = new int[n];
        darker = new int[n];
        Arrays.fill(lighter, -1);
        Arrays.fill(darker, -1);

        // Every pair close enough to be a step, from the closest. A pair is made when neither end has its partner
        // that way yet, so each block ends up with at most one each way and the two ways mirror each other.
        record Pair(int dark, int light, double cost) {}
        List<Pair> pairs = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            for (int j = 0; j < n; j++) {
                // A plain block and a decorated one never pair, so each plain block's ladder is all plain blocks.
                if (lch[j].l() <= lch[i].l() || blocks.get(i).decorated() != blocks.get(j).decorated()) continue;
                double cost = cost(blocks.get(i), lch[i], blocks.get(j), lch[j]);
                if (cost < Double.POSITIVE_INFINITY) pairs.add(new Pair(i, j, cost));
            }
        }
        pairs.sort(Comparator.comparingDouble(Pair::cost).thenComparingInt(Pair::dark).thenComparingInt(Pair::light));
        for (Pair p : pairs) {
            if (lighter[p.dark] >= 0 || darker[p.light] >= 0) continue;
            lighter[p.dark] = p.light;
            darker[p.light] = p.dark;
        }
    }

    /** The step these were made for, within the range. */
    public double step() { return step; }

    /**
     * The index of the block that block {@code from} steps to, among those {@code allowed} says it may become, such
     * as the blocks whose material has its shape: its partner that way, unless that isn't allowed or another block is
     * a clearly better step, and then the closest allowed step. -1 leaves it as it is.
     */
    public int next(int from, Direction d, IntPredicate allowed) {
        int partner = (d == Direction.LIGHTER ? lighter : darker)[from];
        int closest = closest(blocks.get(from), lch[from], d, i -> i != from && allowed.test(i) && reaches(blocks.get(from), i));
        if (partner < 0 || !allowed.test(partner) || closest < 0) return closest;
        // A partner the closest step beats by far was what was left once better pairs were made, as when two blocks
        // both suit the same lighter one, and the step to it would look wrong.
        return cost(from, partner, d) <= cost(from, closest, d) + PARTNER_SLACK ? partner : closest;
    }

    /** The index of the closest allowed step from a block that isn't one of these, or -1 for none. */
    public int next(Look from, Direction d, IntPredicate allowed) {
        return closest(from, from.colour().oklch(), d, i -> allowed.test(i) && reaches(from, i));
    }

    /** Whether a step from a block may land on block {@code to}: a decorated block only from one decorated itself. */
    private boolean reaches(Look from, int to) { return from.decorated() || !blocks.get(to).decorated(); }

    private int closest(Look from, Oklch at, Direction d, IntPredicate allowed) {
        int best = -1;
        double least = Double.POSITIVE_INFINITY;
        for (int i = 0; i < blocks.size(); i++) {
            if (!allowed.test(i)) continue;
            double cost = cost(from, at, i, d);
            if (cost < least) {
                least = cost;
                best = i;
            }
        }
        return best;
    }

    private double cost(int from, int to, Direction d) { return cost(blocks.get(from), lch[from], to, d); }

    /** The cost of a step from a look to block {@code to}, with the darker of the two first. */
    private double cost(Look from, Oklch at, int to, Direction d) {
        return d == Direction.LIGHTER ? cost(from, at, blocks.get(to), lch[to]) : cost(blocks.get(to), lch[to], from, at);
    }

    /**
     * How far {@code light} is from being the next shade up from {@code dark}, the same whichever way it's asked, so
     * the pairs mirror. Infinite when it's too short or long a step, or strays too far in hue, chroma or texture;
     * among a palette's blocks, only when it can't be seen or strays too far in hue or chroma.
     */
    private double cost(Look dark, Oklch d, Look light, Oklch l) {
        double rise = l.l() - d.l();
        if (rise < (palette ? VISIBLE : Math.max(VISIBLE, SHORTEST * step)) || !palette && rise > LONGEST * step) return Double.POSITIVE_INFINITY;
        double hue = hueDistance(d, l), chroma = Math.abs(l.c() - d.c()), spread = Math.abs(light.spread() - dark.spread());
        double maxChroma = Math.max(MIN_CHROMA_CHANGE, (palette ? PALETTE_CHROMA_SHARE : CHROMA_SHARE) * Math.max(d.c(), l.c()));
        if (hue > (palette ? PALETTE_MAX_HUE : MAX_HUE) || chroma > maxChroma || !palette && spread > MAX_SPREAD) return Double.POSITIVE_INFINITY;
        // A grey stays a grey, however little colour the other has: polished blackstone bricks never darken to
        // cracked nether bricks. And stone never turns to wood.
        if (d.c() < Tone.MIN_CHROMA != l.c() < Tone.MIN_CHROMA || !dark.material().equals(light.material())) return Double.POSITIVE_INFINITY;
        double off = (rise - step) / step;
        boolean sameFamily = !dark.family().isEmpty() && dark.family().equals(light.family());
        return off * off + square(hue / HUE_SCALE) + square(chroma / CHROMA_SCALE) + square(spread / SPREAD_SCALE)
                + (sameFamily || palette ? 0 : OTHER_FAMILY) + (dark.shaped() == light.shaped() ? 0 : OTHER_SHAPES);
    }

    /** How far apart two colours' hues put them, as ΔE 2000's ΔH does: by the angle, scaled by both chromas. */
    private static double hueDistance(Oklch a, Oklch b) {
        double angle = Math.toRadians(a.h() - b.h());
        return 2 * Math.sqrt(a.c() * b.c()) * Math.abs(Math.sin(angle / 2));
    }

    private static double square(double v) { return v * v; }
}
