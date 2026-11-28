package dev.chromastudio.client.axiom;

import com.moulberry.axiomclientapi.regions.BlockRegion;
import dev.chromastudio.client.index.BlockIndex;
import dev.chromastudio.core.palette.QueuedChanges;
import java.util.Locale;
import java.util.Optional;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.block.state.BlockState;

final class AxiomChanges {
    private AxiomChanges() {}

    private static QueuedChanges.Origin origin(ClientLevel level) {
        return new QueuedChanges.Origin(level, BlockIndex.get().version(), AxiomTools.visit());
    }

    static QueuedChanges<BlockState> queue(ClientLevel level) { return new QueuedChanges<>(origin(level)); }

    static Optional<Component> send(QueuedChanges<BlockState> changes, AxiomMask.Test before, QueuedChanges.Plan<BlockState> again) {
        if (changes.isEmpty()) return Optional.empty();
        Optional<Component> off = AxiomBridge.whyNot();
        if (off.isPresent()) return off;
        ClientLevel level = AxiomTools.level.get();
        if (level == null) return Optional.of(refusal(QueuedChanges.Refusal.WORLD));
        if (!AxiomEditor.known()) return Optional.of(Component.translatable("axiom.chromastudio.editor.unknown.tools"));
        AxiomMask.Test[] fresh = new AxiomMask.Test[1];
        QueuedChanges.Refusal why = changes.check(() -> origin(level), AxiomTools::editorOn, () -> fresh[0] = AxiomMask.now(level), again);
        if (why == QueuedChanges.Refusal.CHANGED && fresh[0] != null && AxiomMask.differ(before, fresh[0])
                && AxiomMask.aloneChanged(changes, before, fresh[0], again)) {
            return Optional.of(Component.translatable("axiom.chromastudio.refused.masks_differ"));
        }
        if (why != QueuedChanges.Refusal.NONE) return Optional.of(refusal(why));
        BlockRegion region = AxiomTools.regions.createBlock();
        for (QueuedChanges.Change<BlockState> c : changes.changes()) region.addBlock(c.x(), c.y(), c.z(), c.next());
        if (BlockIndex.get().version() != changes.origin().index()) return Optional.of(refusal(QueuedChanges.Refusal.INDEX));
        AxiomTools.tools.pushBlockRegionChange(region);
        return Optional.empty();
    }

    static Component refusal(QueuedChanges.Refusal why) {
        return Component.translatable("axiom.chromastudio.refused." + why.name().toLowerCase(Locale.ROOT));
    }
}
