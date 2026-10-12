package dev.chromastudio.client.index;

import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.EmptyBlockGetter;
import net.minecraft.world.level.block.BambooStalkBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.CactusBlock;
import net.minecraft.world.level.block.CarpetBlock;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.FenceBlock;
import net.minecraft.world.level.block.FenceGateBlock;
import net.minecraft.world.level.block.GrowingPlantBlock;
import net.minecraft.world.level.block.IronBarsBlock;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.MossyCarpetBlock;
import net.minecraft.world.level.block.ShelfBlock;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.SugarCaneBlock;
import net.minecraft.world.level.block.TransparentBlock;
import net.minecraft.world.level.block.TrapDoorBlock;
import net.minecraft.world.level.block.VegetationBlock;
import net.minecraft.world.level.block.VineBlock;
import net.minecraft.world.level.block.WallBlock;
import net.minecraft.world.level.block.WaterloggedTransparentBlock;
import net.minecraft.world.level.block.state.BlockState;

/** What sort of block an entry is, for the kind filter. */
public enum Kind {
    FULL, SLAB, STAIRS, WALL, FENCE, FENCE_GATE, PANE, TRAPDOOR, DOOR, SHELF, CARPET, GLASS, LEAVES, PLANT, FLUID, LIGHT, OTHER;

    /** A block class and the kind its blocks have. A null kind sends the class on to the rules after the table. */
    private record Rule(Class<? extends Block> type, Kind kind) {}

    // The first class a block is an instance of decides, so a modded block that extends one of these gets its kind
    // too. A subclass that should not take its parent's kind goes above the parent.
    private static final List<Rule> BY_CLASS = List.of(
            new Rule(SlabBlock.class, SLAB),
            new Rule(StairBlock.class, STAIRS),
            new Rule(WallBlock.class, WALL),
            new Rule(FenceBlock.class, FENCE),
            new Rule(FenceGateBlock.class, FENCE_GATE),
            // Glass panes are here too, as stained glass panes extend the bars.
            new Rule(IronBarsBlock.class, PANE),
            new Rule(TrapDoorBlock.class, TRAPDOOR),
            new Rule(DoorBlock.class, DOOR),
            new Rule(ShelfBlock.class, SHELF),
            new Rule(CarpetBlock.class, CARPET),
            new Rule(MossyCarpetBlock.class, CARPET),
            // Copper grates share glass's class but aren't glass.
            new Rule(WaterloggedTransparentBlock.class, null),
            new Rule(TransparentBlock.class, GLASS),
            new Rule(LeavesBlock.class, LEAVES),
            new Rule(VegetationBlock.class, PLANT),
            new Rule(GrowingPlantBlock.class, PLANT),
            new Rule(VineBlock.class, PLANT),
            new Rule(SugarCaneBlock.class, PLANT),
            new Rule(CactusBlock.class, PLANT),
            new Rule(BambooStalkBlock.class, PLANT),
            new Rule(LiquidBlock.class, FLUID));

    /** The kind of a state: by the block's class first, then a light source, then a full cube. */
    public static Kind of(BlockState state) {
        Block block = state.getBlock();
        for (Rule rule : BY_CLASS) {
            if (!rule.type.isInstance(block)) continue;
            if (rule.kind != null) return rule.kind;
            break;
        }
        if (lightEmission(state) > 0) return LIGHT;
        return fullCube(state) ? FULL : OTHER;
    }

    // NeoForge deprecates this for a form that takes a world and a position. The index has neither.
    @SuppressWarnings("deprecation")
    static int lightEmission(BlockState state) { return state.getLightEmission(); }

    static boolean fullCube(BlockState state) {
        try {
            return state.isCollisionShapeFullBlock(EmptyBlockGetter.INSTANCE, BlockPos.ZERO);
        } catch (RuntimeException _) {
            // A modded shape that needs a real world.
            return false;
        }
    }
}
