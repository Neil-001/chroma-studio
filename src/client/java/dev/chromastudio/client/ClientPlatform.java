package dev.chromastudio.client;

import java.nio.file.Path;
import java.util.List;
import net.minecraft.client.renderer.block.dispatch.BlockStateModel;
import net.minecraft.client.renderer.block.dispatch.BlockStateModelPart;
import net.minecraft.client.resources.model.sprite.Material;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.state.BlockState;

public interface ClientPlatform {
    Path configDir();

    Path gameDir();

    boolean isModLoaded(String id);

    boolean isFabric();

    void modelParts(BlockStateModel model, BlockState state, RandomSource random, List<BlockStateModelPart> out);

    Material.Baked particleMaterial(BlockStateModel model, BlockState state);

    void sendChatCommand(String command);
}
