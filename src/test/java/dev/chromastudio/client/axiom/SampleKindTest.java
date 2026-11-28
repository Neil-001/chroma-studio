package dev.chromastudio.client.axiom;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.chromastudio.client.index.BlockIndex;
import dev.chromastudio.client.index.TestIndex;
import java.util.LinkedHashMap;
import java.util.Map;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.Half;
import net.minecraft.world.level.block.state.properties.SlabType;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class SampleKindTest {
    private static BlockIndex index;

    @BeforeAll
    static void loadGame() {
        TestIndex.loadGame();
        Map<String, String> blocks = new LinkedHashMap<>();
        blocks.put("stone_bricks", "block/stone_bricks");
        blocks.put("stone_brick_stairs", "block/stone_bricks");
        blocks.put("stone_brick_slab", "block/stone_bricks");
        blocks.put("stone_brick_wall", "block/stone_bricks");
        blocks.put("oak_planks", "block/oak_planks");
        blocks.put("oak_log", "block/oak_log");
        blocks.put("oak_stairs", "block/oak_planks");
        blocks.put("oak_door", "block/oak_planks");
        blocks.put("glass", "block/glass");
        blocks.put("glass_pane", "block/glass");
        blocks.put("lantern", "block/lantern");
        blocks.put("poppy", "block/poppy");
        blocks.put("chest", "block/oak_planks");
        blocks.put("water", "block/water_still");
        blocks.put("mossy_cobblestone_slab", "block/mossy_cobblestone");
        index = TestIndex.index(blocks);
    }

    private static String kind(BlockState s) { return SampleKind.of(index, s, false); }

    @Test
    void stairsSlabsAndWallsCountAsTheirMaterialWhicheverWayTheyFace() {
        assertEquals("minecraft:stone_bricks", kind(Blocks.STONE_BRICKS.defaultBlockState()));
        assertEquals("minecraft:stone_bricks", kind(Blocks.STONE_BRICK_STAIRS.defaultBlockState()
                .setValue(StairBlock.FACING, Direction.WEST).setValue(StairBlock.HALF, Half.TOP)));
        assertEquals("minecraft:stone_bricks", kind(Blocks.STONE_BRICK_SLAB.defaultBlockState()
                .setValue(SlabBlock.TYPE, SlabType.TOP).setValue(BlockStateProperties.WATERLOGGED, true)));
        assertEquals("minecraft:stone_bricks", kind(Blocks.STONE_BRICK_WALL.defaultBlockState()));
        assertEquals("minecraft:oak_planks", kind(Blocks.OAK_STAIRS.defaultBlockState()));
        assertEquals("minecraft:oak_planks", kind(Blocks.OAK_DOOR.defaultBlockState()));
        assertEquals("minecraft:mossy_cobblestone_slab", kind(Blocks.MOSSY_COBBLESTONE_SLAB.defaultBlockState()));
    }

    @Test
    void fullBlocksGlassPanesAndAccentsCountAsThemselves() {
        assertEquals("minecraft:oak_log", kind(Blocks.OAK_LOG.defaultBlockState()));
        assertEquals("minecraft:glass", kind(Blocks.GLASS.defaultBlockState()));
        assertEquals("minecraft:glass_pane", kind(Blocks.GLASS_PANE.defaultBlockState()));
        assertEquals("minecraft:lantern", kind(Blocks.LANTERN.defaultBlockState()));
    }

    @Test
    void plantsCountOnlyWhenAskedFor() {
        assertNull(kind(Blocks.POPPY.defaultBlockState()));
        assertEquals("minecraft:poppy", SampleKind.of(index, Blocks.POPPY.defaultBlockState(), true));
    }

    @Test
    void theWholeBuildSearchNeverPassesThroughPlantsListedOrNot() {
        assertTrue(SampleKind.member(Blocks.STONE_BRICKS.defaultBlockState()));
        assertFalse(SampleKind.member(Blocks.POPPY.defaultBlockState()));
        assertFalse(SampleKind.member(Blocks.DANDELION.defaultBlockState()));
        assertFalse(SampleKind.member(Blocks.AIR.defaultBlockState()));
        assertFalse(SampleKind.member(Blocks.WATER.defaultBlockState()));
    }

    @Test
    void airLiquidsAndBlocksThatHoldThingsNeverCount() {
        for (boolean ground : new boolean[] {false, true}) {
            assertNull(SampleKind.of(index, Blocks.AIR.defaultBlockState(), ground));
            assertNull(SampleKind.of(index, Blocks.WATER.defaultBlockState(), ground));
            assertNull(SampleKind.of(index, Blocks.CHEST.defaultBlockState(), ground));
            assertNull(SampleKind.of(index, Blocks.BEDROCK.defaultBlockState(), ground));
        }
    }
}
