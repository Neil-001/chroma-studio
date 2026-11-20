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

    static QueuedChanges<BlockState> queue(ClientLevel level) { return new QueuedChanges<>(origin(level)); }

    /**
     * Sends the changes when the check passes. Returns why not otherwise, or nothing when they were sent or there was
     * nothing to send.
     */
    static Optional<Component> send(QueuedChanges<BlockState> changes) {
        ClientLevel level = Minecraft.getInstance().level;
        if (changes.isEmpty()) return Optional.empty();
        if (level == null) return Optional.of(refusal(QueuedChanges.Refusal.WORLD));
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        QueuedChanges.Refusal why = changes.check(origin(level), (x, y, z) -> level.getBlockState(pos.set(x, y, z)), AxiomMask.now(level));
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
