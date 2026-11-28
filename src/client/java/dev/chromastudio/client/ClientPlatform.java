package dev.chromastudio.client;

import com.mojang.blaze3d.vertex.PoseStack;
import java.nio.file.Path;
import java.util.List;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.state.BlockState;

public interface ClientPlatform {
    Path configDir();

    Path gameDir();

    boolean isModLoaded(String id);

    boolean isFabric();

    List<BakedQuad> quads(BakedModel model, BlockState state, Direction side, RandomSource random);

    TextureAtlasSprite particleIcon(BakedModel model, BlockState state);

    void renderBlock(BlockState state, PoseStack pose, MultiBufferSource buffers, int light, int overlay);

    void sendChatCommand(String command);
}
