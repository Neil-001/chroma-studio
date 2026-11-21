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
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
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
                Component.translatable("eyedropper.chromastudio.armed.how", Keys.EYEDROPPER.getTranslatedKeyMessage()), ModSettings.readoutSeconds);
    }

    public static void press() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) return;
        boolean open = armed || mc.hasShiftDown();
        BlockHitResult hit = AxiomBridge.editorOpen() ? AxiomBridge.blockUnderMouse() : hit(mc);
        if (hit == null) {
            say("eyedropper.chromastudio.no_block", "eyedropper.chromastudio.no_block.how");
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
            say("eyedropper.chromastudio.not_ready", "eyedropper.chromastudio.not_ready.how");
            return false;
        }
        Entry entry = index.entryOf(state);
        FluidState fluid = mc.level.getFluidState(pos);
        if (entry == null && !fluid.isEmpty()) {
            state = fluid.createLegacyBlock();
            entry = index.entryOf(state);
        }
        if (entry == null) {
            Hud.readout(Component.translatable("eyedropper.chromastudio.not_indexed", state.getBlock().getName()),
                    Component.translatable("eyedropper.chromastudio.not_indexed.how"), ModSettings.readoutSeconds);
            return false;
        }
        armed = false;
        Face face = Face.values()[hit.getDirection().ordinal()];
        TintContext tint = TintContext.at(pos);
        Color colour = index.colourOf(state, face, tint).orElseThrow();
        MatchState.setEntry(entry, state, face, tint);
        MatchState.remember();
        Clipboard.copy(colour.hex());

        String properties = Entry.properties(state);
        Component name = properties.isEmpty() ? entry.name() : Component.translatable("match.chromastudio.name_with_state", entry.name(), properties);
        Hud.readout(name, Component.translatable("eyedropper.chromastudio.readout", faceName(face), colour.hex()), ModSettings.readoutSeconds);
        if (open) ChromaScreen.open("match");
        return true;
    }

    public static Component faceName(Face face) {
        return Component.translatable("face.chromastudio." + face.name().toLowerCase(Locale.ROOT));
    }

    private static void say(String line1, String line2) {
        Hud.readout(Component.translatable(line1), Component.translatable(line2), ModSettings.readoutSeconds);
    }

    private static BlockHitResult hit(Minecraft mc) {
        if (mc.hitResult instanceof BlockHitResult block && block.getType() == HitResult.Type.BLOCK) return block;
        HitResult withFluids = mc.player.pick(mc.player.blockInteractionRange(), 0, true);
        return withFluids instanceof BlockHitResult block && block.getType() == HitResult.Type.BLOCK ? block : null;
    }
}
