package dev.chromastudio.client.index;

import java.util.List;
import java.util.Map;

/** Which blocks are the same material in different shapes, worked out from their ids. */
final class MaterialSets {
    private MaterialSets() {}

    // The shapes a material comes in, in the order a set lists them after its full blocks. A suffix that ends
    // another comes after it, so "_fence_gate" is tried before "_fence" finds nothing.
    private static final List<String> SUFFIXES = List.of(
            "_planks", "_stripped_log", "_log", "_wood", "_stem", "_hyphae",
            "_slab", "_stairs", "_wall_hanging_sign", "_hanging_sign", "_wall_sign", "_sign", "_wall",
            "_fence_gate", "_fence", "_trapdoor", "_door", "_button", "_pressure_plate");

    // Full blocks whose shapes are named after something else: "stone_bricks" has a "stone_brick_slab". Bricks and
    // tiles lose their plural by rule, below. These are the rest.
    private static final Map<String, String> IRREGULAR = Map.ofEntries(
            Map.entry("quartz_block", "quartz"),
            Map.entry("purpur_block", "purpur"),
            Map.entry("bamboo_block", "bamboo"),
            Map.entry("stripped_bamboo_block", "bamboo"),
            Map.entry("iron_block", "iron"),
            Map.entry("copper_block", "copper"),
            Map.entry("waxed_copper_block", "waxed_copper"),
            Map.entry("resin_block", "resin"),
            Map.entry("moss_block", "moss"),
            Map.entry("snow_block", "snow"));

    /** Where a shape comes in its set: full blocks first, then in the order of the suffixes. */
    static int order(String path) {
        for (int i = 0; i < SUFFIXES.size(); i++) if (path.endsWith(SUFFIXES.get(i))) return i + 1;
        return 0;
    }

    /**
     * The material a block id belongs to: its namespace, and its path without the shape. So
     * {@code minecraft:stone_brick_stairs} and {@code minecraft:stone_bricks} both give {@code minecraft:stone_brick},
     * and {@code minecraft:stripped_oak_log} gives {@code minecraft:oak}.
     */
    static String base(String id) {
        int colon = id.indexOf(':');
        String namespace = id.substring(0, colon + 1), path = id.substring(colon + 1);
        String base = IRREGULAR.get(path);
        if (base == null) {
            base = path;
            for (String suffix : SUFFIXES) {
                if (path.endsWith(suffix) && path.length() > suffix.length()) {
                    base = path.substring(0, path.length() - suffix.length());
                    break;
                }
            }
            if (base.equals(path)) {
                // "bricks" and "nether_bricks" name their shapes "brick_slab" and "nether_brick_slab".
                if (path.endsWith("bricks") || path.endsWith("tiles")) base = path.substring(0, path.length() - 1);
            } else if (base.startsWith("stripped_")) {
                base = base.substring("stripped_".length());
            }
        }
        return namespace + base;
    }

    /**
     * A material's name from a block id, for the palette made of its set: the path of {@link #base} with spaces
     * for its underscores and a capital first, so {@code minecraft:cherry_planks} gives "Cherry" and
     * {@code minecraft:stone_brick_stairs} gives "Stone brick". Ids are English, so the name is too.
     */
    static String name(String id) {
        String base = base(id);
        String words = base.substring(base.indexOf(':') + 1).replace('_', ' ').replace('/', ' ').strip();
        return words.isEmpty() ? words : Character.toUpperCase(words.charAt(0)) + words.substring(1);
    }
}
