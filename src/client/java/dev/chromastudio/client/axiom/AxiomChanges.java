package dev.chromastudio.client.axiom;

import com.moulberry.axiomclientapi.regions.BlockRegion;
import dev.chromastudio.client.index.BlockIndex;
import dev.chromastudio.core.palette.QueuedChanges;
import java.util.Locale;
import java.util.Optional;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.block.state.BlockState;

/**
 * The one way Chroma Studio's tools change the world: queued changes go to Axiom as one entry in its history, so
 * Axiom's undo takes them back in one step and its permissions decide whether they're allowed. Before anything is
 * sent, the tool's plan is worked out again from the world, the index, the editor and the tool masks as they are at
 * that moment ({@link QueuedChanges#check}), and only exactly what the player saw is sent; otherwise nothing is, and
 * the tool tells the player why.
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
     * Sends the changes when {@code again}, the steps that made them, makes the same again now. {@code before} is the
     * masks they were made with, which only choose what the player is told when they aren't sent. Returns why not
     * otherwise, or nothing when they were sent or there was nothing to send. A call into Axiom that throws reaches the
     * tool's caller, {@link GuardedTool}, which turns the features off.
     */
    static Optional<Component> send(QueuedChanges<BlockState> changes, AxiomMask.Test before, QueuedChanges.Plan<BlockState> again) {
        if (changes.isEmpty()) return Optional.empty();
        // Once Axiom's features are off for the session, as when a tool couldn't be added, nothing reaches the world.
        Optional<Component> off = AxiomBridge.whyNot();
        if (off.isPresent()) return off;
        ClientLevel level = AxiomTools.level.get();
        if (level == null) return Optional.of(refusal(QueuedChanges.Refusal.WORLD));
        // Without knowing whether the editor closed since the changes were queued, they can't be vouched for.
        if (!AxiomEditor.known()) return Optional.of(Component.translatable("axiom.chromastudio.editor.unknown.tools"));
        AxiomMask.Test[] fresh = new AxiomMask.Test[1];
        QueuedChanges.Refusal why = changes.check(() -> origin(level), AxiomTools::editorOn, () -> fresh[0] = AxiomMask.now(level), again);
        // Only what the player reads: when the masks answering as they did before would have made the same plan, the
        // masks alone changed it, as a script's unseeded noise does, and they're named, so the player knows what to fix.
        if (why == QueuedChanges.Refusal.CHANGED && fresh[0] != null && AxiomMask.differ(before, fresh[0])
                && AxiomMask.aloneChanged(changes, before, fresh[0], again)) {
            return Optional.of(Component.translatable("axiom.chromastudio.refused.masks_differ"));
        }
        if (why != QueuedChanges.Refusal.NONE) return Optional.of(refusal(why));
        BlockRegion region = AxiomTools.regions.createBlock();
        for (QueuedChanges.Change<BlockState> c : changes.changes()) region.addBlock(c.x(), c.y(), c.z(), c.next());
        // The index is rebuilt on its own thread; one published since the check counts, as near the send as can be.
        if (BlockIndex.get().version() != changes.origin().index()) return Optional.of(refusal(QueuedChanges.Refusal.INDEX));
        AxiomTools.tools.pushBlockRegionChange(region);
        return Optional.empty();
    }

    /** What the player reads when changes weren't sent. */
    static Component refusal(QueuedChanges.Refusal why) {
        return Component.translatable("axiom.chromastudio.refused." + why.name().toLowerCase(Locale.ROOT));
    }
}
