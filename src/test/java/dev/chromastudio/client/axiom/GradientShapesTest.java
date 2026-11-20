package dev.chromastudio.client.axiom;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.chromastudio.client.index.TestIndex;
import dev.chromastudio.client.index.TintContext;
import dev.chromastudio.core.color.Face;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.FenceBlock;
import net.minecraft.world.level.block.IronBarsBlock;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.WallBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Half;
import net.minecraft.world.level.block.state.properties.SlabType;
import net.minecraft.world.level.block.state.properties.WallSide;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class GradientShapesTest {
    @BeforeAll
    static void loadGame() {
        TestIndex.loadGame();
        Map<String, String> blocks = new LinkedHashMap<>();
        for (String stone : List.of("stone_brick", "deepslate_tile", "sandstone", "red_sandstone")) {
            String full = stone.endsWith("_brick") || stone.endsWith("_tile") ? stone + "s" : stone;
            blocks.put(full, "block/" + full);
            blocks.put(stone + "_stairs", "block/" + full);
            blocks.put(stone + "_slab", "block/" + full);
        }
        blocks.put("stone_brick_wall", "block/stone_bricks");
        blocks.put("cobblestone", "block/cobblestone");
        blocks.put("cobblestone_wall", "block/cobblestone");
        for (String wood : List.of("oak", "spruce")) {
            blocks.put(wood + "_planks", "block/" + wood + "_planks");
            blocks.put(wood + "_fence", "block/" + wood + "_planks");
        }
        blocks.put("glass_pane", "block/glass");
        blocks.put("iron_bars", "block/iron_bars");
        for (String colour : List.of("white", "black")) {
            blocks.put(colour + "_stained_glass", "block/" + colour + "_stained_glass");
            blocks.put(colour + "_stained_glass_pane", "block/" + colour + "_stained_glass");
        }
        blocks.put("oak_door", "block/oak_planks");
        blocks.put("white_concrete", "block/white_concrete");
        blocks.put("black_concrete", "block/black_concrete");
        TestIndex.index(blocks);
    }

    private static Block block(String path) { return TestIndex.block(path); }

    private static GradientShapes shapes() { return new GradientShapes(Face.NORTH, TintContext.none()); }

    @Test
    void aStairTakesItsStepsMaterialFacingTheSameWay() {
        BlockState stair = block("stone_brick_stairs").defaultBlockState().setValue(StairBlock.FACING, Direction.EAST).setValue(StairBlock.HALF, Half.TOP);
        BlockState out = shapes().apply(block("sandstone").defaultBlockState(), stair);
        assertNotNull(out);
        assertEquals(block("sandstone_stairs"), out.getBlock());
        assertEquals(Direction.EAST, out.getValue(StairBlock.FACING));
        assertEquals(Half.TOP, out.getValue(StairBlock.HALF));
    }

    @Test
    void aSlabKeepsItsHalf() {
        BlockState slab = block("stone_brick_slab").defaultBlockState().setValue(SlabBlock.TYPE, SlabType.TOP);
        BlockState out = shapes().apply(block("deepslate_tiles").defaultBlockState(), slab);
        assertNotNull(out);
        assertEquals(block("deepslate_tile_slab"), out.getBlock());
        assertEquals(SlabType.TOP, out.getValue(SlabBlock.TYPE));
    }

    @Test
    void aStepWithoutTheShapeLendsItFromTheClosestColour() {
        // Concrete has no stairs, so its stairs come from the material nearest its colour.
        BlockState stair = block("stone_brick_stairs").defaultBlockState().setValue(StairBlock.FACING, Direction.WEST);
        BlockState dark = shapes().apply(block("black_concrete").defaultBlockState(), stair);
        assertNotNull(dark);
        assertEquals(block("deepslate_tile_stairs"), dark.getBlock());
        assertEquals(Direction.WEST, dark.getValue(StairBlock.FACING));
        assertEquals(block("sandstone_stairs"), shapes().apply(block("white_concrete").defaultBlockState(), stair).getBlock());
    }

    @Test
    void aDoubleSlabStaysASlab() {
        BlockState slab = block("stone_brick_slab").defaultBlockState().setValue(SlabBlock.TYPE, SlabType.DOUBLE);
        GradientShapes shapes = shapes();
        assertTrue(shapes.shaped(slab));
        BlockState out = shapes.apply(block("sandstone").defaultBlockState(), slab);
        assertNotNull(out);
        assertEquals(block("sandstone_slab"), out.getBlock());
        assertEquals(SlabType.DOUBLE, out.getValue(SlabBlock.TYPE));
        assertFalse(shapes.shaped(block("sandstone").defaultBlockState()));
    }

    @Test
    void panesFollowGlassStepsOnly() {
        GradientShapes shapes = shapes();
        BlockState pane = block("glass_pane").defaultBlockState().setValue(IronBarsBlock.EAST, true);
        BlockState out = shapes.apply(block("black_stained_glass").defaultBlockState(), pane);
        assertNotNull(out);
        assertEquals(block("black_stained_glass_pane"), out.getBlock());
        assertEquals(true, out.getValue(IronBarsBlock.EAST));
        // A window in a stone gradient keeps its glass, and no pane ever becomes iron bars.
        assertNull(shapes.apply(block("black_concrete").defaultBlockState(), pane));
        assertNull(shapes.apply(block("stone_bricks").defaultBlockState(), pane));
    }

    @Test
    void wallsAndFencesKeepTheirConnections() {
        BlockState wall = block("stone_brick_wall").defaultBlockState().setValue(WallBlock.NORTH, WallSide.TALL).setValue(WallBlock.UP, false);
        BlockState newWall = shapes().apply(block("cobblestone").defaultBlockState(), wall);
        assertNotNull(newWall);
        assertEquals(block("cobblestone_wall"), newWall.getBlock());
        assertEquals(WallSide.TALL, newWall.getValue(WallBlock.NORTH));
        assertEquals(false, newWall.getValue(WallBlock.UP));
        BlockState fence = block("oak_fence").defaultBlockState().setValue(FenceBlock.EAST, true).setValue(FenceBlock.SOUTH, true);
        BlockState newFence = shapes().apply(block("spruce_planks").defaultBlockState(), fence);
        assertNotNull(newFence);
        assertEquals(block("spruce_fence"), newFence.getBlock());
        assertEquals(true, newFence.getValue(FenceBlock.EAST));
        assertEquals(true, newFence.getValue(FenceBlock.SOUTH));
        assertEquals(false, newFence.getValue(FenceBlock.WEST));
    }

    @Test
    void blocksAlreadyRightStayAndShapesNoneCanTakeAreLeftAlone() {
        GradientShapes shapes = shapes();
        // Already the step's material in this shape, so it's part of the gradient as it is.
        BlockState stair = block("stone_brick_stairs").defaultBlockState().setValue(StairBlock.FACING, Direction.SOUTH);
        assertEquals(stair, shapes.apply(block("stone_bricks").defaultBlockState(), stair));
        // A door's halves could fall in different steps.
        assertNull(shapes.apply(block("sandstone").defaultBlockState(), block("oak_door").defaultBlockState()));
    }
}
