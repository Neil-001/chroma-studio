package dev.chromastudio.client.action;

import dev.chromastudio.client.Keys;
import dev.chromastudio.client.ModSettings;
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

/**
 * The in-world eyedropper: reads the face of the block the player looks at, as it is placed and in the biome it is
 * in, and makes it the Match target. {@code Keys.onEyedropper} runs {@link #press}.
 */
public final class Eyedropper {
    private Eyedropper() {}

    /** Set by the Match tab's button: the next press opens the window again, as if Shift were held. */
    private static boolean armed;

    /** Makes the next press bring the window back on what it read. */
    public static void arm() {
        armed = true;
        Hud.readout(Component.translatable("eyedropper.chromastudio.armed"),
                Component.translatable("eyedropper.chromastudio.armed.how", Keys.EYEDROPPER.getTranslatedKeyMessage()), ModSettings.readoutSeconds);
    }

    public static void press() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) return;
        boolean open = armed || mc.hasShiftDown();
        BlockHitResult hit = hit(mc);
        if (hit == null) {
            say("eyedropper.chromastudio.no_block", "eyedropper.chromastudio.no_block.how");
            return;
        }
        BlockPos pos = hit.getBlockPos();
        BlockState state = mc.level.getBlockState(pos);
        BlockIndex index = BlockIndex.get();
        if (!index.ready()) {
            say("eyedropper.chromastudio.not_ready", "eyedropper.chromastudio.not_ready.how");
            return;
        }
        Entry entry = index.entryOf(state);
        // Water and lava are indexed as their blocks; a waterlogged block that isn't indexed still shows its water.
        FluidState fluid = mc.level.getFluidState(pos);
        if (entry == null && !fluid.isEmpty()) {
            state = fluid.createLegacyBlock();
            entry = index.entryOf(state);
        }
        if (entry == null) {
            Hud.readout(Component.translatable("eyedropper.chromastudio.not_indexed", state.getBlock().getName()),
                    Component.translatable("eyedropper.chromastudio.not_indexed.how"), ModSettings.readoutSeconds);
            return;
        }
        armed = false;
        // Minecraft's directions and the core's faces are in the same order.
        Face face = Face.values()[hit.getDirection().ordinal()];
        TintContext tint = TintContext.at(pos);
        Color colour = index.colourOf(state, face, tint).orElseThrow();
        MatchState.setEntry(entry, state, face, tint);
        MatchState.remember();
        mc.keyboardHandler.setClipboard(colour.hex());

        // The state as placed, which may be one the index folded into another's entry.
        String properties = Entry.properties(state);
        Component name = properties.isEmpty() ? entry.name() : Component.translatable("match.chromastudio.name_with_state", entry.name(), properties);
        Hud.readout(name, Component.translatable("eyedropper.chromastudio.readout", faceName(face), colour.hex()), ModSettings.readoutSeconds);
        if (open) ChromaScreen.open("match");
    }

    public static Component faceName(Face face) {
        return Component.translatable("face.chromastudio." + face.name().toLowerCase(Locale.ROOT));
    }

    private static void say(String line1, String line2) {
        Hud.readout(Component.translatable(line1), Component.translatable(line2), ModSettings.readoutSeconds);
    }

    /** The block looked at within reach, or failing a block, the water or lava. Null for neither. */
    private static BlockHitResult hit(Minecraft mc) {
        if (mc.hitResult instanceof BlockHitResult block && block.getType() == HitResult.Type.BLOCK) return block;
        HitResult withFluids = mc.player.pick(mc.player.blockInteractionRange(), 0, true);
        return withFluids instanceof BlockHitResult block && block.getType() == HitResult.Type.BLOCK ? block : null;
    }
}
