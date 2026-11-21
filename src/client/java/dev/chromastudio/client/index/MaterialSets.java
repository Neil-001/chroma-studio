package dev.chromastudio.client.index;

import java.util.List;
import java.util.Map;

final class MaterialSets {
    private MaterialSets() {}

    private static final List<String> SUFFIXES = List.of(
            "_planks", "_stripped_log", "_log", "_wood", "_stem", "_hyphae",
            "_slab", "_stairs", "_wall_hanging_sign", "_hanging_sign", "_wall_sign", "_sign", "_wall",
            "_fence_gate", "_fence", "_trapdoor", "_door", "_button", "_pressure_plate");

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

    static int order(String path) {
        for (int i = 0; i < SUFFIXES.size(); i++) if (path.endsWith(SUFFIXES.get(i))) return i + 1;
        return 0;
    }

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
                if (path.endsWith("bricks") || path.endsWith("tiles")) base = path.substring(0, path.length() - 1);
            } else if (base.startsWith("stripped_")) {
                base = base.substring("stripped_".length());
            }
        }
        return namespace + base;
    }

    private static final List<String> VARIANTS = List.of("cracked_", "mossy_", "chiseled_", "infested_");

    static String family(String id) {
        String base = base(id);
        int colon = base.indexOf(':');
        String namespace = base.substring(0, colon + 1), path = base.substring(colon + 1);
        for (String variant : VARIANTS) {
            if (path.startsWith(variant) && path.length() > variant.length()) return base(namespace + path.substring(variant.length()));
        }
        return base;
    }

    static String name(String id) {
        String base = base(id);
        String words = base.substring(base.indexOf(':') + 1).replace('_', ' ').replace('/', ' ').strip();
        return words.isEmpty() ? words : Character.toUpperCase(words.charAt(0)) + words.substring(1);
    }
}
