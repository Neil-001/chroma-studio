package dev.chromastudio.client.index;

import com.mojang.blaze3d.platform.NativeImage;
import dev.chromastudio.client.ModSettings;
import dev.chromastudio.core.color.Badge;
import dev.chromastudio.core.color.Color;
import dev.chromastudio.core.color.Face;
import dev.chromastudio.core.color.Metric;
import dev.chromastudio.core.color.Oklch;
import dev.chromastudio.core.color.Scale;
import dev.chromastudio.core.color.Shading;
import dev.chromastudio.core.color.TextureStats;
import dev.chromastudio.core.color.Tone;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.minecraft.SharedConstants;
import net.minecraft.client.color.block.BlockColors;
import net.minecraft.client.color.block.BlockTintSource;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.Bootstrap;
import net.minecraft.server.packs.PackLocationInfo;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.VanillaPackResourcesBuilder;
import net.minecraft.server.packs.repository.PackSource;
import net.minecraft.server.packs.resources.MultiPackResourceManager;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.world.level.GrassColor;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Checks the index against the game itself: the vanilla textures read from the game's own resources, its block
 * classes and its tints. It loads Minecraft's registries and assets but no client and no window, so models aren't
 * baked: the entries here are put together by hand from texture names, and the dev client's
 * {@code /chroma debug} covers what the models say.
 */
class GameIndexTest {
    private static ResourceManager resources;
    private static final Map<String, Sampled> SAMPLED = new java.util.HashMap<>();

    @BeforeAll
    static void loadGame() throws Exception {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
        resources = new MultiPackResourceManager(PackType.CLIENT_RESOURCES, List.of(new VanillaPackResourcesBuilder()
                .exposeNamespace("minecraft").pushJarResources()
                .build(new PackLocationInfo("vanilla", Component.literal("vanilla"), PackSource.BUILT_IN, Optional.empty())).fullResources()));
        // The client loads the grass colours on a resource reload, which doesn't happen here.
        try (InputStream in = resources.open(Identifier.withDefaultNamespace("textures/colormap/grass.png")); NativeImage map = NativeImage.read(in)) {
            GrassColor.init(map.getPixels());
        }
    }

    /** A vanilla block by its id's path. Many of the game's own constants are grouped by colour, so this reads plainer. */
    private static Block block(String path) {
        return BuiltInRegistries.BLOCK.getOptional(Identifier.withDefaultNamespace(path)).orElseThrow(() -> new AssertionError("No block " + path));
    }

    private static Textures.Frame frame(String sprite) {
        Textures.Frame frame = Textures.read(resources, Identifier.withDefaultNamespace(sprite), true);
        assertNotNull(frame, sprite + " should be readable");
        return frame;
    }

    private static TextureStats stats(String sprite) { return frame(sprite).stats(); }

    private static Sampled sampled(String sprite) {
        return SAMPLED.computeIfAbsent(sprite, s -> new Sampled(frame(s)));
    }

    /** An entry whose faces are these textures, in the order down, up, north, south, west, east. One name does for all six. */
    private static Entry entry(BlockState state, boolean technical, String... sprites) {
        BlockState byDefault = state.getBlock().defaultBlockState();
        FaceLayers[] faces = new FaceLayers[6];
        for (int i = 0; i < 6; i++) {
            String sprite = sprites[sprites.length == 1 ? 0 : i];
            faces[i] = new FaceLayers(List.of(new FaceLayers.Layer("minecraft:" + sprite, sampled(sprite), -1, null, 1)));
        }
        String properties = state == byDefault ? "" : state.toString().replaceAll(".*\\[|]", "");
        return new Entry(state, BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString(), properties, technical, true, faces);
    }

    private static Entry entry(Block block, String... sprites) { return entry(block.defaultBlockState(), false, sprites); }

    /** Puts entries in the index, one list per block, and gives the index back. */
    private static BlockIndex index(List<List<Entry>> blocks) {
        Map<BlockState, Entry> byState = new IdentityHashMap<>();
        for (List<Entry> states : blocks) for (Entry e : states) byState.put(e.state(), e);
        BlockIndex.get().adopt(blocks, byState);
        return BlockIndex.get();
    }

    private static Query query() { return new Query().tint(TintContext.none()).scale(Scale.T16); }

    @Test
    void texturesGetTheBadgeTheyLookLike() {
        assertEquals(Badge.GRAINY, stats("block/stone").badge());
        assertEquals(Badge.FLAT, stats("block/white_concrete").badge());
        assertEquals(Badge.BUSY, stats("block/bookshelf").badge());
        assertEquals(Badge.GRAINY, stats("block/oak_planks").badge());
    }

