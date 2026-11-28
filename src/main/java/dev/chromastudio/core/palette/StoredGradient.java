package dev.chromastudio.core.palette;

import java.util.List;

public record StoredGradient(String name, List<Stop> stops, int steps, String easing, String arc, boolean spread, String kind, List<Step> blocks) {
    public static final int MIN_STEPS = 2, MAX_STEPS = 32;

    public record Stop(int rgb, PaletteEntry block) {
        public Stop {
            rgb &= 0xFFFFFF;
        }

        public static Stop colour(int rgb) { return new Stop(rgb, null); }

        public boolean isBlock() { return block != null; }
    }

    public record Step(int ideal, PaletteEntry block, boolean byHand) {
        public Step {
            ideal &= 0xFFFFFF;
            if (block == null) throw new IllegalArgumentException("A step needs a block");
        }
    }

    public StoredGradient {
        if (name == null || name.isBlank()) throw new IllegalArgumentException("A gradient needs a name");
        name = name.strip();
        if (stops.size() < 2) throw new IllegalArgumentException("A gradient needs at least two stops");
        steps = Math.clamp(steps, MIN_STEPS, MAX_STEPS);
        stops = List.copyOf(stops);
        blocks = List.copyOf(blocks);
        easing = easing == null ? "" : easing;
        arc = arc == null ? "" : arc;
        kind = kind == null ? "" : kind;
    }
}
