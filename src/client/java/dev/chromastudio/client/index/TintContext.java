package dev.chromastudio.client.index;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.state.BlockState;

/** Where a tinted block, such as grass, leaves or water, is looked at. It decides the tint. */
public interface TintContext {
    /** What {@link #colour} gives when there is no tint to apply. */
    int NO_TINT = -1;

    /**
     * Where the player stands, with the game's biome blending. Outside a world it applies no tint. Use it on the
     * client thread only.
     */
    TintContext HERE = Tints.HERE;

    /**
     * One spot in the world the player is in now, with the game's biome blending. It keeps hold of that world, so a
     * search on another thread reads the same place throughout. Make it on the client thread. Outside a world it
     * applies no tint.
     */
    static TintContext at(BlockPos pos) { return Tints.at(pos); }

    /** No tint: tinted textures keep the grey they are drawn in. */
    static TintContext none() { return Tints.NONE; }

    /** The middle of one biome. An unknown biome applies no tint. */
    static TintContext biome(ResourceKey<Biome> key) { return Tints.biome(key); }

    /** The colour a tint source gives a state here, packed 0xRRGGBB, or {@link #NO_TINT}. */
    int colour(TintSource source, BlockState state);
}
