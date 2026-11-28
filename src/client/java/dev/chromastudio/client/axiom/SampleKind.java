package dev.chromastudio.client.axiom;

import dev.chromastudio.client.index.BlockIndex;
import dev.chromastudio.client.index.Entry;
import dev.chromastudio.client.index.Kind;
import java.util.Set;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

final class SampleKind {
    private SampleKind() {}

    private static final Set<Kind> SHAPES = Set.of(Kind.SLAB, Kind.STAIRS, Kind.WALL, Kind.FENCE, Kind.FENCE_GATE, Kind.TRAPDOOR, Kind.DOOR);

    static @Nullable String of(BlockIndex index, BlockState state, boolean ground) {
        if (state.isAir() || state.getBlock() instanceof LiquidBlock || state.getBlock() instanceof EntityBlock) return null;
        Entry e = index.entryOf(state);
        if (e == null || e.technical() || e.kind() == Kind.FLUID || !ground && groundLike(state)) return null;
        if (SHAPES.contains(e.kind())) for (Entry m : index.materialSet(e)) if (m.kind() == Kind.FULL && !m.technical()) return m.id();
        return e.id();
    }

    static boolean member(BlockState state) {
        return !state.isAir() && !(state.getBlock() instanceof LiquidBlock) && !groundLike(state);
    }

    private static boolean groundLike(BlockState state) { return Kind.of(state) == Kind.PLANT || RecolourPlan.ground(state); }
}
