package dev.chromastudio.client.axiom;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.chromastudio.client.action.PaletteBlocks;
import dev.chromastudio.client.index.BlockIndex;
import dev.chromastudio.client.index.Entry;
import dev.chromastudio.client.index.Kind;
import dev.chromastudio.client.index.TestIndex;
import dev.chromastudio.client.index.TintContext;
import dev.chromastudio.core.color.Face;
import dev.chromastudio.core.palette.BuildSearch;
import dev.chromastudio.core.palette.Palette;
import dev.chromastudio.core.palette.PaletteCodec;
import dev.chromastudio.core.palette.PaletteEntry;
import dev.chromastudio.core.palette.Recolour;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.SlabType;
import net.minecraft.world.level.material.FluidState;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/** The palette brush's rules that need no Axiom: the build it finds for itself, and the blocks Scatter places. */
class PaletteBrushTest {
    @BeforeAll
    static void loadGame() {
        TestIndex.loadGame();
        Map<String, String> blocks = new LinkedHashMap<>();
        for (String stone : List.of("stone_brick", "sandstone", "red_sandstone", "cobblestone")) {
            String full = stone.endsWith("_brick") ? stone + "s" : stone;
            blocks.put(full, "block/" + full);
            blocks.put(stone + "_stairs", "block/" + full);
            blocks.put(stone + "_slab", "block/" + full);
        }
        for (String full : List.of("deepslate_tiles", "smooth_stone", "andesite", "polished_andesite", "terracotta",
                "cut_sandstone", "chiseled_sandstone", "glass", "sand", "gravel", "oak_planks")) {
            blocks.put(full, "block/" + full);
        }
        // Every colour of concrete and terracotta, so that the game's blocks run from black to white as they do in play.
        for (String colour : List.of("white", "light_gray", "gray", "black", "brown", "red", "orange", "yellow", "lime", "green", "cyan",
                "light_blue", "blue", "purple", "magenta", "pink")) {
            blocks.put(colour + "_concrete", "block/" + colour + "_concrete");
            blocks.put(colour + "_terracotta", "block/" + colour + "_terracotta");
        }
        blocks.put("smooth_sandstone", "block/sandstone_top");
        blocks.put("oak_leaves", "block/oak_leaves");
        blocks.put("lantern", "block/lantern");
        blocks.put("white_carpet", "block/white_wool");
        // The blocks of the stone house and of the Desert, Nether and Medieval presets.
        Map<String, String> shapes = Map.ofEntries(Map.entry("deepslate_tile", "deepslate_tiles"), Map.entry("smooth_sandstone", "sandstone_top"),
                Map.entry("cut_sandstone", "cut_sandstone"), Map.entry("cut_red_sandstone", "cut_red_sandstone"), Map.entry("birch", "birch_planks"),
                Map.entry("nether_brick", "nether_bricks"), Map.entry("red_nether_brick", "red_nether_bricks"), Map.entry("blackstone", "blackstone"),
                Map.entry("polished_blackstone_brick", "polished_blackstone_bricks"), Map.entry("crimson", "crimson_planks"),
                Map.entry("andesite", "andesite"), Map.entry("mossy_stone_brick", "mossy_stone_bricks"), Map.entry("spruce", "spruce_planks"),
                Map.entry("smooth_stone", "smooth_stone"), Map.entry("polished_andesite", "polished_andesite"));
        shapes.forEach((material, texture) -> {
            for (String shape : List.of("_stairs", "_slab", "_wall", "_fence")) {
                if (BuiltInRegistries.BLOCK.containsKey(Identifier.withDefaultNamespace(material + shape))) {
                    blocks.putIfAbsent(material + shape, "block/" + texture);
                }
            }
        });
        for (String full : List.of("cracked_stone_bricks", "deepslate_bricks", "yellow_terracotta",
                "birch_planks", "cut_red_sandstone", "nether_bricks", "red_nether_bricks", "netherrack", "blackstone", "polished_blackstone_bricks",
                "crimson_planks", "nether_wart_block", "shroomlight", "gilded_blackstone", "mossy_stone_bricks", "spruce_planks", "white_terracotta")) {
            blocks.putIfAbsent(full, "block/" + full);
        }
        blocks.put("basalt", "block/basalt_side");
        blocks.put("crimson_stem", "block/crimson_stem");
        blocks.put("magma_block", "block/magma");
        blocks.put("stripped_spruce_log", "block/stripped_spruce_log");
        blocks.put("dark_oak_log", "block/dark_oak_log");
        blocks.put("cobblestone_wall", "block/cobblestone");
        TestIndex.index(blocks);
    }

