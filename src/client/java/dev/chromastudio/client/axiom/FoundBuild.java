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
import org.jetbrains.annotations.Nullable;

record FoundBuild(long[] blocks, boolean capped, Palette kinds) {
    static final int CAP = 100_000;

    static @Nullable FoundBuild from(BlockGetter level, BlockPos start) {
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        BuildSearch.Found found = blocks(level, start);
        if (found.blocks().length == 0) return null;
        Map<Block, Integer> counts = new LinkedHashMap<>();
        for (long p : found.blocks()) counts.merge(level.getBlockState(pos.set(BuildSearch.x(p), BuildSearch.y(p), BuildSearch.z(p))).getBlock(), 1, Integer::sum);
        Palette kinds = new Palette("build");
        counts.forEach((block, n) -> kinds.entries.add(PaletteBlocks.of(block.defaultBlockState()).withWeight(n)));
        return new FoundBuild(found.blocks(), found.capped(), kinds);
    }

    static BuildSearch.Found blocks(BlockGetter level, BlockPos start) {
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        return BuildSearch.from(start.getX(), start.getY(), start.getZ(), (x, y, z) -> member(level, pos.set(x, y, z), level.getBlockState(pos)), CAP);
    }

    static boolean member(BlockGetter level, BlockPos pos, BlockState s) {
        return !s.isAir() && !(s.getBlock() instanceof LiquidBlock) && !RecolourPlan.ground(s) && !s.getCollisionShape(level, pos).isEmpty();
    }
}
