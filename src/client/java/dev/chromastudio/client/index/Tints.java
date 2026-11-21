package dev.chromastudio.client.index;

import dev.chromastudio.core.color.Face;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import net.minecraft.client.Minecraft;
import net.minecraft.client.color.block.BlockTintSource;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.renderer.block.BlockAndTintGetter;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.data.registries.VanillaRegistries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.CardinalLighting;
import net.minecraft.world.level.ColorResolver;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.lighting.LevelLightEngine;
import net.minecraft.world.level.material.FluidState;

public final class Tints {
    private Tints() {}

    private static final BlockPos SPOT = new BlockPos(0, 64, 0);

    static final TintContext NONE = (_, _) -> TintContext.NO_TINT;

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

    private static int colour(BlockTintSource source, BlockState state, BlockAndTintGetter level, BlockPos pos) {
        try {
            int c = source.colorInWorld(state, level, pos);
            return c == -1 ? TintContext.NO_TINT : c & 0xFFFFFF;
        } catch (Exception _) {
            return TintContext.NO_TINT;
        }
    }

    public static List<ResourceKey<Biome>> biomes() {
        return registries().lookupOrThrow(Registries.BIOME).listElementIds().toList();
    }

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

    private static final class Vanilla {
        static final HolderLookup.Provider LOOKUP = VanillaRegistries.createWorldLookup();
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

    private record OneBiome(Biome biome) implements BlockAndTintGetter {
        @Override public int getBlockTint(BlockPos pos, ColorResolver resolver) { return resolver.getColor(biome, pos.getX(), pos.getZ()); }
        @Override public CardinalLighting cardinalLighting() { return EMPTY.cardinalLighting(); }
        @Override public LevelLightEngine getLightEngine() { return EMPTY.getLightEngine(); }
        @Override public BlockEntity getBlockEntity(BlockPos pos) { return EMPTY.getBlockEntity(pos); }
        @Override public BlockState getBlockState(BlockPos pos) { return EMPTY.getBlockState(pos); }
        @Override public FluidState getFluidState(BlockPos pos) { return EMPTY.getFluidState(pos); }
        @Override public int getHeight() { return EMPTY.getHeight(); }
        @Override public int getMinY() { return EMPTY.getMinY(); }
    }
}
