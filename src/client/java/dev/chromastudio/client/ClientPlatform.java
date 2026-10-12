package dev.chromastudio.client;

import java.nio.file.Path;
import java.util.List;
import net.minecraft.client.renderer.block.dispatch.BlockStateModel;
import net.minecraft.client.renderer.block.dispatch.BlockStateModelPart;
import net.minecraft.client.resources.model.sprite.Material;
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
    void modelParts(BlockStateModel model, BlockState state, RandomSource random, List<BlockStateModelPart> out);

    Material.Baked particleMaterial(BlockStateModel model, BlockState state);

    /** Sends a command as the player, without the slash. Does nothing when not in a world. */
    void sendChatCommand(String command);
}
