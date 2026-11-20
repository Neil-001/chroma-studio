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
import org.jspecify.annotations.Nullable;

/**
 * Axiom's active block, the one its editor's tools paint with, as the Match target, and the block under the mouse in
 * the editor. Only {@link AxiomBridge} calls this, once Axiom is known to be loaded.
 * <p>
 * Axiom's API only gives the active block while its editor is open, and answers plain stone outside it. Chroma
 * Studio's window is never open inside the editor, as it leaves the editor to open, so the block is taken as
 * {@link AxiomEditor} last saw it there. When this Axiom can't say whether its editor is open, the API's answer is
 * taken as it comes, with a note when it's stone, which could be the editor being closed.
 */
final class AxiomBlock {
    private AxiomBlock() {}

    /** Why the block can't be taken now: the editor hasn't been open this session. */
    static Optional<Component> unavailable() {
        if (AxiomEditor.known() && !AxiomEditor.active() && AxiomEditor.lastActiveBlock() == null)
            return Optional.of(Component.translatable("axiom.chromastudio.block.unseen"));
        return Optional.empty();
    }

    /** Which block the button takes, for its tooltip. */
    static Component tip() {
        BlockState last = AxiomEditor.lastActiveBlock();
        if (!AxiomEditor.known() || AxiomEditor.active() || last == null) return Component.translatable("match.chromastudio.axiom.tip");
        return Component.translatable("axiom.chromastudio.block.tip", last.getBlock().getName());
    }

    /** Makes Axiom's active block the Match target, as a block taken from the hotbar is: no face and no biome. */
    static Taken take() {
        boolean known = AxiomEditor.known();
        BlockState state = known && !AxiomEditor.active() ? AxiomEditor.lastActiveBlock() : live();
        if (state == null) return new Taken(false, Component.translatable(known ? "axiom.chromastudio.block.unseen" : "axiom.chromastudio.failed"));
        BlockIndex index = BlockIndex.get();
        if (!index.ready()) return new Taken(false, Component.translatable("eyedropper.chromastudio.not_ready"));
        Entry entry = index.entryOf(state);
        if (entry == null) return new Taken(false, Component.translatable("eyedropper.chromastudio.not_indexed", state.getBlock().getName()));
        MatchState.setEntry(entry, state, Face.ANY, null);
        MatchState.remember();
        String key = !known && state.is(Blocks.STONE) ? "axiom.chromastudio.taken.stone" : "axiom.chromastudio.taken";
        return new Taken(true, Component.translatable(key, entry.name()));
    }

    /** The block under the mouse in the editor, as Axiom's own tools find it, or null for none. */
    static @Nullable BlockHitResult underMouse() {
        try {
            BlockHitResult hit = AxiomTools.tools.raycastBlock();
            return hit != null && hit.getType() == HitResult.Type.BLOCK ? hit : null;
        } catch (Throwable t) {
            ChromaStudioClient.LOGGER.warn("Axiom didn't say which block is under the mouse", t);
            return null;
        }
    }

    /** Axiom's active block as its API gives it now, or null when Axiom couldn't be asked. */
    private static @Nullable BlockState live() {
        try {
            return AxiomTools.tools.getActiveBlock();
        } catch (Throwable t) {
            ChromaStudioClient.LOGGER.warn("Axiom didn't give its active block", t);
            return null;
        }
    }
}