    @Test
    void knownBlocksHaveTheirColours() {
        Oklch stone = stats("block/stone").mean(Scale.T1).oklch();
        assertEquals(Tone.NEUTRAL, Tone.of(stone), "stone is grey");
        assertEquals(0.59, stone.l(), 0.05);

        Oklch planks = stats("block/oak_planks").mean(Scale.T1).oklch();
        assertEquals(Tone.WARM, Tone.of(planks), "oak planks are warm");
        assertEquals(75, planks.h(), 15);

        Oklch red = stats("block/red_concrete").mean(Scale.T1).oklch();
        assertEquals(25, red.h(), 10, "red concrete is red");
        assertTrue(red.c() > 0.12, "and strongly so");

        TextureStats concrete = stats("block/white_concrete");
        assertEquals(1, concrete.coverage(), 1e-9);
        assertTrue(concrete.mean(Scale.T1).oklch().l() > 0.85);
    }

    @Test
    void grassIsGreyUntilItsBiomeTintsIt() {
        TextureStats top = stats("block/grass_block_top");
        assertTrue(top.mean(Scale.T1).oklch().c() < 0.01, "the texture is drawn in grey");

        BlockState grass = block("grass_block").defaultBlockState();
        BlockTintSource source = BlockColors.createDefault().getTintSource(grass, 0);
        assertEquals(TintContext.NO_TINT, TintContext.none().colour(source, grass));
        assertEquals(TintContext.NO_TINT, TintContext.HERE.colour(source, grass), "no world, no tint");
        int plains = TintContext.biome(Biomes.PLAINS).colour(source, grass);
        assertEquals(0x91BD59, plains, "the plains grass colour");
        int swamp = TintContext.biome(Biomes.SWAMP).colour(source, grass);
        assertNotEquals(plains, swamp);

        Oklch green = top.tinted(plains).mean(Scale.T1).oklch();
        assertEquals(130, green.h(), 15, "green in the plains");
        assertTrue(green.c() > 0.08);

        // The same through an entry: its top is tinted, its bottom is plain dirt.
        FaceLayers[] faces = new FaceLayers[6];
        for (int i = 0; i < 6; i++) {
            boolean up = i == Face.UP.ordinal();
            faces[i] = new FaceLayers(List.of(up
                    ? new FaceLayers.Layer("minecraft:block/grass_block_top", sampled("block/grass_block_top"), 0, source, 1)
                    : new FaceLayers.Layer("minecraft:block/dirt", sampled("block/dirt"), -1, null, 1)));
        }
        Entry entry = new Entry(grass, "minecraft:grass_block", "", false, true, faces);
        assertTrue(entry.tinted());
        assertFalse(entry.sameTextureAllRound());
        assertEquals(top.mean(Scale.T1), entry.face(Face.UP, TintContext.none()).mean(Scale.T1));
        assertEquals(top.tinted(plains).mean(Scale.T1), entry.face(Face.UP, TintContext.biome(Biomes.PLAINS)).mean(Scale.T1));
        assertSame(entry.face(Face.UP, TintContext.biome(Biomes.PLAINS)), entry.face(Face.UP, TintContext.biome(Biomes.PLAINS)), "kept per tint");
        assertEquals(plains, entry.tint(Face.UP, TintContext.biome(Biomes.PLAINS)));
        assertEquals(TintContext.NO_TINT, entry.tint(Face.DOWN, TintContext.biome(Biomes.PLAINS)));

        List<Integer> across = Tints.tintsAcross(entry, Face.UP);
        assertEquals(Tints.biomes().size(), across.size());
        assertEquals(plains, across.get(Tints.biomes().indexOf(Biomes.PLAINS)));
        assertEquals(across, Tints.tintsAcross(entry, Face.ANY));
        assertTrue(Tints.tintsAcross(entry, Face.DOWN).isEmpty());
        assertTrue(across.stream().distinct().count() > 5, "biomes differ");
    }

    @Test
    void anOverlayLiesOverTheTextureBelow() {
        BlockState grass = block("grass_block").defaultBlockState();
        BlockTintSource source = BlockColors.createDefault().getTintSource(grass, 0);
        FaceLayers side = new FaceLayers(List.of(
                new FaceLayers.Layer("minecraft:block/grass_block_side", sampled("block/grass_block_side"), -1, null, 1),
                new FaceLayers.Layer("minecraft:block/grass_block_side_overlay", sampled("block/grass_block_side_overlay"), 0, source, 1)));
        assertEquals("minecraft:block/grass_block_side+minecraft:block/grass_block_side_overlay#0", side.key());
        TextureStats plains = side.sample(grass, TintContext.biome(Biomes.PLAINS)).stats;
        assertEquals(1, plains.coverage(), 1e-9);
        // The top row is all fringe, so in the plains it is green, and the bottom row is all dirt.
        int[] pixels = plains.pixels(Scale.T16);
        assertEquals(130, Color.ofRgb(pixels[0]).oklch().h(), 20);
        assertEquals(Tone.WARM, Tone.of(Color.ofRgb(pixels[255]).oklch()));
        // Untinted, the fringe is grey.
        assertTrue(Color.ofRgb(side.sample(grass, TintContext.none()).stats.pixels(Scale.T16)[0]).oklch().c() < 0.01);
    }

