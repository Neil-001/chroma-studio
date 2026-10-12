package dev.chromastudio.client.fabric;

import dev.chromastudio.client.ChromaStudioClient;
import dev.chromastudio.client.ClientPlatform;
import dev.chromastudio.client.Keys;
import java.nio.file.Path;
import java.util.List;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.renderer.block.dispatch.BlockStateModel;
import net.minecraft.client.renderer.block.dispatch.BlockStateModelPart;
import net.minecraft.client.resources.model.sprite.Material;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.state.BlockState;

/** Fabric's client entrypoint. */
public class ChromaStudioFabricClient implements ClientModInitializer, ClientPlatform {
    @Override
    public void onInitializeClient() {
        ChromaStudioClient.init(this);
        KeyMapping.Category.register(Keys.CATEGORY.id());
        Keys.ALL.forEach(KeyMappingHelper::registerKeyMapping);
        ClientTickEvents.END_CLIENT_TICK.register(ChromaStudioClient::tick);
    }

    @Override public Path configDir() { return FabricLoader.getInstance().getConfigDir(); }
    @Override public Path gameDir() { return FabricLoader.getInstance().getGameDir(); }
    @Override public boolean isModLoaded(String id) { return FabricLoader.getInstance().isModLoaded(id); }
    @Override public void modelParts(BlockStateModel model, BlockState state, RandomSource random, List<BlockStateModelPart> out) {
        model.collectParts(random, out);
    }
    @Override public Material.Baked particleMaterial(BlockStateModel model, BlockState state) { return model.particleMaterial(); }
    @Override public void sendChatCommand(String command) {
        ClientPacketListener connection = Minecraft.getInstance().getConnection();
        if (connection != null) connection.sendCommand(command);
    }
}
