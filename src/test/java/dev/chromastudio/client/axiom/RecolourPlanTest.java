package dev.chromastudio.client.axiom;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.chromastudio.client.action.PaletteBlocks;
import dev.chromastudio.client.index.TestIndex;
import dev.chromastudio.client.index.TintContext;
import dev.chromastudio.core.color.Face;
import dev.chromastudio.core.palette.AxiomScript;
import dev.chromastudio.core.palette.Palette;
import dev.chromastudio.core.palette.PaletteEntry;
import dev.chromastudio.core.palette.Recolour;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Half;
import net.minecraft.world.level.block.state.properties.Property;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class RecolourPlanTest {
    @BeforeAll
    static void loadGame() {
        TestIndex.loadGame();
        Map<String, String> blocks = new LinkedHashMap<>();
        for (String stone : List.of("stone_brick", "deepslate_tile", "sandstone", "red_sandstone", "cobblestone")) {
            String full = stone.endsWith("_brick") || stone.endsWith("_tile") ? stone + "s" : stone;
            blocks.put(full, "block/" + full);
            blocks.put(stone + "_stairs", "block/" + full);
            blocks.put(stone + "_slab", "block/" + full);
        }
        blocks.put("stone_brick_wall", "block/stone_bricks");
        blocks.put("terracotta", "block/terracotta");
        blocks.put("sand", "block/sand");
        blocks.put("glass", "block/glass");
        blocks.put("glass_pane", "block/glass");
        blocks.put("smooth_stone", "block/smooth_stone");
        blocks.put("grass_block", "block/dirt");
        blocks.put("glowstone", "block/glowstone");
        blocks.put("sea_lantern", "block/sea_lantern");
        blocks.put("torch", "block/torch");
        blocks.put("lantern", "block/lantern");
        blocks.put("oak_planks", "block/oak_planks");
        blocks.put("oak_log", "block/oak_log");
        blocks.put("oak_stairs", "block/oak_planks");
        blocks.put("white_concrete", "block/white_concrete");
        blocks.put("black_concrete", "block/black_concrete");
        TestIndex.index(blocks);
    }

    private static Palette palette(String name, String... ids) {
        Palette p = new Palette(name);
        for (String id : ids) p.entries.add(new PaletteEntry("minecraft:" + id, ""));
        return p;
    }

    private static Palette desert() { return palette("Desert", "sandstone", "terracotta", "red_sandstone", "sand"); }

    private static RecolourPlan plan(Recolour.Mode mode) throws AxiomBridge.Refused {
        return RecolourPlan.of(desert(), null, mode, Face.NORTH, TintContext.none());
    }

    /** What the script does with a block: the row's block, then each copied property as the block found has it. */
    private static BlockState scripted(List<AxiomScript.Remap> rows, BlockState here) {
        String id = PaletteBlocks.of(here.getBlock().defaultBlockState()).blockId();
        for (AxiomScript.Remap r : rows) {
            if (!r.from().equals(id)) continue;
            BlockState out = PaletteBlocks.state(r.to());
            for (String name : r.copied()) out = withProperty(out, name, value(here, here.getBlock().getStateDefinition().getProperty(name)));
            return out;
        }
        return null;
    }

    private static <T extends Comparable<T>> String value(BlockState s, Property<T> p) { return p.getName(s.getValue(p)); }

    private static BlockState withProperty(BlockState s, String name, String value) {
        Property<?> p = s.getBlock().getStateDefinition().getProperty(name);
        return p == null ? s : set(s, p, value);
    }

    private static <T extends Comparable<T>> BlockState set(BlockState s, Property<T> p, String value) {
        return p.getValue(value).map(v -> s.setValue(p, v)).orElse(s);
    }

    @Test
    void theScriptAndTheBrushGiveTheSameBlocks() throws Exception {
        for (Recolour.Mode mode : Recolour.Mode.values()) {
            RecolourPlan plan = plan(mode);
            List<AxiomScript.Remap> rows = plan.rows();
            assertEquals(plan.size(), rows.size());
            int compared = 0;
            for (String path : List.of("stone_bricks", "stone_brick_stairs", "stone_brick_slab", "stone_brick_wall", "deepslate_tiles",
                    "deepslate_tile_stairs", "cobblestone", "cobblestone_slab", "smooth_stone", "glass", "glass_pane", "sandstone")) {
                for (BlockState here : TestIndex.block(path).getStateDefinition().getPossibleStates()) {
                    assertEquals(scripted(rows, here), plan.apply(here), mode + " " + here);
                    compared++;
                }
            }
            assertTrue(compared > 100);
        }
    }

    @Test
    void stairsStayStairsFacingTheSameWay() throws Exception {
        RecolourPlan plan = plan(Recolour.Mode.SHADING);
        BlockState stair = TestIndex.block("stone_brick_stairs").defaultBlockState()
                .setValue(StairBlock.FACING, Direction.EAST).setValue(StairBlock.HALF, Half.TOP);
        BlockState out = plan.apply(stair);
        assertNotNull(out);
        assertTrue(out.getBlock() instanceof StairBlock, out.toString());
        assertEquals(Direction.EAST, out.getValue(StairBlock.FACING));
        assertEquals(Half.TOP, out.getValue(StairBlock.HALF));
        // The darkest stair takes the darkest palette material that has stairs, as terracotta has none.
        assertEquals(TestIndex.block("red_sandstone_stairs"), plan.apply(TestIndex.block("deepslate_tile_stairs").defaultBlockState()).getBlock());
        assertEquals(TestIndex.block("terracotta"), plan.apply(TestIndex.block("deepslate_tiles").defaultBlockState()).getBlock());
        // No palette material has walls, so a wall stays as it is rather than becoming a full block.
        assertNull(plan.apply(TestIndex.block("stone_brick_wall").defaultBlockState()));
    }

    @Test
    void glassStaysAndSandIsNeverPutInAWall() throws Exception {
        for (Recolour.Mode mode : Recolour.Mode.values()) {
            RecolourPlan plan = plan(mode);
            assertNull(plan.apply(TestIndex.block("glass").defaultBlockState()));
            assertNull(plan.apply(TestIndex.block("glass_pane").defaultBlockState()));
            Block sand = TestIndex.block("sand");
            for (AxiomScript.Remap r : plan.rows()) assertTrue(PaletteBlocks.state(r.to()).getBlock() != sand, r.toString());
        }
    }

    @Test
    void lightsThatAreFullBlocksSwapAndTheRestStay() throws Exception {
        Palette lit = palette("Lit", "sandstone", "sea_lantern", "lantern");
        RecolourPlan plan = RecolourPlan.of(lit, null, Recolour.Mode.SHADING, Face.NORTH, TintContext.none());
        assertEquals(TestIndex.block("sea_lantern"), plan.apply(TestIndex.block("glowstone").defaultBlockState()).getBlock());
        // A torch is no lantern: its shape and the way it hangs on a wall are its own.
        assertNull(plan.apply(TestIndex.block("torch").defaultBlockState()));
        assertNull(plan.apply(TestIndex.block("lantern").defaultBlockState()));
    }

    @Test
    void aFromPaletteChangesItsOwnBlocksGroundIncluded() throws Exception {
        // Without a From palette, grass is left alone by its tag, which only a world loads, so the game shows that.
        RecolourPlan from = RecolourPlan.of(desert(), palette("Garden", "grass_block", "stone_bricks"), Recolour.Mode.SHADING, Face.NORTH, TintContext.none());
        assertNotNull(from.apply(TestIndex.block("grass_block").defaultBlockState()));
        // A From palette limits the recolour to its own blocks.
        assertNull(from.apply(TestIndex.block("cobblestone").defaultBlockState()));
        assertEquals(2, from.size());
    }

    @Test
    void aShapeThePaletteListsBringsNoFullBlockOfItsOwn() throws Exception {
        RecolourPlan plan = RecolourPlan.of(palette("Oak", "oak_log", "oak_stairs"), null, Recolour.Mode.SHADING, Face.NORTH, TintContext.none());
        // Oak stairs lend their shape, but the palette's only full block is the log, so no wall turns to planks.
        assertEquals(TestIndex.block("oak_log"), plan.apply(TestIndex.block("white_concrete").defaultBlockState()).getBlock());
        assertEquals(TestIndex.block("oak_stairs"), plan.apply(TestIndex.block("stone_brick_stairs").defaultBlockState()).getBlock());
        // With no full block listed at all, the stairs' planks stand in for one.
        RecolourPlan stairs = RecolourPlan.of(palette("Stairs", "oak_stairs"), null, Recolour.Mode.SHADING, Face.NORTH, TintContext.none());
        assertEquals(TestIndex.block("oak_planks"), stairs.apply(TestIndex.block("white_concrete").defaultBlockState()).getBlock());
    }

    @Test
    void aWallIsNotGlazedWhenThePaletteHasSomethingSolid() throws Exception {
        RecolourPlan plan = RecolourPlan.of(palette("Lit glass", "glass", "sea_lantern"), null, Recolour.Mode.SHADING, Face.NORTH, TintContext.none());
        assertNull(plan.apply(TestIndex.block("white_concrete").defaultBlockState()));
        assertNull(plan.apply(TestIndex.block("black_concrete").defaultBlockState()));
        // With glass alone, a wall may become it.
        RecolourPlan glass = RecolourPlan.of(palette("Glass", "glass"), null, Recolour.Mode.SHADING, Face.NORTH, TintContext.none());
        assertEquals(TestIndex.block("glass"), glass.apply(TestIndex.block("black_concrete").defaultBlockState()).getBlock());
    }

    @Test
    void theScriptNamesAStateAsTheGameWritesIt() throws Exception {
        Palette logs = new Palette("Logs");
        // An imported palette may carry odd spacing the game's parser forgives.
        logs.entries.add(new PaletteEntry("minecraft:oak_log", "axis= x"));
        RecolourPlan plan = RecolourPlan.of(logs, null, Recolour.Mode.SHADING, Face.NORTH, TintContext.none());
        AxiomScript.Remap row = plan.rows().getFirst();
        assertEquals("axis=x", row.to().properties());
        assertEquals(PaletteBlocks.state(row.to()), plan.apply(TestIndex.block(row.from().replace("minecraft:", "")).defaultBlockState()));
    }
}