    @Test
    void animatedTexturesAreAveragedAndFlagged() {
        Textures.Frame water = frame("block/water_still");
        assertTrue(water.animated());
        assertEquals(256, water.rgb().length);
        assertFalse(frame("block/stone").animated());
        // A flowing texture's frame is 32 pixels wide, and is brought down like any other.
        assertTrue(frame("block/water_flow").animated());
        // One frame alone is patchier than the average of them all.
        Textures.Frame first = Textures.read(resources, Identifier.withDefaultNamespace("block/water_still"), false);
        assertTrue(first.animated());
        assertTrue(first.stats().spread() > water.stats().spread());
        assertNull(Textures.read(resources, Identifier.withDefaultNamespace("block/no_such_texture"), true));
    }

    @Test
    void aSparseCellKeepsItsTrueWeight() {
        // One texel in 64 filled, as in a 128 pixel texture brought down to 16 cells: too faint for 8-bit alpha
        // to pass the stats' cutoff, yet it must still count for what it is.
        double[][] cells = new double[256][4];
        for (double[] cell : cells) {
            cell[0] = 1.0 / 64;
            cell[3] = 1.0 / 64;
        }
        Textures.Frame sparse = Textures.Frame.of(cells, false);
        assertEquals(1.0 / 64, sparse.stats().coverage(), 1e-4);
        assertEquals(0xFF0000, sparse.stats().mean(Scale.T1).rgb());
        // Half a cell and a sliver of one.
        for (double[] cell : cells) cell[3] = cell[0] = 0;
        cells[0][0] = cells[0][3] = 0.5;
        cells[1][0] = cells[1][3] = 0.001;
        assertEquals((0.5 + 0.001) / 256, Textures.Frame.of(cells, false).stats().coverage(), 1e-5);
        assertEquals(1, frame("block/stone").stats().coverage(), 0);
        assertEquals(180 / 255.0, frame("block/water_still").stats().coverage(), 0.01);
    }

    @Test
    void aFoldedStateKeepsItsOwnTint() {
        // Redstone dust's sixteen powers share their textures, so one entry stands for them all, but each has its tint.
        Block wire = block("redstone_wire");
        BlockTintSource source = BlockColors.createDefault().getTintSource(wire.defaultBlockState(), 0);
        FaceLayers[] faces = new FaceLayers[6];
        for (int i = 0; i < 6; i++) {
            faces[i] = new FaceLayers(List.of(new FaceLayers.Layer("minecraft:block/white_concrete", sampled("block/white_concrete"), 0, source, 1)));
        }
        Entry entry = new Entry(wire.defaultBlockState(), "minecraft:redstone_wire", "", false, false, faces);
        BlockState powered = wire.defaultBlockState().setValue(BlockStateProperties.POWER, 15);
        Map<BlockState, Entry> byState = new IdentityHashMap<>(Map.of(entry.state(), entry, powered, entry));
        BlockIndex.get().adopt(List.of(List.of(entry)), byState);
        Color white = stats("block/white_concrete").mean(Scale.T1);
        TintContext ownTint = (tintSource, state) -> tintSource.color(state) & 0xFFFFFF;
        assertEquals(Optional.of(white.tinted(source.color(powered))), BlockIndex.get().colourOf(powered, Face.UP, ownTint));
        assertEquals(Optional.of(white.tinted(source.color(powered))), BlockIndex.get().colourOf(powered, Face.ANY, ownTint));
        assertEquals(Optional.of(white.tinted(source.color(wire.defaultBlockState()))), BlockIndex.get().colourOf(wire.defaultBlockState(), Face.UP, ownTint));
        assertNotEquals(source.color(powered), source.color(wire.defaultBlockState()));
    }