    /** A few blocks in an otherwise empty world. */
    static final class World implements BlockGetter {
        final Map<BlockPos, BlockState> blocks = new HashMap<>();

        void put(int x, int y, int z, Block b) { blocks.put(new BlockPos(x, y, z), b.defaultBlockState()); }

        @Override
        public BlockState getBlockState(BlockPos pos) { return blocks.getOrDefault(pos, Blocks.AIR.defaultBlockState()); }

        @Override
        public FluidState getFluidState(BlockPos pos) { return getBlockState(pos).getFluidState(); }

        @Override
        public BlockEntity getBlockEntity(BlockPos pos) { return null; }

        @Override
        public int getHeight() { return 384; }

        @Override
        public int getMinY() { return -64; }
    }

    private static Block block(String path) { return TestIndex.block(path); }

    /**
     * A grey stone house, six by six and four high, on gravel, which stands for the ground as the tests have no tags:
     * a cobblestone footing, stone brick and andesite walls, deepslate corners, a smooth stone band, glass windows, a
     * torch inside and a roof of stairs and slabs. A sandstone shed stands apart on the same ground.
     */
    private static World house() {
        World w = new World();
        for (int x = -4; x <= 14; x++) for (int z = -4; z <= 9; z++) w.put(x, 0, z, Blocks.GRAVEL);
        for (int x = 0; x <= 5; x++) for (int z = 0; z <= 5; z++) {
            boolean edgeX = x == 0 || x == 5, edgeZ = z == 0 || z == 5;
            if (!edgeX && !edgeZ) continue;
            for (int y = 1; y <= 4; y++) {
                String b = edgeX && edgeZ ? "deepslate_tiles" : y == 1 ? "cobblestone" : y == 4 ? "smooth_stone"
                        : y == 3 && (x == 2 || z == 2) ? "glass" : (x + y + z) % 3 == 0 ? "andesite" : (x + y + z) % 3 == 1 ? "polished_andesite" : "stone_bricks";
                w.put(x, y, z, block(b));
            }
        }
        for (int x = 0; x <= 5; x++) for (int z = 0; z <= 5; z++) w.put(x, 5, z, block(z == 0 || z == 5 ? "stone_brick_stairs" : "stone_brick_slab"));
        w.put(2, 1, 2, Blocks.TORCH);
        w.put(12, 1, 2, block("sandstone"));
        return w;
    }

    /**
     * A larger stone house, built as a player would: a cobblestone footing, walls of stone bricks mixed with cracked
     * stone bricks and andesite, deepslate brick corners, a smooth stone band, glass windows with smooth stone slab
     * sills, a polished andesite floor, a gabled deepslate tile roof and a cobblestone chimney, on ground.
     */
    private static World stoneHouse() {
        World w = new World();
        for (int x = 15; x <= 33; x++) for (int z = 4; z <= 22; z++) w.put(x, -61, z, Blocks.GRAVEL);
        for (int x = 20; x <= 28; x++) for (int z = 10; z <= 16; z++) w.put(x, -61, z, block("polished_andesite"));
        String[] mix = {"stone_bricks", "stone_bricks", "cracked_stone_bricks", "stone_bricks", "andesite", "stone_bricks", "stone_bricks",
                "cracked_stone_bricks", "andesite"};
        for (int y = -60; y <= -56; y++) for (int x = 20; x <= 28; x++) for (int z = 10; z <= 16; z++) {
            boolean edgeX = x == 20 || x == 28, edgeZ = z == 10 || z == 16;
            if (!edgeX && !edgeZ) continue;
            w.put(x, y, z, block(edgeX && edgeZ ? "deepslate_bricks" : y == -60 ? "cobblestone" : y == -56 ? "smooth_stone"
                    : mix[Math.floorMod(x * 7 + y * 3 + z * 5, mix.length)]));
        }
        for (int y = -60; y <= -58; y++) w.blocks.remove(new BlockPos(24, y, 10));
        w.blocks.put(new BlockPos(24, -60, 9), block("stone_brick_stairs").defaultBlockState().setValue(StairBlock.FACING, Direction.SOUTH));
        for (int x : new int[] {21, 22, 26, 27}) {
            for (int y = -58; y <= -57; y++) {
                w.put(x, y, 10, Blocks.GLASS);
                w.put(x, y, 16, Blocks.GLASS);
            }
            w.blocks.put(new BlockPos(x, -59, 9), block("smooth_stone_slab").defaultBlockState().setValue(SlabBlock.TYPE, SlabType.TOP));
        }
        for (int y = -58; y <= -57; y++) for (int z = 12; z <= 14; z++) {
            w.put(20, y, z, Blocks.GLASS);
            w.put(28, y, z, Blocks.GLASS);
        }
        for (int i = 0; i < 4; i++) {
            int y = -55 + i;
            for (int x = 19; x <= 29; x++) {
                w.blocks.put(new BlockPos(x, y, 9 + i), block("deepslate_tile_stairs").defaultBlockState().setValue(StairBlock.FACING, Direction.SOUTH));
                w.blocks.put(new BlockPos(x, y, 17 - i), block("deepslate_tile_stairs").defaultBlockState().setValue(StairBlock.FACING, Direction.NORTH));
            }
            for (int z = 10 + i; z <= 16 - i; z++) {
                w.put(20, y, z, block("stone_bricks"));
                w.put(28, y, z, block("stone_bricks"));
            }
        }
        for (int x = 19; x <= 29; x++) w.put(x, -52, 13, block("deepslate_tile_slab"));
        for (int y = -55; y <= -50; y++) w.put(26, y, 15, block("cobblestone"));
        w.put(26, -49, 15, block("cobblestone_wall"));
        return w;
    }

