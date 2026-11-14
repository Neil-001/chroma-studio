package dev.chromastudio.core.palette;

import java.util.List;

/**
 * A gradient the player saved from the Gradient tab: its stops and controls, and what the tab showed for it, so it
 * comes back as it was and another mod can use the very blocks the player saw.
 *
 * @param steps  how many steps the controls asked for, from 2 to 32
 * @param easing an {@link Easing} by name, kept as text so a file from a later version with a new one still loads
 * @param arc    a {@link HueArc} by name
 * @param kind   the kind filter it was made with, by name, or empty for every kind
 * @param blocks one per step, in order: the colour the step wanted and the block chosen for it
 */
public record StoredGradient(String name, List<Stop> stops, int steps, String easing, String arc, boolean spread, String kind, List<Step> blocks) {
    /** The fewest and most steps a gradient has, as the steps setting allows. */
    public static final int MIN_STEPS = 2, MAX_STEPS = 32;

    /**
     * One stop: a colour, or a block and the colour it showed when saved, which stands in for it where the block is
     * gone. {@code rgb} is {@code 0xRRGGBB}.
     */
    public record Stop(int rgb, PaletteEntry block) {
        public Stop {
            rgb &= 0xFFFFFF;
        }

        public static Stop colour(int rgb) { return new Stop(rgb, null); }

        public boolean isBlock() { return block != null; }
    }

    /**
     * One step of the answer: the colour wanted, as {@code 0xRRGGBB}, the block chosen and whether the player chose
     * it by hand from the swap list.
     */
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
