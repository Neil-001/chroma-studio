package dev.chromastudio.client.index;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.state.BlockState;

public interface TintContext {
    int NO_TINT = -1;

    TintContext HERE = Tints.HERE;

    static TintContext at(BlockPos pos) { return Tints.at(pos); }

    static TintContext none() { return Tints.NONE; }

    static TintContext biome(ResourceKey<Biome> key) { return Tints.biome(key); }

    int colour(TintSource source, BlockState state);
}
