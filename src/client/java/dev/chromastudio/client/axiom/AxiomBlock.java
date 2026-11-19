package dev.chromastudio.client.axiom;

import com.moulberry.axiomclientapi.service.ToolService;
import dev.chromastudio.client.ChromaStudioClient;
import dev.chromastudio.client.axiom.AxiomBridge.Taken;
import dev.chromastudio.client.index.BlockIndex;
import dev.chromastudio.client.index.Entry;
import dev.chromastudio.client.ui.tabs.MatchState;
import dev.chromastudio.core.color.Face;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Makes Axiom's active block, the one its editor's tools paint with, the Match target. Only {@link AxiomBridge}
 * calls this, once Axiom is known to be loaded.
 * <p>
 * Axiom's API only gives the active block while its editor is open. Outside it, the API answers plain stone, which
 * can't be told apart from stone chosen on purpose (and stone is also Axiom's own choice after a restart), so a stone
 * answer comes with a note saying so.
 */
final class AxiomBlock {
    private AxiomBlock() {}

    /** Makes Axiom's active block the Match target, as a block taken from the hotbar is: no face and no biome. */
    static Taken take() {
        BlockState state = activeBlock();
        if (state == null) return new Taken(false, Component.translatable("axiom.chromastudio.failed"));
        BlockIndex index = BlockIndex.get();
        if (!index.ready()) return new Taken(false, Component.translatable("eyedropper.chromastudio.not_ready"));
        Entry entry = index.entryOf(state);
        if (entry == null) return new Taken(false, Component.translatable("eyedropper.chromastudio.not_indexed", state.getBlock().getName()));
        MatchState.setEntry(entry, state, Face.ANY, null);
        MatchState.remember();
        String key = state.is(Blocks.STONE) ? "axiom.chromastudio.taken.stone" : "axiom.chromastudio.taken";
        return new Taken(true, Component.translatable(key, entry.name()));
    }

    /** Axiom's active block, or null when Axiom couldn't be asked, as another version of it might not answer. */
    private static BlockState activeBlock() {
        try {
            return AxiomTools.service(ToolService.class).getActiveBlock();
        } catch (Throwable t) {
            ChromaStudioClient.LOGGER.warn("Axiom didn't give its active block", t);
            return null;
        }
    }
}
