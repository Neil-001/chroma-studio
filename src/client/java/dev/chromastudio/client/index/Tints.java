package dev.chromastudio.client.index;

import dev.chromastudio.core.color.Face;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.data.registries.VanillaRegistries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.ColorResolver;
import net.minecraft.world.level.EmptyBlockAndTintGetter;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.lighting.LevelLightEngine;
import net.minecraft.world.level.material.FluidState;

/** The tint contexts, and the biomes they can be asked about. */
public final class Tints {
    private Tints() {}

    // Biome colours can vary with x and z, as a swamp's grass does, so every biome is asked at the same spot.
    private static final BlockPos SPOT = new BlockPos(0, 64, 0);

    static final TintContext NONE = (source, state) -> TintContext.NO_TINT;

    static final TintContext HERE = (source, state) -> {
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.level == null || mc.player == null) return TintContext.NO_TINT;
        return colour(source, state, mc.level, mc.player.blockPosition());
    };

    static TintContext at(BlockPos pos) {
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.level == null) return NONE;
        BlockAndTintGetter level = mc.level;
        BlockPos spot = pos.immutable();
        return (source, state) -> colour(source, state, level, spot);
    }

    static TintContext biome(ResourceKey<Biome> key) {
        return (source, state) -> {
            Optional<Holder.Reference<Biome>> biome = registries().lookupOrThrow(Registries.BIOME).get(key);
            return biome.isEmpty() ? TintContext.NO_TINT : colour(source, state, new OneBiome(biome.get().value()), SPOT);
        };
    }

    /** A source that throws, as a modded one may without a real world, means no tint. */
    private static int colour(TintSource source, BlockState state, BlockAndTintGetter level, BlockPos pos) {
        try {
            int c = source.colorInWorld(state, level, pos);
            return c == -1 ? TintContext.NO_TINT : c & 0xFFFFFF;
        } catch (Exception ignored) {
            return TintContext.NO_TINT;
        }
    }

    /** Every biome the game knows now: the world's when in one, else the vanilla set. */
    public static List<ResourceKey<Biome>> biomes() {
        return registries().lookupOrThrow(Registries.BIOME).listElementIds().toList();
    }

    /**
     * The tint of an entry's face in every biome, in the order of {@link #biomes()}, each packed 0xRRGGBB or
     * {@link TintContext#NO_TINT}. Empty when the face has no tinted texture. {@link Face#ANY} takes the first
     * face that has one.
     */
    public static List<Integer> tintsAcross(Entry entry, Face face) {
        FaceLayers layers = face == Face.ANY ? entry.firstTintedFace() : entry.layers(face);
        if (layers == null || !layers.tinted()) return List.of();
        List<Integer> out = new ArrayList<>();
        registries().lookupOrThrow(Registries.BIOME).listElements().forEach(biome -> {
            OneBiome level = new OneBiome(biome.value());
            out.add(layers.firstTint((source, state) -> colour(source, state, level, SPOT), entry.state()));
        });
        return out;
    }

    /** The vanilla registries, for when no world is loaded. Building them takes a moment, so it happens once. */
    private static final class Vanilla {
        static final HolderLookup.Provider LOOKUP = VanillaRegistries.createLookup();
    }

    static HolderLookup.Provider registries() {
        Minecraft mc = Minecraft.getInstance();
        if (mc != null) {
            if (mc.level != null) return mc.level.registryAccess();
            ClientPacketListener connection = mc.getConnection();
            if (connection != null) return connection.registryAccess();
        }
        return Vanilla.LOOKUP;
    }

    /** A world that is one biome everywhere and otherwise empty. */
    private record OneBiome(Biome biome) implements BlockAndTintGetter {
        @Override public int getBlockTint(BlockPos pos, ColorResolver resolver) { return resolver.getColor(biome, pos.getX(), pos.getZ()); }
        @Override public float getShade(Direction direction, boolean shaded) { return EmptyBlockAndTintGetter.INSTANCE.getShade(direction, shaded); }
        @Override public LevelLightEngine getLightEngine() { return EmptyBlockAndTintGetter.INSTANCE.getLightEngine(); }
        @Override public BlockEntity getBlockEntity(BlockPos pos) { return EmptyBlockAndTintGetter.INSTANCE.getBlockEntity(pos); }
        @Override public BlockState getBlockState(BlockPos pos) { return EmptyBlockAndTintGetter.INSTANCE.getBlockState(pos); }
        @Override public FluidState getFluidState(BlockPos pos) { return EmptyBlockAndTintGetter.INSTANCE.getFluidState(pos); }
        @Override public int getHeight() { return EmptyBlockAndTintGetter.INSTANCE.getHeight(); }
        @Override public int getMinY() { return EmptyBlockAndTintGetter.INSTANCE.getMinY(); }
    }
}
