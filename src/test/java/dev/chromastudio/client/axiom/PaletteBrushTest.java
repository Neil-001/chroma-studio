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
import dev.chromastudio.core.palette.BuildSearch;
import dev.chromastudio.core.palette.Palette;
import dev.chromastudio.core.palette.PaletteEntry;
import dev.chromastudio.core.palette.Recolour;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
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
        TestIndex.index(blocks);
    }

    /** A few blocks in an otherwise empty world. */
    private static final class World implements BlockGetter {
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
     * A grey stone house, six by six and four high, on gravel, which stands for the ground as the tests have no tags: a cobblestone footing, stone brick and andesite walls,
     * deepslate corners, a smooth stone band, glass windows, a torch inside and a roof of stairs and slabs. A sandstone
     * shed stands apart on the same ground.
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
        ScatterBlocks s = ScatterBlocks.of(p);
        assertEquals(2, s.blocks().size(), s.toString());
        assertEquals(new ScatterBlocks.Placed(block("stone_bricks").defaultBlockState(), 5), s.blocks().get(0), "stairs scatter as their full block");
        BlockState leaves = s.blocks().get(1).state();
        assertEquals(block("oak_leaves"), leaves.getBlock());
        assertTrue(leaves.getValue(BlockStateProperties.PERSISTENT), "leaves placed by the brush don't decay");
        assertEquals(List.of("minecraft:lantern", "minecraft:white_carpet"), s.leftOut().stream().map(PaletteEntry::blockId).toList());
        assertEquals(PaletteBlocks.of(block("stone_bricks").defaultBlockState()), new PaletteEntry("minecraft:stone_bricks", ""));
    }
}
