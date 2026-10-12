package dev.chromastudio.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.chromastudio.client.export.SchematicExporter;
import dev.chromastudio.core.palette.Palette;
import dev.chromastudio.core.palette.PaletteCodec;
import dev.chromastudio.core.palette.PaletteEntry;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class PaletteStoreTest {
    @TempDir
    Path dir;

    private static final List<PaletteEntry> STONES = List.of(new PaletteEntry("minecraft:stone", ""), new PaletteEntry("minecraft:andesite", "", 3),
            new PaletteEntry("minecraft:oak_log", "axis=x"));

    private List<String> fileNames() throws IOException {
        try (Stream<Path> files = Files.list(dir)) {
            return files.map(f -> f.getFileName().toString()).sorted().toList();
        }
    }

    @Test
    void aNewPaletteIsSavedSelectedAndReadBack() throws IOException {
        PaletteStore store = new PaletteStore(dir);
        Palette made = store.create("Castle walls", STONES);
        assertSame(made, store.selected());
        assertEquals(List.of("castle-walls.json"), fileNames());

        PaletteStore again = new PaletteStore(dir);
        assertEquals(1, again.own().size());
        Palette read = again.own().getFirst();
        assertEquals("Castle walls", read.name);
        assertEquals(STONES, read.entries);
        assertFalse(read.preset);
    }

    @Test
    void aMissingFolderIsEmptyAndIsOnlyMadeOnSaving() {
        Path missing = dir.resolve("not").resolve("there");
        PaletteStore store = new PaletteStore(missing);
        assertTrue(store.own().isEmpty());
        assertNull(store.selected());
        assertFalse(Files.exists(missing));
        store.create("First", STONES);
        assertTrue(Files.isRegularFile(missing.resolve("first.json")));
    }

    @Test
    void namesAndFileNamesNeverClash() throws IOException {
        PaletteStore store = new PaletteStore(dir);
        assertEquals("Wall", store.create("Wall", STONES).name);
        assertEquals("Wall (2)", store.create("Wall", STONES).name);
        assertEquals("wall (3)", store.create("wall", STONES).name);
        // Different names that come to the same file name get a numbered file.
        store.create("Wall!", STONES);
        assertEquals(List.of("wall-2.json", "wall-3.json", "wall-4.json", "wall.json"), fileNames());
        assertEquals(4, new PaletteStore(dir).own().size());
    }

    @Test
    void fileNamesAreSafe() {
        assertEquals("castle-walls", PaletteStore.fileBase("  Castle / Walls?  "));
        assertEquals("palette", PaletteStore.fileBase("../.."));
        assertEquals("palette", PaletteStore.fileBase(""));
        assertEquals("palette", PaletteStore.fileBase(null));
        assertEquals(60, PaletteStore.fileBase("a".repeat(200)).length());
    }

    @Test
    void renamingMovesTheFile() throws IOException {
        PaletteStore store = new PaletteStore(dir);
        Palette p = store.create("Old name", STONES);
        assertTrue(store.rename(p, " New name "));
        assertEquals("New name", p.name);
        assertEquals(List.of("new-name.json"), fileNames());
        assertEquals("New name", new PaletteStore(dir).own().getFirst().name);
        assertFalse(store.rename(p, "  "));
        assertEquals("New name", p.name);
        // A name that keeps the file name rewrites the file in place.
        assertTrue(store.rename(p, "NEW NAME"));
        assertEquals(List.of("new-name.json"), fileNames());
        assertEquals("NEW NAME", new PaletteStore(dir).own().getFirst().name);
    }

    @Test
    void renamingNeverTakesAnotherPalettesFile() throws IOException {
        PaletteStore store = new PaletteStore(dir);
        Palette a = store.create("Alpha", STONES);
        Palette b = store.create("Beta", List.of(new PaletteEntry("minecraft:dirt", "")));
        assertTrue(store.rename(b, "Alpha"));
        assertEquals(List.of("alpha-2.json", "alpha.json"), fileNames());
        PaletteStore again = new PaletteStore(dir);
        assertEquals(2, again.own().size());
        assertEquals(STONES, PaletteCodec.fromJson(Files.readString(dir.resolve("alpha.json"))).entries);
        assertEquals(a.entries, STONES);
    }

    @Test
    void deletingRemovesTheFileAndTheSelection() throws IOException {
        PaletteStore store = new PaletteStore(dir);
        Palette keep = store.create("Keep", STONES);
        Palette gone = store.create("Gone", STONES);
        assertTrue(store.delete(gone));
        assertNull(store.selected());
        assertEquals(List.of(keep), store.own());
        assertEquals(List.of("keep.json"), fileNames());
        assertFalse(store.delete(gone));
    }

    @Test
    void savingWritesChangesAndLeavesNoTemporaryFile() throws IOException {
        PaletteStore store = new PaletteStore(dir);
        Palette p = store.create("Notes", STONES);
        p.notes = "For the keep";
        p.tags.add("stone");
        p.entries.set(0, p.entries.get(0).withWeight(5));
        assertTrue(store.save(p));
        assertEquals(List.of("notes.json"), fileNames());
        Palette read = new PaletteStore(dir).own().getFirst();
        assertEquals("For the keep", read.notes);
        assertEquals(List.of("stone"), read.tags);
        assertEquals(5, read.entries.get(0).weight());
    }

    @Test
    void anUnreadableFileIsSkippedAndKept() throws IOException {
        Files.writeString(dir.resolve("broken.json"), "{ not json");
        Files.writeString(dir.resolve("newer.json"), "{\"format\": 2, \"name\": \"Newer\"}");
        Files.writeString(dir.resolve("readme.txt"), "not a palette");
        PaletteStore store = new PaletteStore(dir);
        assertTrue(store.own().isEmpty());
        store.create("Broken", STONES);
        assertEquals("{ not json", Files.readString(dir.resolve("broken.json")));
        assertTrue(Files.isRegularFile(dir.resolve("broken-2.json")));
    }

    @Test
    void aFileWithNoNameIsNamedAfterItself() throws IOException {
        Files.writeString(dir.resolve("by-hand.json"), "{\"format\": 1, \"entries\": [{\"block\": \"minecraft:stone\"}]}");
        assertEquals("by-hand", new PaletteStore(dir).own().getFirst().name);
    }

    @Test
    void importsGetAFreeNameAndBadOnesSayWhy() {
        PaletteStore store = new PaletteStore(dir);
        Palette shared = store.importShare("chroma1:stone,andesite*3,oak_log[axis=x]/Shared");
        assertEquals("Shared", shared.name);
        assertEquals(STONES, shared.entries);
        assertEquals("Shared (2)", store.importJson(PaletteCodec.toJson(shared)).name);
        assertEquals("Palette", store.importShare("chroma1:stone").name);
        assertThrows(IllegalArgumentException.class, () -> store.importShare("stone,andesite"));
        assertThrows(IllegalArgumentException.class, () -> store.importJson("[]"));
        assertEquals(3, store.own().size());
    }

    @Test
    void theSinkAddsToTheSelectedPaletteOrStartsOne() {
        PaletteStore store = new PaletteStore(dir);
        store.add("minecraft:stone", "");
        Palette started = store.selected();
        assertNotNull(started);
        assertEquals(List.of(new PaletteEntry("minecraft:stone", "")), started.entries);
        store.add("minecraft:oak_log", "axis=x");
        assertEquals(2, started.entries.size());
        assertEquals(2, new PaletteStore(dir).own().getFirst().entries.size());

        // A preset can't be changed, so adding with one selected starts a palette of the player's own.
        store.select(store.presets().getFirst());
        store.add("minecraft:dirt", "");
        assertFalse(store.selected().preset);
        assertEquals(List.of(new PaletteEntry("minecraft:dirt", "")), store.selected().entries);

        assertTrue(store.createPalette("From a gradient", STONES));
        assertEquals("From a gradient", store.selected().name);
        assertEquals(STONES, store.selected().entries);
    }

    @Test
    void presetsAreListedAfterThePlayersOwnAndCanOnlyBeCopied() throws IOException {
        PaletteStore store = new PaletteStore(dir);
        Palette mine = store.create("Mine", STONES);
        assertEquals(PaletteStore.PRESETS.size(), store.presets().size());
        assertSame(mine, store.all().getFirst());
        Palette preset = store.presets().getFirst();
        assertTrue(preset.preset);
        assertEquals("medieval", store.presetId(preset));
        assertNull(store.presetId(mine));
        assertFalse(store.save(preset));
        assertFalse(store.rename(preset, "Mine now"));
        assertFalse(store.delete(preset));

        Palette copy = store.copy(preset);
        assertFalse(copy.preset);
        assertEquals(preset.name, copy.name);
        assertEquals(preset.entries, copy.entries);
        assertEquals(preset.notes, copy.notes);
        assertSame(copy, store.selected());
        assertEquals(preset.name + " (2)", store.copy(preset).name);
        assertEquals(3, fileNames().size());
    }

    @Test
    void reloadingKeepsTheSelectionByName() {
        PaletteStore store = new PaletteStore(dir);
        store.create("One", STONES);
        store.create("Two", STONES);
        store.reload();
        assertEquals("Two", store.selected().name);
        store.select(store.presets().get(2));
        store.reload();
        assertTrue(store.selected().preset);
        assertEquals(store.presets().get(2), store.selected());
    }

    @Test
    void everyPresetIsMadeOfBlocksTheGameHas() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
        PaletteStore store = new PaletteStore(dir);
        assertEquals(10, store.presets().size());
        for (Palette preset : store.presets()) {
            assertFalse(preset.name.isBlank());
            assertFalse(preset.notes.isBlank(), preset.name);
            assertFalse(preset.tags.isEmpty(), preset.name);
            assertTrue(preset.entries.size() >= 8, preset.name);
            for (PaletteEntry e : preset.entries) assertNotNull(SchematicExporter.stateFor(e), preset.name + ": " + e.idWithProperties());
            assertEquals(preset.entries.size(), preset.entries.stream().map(PaletteEntry::idWithProperties).distinct().count(), preset.name);
        }
    }
}
