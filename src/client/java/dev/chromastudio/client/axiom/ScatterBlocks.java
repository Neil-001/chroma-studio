package dev.chromastudio.client.axiom;

import dev.chromastudio.client.action.PaletteBlocks;
import dev.chromastudio.client.index.BlockIndex;
import dev.chromastudio.client.index.Entry;
import dev.chromastudio.client.index.Kind;
import dev.chromastudio.core.palette.Palette;
import dev.chromastudio.core.palette.PaletteEntry;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.world.level.block.AbstractCauldronBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.CandleCakeBlock;
import net.minecraft.world.level.block.ComposterBlock;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.FlowerPotBlock;
import net.minecraft.world.level.block.Fallable;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import org.jetbrains.annotations.Nullable;

record ScatterBlocks(List<Placed> blocks, List<PaletteEntry> leftOut) {
    record Placed(BlockState state, int weight) {}

    private static final Set<Kind> WHOLE = Set.of(Kind.FULL, Kind.GLASS, Kind.LEAVES, Kind.LIGHT);
    private static final Set<Kind> SHAPES = Set.of(Kind.SLAB, Kind.STAIRS, Kind.WALL, Kind.FENCE, Kind.FENCE_GATE, Kind.TRAPDOOR, Kind.DOOR);

    static ScatterBlocks of(Palette p) {
        BlockIndex index = BlockIndex.get();
        Map<BlockState, Integer> weights = new LinkedHashMap<>();
        List<PaletteEntry> leftOut = new ArrayList<>();
        for (PaletteEntry pe : p.entries) {
            BlockState state = PaletteBlocks.state(pe);
            if (state == null) continue;
            BlockState placed = placed(index, state);
            if (placed == null) leftOut.add(pe);
            else weights.merge(placed, pe.weight(), (a, b) -> (int) Math.min(Integer.MAX_VALUE, (long) a + b));
        }
        List<Placed> blocks = new ArrayList<>();
        weights.forEach((state, weight) -> blocks.add(new Placed(state, weight)));
        return new ScatterBlocks(blocks, leftOut);
    }

    private static @Nullable BlockState placed(BlockIndex index, BlockState state) {
        Entry e = PaletteBlocks.entry(state);
        if (e == null || state.getBlock() instanceof EntityBlock || state.getBlock() instanceof Fallable) return null;
        if (WHOLE.contains(e.kind()) && (e.kind() != Kind.LIGHT || e.fullCube())) return persistent(state);
        if (!SHAPES.contains(e.kind())) return null;
        for (Entry m : index.materialSet(e)) if (m.kind() == Kind.FULL) return m.state();
        return null;
    }

    static boolean paintsOver(BlockState now) {
        Block b = now.getBlock();
        return !(b instanceof EntityBlock || b instanceof FlowerPotBlock || b instanceof CandleCakeBlock || b instanceof AbstractCauldronBlock
                || b instanceof ComposterBlock) && !now.hasProperty(BlockStateProperties.DOUBLE_BLOCK_HALF)
                && !(now.hasProperty(BlockStateProperties.WATERLOGGED) && now.getValue(BlockStateProperties.WATERLOGGED));
    }

    static BlockState persistent(BlockState state) {
        return state.hasProperty(BlockStateProperties.PERSISTENT) ? state.setValue(BlockStateProperties.PERSISTENT, true) : state;
    }
}
