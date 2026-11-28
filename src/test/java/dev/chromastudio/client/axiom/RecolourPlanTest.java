package dev.chromastudio.client.axiom;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
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
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoorHingeSide;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.block.state.properties.Half;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.level.material.FluidState;
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
        blocks.put("oak_door", "block/oak_door_bottom");
        blocks.put("spruce_planks", "block/spruce_planks");
        blocks.put("spruce_door", "block/spruce_door_bottom");
        blocks.put("bedrock", "block/bedrock");
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
        assertEquals(TestIndex.block("red_sandstone_stairs"), plan.apply(TestIndex.block("deepslate_tile_stairs").defaultBlockState()).getBlock());
        assertEquals(TestIndex.block("terracotta"), plan.apply(TestIndex.block("deepslate_tiles").defaultBlockState()).getBlock());
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
        assertNull(plan.apply(TestIndex.block("torch").defaultBlockState()));
        assertNull(plan.apply(TestIndex.block("lantern").defaultBlockState()));
    }

    @Test
    void aFromPaletteChangesItsOwnBlocksGroundIncluded() throws Exception {
        RecolourPlan from = RecolourPlan.of(desert(), palette("Garden", "grass_block", "stone_bricks"), Recolour.Mode.SHADING, Face.NORTH, TintContext.none());
        assertNotNull(from.apply(TestIndex.block("grass_block").defaultBlockState()));
        assertNull(from.apply(TestIndex.block("cobblestone").defaultBlockState()));
        assertNotNull(from.apply(TestIndex.block("stone_brick_stairs").defaultBlockState()));
        assertEquals(4, from.size(), from.rows().toString());
    }

    @Test
    void bedrockIsTheGroundSoOnlyAFromPaletteChangesIt() throws Exception {
        BlockState bedrock = TestIndex.block("bedrock").defaultBlockState();
        for (Recolour.Mode mode : Recolour.Mode.values()) {
            RecolourPlan plan = plan(mode);
            assertNull(plan.apply(bedrock), mode.toString());
            assertTrue(plan.rows().stream().noneMatch(r -> r.from().equals("minecraft:bedrock")), mode.toString());
        }
        assertNotNull(RecolourPlan.of(desert(), palette("Floor", "bedrock"), Recolour.Mode.SHADING, Face.NORTH, TintContext.none()).apply(bedrock));
    }

    @Test
    void aShapeThePaletteListsBringsNoFullBlockOfItsOwn() throws Exception {
        RecolourPlan plan = RecolourPlan.of(palette("Oak", "oak_log", "oak_stairs"), null, Recolour.Mode.SHADING, Face.NORTH, TintContext.none());
        assertEquals(TestIndex.block("oak_log"), plan.apply(TestIndex.block("white_concrete").defaultBlockState()).getBlock());
        assertEquals(TestIndex.block("oak_stairs"), plan.apply(TestIndex.block("stone_brick_stairs").defaultBlockState()).getBlock());
        RecolourPlan stairs = RecolourPlan.of(palette("Stairs", "oak_stairs"), null, Recolour.Mode.SHADING, Face.NORTH, TintContext.none());
        assertEquals(TestIndex.block("oak_planks"), stairs.apply(TestIndex.block("white_concrete").defaultBlockState()).getBlock());
    }

    @Test
    void aWallIsNotGlazedWhenThePaletteHasSomethingSolid() throws Exception {
        RecolourPlan plan = RecolourPlan.of(palette("Lit glass", "glass", "sea_lantern"), null, Recolour.Mode.SHADING, Face.NORTH, TintContext.none());
        assertNull(plan.apply(TestIndex.block("white_concrete").defaultBlockState()));
        assertNull(plan.apply(TestIndex.block("black_concrete").defaultBlockState()));
        RecolourPlan glass = RecolourPlan.of(palette("Glass", "glass"), null, Recolour.Mode.SHADING, Face.NORTH, TintContext.none());
        assertEquals(TestIndex.block("glass"), glass.apply(TestIndex.block("black_concrete").defaultBlockState()).getBlock());
    }

    @Test
    void theScriptNamesAStateAsTheGameWritesIt() throws Exception {
        Palette logs = new Palette("Logs");
        logs.entries.add(new PaletteEntry("minecraft:oak_log", "axis= x"));
        RecolourPlan plan = RecolourPlan.of(logs, null, Recolour.Mode.SHADING, Face.NORTH, TintContext.none());
        AxiomScript.Remap row = plan.rows().getFirst();
        assertEquals("axis=x", row.to().properties());
        assertEquals(PaletteBlocks.state(row.to()), plan.apply(TestIndex.block(row.from().replace("minecraft:", "")).defaultBlockState()));
    }

    @Test
    void blocksThePaletteHasAreKept() throws Exception {
        Palette logs = new Palette("Logs");
        logs.entries.add(new PaletteEntry("minecraft:oak_log", "axis=x"));
        logs.entries.add(new PaletteEntry("minecraft:stone_bricks", ""));
        RecolourPlan plan = RecolourPlan.of(logs, null, Recolour.Mode.SHADING, Face.NORTH, TintContext.none());
        for (String path : List.of("oak_log", "stone_bricks", "stone_brick_stairs")) {
            for (BlockState here : TestIndex.block(path).getStateDefinition().getPossibleStates()) {
                assertTrue(plan.keeps(here), path);
                BlockState out = plan.apply(here);
                assertTrue(out == null || out == here, here + " to " + out);
            }
        }
        assertTrue(!plan.keeps(TestIndex.block("cobblestone").defaultBlockState()));
        assertTrue(plan.rows().stream().noneMatch(r -> r.from().equals("minecraft:oak_log") || r.from().equals("minecraft:stone_bricks")));
    }

    private static final class World implements BlockGetter {
        final Map<BlockPos, BlockState> blocks = new HashMap<>();

        World with(int x, int y, int z, BlockState state) {
            blocks.put(new BlockPos(x, y, z), state);
            return this;
        }

        @Override
        public BlockState getBlockState(BlockPos pos) { return blocks.getOrDefault(pos.immutable(), Blocks.AIR.defaultBlockState()); }

        @Override
        public FluidState getFluidState(BlockPos pos) { return getBlockState(pos).getFluidState(); }

        @Override
        public BlockEntity getBlockEntity(BlockPos pos) { return null; }

        @Override
        public int getHeight() { return 384; }

        @Override
        public int getMinBuildHeight() { return -64; }
    }

    private static World door() {
        BlockState lower = TestIndex.block("oak_door").defaultBlockState().setValue(DoorBlock.FACING, Direction.EAST)
                .setValue(DoorBlock.HINGE, DoorHingeSide.RIGHT).setValue(DoorBlock.HALF, DoubleBlockHalf.LOWER);
        return new World().with(0, 0, 0, lower).with(0, 1, 0, lower.setValue(DoorBlock.HALF, DoubleBlockHalf.UPPER))
                .with(1, 0, 0, TestIndex.block("oak_planks").defaultBlockState());
    }

    private static RecolourPlan spruce() throws AxiomBridge.Refused {
        return RecolourPlan.of(palette("Spruce", "spruce_planks"), null, Recolour.Mode.SHADING, Face.NORTH, TintContext.none());
    }

    private static Map<BlockPos, BlockState> stroke(RecolourPlan plan, World world, RecolourPlan.Admit admit, BlockPos... reached) {
        Map<BlockPos, BlockState> out = new HashMap<>();
        for (BlockPos p : reached) plan.change(world, p.getX(), p.getY(), p.getZ(), admit, (x, y, z, s) -> out.put(new BlockPos(x, y, z), s));
        return out;
    }

    @Test
    void aStrokeThatReachesOneHalfOfADoorChangesBoth() throws Exception {
        RecolourPlan plan = spruce();
        World world = door();
        Block spruceDoor = TestIndex.block("spruce_door");
        for (BlockPos reached : List.of(new BlockPos(0, 1, 0), new BlockPos(0, 0, 0))) {
            Map<BlockPos, BlockState> out = stroke(plan, world, (ignored, ignored2, ignored3) -> true, reached);
            assertEquals(Set.of(new BlockPos(0, 0, 0), new BlockPos(0, 1, 0)), out.keySet(), "reaching " + reached);
            for (Map.Entry<BlockPos, BlockState> e : out.entrySet()) {
                BlockState was = world.getBlockState(e.getKey());
                assertEquals(spruceDoor, e.getValue().getBlock());
                assertEquals(was.getValue(DoorBlock.HALF), e.getValue().getValue(DoorBlock.HALF));
                assertEquals(Direction.EAST, e.getValue().getValue(DoorBlock.FACING));
                assertEquals(DoorHingeSide.RIGHT, e.getValue().getValue(DoorBlock.HINGE));
                assertEquals(e.getValue(), plan.apply(world, e.getKey().getX(), e.getKey().getY(), e.getKey().getZ(), (ignored, ignored2, ignored3) -> true));
            }
        }
        assertEquals(Set.of(new BlockPos(1, 0, 0)), stroke(plan, world, (ignored, ignored2, ignored3) -> true, new BlockPos(1, 0, 0)).keySet());
    }

    @Test
    void aDoorWhoseOtherHalfIsKeptOutStaysAsItIs() throws Exception {
        RecolourPlan plan = spruce();
        World world = door();
        RecolourPlan.Admit upperOnly = (x, y, z) -> y == 1;
        assertEquals(Map.of(), stroke(plan, world, upperOnly, new BlockPos(0, 1, 0), new BlockPos(0, 0, 0)));
        assertNull(plan.apply(world, 0, 1, 0, upperOnly));
        assertNull(plan.apply(world, 0, 0, 0, upperOnly));
        World half = new World().with(0, 1, 0, world.getBlockState(new BlockPos(0, 1, 0)));
        assertEquals(Set.of(new BlockPos(0, 1, 0)), stroke(plan, half, (ignored, ignored2, ignored3) -> true, new BlockPos(0, 1, 0)).keySet());
        assertEquals(0, RecolourPlan.otherHalf(new World(), BlockPos.ZERO, TestIndex.block("oak_stairs").defaultBlockState()));
    }

    @Test
    void theScriptChangesADoorsOtherHalfOnlyWhenItHasADoor() throws Exception {
        List<AxiomScript.Remap> rows = spruce().rows();
        assertTrue(rows.stream().anyMatch(r -> r.from().equals("minecraft:oak_door") && r.twoHigh()), rows.toString());
        assertTrue(rows.stream().filter(r -> !r.from().endsWith("_door")).noneMatch(AxiomScript.Remap::twoHigh), rows.toString());
        assertTrue(AxiomScript.recolour("Spruce", rows).contains("setBlock(x, oy, z, put)"));
        assertFalse(AxiomScript.recolour("Desert", plan(Recolour.Mode.SHADING).rows()).contains("setBlock"));
    }
}