    /** The OKLab lightness of the full block a block's material has, as the index reads it. */
    private static double lightness(BlockState state) {
        BlockIndex index = BlockIndex.get();
        Entry e = index.entryOf(state);
        for (Entry m : index.materialSet(e)) if (m.kind() == Kind.FULL) e = m;
        return index.colourOf(e.state(), Face.NORTH, TintContext.none()).orElseThrow().oklab().l();
    }

    private static Palette preset(String id) throws IOException {
        try (InputStream in = PaletteBrushTest.class.getResourceAsStream("/assets/chromastudio/presets/" + id + ".json")) {
            return PaletteCodec.fromJson(new String(in.readAllBytes(), StandardCharsets.UTF_8));
        }
    }

    @Test
    void theStoneHouseKeepsItsMaterialsAndPutsItsWallsOnAMainBlock() throws Exception {
        World w = stoneHouse();
        FoundBuild found = FoundBuild.from(w, new BlockPos(20, -58, 11));
        assertNotNull(found);
        List<String> full = List.of("deepslate_bricks", "cobblestone", "andesite", "polished_andesite", "cracked_stone_bricks", "stone_bricks", "smooth_stone");
        for (String id : List.of("desert", "nether", "medieval")) {
            Palette to = preset(id);
            RecolourPlan plan = RecolourPlan.of(to, found.kinds(), Recolour.Mode.SHADING, Face.NORTH, TintContext.none());
            Map<String, String> out = new LinkedHashMap<>();
            for (String b : full) {
                BlockState next = plan.apply(block(b).defaultBlockState());
                out.put(b, next == null ? b : BuiltInRegistries.BLOCK.getKey(next.getBlock()).getPath());
            }
            // With the build's own range and counts, the walls take a main block of the palette, cracked stone bricks
            // with them, and cobblestone, andesite and polished andesite never collapse into one.
            String walls = out.get("stone_bricks");
            // Unless the palette has cracked stone bricks of its own, which then stay, as Medieval's do.
            if (to.entries.stream().noneMatch(e -> e.blockId().equals("minecraft:cracked_stone_bricks"))) assertEquals(walls, out.get("cracked_stone_bricks"), id + " " + out);
            // One of the blocks the preset means for the bulk of a build, weighted 2 or more, and never an accent.
            int weight = to.entries.stream().filter(e -> e.blockId().equals("minecraft:" + walls)).mapToInt(PaletteEntry::weight).max().orElse(0);
            assertTrue(weight >= 2, id + " " + out);
            assertTrue(new HashSet<>(List.of(out.get("cobblestone"), out.get("andesite"), out.get("polished_andesite"))).size() >= 2, id + " " + out);
            assertTrue(new HashSet<>(out.values()).size() >= 5, id + " " + out);
            // The dark roof's stairs stay darker than the walls, though stairs are matched among the blocks that have them.
            BlockState roof = plan.apply(block("deepslate_tile_stairs").defaultBlockState()), wall = plan.apply(block("stone_bricks").defaultBlockState());
            if (roof != null && wall != null) assertTrue(lightness(roof) <= lightness(wall), id + " " + roof + " " + wall);
        }
    }

