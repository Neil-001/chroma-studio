package dev.chromastudio.client.axiom;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.moulberry.axiom.editor.EditorWindowType;
import com.moulberry.axiom.mask.MaskManager;
import dev.chromastudio.client.GradientStore;
import dev.chromastudio.client.ModSettings;
import dev.chromastudio.client.PaletteStore;
import dev.chromastudio.client.index.TestIndex;
import dev.chromastudio.core.palette.Palette;
import dev.chromastudio.core.palette.PaletteEntry;
import dev.chromastudio.core.palette.StoredGradient;
import imgui.moulberry92.ImGui;
import java.lang.reflect.Method;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.block.Blocks;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class PanelTest {
    private FakeAxiom axiom;

    @BeforeAll
    static void loadGame() {
        TestIndex.loadGame();
        Map<String, String> blocks = new LinkedHashMap<>();
        blocks.put("stone_bricks", "block/stone_bricks");
        blocks.put("oak_planks", "block/oak_planks");
        blocks.put("tuff_bricks", "block/tuff_bricks");
        TestIndex.index(blocks);
    }

    @BeforeEach
    void install() {
        axiom = FakeAxiom.install();
        axiom.client();
        ImGui.reset();
    }

    @AfterEach
    void uninstall() {
        ImGui.reset();
        FakeAxiom.uninstall();
    }

    private static void frame(GuardedTool tool, String... pressed) {
        for (String key : pressed) ImGui.press.add(AxiomTools.tr(key));
        tool.displayImguiOptions();
        ImGui.press.clear();
    }

    @Test
    void clearingASavedSampleLeavesTheToolsOn() {
        for (int x = 0; x <= 2; x++) axiom.level.put(x, 0, 0, Blocks.STONE_BRICKS.defaultBlockState());
        axiom.tools.hit = new BlockPos(1, 0, 0);
        SamplerTool sampler = new SamplerTool();
        GuardedTool tool = new GuardedTool(sampler);
        frame(tool);
        frame(tool, "axiom.chromastudio.whole_build");
        assertTrue(tool.callUseTool());
        Palette before = PaletteStore.get().selected();
        frame(tool, "axiom.chromastudio.sampler.save");
        Palette saved = PaletteStore.get().selected();
        assertNotSame(before, saved, "drawn " + ImGui.drawn + ", greyed " + ImGui.disabledButtons);
        try {
            assertEquals(1, saved.entries.size(), saved.name);
            frame(tool, "axiom.chromastudio.sampler.clear");
            frame(tool);
            assertEquals(Optional.<Component>empty(), AxiomBridge.whyNot());
            assertTrue(PaletteStore.get().own().contains(saved));
        } finally {
            PaletteStore.get().delete(saved);
        }
    }

    @Test
    void undoAfterClearBringsBackTheSampleToSave() {
        for (int x = 0; x <= 2; x++) axiom.level.put(x, 0, 0, Blocks.STONE_BRICKS.defaultBlockState());
        axiom.tools.hit = new BlockPos(1, 0, 0);
        GuardedTool tool = new GuardedTool(new SamplerTool());
        frame(tool);
        frame(tool, "axiom.chromastudio.whole_build");
        assertTrue(tool.callUseTool());
        frame(tool, "axiom.chromastudio.sampler.clear");
        Palette before = PaletteStore.get().selected();
        frame(tool, "axiom.chromastudio.sampler.save");
        assertSame(before, PaletteStore.get().selected(), "a cleared sample can't be saved");
        frame(tool, "axiom.chromastudio.sampler.undo");
        frame(tool, "axiom.chromastudio.sampler.save");
        Palette saved = PaletteStore.get().selected();
        assertNotSame(before, saved, "drawn " + ImGui.drawn + ", greyed " + ImGui.disabledButtons);
        try {
            assertEquals(List.of(new PaletteEntry("minecraft:stone_bricks", "", 3)), saved.entries);
        } finally {
            PaletteStore.get().delete(saved);
        }
    }

    @Test
    void undoAfterSavingKeepsTheSampleSaved() {
        for (int x = 0; x <= 2; x++) axiom.level.put(x, 0, 0, Blocks.STONE_BRICKS.defaultBlockState());
        axiom.tools.hit = new BlockPos(1, 0, 0);
        GuardedTool tool = new GuardedTool(new SamplerTool());
        frame(tool);
        frame(tool, "axiom.chromastudio.whole_build");
        assertTrue(tool.callUseTool());
        frame(tool, "axiom.chromastudio.sampler.save");
        Palette saved = PaletteStore.get().selected();
        try {
            frame(tool, "axiom.chromastudio.sampler.clear");
            frame(tool, "axiom.chromastudio.sampler.undo");
            frame(tool, "axiom.chromastudio.sampler.save");
            assertSame(saved, PaletteStore.get().selected(), "undo keeps the sample saved, so Save makes no second palette");
        } finally {
            PaletteStore.get().delete(saved);
        }
    }

    @Test
    void theFromPaletteFollowsARenameAndNeverBecomesAnother() throws Exception {
        PaletteStore store = PaletteStore.get();
        Palette bricks = store.create("Panel test from", List.of(new PaletteEntry("minecraft:stone_bricks", "")));
        Palette same = null, again = null;
        String was = ModSettings.brushFrom;
        try {
            ModSettings.brushFrom = (String) ((List<?>) keys().invoke(null, List.of(bricks))).getFirst();
            PaletteBrushTool tool = new PaletteBrushTool();
            tool.tick();
            store.rename(bricks, "Panel test from, renamed");
            same = store.create("Panel test from", List.of(new PaletteEntry("minecraft:oak_planks", "")));
            tool.tick();
            assertSame(bricks, from(tool), "a new palette with the old name doesn't become From");
            assertEquals(((List<?>) keys().invoke(null, List.of(bricks))).getFirst(), ModSettings.brushFrom, "the renamed palette's key is saved");
            store.delete(bricks);
            tool.tick();
            assertNull(from(tool), "a deleted From stays gone");
            again = store.create("Panel test from, renamed", List.of(new PaletteEntry("minecraft:oak_planks", "")));
            assertNull(from(tool), "a palette made under the deleted one's name doesn't take its place");
            Object bound = FakeAxiom.get(PaletteBrushTool.class, tool, "fromBound");
            Method choose = bound.getClass().getDeclaredMethod("choose", Palette.class, String.class);
            choose.setAccessible(true);
            choose.invoke(bound, again, ModSettings.brushFrom);
            assertSame(again, from(tool), "choosing it in From takes it");
        } finally {
            store.delete(bricks);
            if (same != null) store.delete(same);
            if (again != null) store.delete(again);
            ModSettings.brushFrom = was;
        }
    }

    private static Method keys() throws Exception {
        Method keys = PaletteBrushTool.class.getDeclaredMethod("keys", List.class);
        keys.setAccessible(true);
        return keys;
    }

    private static Object from(PaletteBrushTool tool) throws Exception {
        Method from = PaletteBrushTool.class.getDeclaredMethod("from");
        from.setAccessible(true);
        return from.invoke(tool);
    }

    @Test
    void aFailureThatIsntAxiomsVersionSaysToRestart() {
        AxiomBridge.fail(new NullPointerException());
        assertEquals(Optional.of(Component.translatable("axiom.chromastudio.stopped")), AxiomBridge.whyNot());
        AxiomBridge.forget();
        AxiomBridge.fail(new NoSuchMethodError());
        assertEquals(Optional.of(Component.translatable("axiom.chromastudio.incompatible")), AxiomBridge.whyNot());
    }

    @Test
    void anEmptyPaletteIsAskedForBeforeABuild() throws Exception {
        Palette empty = PaletteStore.get().create("Panel test empty", List.of());
        try {
            PaletteBrushTool brush = new PaletteBrushTool();
            Method keys = PaletteBrushTool.class.getDeclaredMethod("keys", List.class);
            keys.setAccessible(true);
            FakeAxiom.set(PaletteBrushTool.class, brush, "paletteKey", ((List<?>) keys.invoke(null, List.of(empty))).getFirst());
            FakeAxiom.set(PaletteBrushTool.class, brush, "wholeBuild", true);
            GuardedTool tool = new GuardedTool(brush);
            frame(tool);
            frame(tool, "axiom.chromastudio.brush.recolour");
            ImGui.drawn.clear();
            frame(tool);
            assertTrue(ImGui.drawn.contains(AxiomTools.tr("axiom.chromastudio.brush.no_entries")), ImGui.drawn.toString());
            assertFalse(ImGui.drawn.contains(AxiomTools.tr("axiom.chromastudio.brush.pick")), ImGui.drawn.toString());
        } finally {
            PaletteStore.get().delete(empty);
        }
    }

    @Test
    void withNoUsableGradientTheGradientToolDoesntAskForAStart() {
        PaletteEntry slab = new PaletteEntry("minecraft:stone_brick_slab", "");
        StoredGradient slabs = new StoredGradient("Panel test slabs", List.of(StoredGradient.Stop.colour(0), StoredGradient.Stop.colour(0xFFFFFF)), 2,
                "LINEAR", "SHORTEST", false, "", List.of(new StoredGradient.Step(0, slab, false), new StoredGradient.Step(0xFFFFFF, slab, false)));
        assertTrue(GradientStore.get().put(slabs));
        try {
            GradientTool gradient = new GradientTool();
            FakeAxiom.set(GradientTool.class, gradient, "picked", slabs.name());
            frame(new GuardedTool(gradient));
            assertTrue(ImGui.drawn.contains(AxiomTools.tr("axiom.chromastudio.gradient_tool.unusable")), ImGui.drawn.toString());
            assertFalse(ImGui.drawn.contains(AxiomTools.tr("axiom.chromastudio.gradient_tool.start")), ImGui.drawn.toString());
        } finally {
            GradientStore.get().delete(slabs.name());
        }
    }

    @Test
    void theShadeBrushSaysWhenTheMasksKeepThePointedBlockOut() {
        axiom.level.put(0, 0, 0, Blocks.STONE_BRICKS.defaultBlockState());
        axiom.tools.hit = new BlockPos(0, 0, 0);
        MaskManager.mask = _ -> false;
        ShadeBrushTool shade = new ShadeBrushTool();
        GuardedTool tool = new GuardedTool(shade);
        tool.render(axiom.rc);
        frame(tool);
        frame(tool);
        assertTrue(ImGui.drawn.contains(AxiomTools.tr("axiom.chromastudio.shade.left.masked")), ImGui.drawn.toString());
    }

    @Test
    void theShadeBrushNamesThePointedBlockAndWhatItBecomes() {
        axiom.level.put(0, 0, 0, Blocks.STONE_BRICKS.defaultBlockState());
        axiom.tools.hit = new BlockPos(0, 0, 0);
        MaskManager.mask = _ -> true;
        Palette shades = PaletteStore.get().create("Panel test named shades",
                List.of(new PaletteEntry("minecraft:stone_bricks", ""), new PaletteEntry("minecraft:tuff_bricks", "")));
        try {
            ShadeBrushTool shade = new ShadeBrushTool();
            FakeAxiom.set(ShadeBrushTool.class, shade, "from", shades);
            GuardedTool tool = new GuardedTool(shade);
            tool.render(axiom.rc);
            frame(tool);
            frame(tool);
            String tuff = AxiomTools.tr("axiom.chromastudio.shade.becomes", TestIndex.block("tuff_bricks").getName());
            assertTrue(ImGui.drawn.contains(Blocks.STONE_BRICKS.getName().getString()), ImGui.drawn.toString());
            assertTrue(ImGui.drawn.contains(tuff), ImGui.drawn.toString());
            assertFalse(ImGui.tips.contains(tuff), ImGui.tips.toString());
            assertTrue(ImGui.tips.contains("minecraft:tuff_bricks"), ImGui.tips.toString());
        } finally {
            PaletteStore.get().delete(shades);
        }
    }

    @Test
    void theShadeBrushForgetsTheBlockOncePointingAway() {
        axiom.level.put(0, 0, 0, Blocks.STONE_BRICKS.defaultBlockState());
        axiom.tools.hit = new BlockPos(0, 0, 0);
        ShadeBrushTool shade = new ShadeBrushTool();
        GuardedTool tool = new GuardedTool(shade);
        tool.render(axiom.rc);
        assertSame(Blocks.STONE_BRICKS.defaultBlockState(), FakeAxiom.get(ShadeBrushTool.class, shade, "pointed"));
        axiom.tools.hit = null;
        axiom.frame += 2;
        tool.render(axiom.rc);
        assertNull(FakeAxiom.get(ShadeBrushTool.class, shade, "pointed"));
    }

    @Test
    void theLastStrokeStaysWhenThePaletteHasNoShades() {
        Palette logs = PaletteStore.get().create("Panel test logs", List.of(new PaletteEntry("minecraft:oak_log", "")));
        try {
            ShadeBrushTool shade = new ShadeBrushTool();
            FakeAxiom.set(ShadeBrushTool.class, shade, "from", logs);
            FakeAxiom.set(BrushStroke.class, FakeAxiom.get(ShadeBrushTool.class, shade, "stroke"), "sent", 40);
            GuardedTool tool = new GuardedTool(shade);
            frame(tool);
            frame(tool);
            assertTrue(ImGui.drawn.contains(AxiomTools.tr("axiom.chromastudio.shade.no_shades")), ImGui.drawn.toString());
            assertTrue(ImGui.drawn.contains(AxiomTools.count("axiom.chromastudio.changed", 40, 40)), ImGui.drawn.toString());
        } finally {
            PaletteStore.get().delete(logs);
        }
    }

    @Test
    void underALuaMaskTheShadeBrushLeavesTheReplacementToTheScript() {
        axiom.level.put(0, 0, 0, Blocks.STONE_BRICKS.defaultBlockState());
        axiom.tools.hit = new BlockPos(0, 0, 0);
        MaskManager.mask = _ -> false;
        Palette shades = PaletteStore.get().create("Panel test shades",
                List.of(new PaletteEntry("minecraft:stone_bricks", ""), new PaletteEntry("minecraft:tuff_bricks", "")));
        try {
            ShadeBrushTool shade = new ShadeBrushTool();
            FakeAxiom.set(ShadeBrushTool.class, shade, "from", shades);
            GuardedTool tool = new GuardedTool(shade);
            tool.render(axiom.rc);
            frame(tool);
            frame(tool);
            assertTrue(ImGui.drawn.contains(AxiomTools.tr("axiom.chromastudio.shade.left.masked")), ImGui.drawn.toString());

            MaskManager.useLuaScript = true;
            MaskManager.luaScript = "return false";
            ImGui.reset();
            frame(tool);
            Component tuff = TestIndex.block("tuff_bricks").getName();
            String scripted = AxiomTools.tr("axiom.chromastudio.shade.becomes.scripted", tuff);
            assertTrue(ImGui.drawn.contains(scripted), ImGui.drawn.toString());
            assertFalse(ImGui.drawn.contains(AxiomTools.tr("axiom.chromastudio.shade.becomes", tuff)), ImGui.drawn.toString());
            assertFalse(ImGui.drawn.contains(AxiomTools.tr("axiom.chromastudio.shade.left.masked")), ImGui.drawn.toString());

            EditorWindowType.TOOL_MASKS.open = false;
            ImGui.reset();
            frame(tool);
            assertTrue(ImGui.drawn.contains(AxiomTools.tr("axiom.chromastudio.shade.becomes", tuff)), ImGui.drawn.toString());
        } finally {
            PaletteStore.get().delete(shades);
        }
    }

    @Test
    void aMaskThatThrowsOnHoverLeavesTheMasksWorking() {
        axiom.level.put(0, 0, 0, Blocks.STONE_BRICKS.defaultBlockState());
        axiom.tools.hit = new BlockPos(0, 0, 0);
        MaskManager.mask = _ -> {
            throw new IllegalStateException("a mask that fails");
        };
        new GuardedTool(new ShadeBrushTool()).render(axiom.rc);
        assertTrue(AxiomMask.INTERNALS.reason().isEmpty(), AxiomMask.INTERNALS.reason().toString());
    }
}
