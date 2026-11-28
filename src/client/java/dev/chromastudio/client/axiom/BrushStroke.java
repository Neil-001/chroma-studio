package dev.chromastudio.client.axiom;

import com.moulberry.axiomclientapi.pathers.BallShape;
import com.moulberry.axiomclientapi.pathers.ToolPatherUnique;
import com.moulberry.axiomclientapi.regions.BlockRegion;
import dev.chromastudio.client.axiom.AxiomBridge.Refused;
import dev.chromastudio.core.palette.BuildSearch;
import dev.chromastudio.core.palette.QueuedChanges;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

final class BrushStroke {
    static final int USE_TOOL = 1;

    @FunctionalInterface
    interface Painter {
        void paint(int x, int y, int z, BlockState now, RecolourPlan.Admit admit, RecolourPlan.Put put);
    }

    @FunctionalInterface
    interface Setup {
        @Nullable Painter make(Level level) throws Refused;
    }

    private final BlockRegion shown = AxiomTools.regions.createBlock();
    private @Nullable ToolPatherUnique pather;
    private @Nullable QueuedChanges<BlockState> queued;
    private @Nullable Setup setup;
    private @Nullable Painter painter;
    private AxiomMask.@Nullable Test mask;
    private @Nullable ClientLevel level;
    private final LongArrayList reached = new LongArrayList();
    private final Drawn drawn = new Drawn();
    private @Nullable Component refused;
    private int sent;

    boolean start(ClientLevel level, QueuedChanges<BlockState> queue, int radius, Setup setup) throws Refused {
        cancel();
        refused = null;
        sent = 0;
        Painter p = setup.make(level);
        if (p == null) return false;
        painter = p;
        this.setup = setup;
        this.level = level;
        pather = AxiomTools.pathers.createUnique(radius, BallShape.SPHERE);
        queued = queue;
        mask = AxiomMask.now(level);
        drawn.start(AxiomTools.frame());
        return true;
    }

    boolean active() { return pather != null; }

    @Nullable Component refused() { return refused; }

    int sent() { return sent; }

    void forget() {
        refused = null;
        sent = 0;
    }

    void away() {
        if (queued != null) refuse(QueuedChanges.Refusal.EDITOR);
    }

    void cancel() {
        shown.clear();
        pather = null;
        queued = null;
        setup = null;
        painter = null;
        mask = null;
        level = null;
        reached.clear();
        drawn.stop();
    }

    void render(WorldRender rc) {
        if (pather == null || queued == null || painter == null || mask == null || level == null) return;
        if (!drawn.draw(AxiomTools.frame()) || queued.origin().visit() != AxiomTools.visit()) {
            refuse(QueuedChanges.Refusal.EDITOR);
            return;
        }
        if (AxiomTools.level.get() != level) {
            refuse(QueuedChanges.Refusal.WORLD);
            return;
        }
        if (!AxiomTools.tools.isMouseDown(USE_TOOL)) {
            finish();
            return;
        }
        ClientLevel in = level;
        QueuedChanges<BlockState> q = queued;
        Painter p = painter;
        AxiomMask.Test m = mask;
        pather.update((x, y, z) -> {
            reached.add(BuildSearch.pack(x, y, z));
            paint(in, p, m, q, x, y, z, shown);
        });
        if (m.failed()) {
            refuse(QueuedChanges.Refusal.MASK_FAILED);
            return;
        }
        float pulse = (float) Math.sin(rc.nanos() / 400_000_000.0);
        rc.draw(shown, Vec3.ZERO, 0.75f + pulse * 0.25f, 0.3f - pulse * 0.2f);
    }

    private void finish() {
        QueuedChanges<BlockState> q = queued;
        Setup s = setup;
        ClientLevel in = level;
        AxiomMask.Test m = mask;
        long[] at = reached.toLongArray();
        cancel();
        if (q == null || s == null || in == null || m == null) return;
        refused = AxiomChanges.send(q, m, again -> again(in, s, at, again, q.origin())).orElse(null);
        if (refused == null) sent = q.size();
    }

    private void refuse(QueuedChanges.Refusal why) {
        cancel();
        refused = AxiomChanges.refusal(why);
    }

    static QueuedChanges<BlockState> again(Level level, Setup setup, long[] reached, QueuedChanges.Mask mask, QueuedChanges.Origin origin) {
        QueuedChanges<BlockState> q = new QueuedChanges<>(origin);
        Painter p;
        try {
            p = setup.make(level);
        } catch (Refused ignored) {
            return q;
        }
        if (p == null) return q;
        for (long r : reached) paint(level, p, mask, q, BuildSearch.x(r), BuildSearch.y(r), BuildSearch.z(r), null);
        return q;
    }

    private static void paint(Level level, Painter painter, QueuedChanges.Mask mask, QueuedChanges<BlockState> q, int x, int y, int z,
                              @Nullable BlockRegion shown) {
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        BlockState here = level.getBlockState(pos.set(x, y, z));
        if (mask.allows(x, y, z)) {
            painter.paint(x, y, z, here, mask::allows, (px, py, pz, next) -> {
                BlockState was = level.getBlockState(pos.set(px, py, pz));
                if (next == was) return;
                q.add(px, py, pz, was, next);
                if (shown != null) shown.addBlock(px, py, pz, next);
            });
        }
        q.leave(x, y, z, here);
    }

    void tick() {
        if (queued == null) return;
        if (queued.origin().visit() != AxiomTools.visit() || drawn.missed(AxiomTools.frame())) refuse(QueuedChanges.Refusal.EDITOR);
    }
}
