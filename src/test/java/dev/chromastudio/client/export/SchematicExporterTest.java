package dev.chromastudio.client.export;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.chromastudio.client.ChromaStudioClient;
import dev.chromastudio.client.ClientPlatform;
import dev.chromastudio.client.ModSettings;
import dev.chromastudio.client.PlacementChoice;
import dev.chromastudio.client.export.SchematicExporter.Format;
import dev.chromastudio.core.export.Layout;
import dev.chromastudio.core.export.LayoutOptions;
import dev.chromastudio.core.export.LitematicBits;
import dev.chromastudio.core.palette.Palette;
import dev.chromastudio.core.palette.PaletteEntry;
import dev.chromastudio.core.palette.ShareString;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;
import net.minecraft.SharedConstants;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Vec3i;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.server.Bootstrap;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Writes real files with the game's blocks loaded, and reads them back the way the game and Litematica do. It needs no world or client. */
class SchematicExporterTest {
    @TempDir
    Path dir;

    @BeforeAll
    static void loadBlocks() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @AfterEach
    void restore() {
        ModSettings.reset();
        ChromaStudioClient.platform = null;
    }

    private static Palette six() {
        return ShareString.decode("chroma1:stone,andesite*3,oak_log[axis=x],red_concrete,oak_stairs[facing=east,half=top],white_wool/Six Blocks");
    }

    private static LayoutOptions options(Layout layout, boolean gap) { return new LayoutOptions(layout, 4, 2, 10, gap, 9); }

    private static CompoundTag read(Path file) throws IOException { return NbtIo.readCompressed(file, NbtAccounter.unlimitedHeap()); }

    @Test
    void anEntrysPropertiesAreAppliedByName() {
        assertEquals(Blocks.STONE.defaultBlockState(), SchematicExporter.stateFor(new PaletteEntry("minecraft:stone", "")));
        assertEquals(Blocks.OAK_LOG.defaultBlockState().setValue(RotatedPillarBlock.AXIS, Direction.Axis.X),
                SchematicExporter.stateFor(new PaletteEntry("minecraft:oak_log", "axis=x")));
        BlockState stairs = SchematicExporter.stateFor(new PaletteEntry("minecraft:oak_stairs", "facing=east,half=top"));
        assertEquals(Direction.EAST, stairs.getValue(BlockStateProperties.HORIZONTAL_FACING));
        assertEquals("top", stairs.getValue(BlockStateProperties.HALF).getSerializedName());
    }

    @Test
    void anUnknownBlockPropertyOrValueHasNoState() {
        assertNull(SchematicExporter.stateFor(new PaletteEntry("create:andesite_casing", "")));
        assertNull(SchematicExporter.stateFor(new PaletteEntry("Not An Id", "")));
        assertNull(SchematicExporter.stateFor(new PaletteEntry("minecraft:stone", "axis=x")));
        assertNull(SchematicExporter.stateFor(new PaletteEntry("minecraft:oak_log", "axis=w")));
        assertNull(SchematicExporter.stateFor(new PaletteEntry("minecraft:oak_log", "axis")));
    }

    @Test
    void aLitematicHoldsTheLayoutAndTheMetadata() throws IOException {
        Palette p = six();
        SchematicExporter.Result result = SchematicExporter.export(p, options(Layout.WALL, true), dir, "Steve");
        assertEquals(dir.resolve("chroma-six-blocks-wall.litematic"), result.file());
        assertEquals(List.of(), result.skippedIds());

        CompoundTag root = read(result.file());
        assertEquals(6, root.getInt("Version"));
        assertEquals(SharedConstants.getCurrentVersion().getDataVersion().getVersion(), root.getInt("MinecraftDataVersion"));
        CompoundTag meta = root.getCompound("Metadata");
        assertEquals("Six Blocks", meta.getString("Name"));
        assertEquals("Steve", meta.getString("Author"));
        assertEquals(ShareString.encode(p), meta.getString("Description"));
        assertEquals(1, meta.getInt("RegionCount"));
        assertEquals(11 * 4, meta.getInt("TotalVolume"));
        assertEquals(6 * 4, meta.getInt("TotalBlocks"));
        assertTrue(meta.getLong("TimeCreated") > 0);
        assertEquals(meta.getLong("TimeCreated"), meta.getLong("TimeModified"));
        assertEquals(List.of(11, 4, 1), xyz(meta.getCompound("EnclosingSize")));

        assertEquals(1, root.getCompound("Regions").getAllKeys().size());
        CompoundTag region = root.getCompound("Regions").getCompound("Main");
        assertEquals(List.of(11, 4, 1), xyz(region.getCompound("Size")));
        ListTag palette = region.getList("BlockStatePalette", Tag.TAG_COMPOUND);
        assertEquals(7, palette.size());
        assertEquals(Blocks.AIR.defaultBlockState(), state(palette, 0));
        long[] packed = region.getLongArray("BlockStates");
        int bits = LitematicBits.bitsFor(palette.size());
        for (int y = 0; y < 4; y++)
            for (int x = 0; x < 11; x++) {
                BlockState expected = x % 2 == 1 ? Blocks.AIR.defaultBlockState() : SchematicExporter.stateFor(p.entries.get(x / 2));
                assertEquals(expected, state(palette, LitematicBits.get(packed, bits, y * 11 + x)), x + ", " + y);
            }
    }

