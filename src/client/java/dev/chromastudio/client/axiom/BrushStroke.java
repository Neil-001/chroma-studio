package dev.chromastudio.client.axiom;

import com.moulberry.axiomclientapi.IAxiomWorldRenderContext;
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
import org.jspecify.annotations.Nullable;

/**
 * One stroke of a brush that changes blocks, as the palette brush and the shade brush make them. It starts when the
 * tool is used, queues each change the brush's ball reaches while the button is held, shows them pulsing as Axiom's
 * own brushes show a stroke not yet made, and goes to Axiom as one change when the button is let go, through
 * {@link AxiomChanges}. Blocks the tool masks keep out are never queued.
 *
 * <p>Every position the ball reached is kept, the ones the masks kept out included, so that when the button is let go
 * the stroke can be painted again over the same positions, by the same steps, from the world and masks as they are
 * then: only if that gives the same is it sent. A stroke ends with nothing sent when the button is let go after
 * anything changed, when the editor closes or the player leaves the world, or when a frame goes by without Axiom
 * drawing the tool, as when another tool was picked; the panel says why.
 */
final class BrushStroke {
    /**
     * The button that uses a tool, as Axiom numbers them: ImGui's order from 0, in which 1 is the right button. Axiom
     * maps it to its own "use tool" key, so a player who rebinds that key is still followed.
     */
    static final int USE_TOOL = 1;

    /** What a brush does to one block it reaches. */
    @FunctionalInterface
    interface Painter {
        /**
         * Puts what goes at {@code (x, y, z)}, which holds {@code now}, through {@code put}, or nothing to leave it. A
         * door's other half may go too, past the ball's edge, where {@code admit} lets it, so a door is never split.
         */
        void paint(int x, int y, int z, BlockState now, RecolourPlan.Admit admit, RecolourPlan.Put put);
    }

    /**
     * Makes a stroke's painter: once as the stroke starts, and again when it's let go, to paint it again from the world
     * as it is then. Each time, it reads everything the painter depends on afresh, the panel's choices and the
     * palette's blocks included, so nothing from the start stands in for them. Only what the stroke is stays the same:
     * the positions the ball reached, the block it started on and a random seed for its choices.
     */
    @FunctionalInterface
    interface Setup {
        /** The painter, or null when there's nothing to paint with. Throws when the world gives the brush nothing to go on. */
        @Nullable Painter make(Level level) throws Refused;
    }

    private final BlockRegion shown = AxiomTools.regions.createBlock();
    private @Nullable ToolPatherUnique pather;
    private @Nullable QueuedChanges<BlockState> queued;
    private @Nullable Setup setup;
    private @Nullable Painter painter;
    private AxiomMask.@Nullable Test mask;
    private @Nullable ClientLevel level;
    /** Every position the ball has reached, in order, the ones the masks kept out included. */
    private final LongArrayList reached = new LongArrayList();
    private final Drawn drawn = new Drawn();
    /** Why the last stroke wasn't made, until the next starts. */
    private @Nullable Component refused;

    /**
     * Starts a stroke with a ball of {@code radius}, painting as {@code setup} says. False when it has nothing to
     * paint with; throws when it says why not.
     */
    boolean start(ClientLevel level, QueuedChanges<BlockState> queue, int radius, Setup setup) throws Refused {
        cancel();
        refused = null;
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

    /** Why the last stroke wasn't made, or null. */
    @Nullable Component refused() { return refused; }

    /** Forgets why the last stroke wasn't made, as when the tool is used again. */
    void forget() { refused = null; }

    /** Drops a stroke on now as left partway, as when Axiom picks another tool, saying so. */
    void away() {
        if (queued != null) refuse(QueuedChanges.Refusal.EDITOR);
    }

    /** Drops the stroke without changing anything. */
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

    /**
     * Called each frame Axiom draws the tool while a stroke is on: extends it while the button is held, and makes it
     * once the button is let go.
     */
    void render(IAxiomWorldRenderContext rc) {
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
        // A mask that failed at any point in the stroke ends it, whatever it answers later: Axiom forgets a Lua
        // script's error as soon as another context compiles the script, as the check before sending does.
        if (m.failed()) {
            refuse(QueuedChanges.Refusal.MASK_FAILED);
            return;
        }
        // A slow pulse, as Axiom's own brushes show a stroke that hasn't been made yet.
        float pulse = (float) Math.sin(rc.nanos() / 400_000_000.0);
        shown.render(rc, Vec3.ZERO, 0.75f + pulse * 0.25f, 0.3f - pulse * 0.2f);
    }

    /** Sends the stroke, if painting it again now gives the same. Nothing of it is kept either way. */
    private void finish() {
        QueuedChanges<BlockState> q = queued;
        Setup s = setup;
        ClientLevel in = level;
        AxiomMask.Test m = mask;
        long[] at = reached.toLongArray();
        cancel();
        if (q == null || s == null || in == null || m == null) return;
        refused = AxiomChanges.send(q, m, again -> again(in, s, at, again, q.origin())).orElse(null);
    }

    private void refuse(QueuedChanges.Refusal why) {
        cancel();
        refused = AxiomChanges.refusal(why);
    }

    /**
     * Paints a stroke again over the positions it reached, in order, from the world as it is now, with these masks. A
     * world that now gives the brush nothing to go on gives nothing, which is never the same as a stroke that changed
     * something.
     */
    static QueuedChanges<BlockState> again(Level level, Setup setup, long[] reached, QueuedChanges.Mask mask, QueuedChanges.Origin origin) {
        QueuedChanges<BlockState> q = new QueuedChanges<>(origin);
        Painter p;
        try {
            p = setup.make(level);
        } catch (Refused _) {
            return q;
        }
        if (p == null) return q;
        for (long r : reached) paint(level, p, mask, q, BuildSearch.x(r), BuildSearch.y(r), BuildSearch.z(r), null);
        return q;
    }

    /**
     * Paints one position the ball reached, the same way as the stroke goes and when it's checked: the masks are asked
     * about it whether or not anything would change, and it's recorded with what it holds, so a block that changes
     * under it refuses the stroke even where the brush would put the same.
     */
    private static void paint(Level level, Painter painter, QueuedChanges.Mask mask, QueuedChanges<BlockState> q, int x, int y, int z,
                              @Nullable BlockRegion shown) {
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        BlockState here = level.getBlockState(pos.set(x, y, z));
        if (mask.allows(x, y, z)) {
            // The painter's admit tests every position it puts, a door's other half included, against the same masks.
            painter.paint(x, y, z, here, mask::allows, (px, py, pz, next) -> {
                BlockState was = level.getBlockState(pos.set(px, py, pz));
                if (next == was) return;
                q.add(px, py, pz, was, next);
                if (shown != null) shown.addBlock(px, py, pz, next);
            });
        }
        q.leave(x, y, z, here);
    }

    /**
     * Called every client tick. Axiom never tells a tool that its editor closed, and doesn't always tell it that another
     * tool was picked, so a stroke is dropped here once the editor has been left, or a frame has gone by without Axiom
     * drawing the tool.
     */
    void tick() {
        if (queued == null) return;
        if (queued.origin().visit() != AxiomTools.visit() || drawn.missed(AxiomTools.frame())) refuse(QueuedChanges.Refusal.EDITOR);
    }
}