    @Test
    void aBuildIsItsSolidBlocksAndNotTheGroundOrTheShedBesideIt() {
        World w = house();
        assertNull(FoundBuild.from(w, new BlockPos(0, 0, 0)), "the ground is no build");
        FoundBuild found = FoundBuild.from(w, new BlockPos(0, 2, 1));
        assertNotNull(found);
        assertFalse(found.capped());
        Set<Block> kinds = new HashSet<>();
        for (long p : found.blocks()) kinds.add(w.getBlockState(new BlockPos(BuildSearch.x(p), BuildSearch.y(p), BuildSearch.z(p))).getBlock());
        assertEquals(Set.of(block("deepslate_tiles"), block("cobblestone"), block("smooth_stone"), block("glass"), block("andesite"),
                block("polished_andesite"), block("stone_bricks"), block("stone_brick_stairs"), block("stone_brick_slab")), kinds);
        // 20 wall columns of 4, and a 6 by 6 roof.
        assertEquals(20 * 4 + 36, found.blocks().length);
        assertEquals(kinds.size(), found.kinds().entries.size());
        int cobblestone = found.kinds().entries.stream().filter(e -> e.blockId().equals("minecraft:cobblestone")).findFirst().orElseThrow().weight();
        assertEquals(16, cobblestone, "each kind weighs as many blocks as the build has of it");
    }

    @Test
    void theBuildsOwnRangeSpreadsItOverTheWholePalette() throws Exception {
        World w = house();
        FoundBuild found = FoundBuild.from(w, new BlockPos(0, 2, 1));
        assertNotNull(found);
        Palette desert = new Palette("Desert");
        for (String id : List.of("smooth_sandstone", "sandstone", "cut_sandstone", "chiseled_sandstone", "orange_terracotta", "terracotta",
                "red_sandstone", "oak_planks")) {
            desert.entries.add(new PaletteEntry("minecraft:" + id, ""));
        }
        BlockState lightest = block("smooth_stone").defaultBlockState(), darkest = block("deepslate_tiles").defaultBlockState();
        RecolourPlan own = RecolourPlan.of(desert, found.kinds(), Recolour.Mode.SHADING, Face.NORTH, TintContext.none());
        RecolourPlan any = RecolourPlan.of(desert, null, Recolour.Mode.SHADING, Face.NORTH, TintContext.none());
        // The house's darkest and lightest blocks become the palette's, so its shading spans the whole palette.
        assertEquals(block("orange_terracotta"), own.apply(darkest).getBlock());
        assertEquals(block("smooth_sandstone"), own.apply(lightest).getBlock());
        // Against every block in the game, the grey house sits in the middle of the range and misses the palette's lightest.
        assertTrue(any.apply(lightest).getBlock() != block("smooth_sandstone"), any.apply(lightest).toString());
        for (long p : found.blocks()) {
            BlockState next = any.apply(w.getBlockState(new BlockPos(BuildSearch.x(p), BuildSearch.y(p), BuildSearch.z(p))));
            assertTrue(next == null || next.getBlock() != block("smooth_sandstone"), String.valueOf(next));
        }
    }

    @Test
    void scatterPlacesWholeBlocksAndLeavesThatStay() {
        Palette p = new Palette("Mixed");
        p.entries.add(new PaletteEntry("minecraft:stone_bricks", "", 2));
        p.entries.add(new PaletteEntry("minecraft:stone_brick_stairs", "facing=east", 3));
        p.entries.add(new PaletteEntry("minecraft:oak_leaves", ""));
        p.entries.add(new PaletteEntry("minecraft:lantern", ""));
        p.entries.add(new PaletteEntry("minecraft:white_carpet", ""));
        p.entries.add(new PaletteEntry("minecraft:sand", ""));
        ScatterBlocks s = ScatterBlocks.of(p);
        assertEquals(2, s.blocks().size(), s.toString());
        assertEquals(new ScatterBlocks.Placed(block("stone_bricks").defaultBlockState(), 5), s.blocks().get(0), "stairs scatter as their full block");
        BlockState leaves = s.blocks().get(1).state();
        assertEquals(block("oak_leaves"), leaves.getBlock());
        assertTrue(leaves.getValue(BlockStateProperties.PERSISTENT), "leaves placed by the brush don't decay");
        assertEquals(List.of("minecraft:lantern", "minecraft:white_carpet", "minecraft:sand"), s.leftOut().stream().map(PaletteEntry::blockId).toList());
        assertEquals(PaletteBlocks.of(block("stone_bricks").defaultBlockState()), new PaletteEntry("minecraft:stone_bricks", ""));
    }
}
