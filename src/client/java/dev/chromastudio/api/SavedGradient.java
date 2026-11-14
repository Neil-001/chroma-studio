package dev.chromastudio.api;

import java.util.List;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

/**
 * A gradient the player saved in the Gradient tab, as it was when saved. It is a copy: it doesn't change when the
 * player saves the gradient again, and nothing done to it changes the mod. Any thread may read it.
 *
 * @since 1.2.0
 */
public final class SavedGradient {
    private final String name;
    private final List<Stop> stops;
    private final int steps;
    private final List<Step> blocks;

    SavedGradient(String name, List<Stop> stops, int steps, List<Step> blocks) {
        this.name = name;
        this.stops = List.copyOf(stops);
        this.steps = steps;
        this.blocks = List.copyOf(blocks);
    }

    /** The name the player saved it under, which no other saved gradient has, whatever their case. */
    public String name() { return name; }

    /** The stops the gradient runs through, from its start to its end. There are always at least two. */
    public List<Stop> stops() { return stops; }

    /** How many steps the player asked for, from 2 to 32. */
    public int steps() { return steps; }

    /**
     * Each step's ideal colour and the block chosen for it, in order from the gradient's start, as the Gradient tab
     * showed them when the player saved. No block appears twice. There is one for each of {@link #steps()}, though a
     * file edited by hand may hold fewer.
     */
    public List<Step> blocks() { return blocks; }

    /** One stop: a colour the player typed or picked, or a block. */
    public static final class Stop {
        private final int rgb;
        private final @Nullable BlockState block;
        private final @Nullable String blockId;

        Stop(int rgb, @Nullable BlockState block, @Nullable String blockId) {
            this.rgb = rgb;
            this.block = block;
            this.blockId = blockId;
        }

        /** The stop's colour, packed {@code 0xRRGGBB}. For a block, the colour of its face as Chroma Studio saw it when saved. */
        public int rgb() { return rgb; }

        /** Whether the stop is a block rather than a colour. */
        public boolean isBlock() { return blockId != null; }

        /** The block, or null for a colour stop or a block this game doesn't have, such as one from a mod since removed. */
        public @Nullable BlockState block() { return block; }

        /** The block as text, such as {@code minecraft:oak_log[axis=y]}, or null for a colour stop. */
        public @Nullable String blockId() { return blockId; }
    }

    /** One step of the gradient. */
    public static final class Step {
        private final int idealRgb;
        private final @Nullable BlockState block;
        private final String blockId;
        private final boolean chosenByHand;

        Step(int idealRgb, @Nullable BlockState block, String blockId, boolean chosenByHand) {
            this.idealRgb = idealRgb;
            this.block = block;
            this.blockId = blockId;
            this.chosenByHand = chosenByHand;
        }

        /** The colour the step stands for on the smooth run between the stops, packed {@code 0xRRGGBB}. */
        public int idealRgb() { return idealRgb; }

        /** The block chosen for the step, or null when this game doesn't have it, such as one from a mod since removed. */
        public @Nullable BlockState block() { return block; }

        /** The block as text, such as {@code minecraft:oak_log[axis=y]}, which is there even when {@link #block()} isn't. */
        public String blockId() { return blockId; }

        /** Whether the player chose this block by hand from the Gradient tab's swap list, rather than taking the tab's own. */
        public boolean chosenByHand() { return chosenByHand; }
    }
}
