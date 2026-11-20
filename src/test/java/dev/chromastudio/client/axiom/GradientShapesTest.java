package dev.chromastudio.client.axiom;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import dev.chromastudio.client.index.TestIndex;
import dev.chromastudio.client.index.TintContext;
import dev.chromastudio.core.color.Face;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Half;
import net.minecraft.world.level.block.state.properties.SlabType;
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
        blocks.put("glass_pane", "block/glass");
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
    void blocksAlreadyRightAndShapesNoneCanTakeStayAsTheyAre() {
        GradientShapes shapes = shapes();
        BlockState bricks = block("stone_bricks").defaultBlockState();
        // Already the step's material in this shape.
        assertNull(shapes.apply(bricks, block("stone_brick_stairs").defaultBlockState()));
        // The only wall there is is this one.
        assertNull(shapes.apply(block("sandstone").defaultBlockState(), block("stone_brick_wall").defaultBlockState()));
        // A door's halves could fall in different steps, and a pane has no shape a step's material lends.
        assertNull(shapes.apply(block("sandstone").defaultBlockState(), block("oak_door").defaultBlockState()));
        assertNull(shapes.apply(block("sandstone").defaultBlockState(), block("glass_pane").defaultBlockState()));
    }
}
