package dev.chromastudio.client.action;

import dev.chromastudio.client.Keys;
import dev.chromastudio.client.ModSettings;
import dev.chromastudio.client.axiom.AxiomBridge;
import dev.chromastudio.client.index.BlockIndex;
import dev.chromastudio.client.index.Entry;
import dev.chromastudio.client.index.TintContext;
import dev.chromastudio.client.ui.ChromaScreen;
import dev.chromastudio.client.ui.Hud;
import dev.chromastudio.client.ui.tabs.MatchState;
import dev.chromastudio.core.color.Color;
import dev.chromastudio.core.color.Face;
import java.util.Locale;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;

public final class Eyedropper {
    private Eyedropper() {}

    private static boolean armed;

    public static void arm() {
        armed = true;
        Hud.readout(Component.translatable("eyedropper.chromastudio.armed"),
                Component.translatable("eyedropper.chromastudio.armed.how", Hud.key(Keys.EYEDROPPER)), ModSettings.readoutSeconds);
    }

    public static void press() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) return;
        boolean open = armed || Screen.hasShiftDown();
        BlockHitResult hit = AxiomBridge.editorOpen() ? AxiomBridge.blockUnderMouse() : hit(mc);
        if (hit == null) {
            Hud.readout(Hud.Kind.PROBLEM, Component.translatable("eyedropper.chromastudio.no_block"),
                    Component.translatable("eyedropper.chromastudio.no_block.how", Hud.key(Keys.EYEDROPPER)), ModSettings.readoutSeconds);
            return;
        }
        read(hit, open);
    }

    public static boolean read(BlockHitResult hit, boolean open) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) return false;
        BlockPos pos = hit.getBlockPos();
        BlockState state = mc.level.getBlockState(pos);
        BlockIndex index = BlockIndex.get();
        if (!index.ready()) {
            Hud.readout(Hud.Kind.PROBLEM, Component.translatable("eyedropper.chromastudio.not_ready"),
                    Component.translatable("eyedropper.chromastudio.not_ready.how"), ModSettings.readoutSeconds);
            return false;
        }
        Entry entry = index.entryOf(state);
        FluidState fluid = mc.level.getFluidState(pos);
        if (entry == null && !fluid.isEmpty()) {
            state = fluid.createLegacyBlock();
            entry = index.entryOf(state);
        }
        if (entry == null) {
            Hud.readout(Hud.Kind.PROBLEM, Component.translatable("eyedropper.chromastudio.not_indexed", state.getBlock().getName()),
                    Component.translatable("eyedropper.chromastudio.not_indexed.how"), ModSettings.readoutSeconds);
            return false;
        }
        armed = false;
        Face face = Face.values()[hit.getDirection().ordinal()];
        TintContext tint = TintContext.at(pos);
        Color colour = index.colourOf(state, face, tint).orElseThrow();
        MatchState.setEntry(entry, state, face, tint);
        MatchState.remember();
        boolean copied = Clipboard.copy(colour.hex()).isEmpty() && colour.hex().equals(mc.keyboardHandler.getClipboard());

        String properties = Entry.properties(state);
        Component name = properties.isEmpty() ? entry.name() : Component.translatable("match.chromastudio.name_with_state", entry.name(), properties);
        Hud.readout(Hud.Kind.DONE, icon(state), colour.rgb(), name,
                Component.translatable(copied ? "eyedropper.chromastudio.readout.copied" : "eyedropper.chromastudio.readout", faceName(face), colour.hex()), ModSettings.readoutSeconds);
        if (open) ChromaScreen.open("match");
        return true;
    }

    private static ItemStack icon(BlockState state) {
        ItemStack block = new ItemStack(state.getBlock());
        return block.isEmpty() && !state.getFluidState().isEmpty() ? new ItemStack(state.getFluidState().getType().getBucket()) : block;
    }

    public static Component faceName(Face face) {
        return Component.translatable("face.chromastudio." + face.name().toLowerCase(Locale.ROOT));
    }

    private static BlockHitResult hit(Minecraft mc) {
        if (mc.hitResult instanceof BlockHitResult block && block.getType() == HitResult.Type.BLOCK) return block;
        HitResult withFluids = mc.player.pick(mc.player.blockInteractionRange(), 0, true);
        return withFluids instanceof BlockHitResult block && block.getType() == HitResult.Type.BLOCK ? block : null;
    }
}
