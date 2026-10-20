package dev.chromastudio.client.fabric;

import dev.chromastudio.client.ChromaStudioClient;
import dev.chromastudio.client.ClientPlatform;
import dev.chromastudio.client.ChromaCommand;
import dev.chromastudio.client.Keys;
import dev.chromastudio.client.action.Capture;
import dev.chromastudio.client.action.HotbarFill;
import dev.chromastudio.client.index.BlockIndex;
import dev.chromastudio.client.ui.Hud;
import dev.chromastudio.client.ui.Tooltips;
import dev.chromastudio.client.ui.tabs.DioramaPip;
import java.nio.file.Path;
import java.util.List;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.item.v1.ItemTooltipCallback;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.rendering.v1.SpecialGuiElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderEvents;
import net.fabricmc.fabric.api.resource.v1.ResourceLoader;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.render.state.pip.PictureInPictureRenderState;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.renderer.block.model.BlockModelPart;
import net.minecraft.client.renderer.block.model.BlockStateModel;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.PackType;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.state.BlockState;

/** Fabric's client entrypoint. */
public class ChromaStudioFabricClient implements ClientModInitializer, ClientPlatform {
    @Override
    public void onInitializeClient() {
        ChromaStudioClient.init(this);
        KeyMapping.Category.register(Keys.CATEGORY.id());
        Keys.ALL.forEach(KeyBindingHelper::registerKeyBinding);
        ClientTickEvents.END_CLIENT_TICK.register(ChromaStudioClient::tick);
        HudElementRegistry.addLast(Identifier.fromNamespaceAndPath(ChromaStudioClient.MOD_ID, "hud"), (graphics, tick) -> Hud.render(graphics));
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, access) -> dispatcher.register(ChromaCommand.build()));
        ItemTooltipCallback.EVENT.register((stack, context, type, lines) -> Tooltips.append(stack, lines));
        ResourceLoader.get(PackType.CLIENT_RESOURCES).registerReloader(Identifier.fromNamespaceAndPath(ChromaStudioClient.MOD_ID, "index"),
                BlockIndex.install(FabricLoader.getInstance().getAllMods().stream()
                        .map(mod -> mod.getMetadata().getId() + " " + mod.getMetadata().getVersion().getFriendlyString()).toList()));
        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> BlockIndex.get().worldJoined());
        // Keeps the game's replies to an operator's hotbar fill out of chat.
        ClientReceiveMessageEvents.ALLOW_GAME.register((message, overlay) -> !HotbarFill.hidesFeedback(message, overlay));
        SpecialGuiElementRegistry.register(context -> new DioramaPip(context.vertexConsumers()));
        // The capture outline is drawn straight to the game's buffers after the entities, as 1.21.11 has no later hook for lines.
        WorldRenderEvents.AFTER_ENTITIES.register(ctx -> Capture.render(ctx.matrices(), ctx.worldState().cameraRenderState.pos));
    }

    @Override public Path configDir() { return FabricLoader.getInstance().getConfigDir(); }
    @Override public Path gameDir() { return FabricLoader.getInstance().getGameDir(); }
    @Override public boolean isModLoaded(String id) { return FabricLoader.getInstance().isModLoaded(id); }
    @Override public void modelParts(BlockStateModel model, BlockState state, RandomSource random, List<BlockModelPart> out) {
        model.collectParts(random, out);
    }
    @Override public TextureAtlasSprite particleIcon(BlockStateModel model, BlockState state) { return model.particleIcon(); }
    // Fabric API's transitive access wideners open the field.
    @Override public void submitPicture(GuiGraphics g, PictureInPictureRenderState state) { g.guiRenderState.submitPicturesInPictureState(state); }
    @Override public void sendChatCommand(String command) {
        ClientPacketListener connection = Minecraft.getInstance().getConnection();
        if (connection != null) connection.sendCommand(command);
    }
}