    @Test
    void theCacheGivesBackWhatWasSaved(@TempDir Path folder) throws Exception {
        Map<String, Textures.Frame> frames = Map.of("minecraft:block/stone", frame("block/stone"), "minecraft:block/water_still", frame("block/water_still"));
        IndexOptions options = IndexOptions.current();
        Path file = TextureCache.file(folder, List.of("vanilla"), List.of("minecraft 26.3"), options);
        assertNotEquals(file, TextureCache.file(folder, List.of("vanilla", "file/pack.zip"), List.of("minecraft 26.3"), options));
        assertNotEquals(file, TextureCache.file(folder, List.of("vanilla"), List.of("minecraft 26.3", "other 1.0"), options));
        assertEquals(file, TextureCache.file(folder, List.of("vanilla"), List.of("minecraft 26.3"), options));

        Path stale = folder.resolve("index-0000000000000000.bin");
        Files.writeString(stale, "old");
        TextureCache.save(file, frames);
        TextureCache.prune(file);
        assertFalse(Files.exists(stale), "other sets' files go");
        Map<String, Textures.Frame> loaded = TextureCache.load(file);
        assertEquals(frames.keySet(), loaded.keySet());
        assertArrayEquals(frames.get("minecraft:block/stone").rgb(), loaded.get("minecraft:block/stone").rgb());
        assertArrayEquals(frames.get("minecraft:block/water_still").alpha(), loaded.get("minecraft:block/water_still").alpha());
        assertTrue(loaded.get("minecraft:block/water_still").animated());
        assertEquals(frames.get("minecraft:block/stone").stats().spread(), loaded.get("minecraft:block/stone").stats().spread());

        Files.writeString(file, "not a cache");
        assertTrue(TextureCache.load(file).isEmpty());
    }

    @Test
    void blocksAreSortedIntoKinds() {
        Map<Block, Kind> expected = Map.ofEntries(
                Map.entry(block("stone"), Kind.FULL), Map.entry(block("oak_log"), Kind.FULL), Map.entry(block("bookshelf"), Kind.FULL),
                Map.entry(block("copper_grate"), Kind.FULL), Map.entry(block("redstone_lamp"), Kind.FULL),
                Map.entry(block("oak_slab"), Kind.SLAB), Map.entry(block("oak_stairs"), Kind.STAIRS), Map.entry(block("cobblestone_wall"), Kind.WALL),
                Map.entry(block("oak_fence"), Kind.FENCE), Map.entry(block("oak_fence_gate"), Kind.FENCE_GATE),
                Map.entry(block("iron_bars"), Kind.PANE), Map.entry(block("glass_pane"), Kind.PANE), Map.entry(block("red_stained_glass_pane"), Kind.PANE),
                Map.entry(block("oak_trapdoor"), Kind.TRAPDOOR), Map.entry(block("iron_door"), Kind.DOOR), Map.entry(block("oak_shelf"), Kind.SHELF),
                Map.entry(block("white_carpet"), Kind.CARPET), Map.entry(block("moss_carpet"), Kind.CARPET), Map.entry(block("pale_moss_carpet"), Kind.CARPET),
                Map.entry(block("glass"), Kind.GLASS), Map.entry(block("red_stained_glass"), Kind.GLASS), Map.entry(block("tinted_glass"), Kind.GLASS),
                Map.entry(block("oak_leaves"), Kind.LEAVES), Map.entry(block("azalea_leaves"), Kind.LEAVES),
                Map.entry(block("poppy"), Kind.PLANT), Map.entry(block("short_grass"), Kind.PLANT), Map.entry(block("oak_sapling"), Kind.PLANT),
                Map.entry(block("kelp"), Kind.PLANT), Map.entry(block("vine"), Kind.PLANT), Map.entry(block("sugar_cane"), Kind.PLANT),
                Map.entry(block("water"), Kind.FLUID), Map.entry(block("lava"), Kind.FLUID),
                Map.entry(block("glowstone"), Kind.LIGHT), Map.entry(block("torch"), Kind.LIGHT), Map.entry(block("sea_lantern"), Kind.LIGHT),
                Map.entry(block("chest"), Kind.OTHER), Map.entry(block("lever"), Kind.OTHER), Map.entry(block("anvil"), Kind.OTHER));
        expected.forEach((block, kind) -> assertEquals(kind, Kind.of(block.defaultBlockState()), block.toString()));
        // A lamp is a light only while it is lit.
        assertEquals(Kind.LIGHT, Kind.of(block("redstone_lamp").defaultBlockState().setValue(BlockStateProperties.LIT, true)));
        // Every block lands somewhere, without a model or a world.
        for (Block block : BuiltInRegistries.BLOCK) assertNotNull(Kind.of(block.defaultBlockState()), block.toString());
    }

