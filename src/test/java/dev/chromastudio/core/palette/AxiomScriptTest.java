package dev.chromastudio.core.palette;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.MatchResult;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

class AxiomScriptTest {
    // The pattern Axiom 6.1 finds its controls with: anything between two dollar signs on one line.
    private static final Pattern CONTROL = Pattern.compile("\\$([^$()\\n]+)(?:\\(([^$\\n]*)\\))?\\$");

    private static List<String> controls(String script) {
        Matcher m = CONTROL.matcher(script);
        return m.results().map(MatchResult::group).toList();
    }

    private static Palette palette(String name, PaletteEntry... entries) {
        Palette p = new Palette(name);
        p.entries.addAll(List.of(entries));
        return p;
    }

    private static final List<AxiomScript.Remap> ROWS = List.of(
            new AxiomScript.Remap("minecraft:oak_planks", new PaletteEntry("minecraft:stone_bricks", ""), List.of()),
            new AxiomScript.Remap("minecraft:oak_stairs", new PaletteEntry("minecraft:stone_brick_stairs", ""), List.of("facing", "half", "shape", "waterlogged")),
            new AxiomScript.Remap("create:andesite_casing", new PaletteEntry("minecraft:oak_log", "axis=y"), List.of("axis")));

    @Test
    void recolourWritesEachTargetOnceAndEachRowAsAPair() {
        List<AxiomScript.Remap> rows = new ArrayList<>(ROWS);
        rows.add(new AxiomScript.Remap("minecraft:birch_planks", new PaletteEntry("minecraft:stone_bricks", ""), List.of()));
        String script = AxiomScript.recolour("Sandstone", rows);
        assertTrue(script.startsWith("-- Chroma Studio: Sandstone\n"), script);
        assertTrue(script.contains("if chroma_rows == nil then\n"), script);
        assertTrue(script.contains("""
                \tlocal targets = {
                \t\t{block("minecraft", "stone_bricks")},
                \t\t{block("minecraft", "stone_brick_stairs"), "facing", "half", "shape", "waterlogged"},
                \t\t{block("minecraft", "oak_log", "axis=y"), "axis"},
                \t}
                """), script);
        assertTrue(script.contains("\tlocal data = [[\noak_planks=1 oak_stairs=2 create:andesite_casing=3 birch_planks=1\n]]\n"), script);
        assertTrue(script.contains("local row = chroma_rows[getBlock(x, y, z)]\nif row == nil then return nil end\n"), script);
        // Not one dollar sign, which Axiom might read as the start of a control.
        assertFalse(script.contains("$"), script);
    }

    @Test
    void longDataWrapsIntoLines() {
        List<AxiomScript.Remap> rows = new ArrayList<>();
        for (int i = 0; i < 200; i++) rows.add(new AxiomScript.Remap("minecraft:block_" + i, new PaletteEntry("minecraft:stone", ""), List.of()));
        String script = AxiomScript.recolour("Many", rows);
        String data = script.substring(script.indexOf("[[\n") + 3, script.indexOf("\n]]"));
        assertEquals(200, data.split("\\s+").length);
        for (String line : data.split("\n")) assertTrue(line.length() < 100, line);
    }

    @Test
    void scatterWeighsItsPicks() {
        String script = AxiomScript.scatter(palette("Mix", new PaletteEntry("minecraft:stone", "", 3), new PaletteEntry("minecraft:oak_log", "axis=x", 1)));
        assertTrue(script.contains("\tput(block(\"minecraft\", \"stone\"), 3)\n\tput(block(\"minecraft\", \"oak_log\", \"axis=x\"), 1)\n"), script);
        assertTrue(script.contains("local roll = math.random() * chroma_total\n"), script);
        assertEquals(List.of("$boolean(Paint air, false)$"), controls(script));
    }

