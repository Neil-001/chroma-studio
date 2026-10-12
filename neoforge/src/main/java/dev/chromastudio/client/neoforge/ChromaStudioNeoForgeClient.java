package dev.chromastudio.client.neoforge;

import dev.chromastudio.client.ChromaStudioClient;
import dev.chromastudio.client.ClientPlatform;
import dev.chromastudio.client.Keys;
import java.nio.file.Path;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.renderer.block.BlockAndTintGetter;
import net.minecraft.client.renderer.block.dispatch.BlockStateModel;
import net.minecraft.client.renderer.block.dispatch.BlockStateModelPart;
import net.minecraft.client.resources.model.sprite.Material;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.common.NeoForge;

/** NeoForge's client entrypoint, and the mod's only one: a server loads nothing from the jar. */
@Mod(value = ChromaStudioClient.MOD_ID, dist = Dist.CLIENT)
public final class ChromaStudioNeoForgeClient implements ClientPlatform {
    public ChromaStudioNeoForgeClient(IEventBus modBus) {
        ChromaStudioClient.init(this);
        modBus.addListener(RegisterKeyMappingsEvent.class, event -> {
            event.registerCategory(Keys.CATEGORY);
            Keys.ALL.forEach(event::register);
        });
        NeoForge.EVENT_BUS.addListener(ClientTickEvent.Post.class, _ -> ChromaStudioClient.tick(Minecraft.getInstance()));
    }

    @Override public Path configDir() { return FMLPaths.CONFIGDIR.get(); }
    @Override public Path gameDir() { return FMLPaths.GAMEDIR.get(); }
    @Override public boolean isModLoaded(String id) { return ModList.get().isLoaded(id); }
    // The colours are worked out without a world, so NeoForge's methods get an empty one.
    @Override public void modelParts(BlockStateModel model, BlockState state, RandomSource random, List<BlockStateModelPart> out) {
        model.collectParts(BlockAndTintGetter.EMPTY, BlockPos.ZERO, state, random, out);
    }
    @Override public Material.Baked particleMaterial(BlockStateModel model, BlockState state) {
        return model.particleMaterial(BlockAndTintGetter.EMPTY, BlockPos.ZERO, state);
    }
    @Override public void sendChatCommand(String command) {
        ClientPacketListener connection = Minecraft.getInstance().getConnection();
        if (connection != null) connection.sendCommand(command);
    }
}