    @Test
    void creativeOnlyBlocksAreTagged() {
        for (Block block : List.of(block("bedrock"), block("reinforced_deepslate"), block("end_portal_frame"), block("end_portal"), block("end_gateway"),
                block("spawner"), block("trial_spawner"), block("vault"), block("petrified_oak_slab"), block("infested_stone"), block("infested_deepslate"),
                block("command_block"), block("chain_command_block"), block("repeating_command_block"), block("jigsaw"), block("structure_block"),
                block("structure_void"), block("barrier"), block("light"), block("budding_amethyst"), block("chorus_plant"), block("frosted_ice"),
                block("bubble_column"), block("moving_piston"), block("piston_head"), block("nether_portal"), block("farmland"), block("dirt_path"),
                block("suspicious_sand"), block("suspicious_gravel"))) {
            assertTrue(CreativeOnly.is(block), block.toString());
        }
        for (Block block : List.of(block("stone"), block("oak_slab"), block("obsidian"), block("amethyst_block"), block("chorus_flower"), block("ice"),
                block("piston"), block("dirt"), block("sand"), block("deepslate"), block("stone_bricks"), block("water"))) {
            assertFalse(CreativeOnly.is(block), block.toString());
        }
        // Every infested block is caught by its class.
        long infested = BuiltInRegistries.BLOCK.stream().filter(b -> BuiltInRegistries.BLOCK.getKey(b).getPath().startsWith("infested_")).count();
        assertEquals(7, infested);
        BuiltInRegistries.BLOCK.stream().filter(b -> BuiltInRegistries.BLOCK.getKey(b).getPath().startsWith("infested_"))
                .forEach(b -> assertTrue(CreativeOnly.is(b), b.toString()));
        assertTrue(entry(block("bedrock"), "block/bedrock").creativeOnly());
        assertFalse(entry(block("stone"), "block/stone").creativeOnly());
    }

    @Test
    void shapesOfOneMaterialShareABase() {
        Map<String, String> expected = Map.ofEntries(
                Map.entry("minecraft:oak_planks", "minecraft:oak"), Map.entry("minecraft:oak_slab", "minecraft:oak"),
                Map.entry("minecraft:oak_fence_gate", "minecraft:oak"), Map.entry("minecraft:oak_wall_hanging_sign", "minecraft:oak"),
                Map.entry("minecraft:stripped_oak_log", "minecraft:oak"), Map.entry("minecraft:oak_pressure_plate", "minecraft:oak"),
                Map.entry("minecraft:crimson_stem", "minecraft:crimson"), Map.entry("minecraft:dark_oak_door", "minecraft:dark_oak"),
                Map.entry("minecraft:stone_bricks", "minecraft:stone_brick"), Map.entry("minecraft:stone_brick_stairs", "minecraft:stone_brick"),
                Map.entry("minecraft:mossy_stone_brick_wall", "minecraft:mossy_stone_brick"),
                Map.entry("minecraft:cobblestone", "minecraft:cobblestone"), Map.entry("minecraft:cobblestone_wall", "minecraft:cobblestone"),
                Map.entry("minecraft:bricks", "minecraft:brick"), Map.entry("minecraft:brick_slab", "minecraft:brick"),
                Map.entry("minecraft:red_nether_bricks", "minecraft:red_nether_brick"), Map.entry("minecraft:nether_brick_fence", "minecraft:nether_brick"),
                Map.entry("minecraft:quartz_block", "minecraft:quartz"), Map.entry("minecraft:quartz_stairs", "minecraft:quartz"),
                Map.entry("minecraft:smooth_quartz_slab", "minecraft:smooth_quartz"),
                Map.entry("minecraft:purpur_block", "minecraft:purpur"), Map.entry("minecraft:purpur_slab", "minecraft:purpur"),
                Map.entry("minecraft:prismarine_bricks", "minecraft:prismarine_brick"), Map.entry("minecraft:dark_prismarine_stairs", "minecraft:dark_prismarine"),
                Map.entry("minecraft:red_sandstone_wall", "minecraft:red_sandstone"), Map.entry("minecraft:end_stone_bricks", "minecraft:end_stone_brick"),
                Map.entry("minecraft:polished_blackstone_button", "minecraft:polished_blackstone"),
                Map.entry("minecraft:deepslate_tiles", "minecraft:deepslate_tile"), Map.entry("minecraft:deepslate_tile_wall", "minecraft:deepslate_tile"),
                Map.entry("minecraft:tuff_brick_slab", "minecraft:tuff_brick"), Map.entry("minecraft:waxed_cut_copper_stairs", "minecraft:waxed_cut_copper"),
                Map.entry("minecraft:copper_block", "minecraft:copper"), Map.entry("minecraft:copper_trapdoor", "minecraft:copper"),
                Map.entry("minecraft:stone", "minecraft:stone"), Map.entry("minecraft:stone_slab", "minecraft:stone"),
                Map.entry("somemod:ruby_bricks", "somemod:ruby_brick"), Map.entry("somemod:ruby_brick_slab", "somemod:ruby_brick"),
                Map.entry("minecraft:slab", "minecraft:slab"));
        expected.forEach((id, base) -> assertEquals(base, MaterialSets.base(id), id));
        // Every vanilla slab, stair and wall has a full block that shares its base.
        List<String> ids = BuiltInRegistries.BLOCK.keySet().stream().map(Identifier::toString).toList();
        java.util.Set<String> bases = new java.util.HashSet<>();
        List<String> orphans = new ArrayList<>();
        for (String id : ids) {
            if (id.endsWith("_slab") || id.endsWith("_stairs") || id.endsWith("_wall")) orphans.add(id);
            else bases.add(MaterialSets.base(id));
        }
        orphans.removeIf(id -> bases.contains(MaterialSets.base(id)));
        assertEquals(List.of("minecraft:petrified_oak_slab"), orphans);
    }

