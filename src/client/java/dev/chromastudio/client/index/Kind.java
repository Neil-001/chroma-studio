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

public enum Kind {
    FULL, SLAB, STAIRS, WALL, FENCE, FENCE_GATE, PANE, TRAPDOOR, DOOR, SHELF, CARPET, GLASS, LEAVES, PLANT, FLUID, LIGHT, OTHER;

    private record Rule(Class<? extends Block> type, Kind kind) {}

    private static final List<Rule> BY_CLASS = List.of(
            new Rule(SlabBlock.class, SLAB),
            new Rule(StairBlock.class, STAIRS),
            new Rule(WallBlock.class, WALL),
            new Rule(FenceBlock.class, FENCE),
            new Rule(FenceGateBlock.class, FENCE_GATE),
            new Rule(IronBarsBlock.class, PANE),
            new Rule(TrapDoorBlock.class, TRAPDOOR),
            new Rule(DoorBlock.class, DOOR),
            new Rule(ShelfBlock.class, SHELF),
            new Rule(CarpetBlock.class, CARPET),
            new Rule(MossyCarpetBlock.class, CARPET),
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

    @SuppressWarnings("deprecation")
    static int lightEmission(BlockState state) { return state.getLightEmission(); }

    static boolean fullCube(BlockState state) {
        try {
            return state.isCollisionShapeFullBlock(EmptyBlockGetter.INSTANCE, BlockPos.ZERO);
        } catch (RuntimeException ignored) {
            return false;
        }
    }
}
