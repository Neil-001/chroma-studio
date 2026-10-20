package dev.chromastudio.client.fabric;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.chromastudio.client.ChromaStudioClient;
import dev.chromastudio.client.ClientPlatform;
import dev.chromastudio.client.ChromaCommand;
import dev.chromastudio.client.Keys;
import dev.chromastudio.client.action.Capture;
import dev.chromastudio.client.action.HotbarFill;
import dev.chromastudio.client.index.BlockIndex;
import dev.chromastudio.client.ui.Hud;
import dev.chromastudio.client.ui.Tooltips;
import java.nio.file.Path;
import java.util.List;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.item.v1.ItemTooltipCallback;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.fabricmc.fabric.api.resource.ResourceManagerHelper;
import net.fabricmc.fabric.api.resource.SimpleSynchronousResourceReloadListener;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.state.BlockState;

/** Fabric's client entrypoint. */
public class ChromaStudioFabricClient implements ClientModInitializer, ClientPlatform {
    @Override
    public void onInitializeClient() {
        ChromaStudioClient.init(this);
        Keys.ALL.forEach(KeyBindingHelper::registerKeyBinding);
        ClientTickEvents.END_CLIENT_TICK.register(ChromaStudioClient::tick);
        HudRenderCallback.EVENT.register((graphics, tick) -> Hud.render(graphics));
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, access) -> dispatcher.register(ChromaCommand.build()));
        ItemTooltipCallback.EVENT.register((stack, context, type, lines) -> Tooltips.append(stack, lines));
        ResourceManagerReloadListener index = BlockIndex.install(FabricLoader.getInstance().getAllMods().stream()
                .map(mod -> mod.getMetadata().getId() + " " + mod.getMetadata().getVersion().getFriendlyString()).toList());
        ResourceManagerHelper.get(PackType.CLIENT_RESOURCES).registerReloadListener(new SimpleSynchronousResourceReloadListener() {
            @Override public ResourceLocation getFabricId() { return ResourceLocation.fromNamespaceAndPath(ChromaStudioClient.MOD_ID, "index"); }
            @Override public void onResourceManagerReload(ResourceManager manager) { index.onResourceManagerReload(manager); }
        });
        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> BlockIndex.get().worldJoined());
        // Keeps the game's replies to an operator's hotbar fill out of chat.
        ClientReceiveMessageEvents.ALLOW_GAME.register((message, overlay) -> !HotbarFill.hidesFeedback(message, overlay));
        // The capture outline is drawn straight to the game's buffers after the entities.
        WorldRenderEvents.AFTER_ENTITIES.register(ctx -> Capture.render(ctx.matrixStack(), ctx.camera().getPosition()));
    }

    @Override public Path configDir() { return FabricLoader.getInstance().getConfigDir(); }
    @Override public Path gameDir() { return FabricLoader.getInstance().getGameDir(); }
    @Override public boolean isModLoaded(String id) { return FabricLoader.getInstance().isModLoaded(id); }
    @Override public List<BakedQuad> quads(BakedModel model, BlockState state, Direction side, RandomSource random) {
        return model.getQuads(state, side, random);
    }
    @Override public TextureAtlasSprite particleIcon(BakedModel model, BlockState state) { return model.getParticleIcon(); }
    @Override public void renderBlock(BlockState state, PoseStack pose, MultiBufferSource buffers, int light, int overlay) {
        Minecraft.getInstance().getBlockRenderer().renderSingleBlock(state, pose, buffers, light, overlay);
    }
    @Override public void sendChatCommand(String command) {
        ClientPacketListener connection = Minecraft.getInstance().getConnection();
        if (connection != null) connection.sendCommand(command);
    }
}