    @Test
    void aSetListsFullBlocksThenShapes() {
        BlockIndex index = index(List.of(
                List.of(entry(block("oak_stairs"), "block/oak_planks")), List.of(entry(block("oak_fence"), "block/oak_planks")),
                List.of(entry(block("oak_planks"), "block/oak_planks")), List.of(entry(block("oak_slab"), "block/oak_planks")),
                List.of(entry(block("oak_log"), "block/oak_log")), List.of(entry(block("stone"), "block/stone")),
                List.of(entry(block("oak_wall_sign").defaultBlockState(), true, "block/oak_planks"))));
        Entry slab = index.statesOf(block("oak_slab")).getFirst();
        assertEquals(List.of("minecraft:oak_planks", "minecraft:oak_log", "minecraft:oak_slab", "minecraft:oak_stairs", "minecraft:oak_fence"),
                index.materialSet(slab).stream().map(Entry::id).toList());
        assertEquals(List.of("minecraft:stone"), index.materialSet(index.statesOf(block("stone")).getFirst()).stream().map(Entry::id).toList());
        assertEquals(java.util.Set.of("minecraft"), index.namespaces());
        assertEquals(6, index.entries().size(), "the technical wall sign is left out");
        ModSettings.technicalBlocks = true;
        try {
            assertEquals(7, index.entries().size());
            assertEquals(6, index.materialSet(slab).size());
        } finally {
            ModSettings.reset();
        }
    }

