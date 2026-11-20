package dev.chromastudio.client.axiom;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.chromastudio.client.action.PaletteBlocks;
import dev.chromastudio.client.axiom.PaletteBrushTest.World;
import dev.chromastudio.client.index.BlockIndex;
import dev.chromastudio.client.index.TestIndex;
import dev.chromastudio.client.index.TintContext;
import dev.chromastudio.core.color.Face;
import dev.chromastudio.core.palette.AxiomScript;
import dev.chromastudio.core.palette.BuildSample;
import dev.chromastudio.core.palette.BuildSearch;
import dev.chromastudio.core.palette.Palette;
import dev.chromastudio.core.palette.PaletteEntry;
import dev.chromastudio.core.palette.Recolour;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * The recolours a builder tried in game and found wrong, with the game's own textures: a build of one main material
 * into a palette made mostly of that material, and a cottage into a palette sampled from it or from its own blocks.
 */
class RecolourCasesTest {
    @BeforeAll
    static void loadGame() {
        TestIndex.loadGame();
        Map<String, String> blocks = new LinkedHashMap<>();
        for (String b : List.of("stone_bricks", "mossy_stone_bricks", "cracked_stone_bricks", "chiseled_stone_bricks", "andesite", "polished_andesite",
                "oxidized_copper", "weathered_cut_copper", "prismarine", "sand", "dark_oak_planks", "terracotta", "red_terracotta", "yellow_terracotta",
                "cobblestone", "smooth_stone", "spruce_planks")) {
            blocks.put(b, "block/" + b);
        }
        blocks.put("stone_brick_stairs", "block/stone_bricks");
        blocks.put("stone_brick_slab", "block/stone_bricks");
        blocks.put("stone_brick_wall", "block/stone_bricks");
        blocks.put("dark_oak_stairs", "block/dark_oak_planks");
        blocks.put("andesite_stairs", "block/andesite");
        blocks.put("glass_pane", "block/glass");
        blocks.put("spruce_door", "block/spruce_door_bottom");
        blocks.put("lantern", "block/lantern");
        TestIndex.index(blocks);
    }

    private static Block block(String path) { return TestIndex.block(path); }

    private static String id(Block b) { return BuiltInRegistries.BLOCK.getKey(b).getPath(); }

    /** The player's "Harbour wall": mostly stone bricks, with worn bricks and andesite, and copper, sea and wood accents. */
    private static Palette harbourWall() {
        Palette p = new Palette("Harbour wall");
        Object[][] entries = {{"stone_bricks", 5}, {"mossy_stone_bricks", 2}, {"cracked_stone_bricks", 2}, {"andesite", 2}, {"oxidized_copper", 1},
                {"weathered_cut_copper", 1}, {"prismarine", 1}, {"sand", 1}, {"dark_oak_planks", 1}, {"terracotta", 1}, {"red_terracotta", 1},
                {"yellow_terracotta", 1}};
        for (Object[] e : entries) p.entries.add(new PaletteEntry("minecraft:" + e[0], "", (Integer) e[1]));
        return p;
    }

    /** A solid block of stone bricks, 20 by 10 by 20, capped with a slab of polished andesite. */
    private static World bigWall() {
        World w = new World();
        for (int x = 0; x < 20; x++) for (int y = 0; y < 10; y++) for (int z = 0; z < 20; z++) w.put(x, y, z, block("stone_bricks"));
        for (int x = 5; x < 15; x++) for (int z = 5; z < 15; z++) w.put(x, 10, z, block("polished_andesite"));
        return w;
    }

    /**
     * The cottage the builder recoloured: a cobblestone footing, stone brick walls with polished andesite corners and
     * a chiseled band, glass panes, a spruce floor and door, lanterns and a stone brick stair roof.
     */
    static World cottage() {
        World w = new World();
        for (int x = 0; x <= 10; x++) for (int z = 0; z <= 8; z++) {
            boolean edge = x == 0 || x == 10 || z == 0 || z == 8;
            w.put(x, 0, z, block(edge ? "cobblestone" : "spruce_planks"));
            if (!edge) continue;
            boolean corner = (x == 0 || x == 10) && (z == 0 || z == 8);
            for (int y = 1; y <= 4; y++) {
                boolean window = (y == 2 || y == 3) && (x == 2 || x == 3 || x == 7 || x == 8) && (z == 0 || z == 8);
                w.put(x, y, z, block(corner ? "polished_andesite" : y == 4 ? "chiseled_stone_bricks" : window ? "glass_pane" : "stone_bricks"));
            }
        }
        BlockState door = block("spruce_door").defaultBlockState().setValue(DoorBlock.FACING, Direction.NORTH);
        w.blocks.put(new BlockPos(5, 1, 0), door.setValue(DoorBlock.HALF, DoubleBlockHalf.LOWER));
        w.blocks.put(new BlockPos(5, 2, 0), door.setValue(DoorBlock.HALF, DoubleBlockHalf.UPPER));
        w.put(4, 3, 1, block("lantern"));
        for (int i = 0; i < 4; i++) {
            for (int x = -1; x <= 11; x++) {
                w.blocks.put(new BlockPos(x, 5 + i, i), block("stone_brick_stairs").defaultBlockState().setValue(StairBlock.FACING, Direction.SOUTH));
                w.blocks.put(new BlockPos(x, 5 + i, 8 - i), block("stone_brick_stairs").defaultBlockState().setValue(StairBlock.FACING, Direction.NORTH));
            }
            for (int z = i + 1; z <= 7 - i; z++) {
                w.put(0, 5 + i, z, block("stone_bricks"));
                w.put(10, 5 + i, z, block("stone_bricks"));
            }
        }
        for (int x = -1; x <= 11; x++) w.put(x, 9, 4, block("stone_brick_slab"));
        return w;
    }

