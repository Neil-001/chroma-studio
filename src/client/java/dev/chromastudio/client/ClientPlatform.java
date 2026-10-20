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

/** What the client needs from the mod loader. The loader's client entrypoint passes one to {@link ChromaStudioClient#init}. */
public interface ClientPlatform {
    Path configDir();

    Path gameDir();

    boolean isModLoaded(String id);

    /**
     * A block model's quads on one side, or those on no side for null, and its particle texture, without a world.
     * NeoForge extends the game's methods for these with model data, so each loader calls its own.
     */
    List<BakedQuad> quads(BakedModel model, BlockState state, Direction side, RandomSource random);

    TextureAtlasSprite particleIcon(BakedModel model, BlockState state);

    /** Draws a block on its own, as the game draws one in the GUI, without a world. */
    void renderBlock(BlockState state, PoseStack pose, MultiBufferSource buffers, int light, int overlay);

    /** Sends a command as the player, without the slash. Does nothing when not in a world. */
    void sendChatCommand(String command);
}