    @Test
    void closestFindsBlocksByColourAndKeepsToTheFilters() {
        BlockState lit = block("redstone_lamp").defaultBlockState().setValue(BlockStateProperties.LIT, true);
        BlockState logX = block("oak_log").defaultBlockState().setValue(BlockStateProperties.AXIS, Direction.Axis.X);
        BlockIndex index = index(List.of(
                List.of(entry(block("stone"), "block/stone")),
                List.of(entry(block("red_concrete"), "block/red_concrete")),
                List.of(entry(block("white_concrete"), "block/white_concrete")),
                List.of(entry(block("oak_planks"), "block/oak_planks")),
                List.of(entry(block("oak_slab"), "block/oak_planks")),
                List.of(entry(block("bookshelf"), "block/oak_planks", "block/oak_planks", "block/bookshelf", "block/bookshelf", "block/bookshelf", "block/bookshelf")),
                List.of(entry(block("oak_log"), "block/oak_log_top", "block/oak_log_top", "block/oak_log", "block/oak_log", "block/oak_log", "block/oak_log"),
                        entry(logX, false, "block/oak_log", "block/oak_log", "block/oak_log", "block/oak_log", "block/oak_log_top", "block/oak_log_top")),
                List.of(entry(block("redstone_lamp"), "block/redstone_lamp"), entry(lit, false, "block/redstone_lamp_on")),
                List.of(entry(block("bedrock"), "block/bedrock")),
                List.of(entry(block("command_block").defaultBlockState(), true, "block/command_block_front"))));

        Color red = stats("block/red_concrete").mean(Scale.T1);
        List<BlockIndex.Match> matches = index.closest(Target.of(red), query());
        assertEquals("minecraft:red_concrete", matches.getFirst().entry().id());
        // A flat target's distance is the texture's mean distance from it, so a flat texture is nearly on it.
        assertTrue(matches.getFirst().distance() < 0.01);
        assertEquals(9, matches.size(), "one row a block, and the technical command block is left out");
        for (int i = 1; i < matches.size(); i++) assertTrue(matches.get(i - 1).distance() <= matches.get(i).distance());
        assertEquals(3, index.closest(Target.of(red), query().limit(3)).size());

        // States: the lit lamp is found as its own state, and as its block's best when collapsed. Its texture is
        // patchy, so it is only the nearest to its own average from far enough away to blend.
        Color litColour = stats("block/redstone_lamp_on").mean(Scale.T1);
        BlockIndex.Match lamp = index.closest(Target.of(litColour), query().scale(Scale.T1)).getFirst();
        assertEquals("minecraft:redstone_lamp", lamp.entry().id());
        assertEquals("lit=true", lamp.entry().properties());
        assertFalse(lamp.entry().isDefaultState());
        Query separately = query();
        separately.statesSeparately = true;
        assertEquals(11, index.closest(Target.of(litColour), separately).size());

        // Faces: with any face, the log's end grain is found on the face that shows it.
        Target endGrain = Target.of(stats("block/oak_log_top").mean(Scale.T1));
        BlockIndex.Match log = index.closest(endGrain, query()).stream().filter(m -> m.entry().block() == block("oak_log")).findFirst().orElseThrow();
        assertTrue(log.face() == Face.DOWN || log.face() == Face.UP || log.face() == Face.WEST || log.face() == Face.EAST);
        assertEquals(Face.UP, index.statesOf(block("oak_log")).getFirst().bestFace(endGrain, Scale.T16, Metric.OKLAB, TintContext.none()));
        // Asked for the north face, only the bark counts.
        BlockIndex.Match north = index.closest(endGrain, query().face(Face.NORTH)).stream()
                .filter(m -> m.entry().block() == block("oak_log")).findFirst().orElseThrow();
        assertEquals(Face.NORTH, north.face());
        assertTrue(north.distance() > log.distance());

        // A texture target matches itself exactly at every scale and in every metric.
        Entry shelf = index.statesOf(block("bookshelf")).getFirst();
        Target shelves = Target.of(shelf, Face.NORTH, TintContext.none());
        assertFalse(shelves.flat());
        for (Scale s : Scale.values()) {
            for (Metric m : Metric.values()) {
                BlockIndex.Match first = index.closest(shelves, query().scale(s).metric(m)).getFirst();
                assertEquals(0, first.distance(), 1e-9, s + " " + m);
            }
        }
        assertEquals(shelf, index.closest(shelves, query()).getFirst().entry());

        // Shading darkens the sides, so a colour shaded as a north face is found on one.
        Color planks = stats("block/oak_planks").mean(Scale.T1);
        BlockIndex.Match shaded = index.closest(Target.of(Shading.apply(planks, Face.NORTH)), query().shading(true).scale(Scale.T1)).getFirst();
        assertTrue(shaded.face() == Face.NORTH || shaded.face() == Face.SOUTH, shaded.face().toString());
        assertTrue(shaded.distance() < 0.01);
        assertEquals(Face.UP, index.closest(Target.of(planks), query().scale(Scale.T1)).getFirst().face(), "unshaded, the top wins among equals");

        // Filters.
        Target any = Target.of(Color.ofRgb(0x808080));
        Query q = query();
        q.kinds.add(Kind.SLAB);
        assertEquals(List.of("minecraft:oak_slab"), ids(index.closest(any, q)));
        q = query();
        q.technical = true;
        assertEquals(10, index.closest(any, q).size());
        q.creativeOnly = Query.Tri.ONLY;
        assertEquals(List.of("minecraft:bedrock", "minecraft:command_block"), ids(index.closest(any, q)).stream().sorted().toList());
        q.creativeOnly = Query.Tri.EXCLUDE;
        assertEquals(8, index.closest(any, q).size());
        q = query();
        q.lightSource = Query.Tri.ONLY;
        assertEquals(List.of("minecraft:redstone_lamp"), ids(index.closest(any, q)));
        assertEquals("lit=true", index.closest(any, q).getFirst().entry().properties());
        q = query();
        q.sameTexture = true;
        assertFalse(ids(index.closest(any, q)).contains("minecraft:bookshelf"));
        assertTrue(ids(index.closest(any, q)).contains("minecraft:stone"));
        q = query();
        q.fullCube = true;
        assertFalse(ids(index.closest(any, q)).contains("minecraft:oak_slab"));
        q = query();
        q.badges.add(Badge.FLAT);
        assertEquals(List.of("minecraft:red_concrete", "minecraft:white_concrete"), ids(index.closest(any, q)).stream().sorted().toList());
        q = query();
        q.badges.add(Badge.BUSY);
        assertEquals(Face.NORTH, index.closest(any, q).stream().filter(m -> m.entry().block() == block("bookshelf")).findFirst().orElseThrow().face(),
                "only the bookshelf's sides are busy");
        q = query();
        q.tone = Tone.NEUTRAL;
        assertTrue(ids(index.closest(any, q)).contains("minecraft:stone"));
        assertFalse(ids(index.closest(any, q)).contains("minecraft:red_concrete"));
        q = query();
        q.lightnessMin = 0.85;
        assertEquals(List.of("minecraft:white_concrete"), ids(index.closest(any, q)));
        q = query();
        q.chromaMin = 0.12;
        assertEquals(List.of("minecraft:red_concrete"), ids(index.closest(any, q)));
        q = query();
        q.namespacesExcluded.add("minecraft");
        assertTrue(index.closest(any, q).isEmpty());
        q = query();
        q.namespaces.add("minecraft");
        assertEquals(9, index.closest(any, q).size());
        q = query();
        q.tinted = Query.Tri.ONLY;
        assertTrue(index.closest(any, q).isEmpty());

        // The API's seam.
        assertEquals(Optional.of(red), index.colourOf(block("red_concrete").defaultBlockState(), Face.UP, TintContext.none()));
        assertEquals(Optional.of(litColour), index.colourOf(lit, Face.NORTH, TintContext.none()));
        assertEquals(Optional.of(red), index.colourOf(block("red_concrete").defaultBlockState(), Face.ANY, TintContext.none()));
        assertEquals(Optional.empty(), index.colourOf(block("diamond_block").defaultBlockState(), Face.UP, TintContext.none()));
    }

