package dev.chromastudio.client.axiom;

import dev.chromastudio.client.index.BlockIndex;
import dev.chromastudio.client.index.Entry;
import dev.chromastudio.client.index.Kind;
import java.util.Set;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.jspecify.annotations.Nullable;

/**
 * What a block counts as when {@link SamplerTool} samples a build, kept apart from the tool so it can be tested
 * without Axiom. A shape counts as its material's full block, as a palette's recolour reads it: stone brick stairs,
 * slabs and walls are all stone bricks, so a sampled palette lists the material once with all of it as its weight.
 */
final class SampleKind {
    private SampleKind() {}

    /** Shapes that count as their material's full block. */
    private static final Set<Kind> SHAPES = Set.of(Kind.SLAB, Kind.STAIRS, Kind.WALL, Kind.FENCE, Kind.FENCE_GATE, Kind.TRAPDOOR, Kind.DOOR);

    /**
     * The block id a block counts as: its material's first full block for a shape such as a stair, or its own id when
     * it's not a shape or its material has no full block. Null for a block that never counts: air, liquids, blocks
     * that hold items or text, such as chests and signs, blocks the index doesn't list or marks technical, and the
     * ground and plants unless {@code ground} says to count them.
     */
    static @Nullable String of(BlockIndex index, BlockState state, boolean ground) {
        if (state.isAir() || state.getBlock() instanceof LiquidBlock || state.getBlock() instanceof EntityBlock) return null;
        Entry e = index.entryOf(state);
        if (e == null || e.technical() || e.kind() == Kind.FLUID || !ground && groundLike(state)) return null;
        if (SHAPES.contains(e.kind())) for (Entry m : index.materialSet(e)) if (m.kind() == Kind.FULL && !m.technical()) return m.id();
        return e.id();
    }

    /**
     * Whether a search for the whole build may pass through a block: anything but air, liquid, the ground and plants,
     * whether they're counted or not, or one use on a house would spread across the landscape.
     */
    static boolean member(BlockState state) {
        return !state.isAir() && !(state.getBlock() instanceof LiquidBlock) && !groundLike(state);
    }

    /**
     * The ground, as a recolour of every block leaves it alone, and the plants that grow on it, read from the block
     * itself, so a plant the index doesn't list still can't join a build to its neighbour.
     */
    private static boolean groundLike(BlockState state) { return Kind.of(state) == Kind.PLANT || RecolourPlan.ground(state); }
}
