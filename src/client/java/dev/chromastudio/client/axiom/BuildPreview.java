package dev.chromastudio.client.axiom;

import com.moulberry.axiomclientapi.Effects;
import com.moulberry.axiomclientapi.IAxiomWorldRenderContext;
import com.moulberry.axiomclientapi.regions.BooleanRegion;
import dev.chromastudio.core.palette.QueuedChanges;
import java.util.Optional;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * A change to a whole build shown in the world before it's made, as whole-build recolour and the gradient tool
 * show one: the world draws each block as it will be, the blocks the tool covers are outlined, and Apply sends the
 * lot to Axiom as one change through {@link AxiomChanges}, or nothing when anything it was made from has changed.
 * Blocks the tool masks keep out are left out of it when it's made.
 */
final class BuildPreview {
    private final String id;
    private final BooleanRegion outline = AxiomTools.regions.createBoolean();
    private @Nullable QueuedChanges<BlockState> queued;
    private AxiomMask.Test mask;
    private boolean overriding;
    private long drawn;

    /** @param id our name for the world's look while the preview shows, one per tool */
    BuildPreview(String id) { this.id = id; }

    /** Starts a new preview in {@code level}, with the tool masks as they are now. */
    void begin(ClientLevel level) {
        clear();
        queued = AxiomChanges.queue(level);
        mask = AxiomMask.now(level);
        AxiomTools.tools.acquireChunkRenderOverrider(id);
        overriding = true;
        drawn = System.nanoTime();
    }

    /** Whether the masks let a block change, for the preview being made. */
    boolean allows(int x, int y, int z) { return mask != null && mask.allows(x, y, z); }

    /** Whether the masks failed while the preview was made, so it can't be applied. */
    boolean maskFailed() { return mask != null && mask.failed(); }

    /** Outlines a block the tool covers, whether or not it changes. */
    void outline(int x, int y, int z) { outline.add(x, y, z); }

    /** Shows a block as it will be, and queues the change. Call only for blocks the masks allow. */
    void change(int x, int y, int z, BlockState was, BlockState next) {
        if (queued == null) return;
        queued.add(x, y, z, was, next);
        AxiomTools.tools.setBlockRenderOverride(x, y, z, next);
    }

    /** How many blocks will change. */
    int count() { return queued == null ? 0 : queued.size(); }

    boolean isEmpty() { return count() == 0; }

    void clear() {
        outline.clear();
        queued = null;
        mask = null;
        if (overriding) {
            AxiomTools.tools.releaseChunkRenderOverrider(id);
            overriding = false;
        }
    }

    /** Called each frame Axiom draws the tool. */
    void render(IAxiomWorldRenderContext rc) {
        drawn = System.nanoTime();
        outline.render(rc, Vec3.ZERO, Effects.OUTLINE);
    }

    /** Called just after a big preview was made, which can take longer than {@link #tick} waits. */
    void drawnNow() { drawn = System.nanoTime(); }

    /**
     * Called every client tick. Drops the preview once Axiom has stopped drawing the tool, as when the player leaves
     * the editor or picks another tool, neither of which Axiom tells a tool about: the world would otherwise go on
     * showing blocks that aren't there. Returns whether it did, so the tool can make it again when it's back.
     */
    boolean tick() {
        if (!overriding || System.nanoTime() - drawn <= BrushStroke.PUT_AWAY) return false;
        clear();
        return true;
    }

    /** Sends the change. Returns why not, or nothing when it went or there was nothing to send. */
    Optional<Component> apply() {
        if (queued == null) return Optional.empty();
        if (maskFailed()) return Optional.of(AxiomChanges.refusal(QueuedChanges.Refusal.MASK_FAILED));
        Optional<Component> why = AxiomChanges.send(queued);
        if (why.isEmpty()) clear();
        return why;
    }
}