    /** What the Match tab and the eyedropper lean on: a choice of faces, a state's entry, and knowing when a build is new. */
    @Test
    void aQueryCanChooseAmongTheSidesAndAStateFindsItsEntry() {
        // Red on top and bottom, white round the sides.
        Entry twoTone = entry(block("oak_log"), "block/red_concrete", "block/red_concrete", "block/white_concrete", "block/white_concrete",
                "block/white_concrete", "block/white_concrete");
        Entry lantern = entry(block("lantern"), "block/stone");
        int before = BlockIndex.get().version();
        BlockIndex index = index(List.of(List.of(twoTone), List.of(lantern)));
        assertNotEquals(before, index.version(), "a build is a new version");

        Target red = Target.of(stats("block/red_concrete").mean(Scale.T1));
        Query q = query();
        assertEquals(Face.UP, index.closest(red, q).getFirst().face(), "any face finds the red top");
        q.faces.addAll(List.of(Face.NORTH, Face.SOUTH, Face.WEST, Face.EAST));
        BlockIndex.Match side = index.closest(red, q).stream().filter(m -> m.entry() == twoTone).findFirst().orElseThrow();
        assertEquals(Face.NORTH, side.face(), "among the sides only, the first side is the best there is");
        assertTrue(side.distance() > 0.1, "and it is far from red");
        q.face(Face.DOWN);
        assertEquals(Face.DOWN, index.closest(red, q).getFirst().face(), "one named face overrides the choice");

        BlockState log = block("oak_log").defaultBlockState();
        assertSame(twoTone, index.entryOf(log));
        assertSame(twoTone, index.entryOf(log.setValue(BlockStateProperties.AXIS, Direction.Axis.X)),
                "a state the build left out answers as the block's first entry");
        assertSame(lantern, index.entryOf(block("lantern").defaultBlockState().setValue(BlockStateProperties.WATERLOGGED, true)),
                "a waterlogged state answers as the dry one");
        assertNull(index.entryOf(block("diamond_block").defaultBlockState()));

        // Outside a world there is nowhere to stand, so a place in it tints nothing.
        assertSame(TintContext.none(), TintContext.at(BlockPos.ZERO));
    }

    private static List<String> ids(List<BlockIndex.Match> matches) { return matches.stream().map(m -> m.entry().id()).toList(); }

    /**
     * How long a flat target takes against an index the size of the vanilla one. Off by default, as timings depend
     * on the machine; run it with {@code -Dchroma.timing=true}.
     */
    @Test
    @EnabledIfSystemProperty(named = "chroma.timing", matches = "true")
    void aFlatTargetAnswersWithinAFrameOrTwo() {
        List<List<Entry>> blocks = new ArrayList<>();
        List<Identifier> textures = new ArrayList<>(resources.listResources("textures/block", id -> id.getPath().endsWith(".png")).keySet());
        List<Block> all = BuiltInRegistries.BLOCK.stream().filter(b -> !b.defaultBlockState().isAir()).toList();
        for (int i = 0; i < all.size(); i++) {
            // Two states a block with six different faces is more than the vanilla index holds.
            List<Entry> states = new ArrayList<>();
            for (int s = 0; s < 2; s++) {
                String[] sprites = new String[6];
                for (int f = 0; f < 6; f++) {
                    String path = textures.get((i * 13 + s * 7 + f) % textures.size()).getPath();
                    sprites[f] = path.substring("textures/".length(), path.length() - ".png".length());
                }
                states.add(entry(all.get(i).defaultBlockState(), false, sprites));
            }
            blocks.add(states);
        }
        BlockIndex index = index(blocks);
        Target target = Target.of(Color.ofRgb(0x3A7D44));
        for (Metric metric : Metric.values()) {
            for (boolean shading : new boolean[] {false, true}) {
                Query q = query().metric(metric).shading(shading);
                for (int warm = 0; warm < 5; warm++) index.closest(target, q);
                long start = System.nanoTime();
                int runs = 20;
                for (int i = 0; i < runs; i++) index.closest(target, q);
                double ms = (System.nanoTime() - start) / 1e6 / runs;
                System.out.printf("closest: %d blocks, %s, shading %s: %.2f ms%n", blocks.size(), metric, shading, ms);
                assertTrue(ms < 100, metric + " took " + ms + " ms");
            }
        }
    }
}
