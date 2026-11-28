package dev.chromastudio.client.index;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.minecraft.SharedConstants;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.Bootstrap;
import net.minecraft.server.packs.PackLocationInfo;
import net.minecraft.server.packs.PackResources;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.VanillaPackResourcesBuilder;
import net.minecraft.server.packs.repository.PackSource;
import net.minecraft.server.packs.resources.MultiPackResourceManager;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.tags.TagKey;
import net.minecraft.tags.TagLoader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

public final class TestIndex {
    private TestIndex() {}

    private static ResourceManager resources;

    public static void loadGame() {
        if (resources != null) return;
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
        try (MultiPackResourceManager data = new MultiPackResourceManager(PackType.SERVER_DATA, List.of(vanilla()))) {
            Map<TagKey<Block>, List<Holder<Block>>> tags = new HashMap<>();
            new TagLoader<Holder<Block>>(id -> BuiltInRegistries.BLOCK.getHolder(ResourceKey.create(Registries.BLOCK, id)), Registries.tagsDirPath(Registries.BLOCK))
                    .loadAndBuild(data).forEach((id, blocks) -> tags.put(TagKey.create(Registries.BLOCK, id), List.copyOf(blocks)));
            BuiltInRegistries.BLOCK.bindTags(tags);
        }
        resources = new MultiPackResourceManager(PackType.CLIENT_RESOURCES, List.of(vanilla()));
    }

    public static ResourceManager resources() {
        loadGame();
        return resources;
    }

    private static PackResources vanilla() {
        return new VanillaPackResourcesBuilder().exposeNamespace("minecraft").pushJarResources()
                .build(new PackLocationInfo("vanilla", Component.literal("vanilla"), PackSource.BUILT_IN, Optional.empty()));
    }

    public static Block block(String path) {
        return BuiltInRegistries.BLOCK.getOptional(ResourceLocation.withDefaultNamespace(path)).orElseThrow(() -> new AssertionError("No block " + path));
    }

    public static BlockIndex index(Map<String, String> blocksAndTextures) {
        List<List<Entry>> blocks = new ArrayList<>();
        Map<BlockState, Entry> byState = new IdentityHashMap<>();
        blocksAndTextures.forEach((path, sprite) -> {
            Block block = block(path);
            Sampled sampled = new Sampled(Textures.read(resources, ResourceLocation.withDefaultNamespace(sprite), true));
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
