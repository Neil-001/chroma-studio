package dev.chromastudio.client.axiom;

import com.moulberry.axiomclientapi.Effects;
import com.moulberry.axiomclientapi.IAxiomWorldRenderContext;
import com.moulberry.axiomclientapi.regions.BooleanRegion;
import dev.chromastudio.core.palette.BuildSearch;
import dev.chromastudio.core.palette.QueuedChanges;
import java.util.Optional;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * A change to a whole build shown in the world before it's made, as whole-build recolour and the gradient tool
 * show one: the world draws each block as it will be, and the blocks the tool covers are outlined. Apply sends the
 * lot to Axiom as one change through {@link AxiomChanges}, only if the steps that made it make exactly the same again
 * then, and lets go of it either way, so a refused Apply leaves nothing to send until the tool is used again.
 */
final class BuildPreview {
    private final String id;
    private final BooleanRegion outline = AxiomTools.regions.createBoolean();
    private @Nullable QueuedChanges<BlockState> queued;
    private QueuedChanges.@Nullable Plan<BlockState> again;
    /** The masks the preview was made with, to tell the player when they answer otherwise at Apply. */
    private AxiomMask.@Nullable Test mask;
    private boolean overriding;
    private final Drawn drawn = new Drawn();

    /** @param id our name for the world's look while the preview shows, one per tool */
    BuildPreview(String id) { this.id = id; }

    /**
     * Shows {@code plan}, made with {@code mask}, in the world, with the blocks in {@code outlined} outlined. Apply
     * sends it only when {@code again}, the steps that made it, makes the same again from the world, masks and the
     * tool's choices as they are then. False,
     * showing nothing, when another tool holds the world's look, which Axiom gives one tool at a time: its Text tool
     * keeps it while it's put away with "Clear tool when swapping" off.
     */
    boolean show(QueuedChanges<BlockState> plan, AxiomMask.Test mask, long[] outlined, QueuedChanges.Plan<BlockState> again) {
        clear();
        try {
            AxiomTools.tools.acquireChunkRenderOverrider(id);
        } catch (LinkageError | VirtualMachineError e) {
            // Not a hold by another tool, but this Axiom or the game failing: GuardedTool turns the features off.
            throw e;
        } catch (Error | RuntimeException _) {
            return false;
        }
        queued = plan;
        this.again = again;
        this.mask = mask;
        overriding = true;
        for (QueuedChanges.Change<BlockState> c : plan.changes()) AxiomTools.tools.setBlockRenderOverride(c.x(), c.y(), c.z(), c.next());
        for (long p : outlined) outline.add(BuildSearch.x(p), BuildSearch.y(p), BuildSearch.z(p));
        drawn.start(AxiomTools.frame());
        return true;
    }

    /** How many blocks will change. */
    int count() { return queued == null ? 0 : queued.size(); }

    boolean isEmpty() { return count() == 0; }

    /**
     * Drops the preview, and lets the world show its own blocks again. The preview is dropped first, so nothing is left
     * to send whatever Axiom does; the hold on the world's look counts as let go only once Axiom has taken it back, so
     * when Axiom throws, the tool's clean-up ({@link GuardedTool}) tries again.
     */
    void clear() {
        outline.clear();
        queued = null;
        again = null;
        mask = null;
        drawn.stop();
        if (overriding) {
            AxiomTools.tools.releaseChunkRenderOverrider(id);
            overriding = false;
        }
    }

    /** Called each frame Axiom draws the tool. */
    void render(IAxiomWorldRenderContext rc) {
        if (overriding) drawn.draw(AxiomTools.frame());
        outline.render(rc, Vec3.ZERO, Effects.OUTLINE);
    }

    /**
     * Called every client tick. Drops the preview once the editor has been left, or a frame has gone by without Axiom
     * drawing the tool, as when the player picks another tool, which Axiom doesn't always tell a tool about: the world
     * would otherwise go on showing blocks that aren't there. Returns whether it did, so the tool can make it again
     * when it's back.
     */
    boolean tick() {
        if (!overriding || queued == null) return false;
        if (queued.origin().visit() == AxiomTools.visit() && !drawn.missed(AxiomTools.frame())) return false;
        clear();
        return true;
    }

    /** Sends the change, and drops the preview whether or not it went. Returns why not, or nothing when it went or there was nothing to send. */
    Optional<Component> apply() {
        QueuedChanges<BlockState> q = queued;
        QueuedChanges.Plan<BlockState> a = again;
        AxiomMask.Test m = mask;
        clear();
        if (q == null || a == null || m == null) return Optional.empty();
        return AxiomChanges.send(q, m, a);
    }
}
