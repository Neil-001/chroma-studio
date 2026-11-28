package dev.chromastudio.client.axiom;

import dev.chromastudio.client.ChromaStudioClient;
import dev.chromastudio.client.axiom.AxiomBridge.Taken;
import dev.chromastudio.client.index.BlockIndex;
import dev.chromastudio.client.index.Entry;
import dev.chromastudio.client.ui.tabs.MatchState;
import dev.chromastudio.core.color.Face;
import java.util.Optional;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import org.jetbrains.annotations.Nullable;

final class AxiomBlock {
    private AxiomBlock() {}

    static Optional<Component> unavailable() {
        if (AxiomEditor.known() && !AxiomEditor.active() && AxiomEditor.lastActiveBlock() == null)
            return Optional.of(Component.translatable(AxiomEditor.lastReadFailed() ? "axiom.chromastudio.failed" : "axiom.chromastudio.block.unseen"));
        return Optional.empty();
    }

    static Component tip() {
        BlockState last = AxiomEditor.lastActiveBlock();
        if (!AxiomEditor.known() || AxiomEditor.active() || last == null) return Component.translatable("match.chromastudio.axiom.tip");
        return Component.translatable("axiom.chromastudio.block.tip", last.getBlock().getName());
    }

    static Taken take() {
        boolean known = AxiomEditor.known(), remembered = known && !AxiomEditor.active();
        BlockState state = remembered ? AxiomEditor.lastActiveBlock() : live();
        if (state == null)
            return new Taken(false, Component.translatable(remembered && !AxiomEditor.lastReadFailed() ? "axiom.chromastudio.block.unseen" : "axiom.chromastudio.failed"));
        BlockIndex index = BlockIndex.get();
        if (!index.ready()) return new Taken(false, Component.translatable("eyedropper.chromastudio.not_ready"));
        Entry entry = index.entryOf(state);
        if (entry == null) return new Taken(false, Component.translatable("eyedropper.chromastudio.not_indexed", state.getBlock().getName()));
        MatchState.setEntry(entry, state, Face.ANY, null);
        MatchState.remember();
        String key = !known && state.is(Blocks.STONE) ? "axiom.chromastudio.taken.stone" : "axiom.chromastudio.taken";
        return new Taken(true, Component.translatable(key, entry.name()));
    }

    static @Nullable BlockHitResult underMouse() {
        try {
            BlockHitResult hit = AxiomTools.tools.raycastBlock();
            return hit != null && hit.getType() == HitResult.Type.BLOCK ? hit : null;
        } catch (Throwable t) {
            ChromaStudioClient.LOGGER.warn("Axiom didn't say which block is under the mouse", t);
            return null;
        }
    }

    private static @Nullable BlockState live() {
        try {
            return AxiomTools.tools.getActiveBlock();
        } catch (Throwable t) {
            ChromaStudioClient.LOGGER.warn("Axiom didn't give its active block", t);
            return null;
        }
    }
}