    private static RecolourPlan plan(Palette to, Palette from) throws AxiomBridge.Refused {
        return RecolourPlan.of(to, from, Recolour.Mode.SHADING, Face.NORTH, TintContext.none());
    }

    /** What a block of the build becomes: its own block when the plan leaves it alone. */
    private static Block after(RecolourPlan plan, String path) {
        BlockState next = plan.apply(block(path).defaultBlockState());
        return next == null ? block(path) : next.getBlock();
    }

    /** The cottage sampled as the sampler tool samples it: every block of the build by what it counts as. */
    private static Palette sampled(World w, FoundBuild found) {
        BuildSample sample = new BuildSample();
        for (long p : found.blocks()) {
            int x = BuildSearch.x(p), y = BuildSearch.y(p), z = BuildSearch.z(p);
            sample.add(x, y, z, SampleKind.of(BlockIndex.get(), w.getBlockState(new BlockPos(x, y, z)), false));
        }
        Palette out = new Palette("Stone cottage");
        out.entries.addAll(sample.entries());
        return out;
    }

    @Test
    void aBuildOfTheMainMaterialStaysOnIt() throws Exception {
        World w = bigWall();
        FoundBuild found = FoundBuild.from(w, new BlockPos(0, 0, 0));
        assertNotNull(found);
        RecolourPlan plan = plan(harbourWall(), found.kinds());
        // The palette is mostly stone bricks, so the stone brick build stays stone bricks, not the palette's darkest block.
        assertNull(plan.apply(block("stone_bricks").defaultBlockState()));
        // Its lighter cap stays a grey lighter than the walls: andesite, not wood, copper or the sea.
        Block cap = after(plan, "polished_andesite");
        assertEquals("andesite", id(cap));
        // Scaled down as a palette sampled from it would be, the build recolours the same.
        Palette scaled = new Palette("scaled");
        for (PaletteEntry e : found.kinds().entries) scaled.entries.add(e.withWeight(Math.max(1, e.weight() * BuildSample.MAX_WEIGHT / 4000)));
        RecolourPlan small = plan(harbourWall(), scaled);
        for (String b : List.of("stone_bricks", "polished_andesite")) assertEquals(after(plan, b), after(small, b), b);
    }

    @Test
    void aCottageKeepsTheBlocksThePaletteHasAndItsShadingAroundThem() throws Exception {
        World w = cottage();
        FoundBuild found = FoundBuild.from(w, new BlockPos(0, 1, 0));
        assertNotNull(found);
        Palette sampled = sampled(w, found);
        for (Palette from : List.of(found.kinds(), sampled)) {
            RecolourPlan plan = plan(harbourWall(), from);
            String what = from.name;
            assertEquals("stone_bricks", id(after(plan, "stone_bricks")), what);
            assertEquals("stone_brick_stairs", id(after(plan, "stone_brick_stairs")), what);
            // Polished andesite, cobblestone and smooth stone are greys a little lighter than the walls; none turns to
            // wood or terracotta.
            for (String grey : List.of("polished_andesite", "cobblestone", "smooth_stone")) {
                Block to = after(plan, grey);
                assertTrue(!id(to).contains("terracotta") && !id(to).contains("planks"), what + ": " + grey + " to " + id(to));
            }
        }
    }

    @Test
    void thisBuildASampleOfItAndTheScriptGiveTheSameBlocks() throws Exception {
        World w = cottage();
        FoundBuild found = FoundBuild.from(w, new BlockPos(0, 1, 0));
        assertNotNull(found);
        for (Palette to : List.of(harbourWall(), palette("Warm", "terracotta", "red_terracotta", "yellow_terracotta", "dark_oak_planks", "sand"))) {
            RecolourPlan build = plan(to, found.kinds()), sample = plan(to, sampled(w, found));
            List<AxiomScript.Remap> rows = sample.rows();
            for (long p : found.blocks()) {
                BlockState here = w.getBlockState(new BlockPos(BuildSearch.x(p), BuildSearch.y(p), BuildSearch.z(p)));
                BlockState a = build.apply(here), b = sample.apply(here);
                assertEquals(a, b, to.name + ": " + here);
                String row = rows.stream().filter(r -> r.from().equals(PaletteBlocks.of(here.getBlock().defaultBlockState()).blockId()))
                        .map(r -> r.to().blockId()).findFirst().orElse(null);
                assertEquals(b == null ? null : PaletteBlocks.of(b.getBlock().defaultBlockState()).blockId(), row, to.name + ": " + here);
            }
        }
    }

    private static Palette palette(String name, String... ids) {
        Palette p = new Palette(name);
        for (String id : ids) p.entries.add(new PaletteEntry("minecraft:" + id, ""));
        return p;
    }

    @Test
    void aPaletteOfTheBuildsOwnBlocksLeavesItAsItIs() throws Exception {
        World w = cottage();
        FoundBuild found = FoundBuild.from(w, new BlockPos(0, 1, 0));
        assertNotNull(found);
        Palette own = sampled(w, found);
        for (Palette from : List.of(found.kinds(), own)) {
            RecolourPlan plan = plan(own, from);
            assertEquals(0, plan.size(), from.name + ": " + plan.rows());
        }
    }
}
