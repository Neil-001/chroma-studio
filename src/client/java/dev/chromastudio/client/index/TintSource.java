package dev.chromastudio.client.index;

import net.minecraft.client.color.block.BlockColors;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.block.state.BlockState;

@FunctionalInterface
public interface TintSource {
    int colorInWorld(BlockState state, BlockAndTintGetter level, BlockPos pos);

    static TintSource of(BlockColors colours, int tintIndex) {
        return (state, level, pos) -> colours.getColor(state, level, pos, tintIndex);
    }
}
