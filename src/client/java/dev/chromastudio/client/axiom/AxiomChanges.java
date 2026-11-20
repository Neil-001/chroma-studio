package dev.chromastudio.client.axiom;

import com.moulberry.axiomclientapi.regions.BlockRegion;
import dev.chromastudio.client.index.BlockIndex;
import dev.chromastudio.core.palette.QueuedChanges;
import java.util.Locale;
import java.util.Optional;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.block.state.BlockState;

/**
 * The one way Chroma Studio's tools change the world: queued changes go to Axiom as one entry in its history, so
 * Axiom's undo takes them back in one step and its permissions decide whether they're allowed. Before anything is
 * sent, {@link QueuedChanges#check} must pass against the world, the index, the player's visit to the editor and
 * the tool masks as they are now; otherwise nothing is sent, and the tool tells the player why.
 */
final class AxiomChanges {
    private AxiomChanges() {}

    /** Where changes queued now are made: this world, this index and this visit to the editor. */
    private static QueuedChanges.Origin origin(ClientLevel level) {
        return new QueuedChanges.Origin(level, BlockIndex.get().version(), AxiomTools.visit());
    }

    /**
     * A queue for changes made now. Take it before reading the index to work out what to change, so a rebuilt index
     * that the plan might already have read refuses the changes rather than vouching for them.
     */
    static QueuedChanges<BlockState> queue(ClientLevel level) { return new QueuedChanges<>(origin(level)); }

    /**
     * Sends the changes when the check passes. Returns why not otherwise, or nothing when they were sent or there was
     * nothing to send.
     */
    static Optional<Component> send(QueuedChanges<BlockState> changes) {
        ClientLevel level = Minecraft.getInstance().level;
        if (changes.isEmpty()) return Optional.empty();
        // Once Axiom's features are off for the session, as when a tool couldn't be added, nothing reaches the world.
        Optional<Component> off = AxiomBridge.whyNot();
        if (off.isPresent()) return off;
        if (level == null) return Optional.of(refusal(QueuedChanges.Refusal.WORLD));
        // Without knowing whether the editor closed since the changes were queued, they can't be vouched for.
        if (!AxiomEditor.known()) return Optional.of(Component.translatable("axiom.chromastudio.editor.unknown.tools"));
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        QueuedChanges.Origin now = origin(level);
        QueuedChanges.Refusal why = changes.check(now, (x, y, z) -> level.getBlockState(pos.set(x, y, z)), AxiomMask.now(level));
        // The index is rebuilt on its own thread, so one published while the blocks were checked refuses them too.
        if (why == QueuedChanges.Refusal.NONE && BlockIndex.get().version() != now.index()) why = QueuedChanges.Refusal.INDEX;
        if (why != QueuedChanges.Refusal.NONE) return Optional.of(refusal(why));
        BlockRegion region = AxiomTools.regions.createBlock();
        for (QueuedChanges.Change<BlockState> c : changes.changes()) region.addBlock(c.x(), c.y(), c.z(), c.next());
        AxiomTools.tools.pushBlockRegionChange(region);
        return Optional.empty();
    }

    /** What the player reads when changes weren't sent. */
    static Component refusal(QueuedChanges.Refusal why) {
        return Component.translatable("axiom.chromastudio.refused." + why.name().toLowerCase(Locale.ROOT));
    }
}
