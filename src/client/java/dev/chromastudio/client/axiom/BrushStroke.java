package dev.chromastudio.client.axiom;

import com.moulberry.axiomclientapi.IAxiomWorldRenderContext;
import com.moulberry.axiomclientapi.pathers.BallShape;
import com.moulberry.axiomclientapi.pathers.ToolPatherUnique;
import com.moulberry.axiomclientapi.regions.BlockRegion;
import dev.chromastudio.core.palette.QueuedChanges;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * One stroke of a brush that changes blocks, as the palette brush and the shade brush make them. It starts when the
 * tool is used, queues each change the brush's ball reaches while the button is held, shows them pulsing as Axiom's
 * own brushes show a stroke not yet made, and goes to Axiom as one change when the button is let go, through
 * {@link AxiomChanges}. Blocks the tool masks keep out are never queued. A stroke that's left, as when the player
 * leaves the editor or picks another tool with the button held, is dropped, and so is one whose world, blocks or
 * masks changed under it: the panel says why.
 */
final class BrushStroke {
    /**
     * The button that uses a tool, as Axiom numbers them: ImGui's order from 0, in which 1 is the right button. Axiom
     * maps it to its own "use tool" key, so a player who rebinds that key is still followed.
     */
    static final int USE_TOOL = 1;
    /** How long Axiom may go without drawing a tool before it counts as put away, in nanoseconds. */
    static final long PUT_AWAY = 250_000_000L;

    /** What a brush does to one block it reaches. */
    @FunctionalInterface
    interface Painter {
        /** Puts what goes at {@code (x, y, z)}, which holds {@code now}, through {@code put}, or nothing to leave it. */
        void paint(int x, int y, int z, BlockState now, Put put);
    }

    @FunctionalInterface
    interface Put {
        void put(int x, int y, int z, BlockState next);
    }

    private final BlockRegion shown = AxiomTools.regions.createBlock();
    private @Nullable ToolPatherUnique pather;
    private @Nullable QueuedChanges<BlockState> queued;
    private AxiomMask.Test mask;
    private @Nullable ClientLevel level;
    private long drawn;
    /** Why the last stroke wasn't made, until the next starts. */
    private @Nullable Component refused;

    /** Starts a stroke with a ball of {@code radius}. */
    void start(ClientLevel level, int radius) {
        cancel();
        refused = null;
        this.level = level;
        pather = AxiomTools.pathers.createUnique(radius, BallShape.SPHERE);
        queued = AxiomChanges.queue(level);
        mask = AxiomMask.now(level);
        drawn = System.nanoTime();
    }

    boolean active() { return pather != null; }

    /** Why the last stroke wasn't made, or null. */
    @Nullable Component refused() { return refused; }

    void say(@Nullable Component why) { refused = why; }

    /** Drops the stroke without changing anything. */
    void cancel() {
        shown.clear();
        pather = null;
        queued = null;
        level = null;
    }

    /**
     * Called each frame Axiom draws the tool while a stroke is on: extends it while the button is held, and makes it
     * once the button is let go.
     */
    void render(IAxiomWorldRenderContext rc, Painter painter) {
        if (pather == null || queued == null) return;
        drawn = System.nanoTime();
        ClientLevel now = Minecraft.getInstance().level;
        if (now != level) {
            refused = AxiomChanges.refusal(QueuedChanges.Refusal.WORLD);
            cancel();
            return;
        }
        if (!AxiomTools.tools.isMouseDown(USE_TOOL)) {
            refused = AxiomChanges.send(queued).orElse(null);
            cancel();
            return;
        }
        QueuedChanges<BlockState> q = queued;
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        Put put = (x, y, z, next) -> {
            BlockState was = now.getBlockState(pos.set(x, y, z));
            if (next == was || !mask.allows(x, y, z)) return;
            q.add(x, y, z, was, next);
            shown.addBlock(x, y, z, next);
        };
        pather.update((x, y, z) -> {
            if (mask.allows(x, y, z)) painter.paint(x, y, z, now.getBlockState(pos.set(x, y, z)), put);
        });
        // A slow pulse, as Axiom's own brushes show a stroke that hasn't been made yet.
        float pulse = (float) Math.sin(rc.nanos() / 400_000_000.0);
        shown.render(rc, Vec3.ZERO, 0.75f + pulse * 0.25f, 0.3f - pulse * 0.2f);
    }

    /**
     * Called every client tick. Axiom never tells a tool that its editor closed or that another tool was picked, so a
     * stroke it has stopped drawing is dropped here; one still held when the editor comes back would otherwise be made.
     */
    void tick() {
        if (pather != null && System.nanoTime() - drawn > PUT_AWAY) {
            refused = AxiomChanges.refusal(QueuedChanges.Refusal.EDITOR);
            cancel();
        }
    }
}