    @Test
    void theGameLoadsAnExportedStructure() throws IOException {
        Palette p = six();
        SchematicExporter.Result result = SchematicExporter.export(p, options(Layout.PATCH_GRID, true), Format.NBT, dir, "Steve");
        assertEquals("chroma-six-blocks-patch-grid.nbt", result.file().getFileName().toString());
        CompoundTag root = read(result.file());
        assertEquals(SharedConstants.getCurrentVersion().getDataVersion().getVersion(), root.getInt("DataVersion"));
        assertEquals(0, root.getList("entities", Tag.TAG_COMPOUND).size());
        assertEquals(6, root.getList("palette", Tag.TAG_COMPOUND).size());
        // The gaps aren't written, so there is no air.
        assertEquals(6 * 4, root.getList("blocks", Tag.TAG_COMPOUND).size());

        StructureTemplate template = new StructureTemplate();
        template.load(BuiltInRegistries.BLOCK.asLookup(), root);
        // Three patches across and two down, each two blocks square with a gap between.
        assertEquals(new Vec3i(8, 5, 1), template.getSize());
        StructurePlaceSettings settings = new StructurePlaceSettings();
        assertEquals(4, template.filterBlocks(BlockPos.ZERO, settings, Blocks.STONE).size());
        assertEquals(0, template.filterBlocks(BlockPos.ZERO, settings, Blocks.AIR).size());
        List<StructureTemplate.StructureBlockInfo> logs = template.filterBlocks(BlockPos.ZERO, settings, Blocks.OAK_LOG);
        assertEquals(4, logs.size());
        for (StructureTemplate.StructureBlockInfo log : logs) {
            assertEquals(Direction.Axis.X, log.state().getValue(RotatedPillarBlock.AXIS));
            // The third entry is at the right of the top row.
            assertTrue(log.pos().getX() >= 6 && log.pos().getY() >= 3, log.pos().toString());
        }
        List<StructureTemplate.StructureBlockInfo> stairs = template.filterBlocks(BlockPos.ZERO, settings, Blocks.OAK_STAIRS);
        assertEquals(SchematicExporter.stateFor(p.entries.get(4)), stairs.get(0).state());
        assertTrue(stairs.get(0).pos().getY() <= 1);
    }

    @Test
    void everyLayoutExportsInBothFormats() throws IOException {
        for (Layout layout : Layout.values())
            for (Format format : Format.values()) {
                SchematicExporter.Result result = SchematicExporter.export(six(), options(layout, false), format, dir, "Steve");
                assertTrue(Files.size(result.file()) > 0, result.file().toString());
                CompoundTag root = read(result.file());
                if (format == Format.NBT) new StructureTemplate().load(BuiltInRegistries.BLOCK.asLookup(), root);
                else assertTrue(root.getCompound("Metadata").getInt("TotalBlocks") >= 6);
            }
        try (Stream<Path> files = Files.list(dir)) {
            assertEquals(12, files.count());
        }
    }

    @Test
    void unknownEntriesAreSkippedAndListed() throws IOException {
        Palette p = ShareString.decode("chroma1:stone,create:andesite_casing,oak_log[axis=w],andesite[lit=true],dirt*2/Mixed");
        SchematicExporter.Result result = SchematicExporter.export(p, options(Layout.STRIP, false), Format.NBT, dir, "Steve");
        assertEquals(List.of("create:andesite_casing", "minecraft:oak_log[axis=w]", "minecraft:andesite[lit=true]"), result.skippedIds());
        StructureTemplate template = new StructureTemplate();
        template.load(BuiltInRegistries.BLOCK.asLookup(), read(result.file()));
        assertEquals(new Vec3i(2, 1, 1), template.getSize());
    }

