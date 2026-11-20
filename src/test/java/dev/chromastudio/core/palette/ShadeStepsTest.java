package dev.chromastudio.core.palette;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.chromastudio.core.color.Color;
import dev.chromastudio.core.color.Metric;
import dev.chromastudio.core.color.Oklch;
import dev.chromastudio.core.color.Tone;
import dev.chromastudio.core.palette.ShadeSteps.Direction;
import dev.chromastudio.core.palette.ShadeSteps.Look;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class ShadeStepsTest {
    private record Block(String id, Look look) {}

    // Minecraft 26.3's own blocks as the index reads them: OKLab lightness, chroma and hue of each texture's mean,
    // its spread, and whether its material comes in stairs or slabs. Cracked stone bricks, calcite, clay, terracotta
    // and concrete don't.
    private static final List<Block> BLOCKS = List.of(
            block("nether_bricks", 0.238, 0.038, 9.1, 0.040, true),
            block("black_concrete", 0.145, 0.011, 266.8, 0.005, false),
            block("blackstone", 0.281, 0.014, 316.4, 0.065, true),
            block("polished_blackstone_bricks", 0.310, 0.017, 310.1, 0.069, true),
            block("dark_oak_planks", 0.317, 0.050, 63.1, 0.043, true),
            block("polished_blackstone", 0.329, 0.017, 305.5, 0.060, true),
            block("deepslate_tiles", 0.345, 0.000, 0.0, 0.058, true),
            block("gray_concrete", 0.347, 0.008, 255.5, 0.002, false),
            block("brown_terracotta", 0.348, 0.045, 50.7, 0.006, false),
            block("blue_concrete", 0.371, 0.155, 274.9, 0.002, false),
            block("brown_concrete", 0.392, 0.066, 57.2, 0.002, false),
            block("deepslate_bricks", 0.413, 0.000, 0.0, 0.083, true),
            block("polished_deepslate", 0.417, 0.000, 0.0, 0.076, true),
            block("red_concrete", 0.429, 0.145, 25.8, 0.002, false),
            block("cobbled_deepslate", 0.436, 0.003, 286.3, 0.076, true),
            block("green_concrete", 0.443, 0.083, 124.8, 0.002, false),
            block("red_terracotta", 0.467, 0.115, 31.6, 0.008, false),
            block("spruce_planks", 0.477, 0.067, 70.0, 0.053, true),
            block("polished_tuff", 0.514, 0.010, 156.9, 0.045, true),
            block("tuff_bricks", 0.516, 0.011, 125.8, 0.077, true),
            block("pink_terracotta", 0.529, 0.112, 20.9, 0.008, false),
            block("orange_terracotta", 0.532, 0.119, 48.4, 0.007, false),
            block("tuff", 0.540, 0.009, 106.7, 0.071, true),
            block("terracotta", 0.540, 0.085, 44.7, 0.006, false),
            block("mud_bricks", 0.549, 0.057, 57.5, 0.063, true),
            block("light_gray_terracotta", 0.553, 0.039, 38.6, 0.006, false),
            block("bricks", 0.559, 0.070, 33.6, 0.078, true),
            block("cracked_stone_bricks", 0.570, 0.002, 325.6, 0.064, false),
            block("mossy_stone_bricks", 0.575, 0.020, 124.2, 0.071, true),
            block("chiseled_stone_bricks", 0.577, 0.002, 325.6, 0.076, false),
            block("polished_granite", 0.579, 0.066, 40.7, 0.075, true),
            block("stone_bricks", 0.586, 0.000, 0.0, 0.064, true),
            block("light_gray_concrete", 0.587, 0.015, 106.9, 0.002, false),
            block("stone", 0.593, 0.000, 0.0, 0.037, true),
            block("light_blue_concrete", 0.604, 0.129, 241.6, 0.003, false),
            block("jungle_planks", 0.604, 0.075, 57.2, 0.083, true),
            block("cobblestone", 0.613, 0.000, 0.0, 0.099, true),
            block("polished_andesite", 0.628, 0.003, 165.0, 0.071, true),
            block("andesite", 0.630, 0.001, 286.4, 0.054, true),
            block("oak_planks", 0.637, 0.080, 80.0, 0.086, true),
            block("orange_concrete", 0.642, 0.179, 46.5, 0.003, false),
            block("yellow_terracotta", 0.653, 0.125, 77.6, 0.007, false),
            block("lime_concrete", 0.660, 0.183, 134.5, 0.003, false),
            block("prismarine_bricks", 0.697, 0.074, 182.1, 0.072, true),
            block("smooth_stone", 0.706, 0.000, 0.0, 0.059, true),
            block("clay", 0.728, 0.020, 265.9, 0.023, false),
            block("birch_planks", 0.762, 0.075, 92.9, 0.066, true),
            block("white_terracotta", 0.787, 0.044, 49.6, 0.007, false),
            block("diorite", 0.811, 0.000, 0.0, 0.110, true),
            block("polished_diorite", 0.824, 0.001, 286.4, 0.109, true),
            block("sandstone", 0.844, 0.062, 94.2, 0.054, true),
            block("white_concrete", 0.869, 0.007, 208.8, 0.003, false),
            block("end_stone_bricks", 0.893, 0.081, 112.5, 0.074, true),
            block("calcite", 0.911, 0.005, 117.9, 0.059, false));

    private static Block block(String id, double l, double c, double h, double spread, boolean shaped) {
        String family = id.substring(id.lastIndexOf('_') + 1);
        String material = id.endsWith("planks") ? "wood" : id.endsWith("wool") ? "wool" : id.equals("clay") ? "earth" : "stone";
        return new Block(id, new Look(Color.ofOklch(new Oklch(l, c, h)), spread, family, material, shaped, ShadeSteps.decorated("minecraft:" + id)));
    }

    private static final double DEFAULT = 0.06;

    private static ShadeSteps steps(double step) { return new ShadeSteps(BLOCKS.stream().map(Block::look).toList(), step); }

    private static int at(String id) {
        for (int i = 0; i < BLOCKS.size(); i++) if (BLOCKS.get(i).id.equals(id)) return i;
        throw new AssertionError(id);
    }

    private static String next(ShadeSteps steps, String id, Direction d) {
        int i = steps.next(at(id), d, _ -> true);
        return i < 0 ? null : BLOCKS.get(i).id;
    }

    /** The blocks a few steps each way from one, darkest first, for a failure to show. */
    private static List<String> ladder(ShadeSteps steps, String id) {
        List<String> out = new ArrayList<>(List.of(id));
        for (Direction d : Direction.values()) {
            String at = id;
            for (int k = 0; k < 3 && at != null; k++) {
                at = next(steps, at, d);
                if (at != null) out.add(d == Direction.DARKER ? 0 : out.size(), at);
            }
        }
        return out;
    }

    /**
     * Whether a step back came to the block it started from, or to one within two just-noticeable steps of it, as
     * andesite is of polished andesite and stone of both. Two such blocks can't both pair with smooth stone.
     */
    private static boolean twins(String start, String back) {
        if (start.equals(back)) return true;
        if (back == null) return false;
        return BLOCKS.get(at(start)).look.colour().oklab().distance(BLOCKS.get(at(back)).look.colour().oklab()) < 2 * Metric.OKLAB.justNoticeable();
    }

    @Test
    void stoneBricksStepToTheGreyBlocksABuilderWouldUse() {
        ShadeSteps steps = steps(DEFAULT);
        assertEquals(List.of("polished_blackstone_bricks", "deepslate_bricks", "tuff_bricks", "stone_bricks", "polished_andesite", "smooth_stone"),
                ladder(steps, "stone_bricks"));
    }

    // Bricks and white terracotta are left out. No other red block is near bricks, so their steps reach for planks
    // and mud bricks; and white concrete, white terracotta's step up, is paired down with clay.
    @Test
    void aStepUpThenDownReturnsToTheStartForCommonBlocks() {
        // At the smaller sizes and the default. A larger step skips rungs that are unevenly spaced, as planks are, so
        // oak steps down past jungle to spruce, and spruce back up to jungle.
        for (double step : new double[] {ShadeSteps.MIN_STEP, 0.045, DEFAULT}) {
            ShadeSteps steps = steps(step);
            for (String id : List.of("stone_bricks", "tuff_bricks", "deepslate_bricks", "polished_andesite", "cobbled_deepslate",
                    "oak_planks", "spruce_planks", "birch_planks", "red_terracotta")) {
                for (Direction d : Direction.values()) {
                    String there = next(steps, id, d);
                    if (there == null) continue;
                    String back = next(steps, there, d.opposite());
                    assertTrue(twins(id, back), id + " " + d + " to " + there + " and back to " + back + " at " + step + ": " + ladder(steps, id));
                }
            }
        }
    }

    /**
     * Common building blocks, stepped each way at every size the brush offers, come back to themselves, or to a block
     * that can't be told from them. The only exceptions are blocks that share their one neighbour that way with
     * another block, which the closer pair keeps: terracotta and pink terracotta both darken to red terracotta, which
     * lightens to pink; oak and jungle planks both darken to spruce at the larger steps; and at the two largest,
     * cobblestone darkens to cobbled deepslate, which lightens to tuff.
     */
    @Test
    void commonBlocksRoundTripAtEveryStepSize() {
        List<String> failures = new ArrayList<>();
        for (double step = ShadeSteps.MIN_STEP; step <= ShadeSteps.MAX_STEP + 1e-9; step += 0.005) {
            ShadeSteps steps = steps(step);
            for (String id : List.of("stone_bricks", "tuff_bricks", "deepslate_bricks", "cobbled_deepslate", "polished_andesite", "cobblestone",
                    "smooth_stone", "oak_planks", "spruce_planks", "birch_planks", "dark_oak_planks", "red_terracotta", "terracotta")) {
                for (Direction d : Direction.values()) {
                    String there = next(steps, id, d);
                    if (there == null) continue;
                    String back = next(steps, there, d.opposite());
                    boolean shared = id.equals("terracotta") && back.equals("pink_terracotta") || id.equals("oak_planks") && back.equals("jungle_planks")
                            || id.equals("cobblestone") && back.equals("tuff") && step > 0.095;
                    if (!twins(id, back) && !shared) failures.add(String.format("%.3f %s %s to %s and back to %s", step, id, d, there, back));
                }
            }
        }
        assertTrue(failures.isEmpty(), failures.size() + " failures: " + String.join("; ", failures));
        // Stone bricks, as the brush's own example: darker to tuff bricks and lighter back, at every size.
        for (double step = ShadeSteps.MIN_STEP; step <= ShadeSteps.MAX_STEP + 1e-9; step += 0.005) {
            ShadeSteps steps = steps(step);
            String down = next(steps, "stone_bricks", Direction.DARKER);
            assertEquals("stone_bricks", next(steps, down, Direction.LIGHTER), String.format("%.3f via %s", step, down));
        }
    }

    /** A worn, overgrown or carved block is no shade of a plain one: stone bricks never step to mossy or cracked bricks. */
    @Test
    void aPlainBlockNeverStepsToADecoratedOne() {
        for (double step = ShadeSteps.MIN_STEP; step <= ShadeSteps.MAX_STEP + 1e-9; step += 0.005) {
            ShadeSteps steps = steps(step);
            for (Block b : BLOCKS) {
                if (decorated(b.id)) continue;
                for (Direction d : Direction.values()) {
                    String there = next(steps, b.id, d);
                    assertTrue(there == null || !decorated(there), String.format("%.3f %s %s to %s", step, b.id, d, there));
                }
            }
        }
        // A decorated block itself still has shades.
        assertTrue(next(steps(DEFAULT), "mossy_stone_bricks", Direction.DARKER) != null);
    }

    private static boolean decorated(String id) { return id.startsWith("mossy_") || id.startsWith("cracked_") || id.startsWith("chiseled_") || id.startsWith("infested_"); }

    @Test
    void mostStepsUndoAcrossTheWholeSet() {
        ShadeSteps steps = steps(DEFAULT);
        int stepped = 0, undone = 0;
        for (Block b : BLOCKS) {
            for (Direction d : Direction.values()) {
                String there = next(steps, b.id, d);
                if (there == null) continue;
                stepped++;
                if (twins(b.id, next(steps, there, d.opposite()))) undone++;
            }
        }
        assertTrue(undone >= stepped * 0.8, undone + " of " + stepped);
    }

    @Test
    void colouredBlocksKeepTheirHue() {
        ShadeSteps steps = steps(DEFAULT);
        for (Block b : BLOCKS) {
            Oklch from = b.look.colour().oklch();
            if (from.c() < 0.08) continue;
            for (Direction d : Direction.values()) {
                String there = next(steps, b.id, d);
                if (there == null) continue;
                Oklch to = BLOCKS.get(at(there)).look.colour().oklch();
                double turn = Math.abs(((to.h() - from.h()) % 360 + 540) % 360 - 180);
                assertTrue(turn < 25, b.id + " " + d + " turns " + turn + " degrees to " + there);
            }
        }
        assertEquals("red_terracotta", next(steps, "orange_terracotta", Direction.DARKER));
    }

    @Test
    void aGreyStaysGrey() {
        for (double step : new double[] {ShadeSteps.MIN_STEP, DEFAULT, ShadeSteps.MAX_STEP}) {
            ShadeSteps steps = steps(step);
            for (Block b : BLOCKS) {
                if (b.look.colour().oklch().c() >= Tone.MIN_CHROMA) continue;
                for (Direction d : Direction.values()) {
                    String there = next(steps, b.id, d);
                    if (there != null) assertTrue(BLOCKS.get(at(there)).look.colour().oklch().c() < Tone.MIN_CHROMA, b.id + " " + d + " to " + there);
                }
            }
        }
    }

    @Test
    void stoneNeverTurnsToWood() {
        // Mossy stone bricks lighter, as stairs, where poplar is the only near match that comes as stairs.
        List<Look> looks = List.of(new Look(Color.ofOklch(new Oklch(0.575, 0.020, 124.2)), 0.071, "bricks", "stone", true),
                new Look(Color.ofOklch(new Oklch(0.644, 0.022, 65.0)), 0.063, "planks", "wood", true));
        assertEquals(-1, new ShadeSteps(looks, DEFAULT).next(0, Direction.LIGHTER, _ -> true));
    }

    @Test
    void eachStepGoesTheWayAskedByAVisibleAmount() {
        for (double step : new double[] {ShadeSteps.MIN_STEP, DEFAULT, ShadeSteps.MAX_STEP}) {
            ShadeSteps steps = steps(step);
            for (int i = 0; i < BLOCKS.size(); i++) {
                double l = BLOCKS.get(i).look.colour().oklch().l();
                int up = steps.next(i, Direction.LIGHTER, _ -> true), down = steps.next(i, Direction.DARKER, _ -> true);
                if (up >= 0) assertTrue(BLOCKS.get(up).look.colour().oklch().l() - l >= 0.02, BLOCKS.get(i).id + " to " + BLOCKS.get(up).id);
                if (down >= 0) assertTrue(l - BLOCKS.get(down).look.colour().oklch().l() >= 0.02, BLOCKS.get(i).id + " to " + BLOCKS.get(down).id);
            }
        }
    }

    @Test
    void aLargerStepReachesFurther() {
        double small = rise(steps(ShadeSteps.MIN_STEP)), large = rise(steps(ShadeSteps.MAX_STEP));
        assertTrue(large > small * 1.5, small + " and " + large);
    }

    /** The average lightness one step darker moves a block, over the blocks that have a step. */
    private static double rise(ShadeSteps steps) {
        double total = 0;
        int n = 0;
        for (int i = 0; i < BLOCKS.size(); i++) {
            int down = steps.next(i, Direction.DARKER, _ -> true);
            if (down < 0) continue;
            total += BLOCKS.get(i).look.colour().oklch().l() - BLOCKS.get(down).look.colour().oklch().l();
            n++;
        }
        return total / n;
    }

    @Test
    void aBlockWithNoCloseShadeThatWayStays() {
        ShadeSteps steps = steps(DEFAULT);
        // Nothing lighter than calcite, and no blue lighter than light blue concrete is close enough in hue.
        assertEquals(null, next(steps, "calcite", Direction.LIGHTER));
        assertEquals(null, next(steps, "light_blue_concrete", Direction.LIGHTER));
        assertEquals(null, next(steps, "black_concrete", Direction.DARKER));
    }

    @Test
    void onlyAllowedBlocksArePicked() {
        ShadeSteps steps = steps(DEFAULT);
        // As for stairs, when tuff bricks had none: the next best darker stone.
        int tuffBricks = at("tuff_bricks");
        int picked = steps.next(at("stone_bricks"), Direction.DARKER, i -> i != tuffBricks);
        assertTrue(picked >= 0 && picked != tuffBricks);
        assertTrue(Set.of("tuff", "polished_tuff", "cobbled_deepslate", "polished_deepslate", "deepslate_bricks").contains(BLOCKS.get(picked).id), BLOCKS.get(picked).id);
        assertEquals(-1, steps.next(at("stone_bricks"), Direction.DARKER, _ -> false));
    }

    @Test
    void aBlockOutsideTheSetStepsToTheClosest() {
        ShadeSteps steps = steps(DEFAULT);
        // Chiseled stone bricks, which the set leaves out.
        Look chiseled = new Look(Color.ofOklch(new Oklch(0.577, 0.002, 325.6)), 0.076, "bricks", "stone", false);
        int down = steps.next(chiseled, Direction.DARKER, _ -> true);
        assertTrue(down >= 0 && BLOCKS.get(down).id.contains("tuff"), down < 0 ? "none" : BLOCKS.get(down).id);
    }

    @Test
    void theStepIsHeldWithinItsRange() {
        assertEquals(ShadeSteps.MAX_STEP, new ShadeSteps(List.of(), 1).step());
        assertEquals(ShadeSteps.MIN_STEP, new ShadeSteps(List.of(), 0).step());
    }

    // Desert's blocks as shades: the full blocks the brush may use, with the faces it averages. Sand falls, so it can't
    // be a shade.
    private static final List<Block> DESERT = List.of(
            block("terracotta", 0.540, 0.085, 44.7, 0.006, false),
            block("orange_terracotta", 0.532, 0.119, 48.4, 0.007, false),
            block("red_sandstone", 0.592, 0.135, 53.4, 0.040, true),
            block("cut_red_sandstone", 0.597, 0.135, 54.0, 0.036, true),
            block("yellow_terracotta", 0.653, 0.125, 77.6, 0.007, false),
            block("birch_planks", 0.762, 0.075, 92.9, 0.066, true),
            block("sandstone", 0.848, 0.062, 94.6, 0.048, true),
            block("chiseled_sandstone", 0.848, 0.062, 94.6, 0.050, false),
            block("cut_sandstone", 0.859, 0.060, 95.9, 0.038, true),
            block("smooth_sandstone", 0.873, 0.058, 96.5, 0.018, true));

    private static String nextIn(List<Block> set, ShadeSteps steps, String id, Direction d) {
        int from = -1;
        for (int i = 0; i < set.size(); i++) if (set.get(i).id.equals(id)) from = i;
        int i = steps.next(from, d, _ -> true);
        return i < 0 ? null : set.get(i).id;
    }

    /**
     * With Shades set to Desert, sandstone darkens to the palette's next block down, yellow terracotta, at every step
     * size, though it's a long step to a flatter, more orange block that no step among every building block takes.
     * The palette's steps still keep stone from wood and plain blocks from decorated ones.
     */
    @Test
    void aPalettesBlocksStepToItsNextBlockThatWay() {
        for (double step = ShadeSteps.MIN_STEP; step <= ShadeSteps.MAX_STEP + 1e-9; step += 0.005) {
            ShadeSteps steps = new ShadeSteps(DESERT.stream().map(Block::look).toList(), step, true);
            String at = String.format("%.3f", step);
            assertEquals("yellow_terracotta", nextIn(DESERT, steps, "sandstone", Direction.DARKER), at);
            assertEquals("yellow_terracotta", nextIn(DESERT, steps, "cut_sandstone", Direction.DARKER), at);
            assertEquals("sandstone", nextIn(DESERT, steps, "smooth_sandstone", Direction.DARKER), at);
            assertEquals("smooth_sandstone", nextIn(DESERT, steps, "sandstone", Direction.LIGHTER), at);
            assertEquals("orange_terracotta", nextIn(DESERT, steps, "red_sandstone", Direction.DARKER), at);
            // On down the palette, through the nearer red sandstone rather than skipping it for another terracotta.
            assertEquals("cut_red_sandstone", nextIn(DESERT, steps, "yellow_terracotta", Direction.DARKER), at);
            for (Block b : DESERT) {
                for (Direction d : Direction.values()) {
                    String there = nextIn(DESERT, steps, b.id, d);
                    if (there == null) continue;
                    assertEquals(b.id.equals("birch_planks"), there.equals("birch_planks"), at + " " + b.id + " " + d + " to " + there);
                    assertTrue(decorated(b.id) || !decorated(there), at + " " + b.id + " " + d + " to " + there);
                }
            }
        }
        // Among every building block, nothing darker is close enough to sandstone in colour and texture.
        assertEquals(null, next(steps(DEFAULT), "sandstone", Direction.DARKER));
    }

    @Test
    void aGreyInAPaletteStaysGrey() {
        // Stone bricks among a palette's warm blocks, of which light grey terracotta and bricks are darker: a grey never
        // takes on colour.
        List<Look> looks = List.of(BLOCKS.get(at("stone_bricks")).look, BLOCKS.get(at("light_gray_terracotta")).look, BLOCKS.get(at("bricks")).look);
        assertEquals(-1, new ShadeSteps(looks, DEFAULT, true).next(0, Direction.DARKER, _ -> true));
    }
}
