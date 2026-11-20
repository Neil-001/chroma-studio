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
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;

/**
 * The blocks the palette brush's Scatter places for a palette. Scatter fills a ball, so it places whole blocks: a
 * stair or slab placed at random, always facing the same way, reads as noise rather than shape. A palette's stairs,
 * slabs, walls and the like scatter as their material's full block, adding their weight to it, and a block that has
 * none, such as a lantern, a carpet or a flower, is left out. So are blocks that hold items, such as chests. Leaves
 * are placed persistent, as a player's own are, so they don't decay and drop saplings where the brush put them.
 */
record ScatterBlocks(List<Placed> blocks, List<PaletteEntry> leftOut) {
    /** A block Scatter places, and how often. */
    record Placed(BlockState state, int weight) {}

    /** Kinds that Scatter places as they are. Lights only when they're whole blocks, as glowstone is. */
    private static final Set<Kind> WHOLE = Set.of(Kind.FULL, Kind.GLASS, Kind.LEAVES, Kind.LIGHT);
    /** Shapes that scatter as their material's full block. */
    private static final Set<Kind> SHAPES = Set.of(Kind.SLAB, Kind.STAIRS, Kind.WALL, Kind.FENCE, Kind.FENCE_GATE, Kind.TRAPDOOR, Kind.DOOR);

    /** What Scatter does with each of a palette's blocks. Needs the index; a block this game lacks is in neither list. */
    static ScatterBlocks of(Palette p) {
        BlockIndex index = BlockIndex.get();
        Map<BlockState, Integer> weights = new LinkedHashMap<>();
        List<PaletteEntry> leftOut = new ArrayList<>();
        for (PaletteEntry pe : p.entries) {
            BlockState state = PaletteBlocks.state(pe);
            if (state == null) continue;
            BlockState placed = placed(index, state);
            if (placed == null) leftOut.add(pe);
            else weights.merge(placed, pe.weight(), Integer::sum);
        }
        List<Placed> blocks = new ArrayList<>();
        weights.forEach((state, weight) -> blocks.add(new Placed(state, weight)));
        return new ScatterBlocks(blocks, leftOut);
    }

    /** The block Scatter places for a palette block, or null to leave it out. */
    private static BlockState placed(BlockIndex index, BlockState state) {
        Entry e = PaletteBlocks.entry(state);
        if (e == null || state.getBlock() instanceof EntityBlock) return null;
        if (WHOLE.contains(e.kind()) && (e.kind() != Kind.LIGHT || e.fullCube())) return persistent(state);
        if (!SHAPES.contains(e.kind())) return null;
        for (Entry m : index.materialSet(e)) if (m.kind() == Kind.FULL) return m.state();
        return null;
    }

    /** Leaves that won't decay. Any other block as it is. */
    static BlockState persistent(BlockState state) {
        return state.hasProperty(BlockStateProperties.PERSISTENT) ? state.setValue(BlockStateProperties.PERSISTENT, true) : state;
    }
}