    @Test
    void entriesPastTheLayoutsLimitAreListedToo() throws IOException {
        Palette p = new Palette("Long");
        for (int i = 0; i < 130; i++) p.entries.add(new PaletteEntry(i == 129 ? "minecraft:dirt" : "minecraft:stone", ""));
        SchematicExporter.Result result = SchematicExporter.export(p, options(Layout.STRIP, true), dir, "Steve");
        assertEquals(List.of("minecraft:stone", "minecraft:dirt"), result.skippedIds());
    }

    @Test
    void aPaletteWithNothingKnownWritesNoFile() throws IOException {
        SchematicExporter.NoBlocksException e = assertThrows(SchematicExporter.NoBlocksException.class,
                () -> SchematicExporter.export(ShareString.decode("chroma1:create:andesite_casing/Modded"), options(Layout.STRIP, false), dir, "Steve"));
        assertEquals(List.of("create:andesite_casing"), e.skippedIds());
        assertThrows(SchematicExporter.NoBlocksException.class, () -> SchematicExporter.export(new Palette("Empty"), options(Layout.STRIP, false), dir, "Steve"));
        try (Stream<Path> files = Files.list(dir)) {
            assertEquals(0, files.count());
        }
    }

    @Test
    void aTakenNameGetsANumberAndNothingIsOverwritten() throws IOException {
        Path first = SchematicExporter.export(six(), options(Layout.STRIP, false), dir, "Steve").file();
        byte[] before = Files.readAllBytes(first);
        Path second = SchematicExporter.export(six(), options(Layout.WALL, false), dir, "Steve").file();
        Files.writeString(dir.resolve("chroma-six-blocks-strip-2.litematic"), "someone else's");
        Path third = SchematicExporter.export(six(), options(Layout.STRIP, true), dir, "Alex").file();
        Path nbt = SchematicExporter.export(six(), options(Layout.STRIP, false), Format.NBT, dir, "Steve").file();

        assertEquals("chroma-six-blocks-strip.litematic", first.getFileName().toString());
        assertEquals("chroma-six-blocks-wall.litematic", second.getFileName().toString());
        assertEquals("chroma-six-blocks-strip-3.litematic", third.getFileName().toString());
        // The other format's names are its own.
        assertEquals("chroma-six-blocks-strip.nbt", nbt.getFileName().toString());
        assertArrayEquals(before, Files.readAllBytes(first));
        assertEquals("someone else's", Files.readString(dir.resolve("chroma-six-blocks-strip-2.litematic")));
        try (Stream<Path> files = Files.list(dir)) {
            assertFalse(files.anyMatch(f -> f.toString().endsWith(".tmp")));
        }
    }

    @Test
    void otherPeoplesTemporaryFilesAndLinksAreLeftAlone() throws IOException {
        Path leftover = dir.resolve("chroma-six-blocks-strip.litematic.tmp");
        Files.writeString(leftover, "a leftover");
        Path first = SchematicExporter.export(six(), options(Layout.STRIP, false), dir, "Steve").file();
        assertEquals("a leftover", Files.readString(leftover));
        byte[] before = Files.readAllBytes(first);

        // A link at the next export's old temporary name must not lead the writer to the first file.
        Path link = dir.resolve("chroma-six-blocks-strip-2.litematic.tmp");
        try {
            Files.createSymbolicLink(link, first);
        } catch (IOException | UnsupportedOperationException ignored) {
            return;
        }
        Path second = SchematicExporter.export(ShareString.decode("chroma1:dirt/Six Blocks"), options(Layout.STRIP, false), dir, "Alex").file();
        assertArrayEquals(before, Files.readAllBytes(first));
        assertTrue(Files.isSymbolicLink(link));
        assertFalse(Files.isSymbolicLink(second));
        assertEquals("Alex", read(second).getCompound("Metadata").getString("Author"));
        try (Stream<Path> files = Files.list(dir)) {
            assertEquals(4, files.count());
        }
    }

