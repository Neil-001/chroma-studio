package dev.chromastudio.client.index;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.minecraft.SharedConstants;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.Bootstrap;
import net.minecraft.server.packs.PackLocationInfo;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.VanillaPackResourcesBuilder;
import net.minecraft.server.packs.repository.PackSource;
import net.minecraft.server.packs.resources.MultiPackResourceManager;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Fills the block index from the game's own textures, for tests outside this package, as {@link GameIndexTest} does
 * inside it: each block's faces are given by texture name, as no models are baked without a client.
 */
public final class TestIndex {
    private TestIndex() {}

    private static ResourceManager resources;

    /** Loads the game's registries and its vanilla textures, once. */
    public static void loadGame() {
        if (resources != null) return;
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
        resources = new MultiPackResourceManager(PackType.CLIENT_RESOURCES, List.of(new VanillaPackResourcesBuilder()
                .exposeNamespace("minecraft").pushJarResources()
                .build(new PackLocationInfo("vanilla", Component.literal("vanilla"), PackSource.BUILT_IN, Optional.empty())).fullResources()));
    }

    /** A vanilla block by its id's path. */
    public static Block block(String path) {
        return BuiltInRegistries.BLOCK.getOptional(Identifier.withDefaultNamespace(path)).orElseThrow(() -> new AssertionError("No block " + path));
    }

    /** Puts these blocks in the index, each one entry, its default state, with one texture on all six faces. */
    public static BlockIndex index(Map<String, String> blocksAndTextures) {
        List<List<Entry>> blocks = new ArrayList<>();
        Map<BlockState, Entry> byState = new IdentityHashMap<>();
        blocksAndTextures.forEach((path, sprite) -> {
            Block block = block(path);
            Sampled sampled = new Sampled(Textures.read(resources, Identifier.withDefaultNamespace(sprite), true));
            FaceLayers[] faces = new FaceLayers[6];
            for (int i = 0; i < 6; i++) faces[i] = new FaceLayers(List.of(new FaceLayers.Layer("minecraft:" + sprite, sampled, -1, null, 1)));
            Entry e = new Entry(block.defaultBlockState(), "minecraft:" + path, "", false, true, faces);
            blocks.add(List.of(e));
            byState.put(e.state(), e);
        });
        BlockIndex.get().adopt(blocks, byState);
        return BlockIndex.get();
    }
}
