package dev.chromastudio.client.export;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;

import dev.chromastudio.client.export.ExportCommand.Request;
import dev.chromastudio.client.export.SchematicExporter.Format;
import dev.chromastudio.core.export.Layout;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ExportCommandTest {
    @Test
    void aShareStringAloneLeavesTheRestToTheSettings() {
        assertEquals(new Request("chroma1:stone,andesite*3,oak_log[axis=y]/Name", null, null, false),
                ExportCommand.parse(" chroma1:stone,andesite*3,oak_log[axis=y]/Name "));
    }

    @Test
    void aLayoutAFormatAndPlaceFollowInAnyOrder() {
        assertEquals(new Request("chroma1:stone", Layout.WALL, null, false), ExportCommand.parse("chroma1:stone wall"));
        assertEquals(new Request("chroma1:stone", Layout.PATCH_GRID, Format.NBT, false), ExportCommand.parse("chroma1:stone patch_grid nbt"));
        assertEquals(new Request("chroma1:stone", Layout.NOISE_CLUSTERS, Format.LITEMATIC, true), ExportCommand.parse("chroma1:stone LITEMATIC place Noise-Clusters"));
        assertEquals(new Request("chroma1:stone", null, Format.NBT, true), ExportCommand.parse("chroma1:stone nbt  place"));
    }

    @Test
    void aNameKeepsItsSpaces() {
        assertEquals(new Request("chroma1:stone/Warm  grey stone", Layout.DITHER, null, false), ExportCommand.parse("chroma1:stone/Warm  grey stone dither"));
        // Only one layout is an option, so the word before it belongs to the name.
        assertEquals(new Request("chroma1:stone/Castle wall", Layout.STRIP, null, false), ExportCommand.parse("chroma1:stone/Castle wall strip"));
    }

    @Test
    void quotesKeepANameThatEndsInAnOption() {
        assertEquals(new Request("chroma1:stone/Castle wall", null, null, false), ExportCommand.parse("\"chroma1:stone/Castle wall\""));
        assertEquals(new Request("chroma1:stone/Castle wall", Layout.STRIP, Format.NBT, false), ExportCommand.parse("\"chroma1:stone/Castle wall\" strip nbt"));
        assertInstanceOf(Component.class, ExportCommand.parse("\"chroma1:stone/Castle wall\" tall nbt"));
        assertInstanceOf(Component.class, ExportCommand.parse("\"chroma1:stone/Castle wall"));
    }

    /** The option a problem names, or what else {@code parse} gave. */
    private static Object unknownOption(String text) {
        Object parsed = ExportCommand.parse(text);
        if (!(parsed instanceof Component c) || !(c.getContents() instanceof TranslatableContents t)) return parsed;
        return t.getKey().equals("command.chromastudio.export.unknown_option") ? t.getArgs()[0] : parsed;
    }

    @Test
    void anUnknownOptionIsNamedNotTheShareString() {
        assertEquals("tall", unknownOption("chroma1:stone tall"));
        assertEquals("tall", unknownOption("chroma1:stone tall nbt"));
        assertEquals("tall thin", unknownOption("chroma1:stone,andesite*3 tall thin place"));
        assertEquals("tall", unknownOption("chroma1:stone, andesite tall wall"));
        assertEquals("tall", unknownOption("\"chroma1:stone/Castle wall\" tall nbt"));
        // A name takes any words, and entries may have spaces after their commas.
        assertEquals(new Request("chroma1:stone/Castle tall", null, Format.NBT, false), ExportCommand.parse("chroma1:stone/Castle tall nbt"));
        assertEquals(new Request("chroma1:stone, andesite", Layout.WALL, null, false), ExportCommand.parse("chroma1:stone, andesite wall"));
        // A share string that can't be read at any length is left for the export to explain.
        assertEquals(new Request("stone tall", null, Format.NBT, false), ExportCommand.parse("stone tall nbt"));
    }

    @Test
    void nothingToExportSaysHowToAsk() {
        assertInstanceOf(Component.class, ExportCommand.parse("  "));
        assertInstanceOf(Component.class, ExportCommand.parse("wall nbt"));
        assertInstanceOf(Component.class, ExportCommand.parse("\"\" wall"));
    }

    @Test
    void aFileIsFoundByNameInsideTheFolderOnly(@TempDir Path root) throws IOException {
        Path dir = Files.createDirectories(root.resolve("schematics"));
        Files.writeString(dir.resolve("chroma-a-strip.litematic"), "x");
        Files.writeString(dir.resolve("chroma-b-strip.nbt"), "x");
        Files.writeString(root.resolve("secret.nbt"), "x");
        assertEquals(dir.resolve("chroma-a-strip.litematic"), ExportCommand.find(dir, "chroma-a-strip.litematic"));
        assertEquals(dir.resolve("chroma-a-strip.litematic"), ExportCommand.find(dir, "chroma-a-strip"));
        assertEquals(dir.resolve("chroma-b-strip.nbt"), ExportCommand.find(dir, "chroma-b-strip"));
        assertNull(ExportCommand.find(dir, "chroma-c-strip"));
        assertNull(ExportCommand.find(dir, "../secret.nbt"));
        assertNull(ExportCommand.find(dir, root.resolve("secret.nbt").toString()));
        assertNull(ExportCommand.find(dir, ""));
    }
}
