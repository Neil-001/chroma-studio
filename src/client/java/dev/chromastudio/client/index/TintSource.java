package dev.chromastudio.client.index;

import net.minecraft.client.color.block.BlockColors;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.block.state.BlockState;

/**
 * What gives a tinted texture its colour in a world. 1.21.1 has no tint source of its own: the block colours are
 * asked with a state, a world, a position and the model's tint index, as the game asks them for a quad.
 */
@FunctionalInterface
public interface TintSource {
    /** The tint for a state at a spot, packed 0xRRGGBB, or -1 for none. */
    int colorInWorld(BlockState state, BlockAndTintGetter level, BlockPos pos);

    /** The block colours asked for one tint index. Water's is among them, so fluids need nothing else. */
    static TintSource of(BlockColors colours, int tintIndex) {
        return (state, level, pos) -> colours.getColor(state, level, pos, tintIndex);
    }
}
