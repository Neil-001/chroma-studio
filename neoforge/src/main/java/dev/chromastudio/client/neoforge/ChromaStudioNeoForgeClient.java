package dev.chromastudio.client.neoforge;

import dev.chromastudio.client.ChromaStudioClient;
import dev.chromastudio.client.ClientPlatform;
import dev.chromastudio.client.ChromaCommand;
import dev.chromastudio.client.Keys;
import dev.chromastudio.client.action.Capture;
import dev.chromastudio.client.action.HotbarFill;
import dev.chromastudio.client.index.BlockIndex;
import dev.chromastudio.client.ui.Hud;
import dev.chromastudio.client.ui.SettingsScreen;
import dev.chromastudio.client.ui.Tooltips;
import dev.chromastudio.client.ui.tabs.DioramaPip;
import java.nio.file.Path;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.renderer.block.BlockAndTintGetter;
import net.minecraft.client.renderer.block.dispatch.BlockStateModel;
import net.minecraft.client.renderer.block.dispatch.BlockStateModelPart;
import net.minecraft.client.resources.model.sprite.Material;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.neoforge.client.event.AddClientReloadListenersEvent;
import net.neoforged.neoforge.client.event.ClientChatReceivedEvent;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterClientCommandsEvent;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.event.RegisterPictureInPictureRenderersEvent;
import net.neoforged.neoforge.client.event.SubmitCustomGeometryEvent;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.ItemTooltipEvent;

/** NeoForge's client entrypoint, and the mod's only one: a server loads nothing from the jar. */
@Mod(value = ChromaStudioClient.MOD_ID, dist = Dist.CLIENT)
public final class ChromaStudioNeoForgeClient implements ClientPlatform {
    public ChromaStudioNeoForgeClient(IEventBus modBus, ModContainer container) {
        ChromaStudioClient.init(this);
        // The mod list's Config button.
        container.registerExtensionPoint(IConfigScreenFactory.class, (_, modList) -> new SettingsScreen(modList));
        modBus.addListener(RegisterKeyMappingsEvent.class, event -> {
            event.registerCategory(Keys.CATEGORY);
            Keys.ALL.forEach(event::register);
        });
        // Hud.render checks for a hidden HUD (F1) itself: NeoForge draws modded layers even then.
        modBus.addListener(RegisterGuiLayersEvent.class, event -> event.registerAboveAll(
                Identifier.fromNamespaceAndPath(ChromaStudioClient.MOD_ID, "hud"), (graphics, _) -> Hud.render(graphics)));
        modBus.addListener(AddClientReloadListenersEvent.class, event -> event.addListener(Identifier.fromNamespaceAndPath(ChromaStudioClient.MOD_ID, "index"),
                BlockIndex.install(ModList.get().getMods().stream().map(mod -> mod.getModId() + " " + mod.getVersion()).toList())));
        modBus.addListener(RegisterPictureInPictureRenderersEvent.class, event -> event.register(DioramaPip.State.class, DioramaPip::new));
        NeoForge.EVENT_BUS.addListener(ClientPlayerNetworkEvent.LoggingIn.class, _ -> BlockIndex.get().worldJoined());
        NeoForge.EVENT_BUS.addListener(RegisterClientCommandsEvent.class, event -> event.getDispatcher().register(ChromaCommand.build()));
        NeoForge.EVENT_BUS.addListener(ItemTooltipEvent.class, event -> Tooltips.append(event.getItemStack(), event.getToolTip()));
        NeoForge.EVENT_BUS.addListener(SubmitCustomGeometryEvent.class,
                event -> Capture.render(event.getSubmitNodeCollector(), event.getPoseStack(), event.getLevelRenderState().cameraRenderState.pos));
        // Keeps the game's replies to an operator's hotbar fill out of chat.
        NeoForge.EVENT_BUS.addListener(ClientChatReceivedEvent.System.class, event -> {
            if (HotbarFill.hidesFeedback(event.getMessage(), event.isOverlay())) event.setCanceled(true);
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
