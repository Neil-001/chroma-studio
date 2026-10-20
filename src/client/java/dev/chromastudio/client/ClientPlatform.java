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

/** What the client needs from the mod loader. The loader's client entrypoint passes one to {@link ChromaStudioClient#init}. */
public interface ClientPlatform {
    Path configDir();

    Path gameDir();

    boolean isModLoaded(String id);

    /**
     * A block's model parts and its particle texture, without a world. NeoForge deprecates the game's own methods for
     * these in favour of ones that take a world and a position, so each loader calls its own.
     */
    void modelParts(BlockStateModel model, BlockState state, RandomSource random, List<BlockModelPart> out);

    TextureAtlasSprite particleIcon(BlockStateModel model, BlockState state);

    /**
     * Asks for a picture-in-picture to be drawn this frame. The game keeps the GUI's render state to itself: Fabric
     * opens the field and NeoForge adds a method, so each loader submits in its own way.
     */
    void submitPicture(GuiGraphics g, PictureInPictureRenderState state);

    /** Sends a command as the player, without the slash. Does nothing when not in a world. */
    void sendChatCommand(String command);
}
