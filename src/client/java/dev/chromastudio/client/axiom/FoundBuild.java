package dev.chromastudio.client.axiom;

import dev.chromastudio.client.action.PaletteBlocks;
import dev.chromastudio.core.palette.BuildSearch;
import dev.chromastudio.core.palette.Palette;
import java.util.LinkedHashMap;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.jspecify.annotations.Nullable;

/**
 * A build the palette brush found for itself: the solid blocks joined to the one the player used it on, standing on
 * the ground but not counting it. Its kinds of block, as a From palette, set the darkest and lightest a recolour
 * spreads over the palette, so a grey stone house uses the whole palette rather than the few blocks that the greys
 * of every block in the game would give it.
 *
 * <p>Air, liquids, the ground ({@link RecolourPlan#ground}) and blocks you can walk through, such as grass, flowers and
 * torches, don't join a build, so the search stops at the lawn rather than running on into the landscape.
 */
record FoundBuild(long[] blocks, boolean capped, Palette kinds) {
    /** The most blocks a search takes, as the gradient tool's, so a build that runs on can't stall the game. */
    static final int CAP = 100_000;

    /** The build joined to {@code start}, or null when that block isn't part of one, as the ground isn't. */
    static @Nullable FoundBuild from(BlockGetter level, BlockPos start) {
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        BuildSearch.Found found = BuildSearch.from(start.getX(), start.getY(), start.getZ(),
                (x, y, z) -> member(level, pos.set(x, y, z), level.getBlockState(pos)), CAP);
        if (found.blocks().length == 0) return null;
        // Each kind once, with how many of it there are as its weight, in the order the search met them.
        Map<Block, Integer> counts = new LinkedHashMap<>();
        for (long p : found.blocks()) counts.merge(level.getBlockState(pos.set(BuildSearch.x(p), BuildSearch.y(p), BuildSearch.z(p))).getBlock(), 1, Integer::sum);
        Palette kinds = new Palette("build");
        counts.forEach((block, n) -> kinds.entries.add(PaletteBlocks.of(block.defaultBlockState()).withWeight(n)));
        return new FoundBuild(found.blocks(), found.capped(), kinds);
    }

    /** Whether a block can be part of a build. */
    static boolean member(BlockGetter level, BlockPos pos, BlockState s) {
        return !s.isAir() && !(s.getBlock() instanceof LiquidBlock) && !RecolourPlan.ground(s) && !s.getCollisionShape(level, pos).isEmpty();
    }
}
