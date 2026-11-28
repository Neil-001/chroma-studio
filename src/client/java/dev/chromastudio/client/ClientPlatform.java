package dev.chromastudio.client;

import java.nio.file.Path;
import java.util.List;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.render.state.pip.PictureInPictureRenderState;
import net.minecraft.client.renderer.block.model.BlockModelPart;
import net.minecraft.client.renderer.block.model.BlockStateModel;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.state.BlockState;

public interface ClientPlatform {
    Path configDir();

    Path gameDir();

    boolean isModLoaded(String id);

    boolean isFabric();

    void modelParts(BlockStateModel model, BlockState state, RandomSource random, List<BlockModelPart> out);

    TextureAtlasSprite particleIcon(BlockStateModel model, BlockState state);

    void submitPicture(GuiGraphics g, PictureInPictureRenderState state);

    void sendChatCommand(String command);
}