    @Test
    void aGradientAlongYHasBothEndsAsControls() {
        List<PaletteEntry> steps = List.of(new PaletteEntry("minecraft:white_wool", ""), new PaletteEntry("minecraft:black_wool", ""));
        String script = AxiomScript.gradient("Grey", steps, AxiomScript.Axis.Y, 70, 8);
        assertEquals(List.of("$int(Bottom, 70, -64, 319)$", "$int(Top, 77, -64, 319)$", "$float(Blend, 0.5, 0.0, 2.0)$", "$boolean(Paint air, false)$"), controls(script));
        assertTrue(script.contains("local t = ((y - first) * (span < 0 and -1 or 1) + 0.5) / (math.abs(span) + 1)\n"), script);
        // Downwards, the ends swap so the bottom control stays the lower.
        assertTrue(AxiomScript.gradient("Grey", steps, AxiomScript.Axis.Y, 70, -8).contains("$int(Bottom, 63, -64, 319)$"));
    }

    @Test
    void aGradientAlongXStartsWhereItWasMade() {
        List<PaletteEntry> steps = List.of(new PaletteEntry("minecraft:white_wool", ""), new PaletteEntry("minecraft:black_wool", ""));
        String script = AxiomScript.gradient("Grey", steps, AxiomScript.Axis.X, -1200, -300);
        assertTrue(script.contains("local first = -1200\nlocal last = first + $int(Length, -300, -300, 300)$\n"), script);
        assertTrue(script.contains("local t = ((x - first)"), script);
    }

    @Test
    void namesCannotAddControlsOrEndTheComment() {
        String script = AxiomScript.scatter(palette("Evil $int(Tries, 64, 0, 256)$\nreturn blocks.tnt", new PaletteEntry("minecraft:stone", "")));
        assertTrue(script.startsWith("-- Chroma Studio: Evil  int(Tries, 64, 0, 256) return blocks.tnt\n"), script);
        assertEquals(List.of("$boolean(Paint air, false)$"), controls(script));
        assertEquals("Untitled", AxiomScript.safe("  \n "));
        assertEquals("Untitled", AxiomScript.safe(null));
    }

    @Test
    void idsAreQuotedSoAnyPathWorks() {
        assertEquals("block(\"minecraft\", \"stone\")", AxiomScript.ref("minecraft:stone", ""));
        assertEquals("block(\"minecraft\", \"stone\")", AxiomScript.ref("stone", null));
        assertEquals("block(\"mod\", \"a/b.c-d\", \"axis=y\")", AxiomScript.ref("mod:a/b.c-d", "axis=y"));
        assertEquals("\"a\\\"b\\\\c\\036\"", AxiomScript.quote("a\"b\\c$"));
    }

    @Test
    void lengthIsCountedAsAxiomCountsIt() {
        assertEquals(0, AxiomScript.counted("local function end return \n\t "));
        assertEquals("x=1".length(), AxiomScript.counted("local x = 1"));
        assertTrue(AxiomScript.counted(AxiomScript.scatter(palette("P", new PaletteEntry("minecraft:stone", "")))) < AxiomScript.SERVER_LIMIT);
        assertTrue(AxiomScript.counted(AxiomScript.recolour("P", ROWS)) > 300);
    }

    @Test
    void whatCannotBeMadeIsRefused() {
        assertThrows(IllegalArgumentException.class, () -> AxiomScript.recolour("P", List.of()));
        assertThrows(IllegalArgumentException.class, () -> AxiomScript.scatter(palette("P")));
        List<PaletteEntry> one = List.of(new PaletteEntry("minecraft:stone", ""));
        assertThrows(IllegalArgumentException.class, () -> AxiomScript.gradient("G", one, AxiomScript.Axis.Y, 0, 4));
        List<PaletteEntry> two = List.of(new PaletteEntry("minecraft:stone", ""), new PaletteEntry("minecraft:dirt", ""));
        assertThrows(IllegalArgumentException.class, () -> AxiomScript.gradient("G", two, AxiomScript.Axis.Y, 0, 0));
    }
}
