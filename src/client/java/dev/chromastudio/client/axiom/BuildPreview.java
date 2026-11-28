package dev.chromastudio.client.axiom;

import com.moulberry.axiomclientapi.Effects;
import com.moulberry.axiomclientapi.regions.BooleanRegion;
import dev.chromastudio.core.palette.BuildSearch;
import dev.chromastudio.core.palette.QueuedChanges;
import java.util.Optional;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

final class BuildPreview {
    private final String id;
    private final BooleanRegion outline = AxiomTools.regions.createBoolean();
    private @Nullable QueuedChanges<BlockState> queued;
    private QueuedChanges.@Nullable Plan<BlockState> again;
    private AxiomMask.@Nullable Test mask;
    private boolean overriding;
    private final Drawn drawn = new Drawn();

    BuildPreview(String id) { this.id = id; }

    boolean show(QueuedChanges<BlockState> plan, AxiomMask.Test mask, long[] outlined, QueuedChanges.Plan<BlockState> again) {
        clear();
        try {
            AxiomTools.tools.acquireChunkRenderOverrider(id);
        } catch (LinkageError | VirtualMachineError e) {
            throw e;
        } catch (Error | RuntimeException ignored) {
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

    int count() { return queued == null ? 0 : queued.size(); }

    boolean isEmpty() { return count() == 0; }

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

    void render(WorldRender rc) {
        if (overriding) drawn.draw(AxiomTools.frame());
        rc.draw(outline, Vec3.ZERO, Effects.OUTLINE);
    }

    boolean tick() {
        if (!overriding || queued == null) return false;
        if (queued.origin().visit() == AxiomTools.visit() && !drawn.missed(AxiomTools.frame())) return false;
        clear();
        return true;
    }

    Optional<Component> apply() {
        QueuedChanges<BlockState> q = queued;
        QueuedChanges.Plan<BlockState> a = again;
        AxiomMask.Test m = mask;
        clear();
        if (q == null || a == null || m == null) return Optional.empty();
        return AxiomChanges.send(q, m, a);
    }
}