    /** A schematic is the player's to share, so it is as readable as any file they save, not kept to its owner. */
    @Test
    void anExportedFileHasTheFoldersUsualPermissions() throws IOException {
        Assumptions.assumeTrue(Files.getFileStore(dir).supportsFileAttributeView("posix"));
        Path usual = Files.createFile(dir.resolve("usual.txt"));
        for (Format format : Format.values()) {
            Path file = SchematicExporter.export(six(), options(Layout.STRIP, false), format, dir, "Steve").file();
            assertEquals(Files.getPosixFilePermissions(usual), Files.getPosixFilePermissions(file), format.toString());
        }
    }

    @Test
    void theFileNameIsSafe() {
        LayoutOptions strip = LayoutOptions.of(Layout.STRIP), clusters = LayoutOptions.of(Layout.NOISE_CLUSTERS);
        assertEquals("chroma-warm-stone-strip", SchematicExporter.fileName(new Palette("Warm Stone"), strip));
        assertEquals("chroma-a-b-c-noise-clusters", SchematicExporter.fileName(new Palette("  ../A/b\\\\C?! "), clusters));
        assertEquals("chroma-mid-2-tone-strip", SchematicExporter.fileName(new Palette("Mid-2_tone"), strip));
        assertEquals("chroma-palette-strip", SchematicExporter.fileName(new Palette(""), strip));
        assertEquals("chroma-palette-strip", SchematicExporter.fileName(new Palette("///"), strip));
        assertEquals("chroma-暖色-strip", SchematicExporter.fileName(new Palette("暖色"), strip));
        assertEquals("chroma-" + "a".repeat(60) + "-strip", SchematicExporter.fileName(new Palette("a".repeat(200)), strip));
    }

    @Test
    void anUnnamedPaletteIsNamedAfterItsFile() throws IOException {
        Path file = SchematicExporter.export(ShareString.decode("chroma1:stone"), options(Layout.STRIP, false), dir, "Steve").file();
        assertEquals("chroma-palette-strip.litematic", file.getFileName().toString());
        assertEquals("chroma-palette-strip", read(file).getCompound("Metadata").getString("Name"));
    }

    @Test
    void exportsGoToTheSettingsFolderOrTheGamesSchematics() throws IOException {
        Path game = dir.resolve("game");
        ChromaStudioClient.platform = new Platform(game);
        assertFalse(LitematicaBridge.isAvailable());
        assertEquals(game.resolve("schematics"), LitematicaBridge.schematicsDir());
        assertEquals(game.resolve("schematics"), SchematicExporter.directory());
        ModSettings.exportFolder = " builds/palettes ";
        assertEquals(game.resolve("builds/palettes"), SchematicExporter.directory());
        Path elsewhere = dir.resolve("elsewhere").toAbsolutePath();
        ModSettings.exportFolder = elsewhere.toString();
        assertEquals(elsewhere, SchematicExporter.directory());
        ModSettings.exportFolder = "bad\0name";
        assertThrows(IOException.class, SchematicExporter::directory);
    }

    @Test
    void withoutLitematicaPlacingSaysSoAndLeavesTheFile() throws IOException {
        ChromaStudioClient.platform = new Platform(dir);
        Path file = SchematicExporter.export(six(), options(Layout.STRIP, false), dir, "Steve").file();
        assertTrue(LitematicaBridge.place(file, BlockPos.ZERO, "Six").isPresent());
        assertTrue(Files.exists(file));
        assertEquals(PlacementChoice.LOOKING_AT, ModSettings.litematicaPlacement);
    }

    private static List<Integer> xyz(CompoundTag vec) { return List.of(vec.getInt("x"), vec.getInt("y"), vec.getInt("z")); }

    private static BlockState state(ListTag palette, int index) { return NbtUtils.readBlockState(BuiltInRegistries.BLOCK.asLookup(), palette.getCompound(index)); }

    /** A loader with a game folder and no other mods. */
    private record Platform(Path gameDir) implements ClientPlatform {
        @Override public Path configDir() { return gameDir.resolve("config"); }
        @Override public boolean isModLoaded(String id) { return false; }
        @Override public List<BakedQuad> quads(BakedModel model, BlockState state, Direction side, RandomSource random) { return List.of(); }
        @Override public TextureAtlasSprite particleIcon(BakedModel model, BlockState state) { return null; }
        @Override public void renderBlock(BlockState state, PoseStack pose, MultiBufferSource buffers, int light, int overlay) { }
        @Override public void sendChatCommand(String command) { }
    }
}
