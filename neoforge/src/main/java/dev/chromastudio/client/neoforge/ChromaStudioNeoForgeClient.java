package dev.chromastudio.client.neoforge;

import com.mojang.blaze3d.vertex.PoseStack;
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
import java.nio.file.Path;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.neoforge.client.event.ClientChatReceivedEvent;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterClientCommandsEvent;
import net.neoforged.neoforge.client.event.RegisterClientReloadListenersEvent;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;
import net.neoforged.neoforge.client.model.data.ModelData;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.ItemTooltipEvent;

/** NeoForge's client entrypoint, and the mod's only one: a server loads nothing from the jar. */
@Mod(value = ChromaStudioClient.MOD_ID, dist = Dist.CLIENT)
public final class ChromaStudioNeoForgeClient implements ClientPlatform {
    public ChromaStudioNeoForgeClient(IEventBus modBus, ModContainer container) {
        ChromaStudioClient.init(this);
        // The mod list's Config button.
        container.registerExtensionPoint(IConfigScreenFactory.class, (mod, modList) -> new SettingsScreen(modList));
        modBus.addListener(RegisterKeyMappingsEvent.class, event -> Keys.ALL.forEach(event::register));
        // Hud.render checks for a hidden HUD (F1) itself: NeoForge draws modded layers even then.
        modBus.addListener(RegisterGuiLayersEvent.class, event -> event.registerAboveAll(
                ResourceLocation.fromNamespaceAndPath(ChromaStudioClient.MOD_ID, "hud"), (graphics, tick) -> Hud.render(graphics)));
        modBus.addListener(RegisterClientReloadListenersEvent.class, event -> event.registerReloadListener(
                BlockIndex.install(ModList.get().getMods().stream().map(mod -> mod.getModId() + " " + mod.getVersion()).toList())));
        NeoForge.EVENT_BUS.addListener(ClientPlayerNetworkEvent.LoggingIn.class, event -> BlockIndex.get().worldJoined());
        NeoForge.EVENT_BUS.addListener(RegisterClientCommandsEvent.class, event -> event.getDispatcher().register(ChromaCommand.build()));
        NeoForge.EVENT_BUS.addListener(ItemTooltipEvent.class, event -> Tooltips.append(event.getItemStack(), event.getToolTip()));
        // The capture outline is drawn straight to the game's buffers after the entities.
        NeoForge.EVENT_BUS.addListener(RenderLevelStageEvent.class, event -> {
            if (event.getStage() == RenderLevelStageEvent.Stage.AFTER_ENTITIES)
                Capture.render(event.getPoseStack() != null ? event.getPoseStack() : new PoseStack(), event.getCamera().getPosition());
        });
        // Keeps the game's replies to an operator's hotbar fill out of chat.
        NeoForge.EVENT_BUS.addListener(ClientChatReceivedEvent.System.class, event -> {
            if (HotbarFill.hidesFeedback(event.getMessage(), event.isOverlay())) event.setCanceled(true);
        });
        NeoForge.EVENT_BUS.addListener(ClientTickEvent.Post.class, event -> ChromaStudioClient.tick(Minecraft.getInstance()));
    }

    @Override public Path configDir() { return FMLPaths.CONFIGDIR.get(); }
    @Override public Path gameDir() { return FMLPaths.GAMEDIR.get(); }
    @Override public boolean isModLoaded(String id) { return ModList.get().isLoaded(id); }
    // The colours are worked out without a world, so NeoForge's methods get no model data.
    @Override public List<BakedQuad> quads(BakedModel model, BlockState state, Direction side, RandomSource random) {
        return model.getQuads(state, side, random, ModelData.EMPTY, null);
    }
    @Override public TextureAtlasSprite particleIcon(BakedModel model, BlockState state) { return model.getParticleIcon(ModelData.EMPTY); }
    @Override public void renderBlock(BlockState state, PoseStack pose, MultiBufferSource buffers, int light, int overlay) {
        Minecraft.getInstance().getBlockRenderer().renderSingleBlock(state, pose, buffers, light, overlay, ModelData.EMPTY, null);
    }
    @Override public void sendChatCommand(String command) {
        ClientPacketListener connection = Minecraft.getInstance().getConnection();
        if (connection != null) connection.sendCommand(command);
    }
}
