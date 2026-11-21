package dev.chromastudio.client.axiom;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.chromastudio.client.GradientStore;
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
    void theFromPaletteFollowsARenameAndNeverBecomesAnother() throws Exception {
        PaletteStore store = PaletteStore.get();
        Palette bricks = store.create("Panel test from", List.of(new PaletteEntry("minecraft:stone_bricks", "")));
        Palette same = null;
        try {
            PaletteBrushTool tool = new PaletteBrushTool();
            Method keys = PaletteBrushTool.class.getDeclaredMethod("keys", List.class);
            keys.setAccessible(true);
            FakeAxiom.set(PaletteBrushTool.class, tool, "fromKey", ((List<?>) keys.invoke(null, List.of(bricks))).getFirst());
            Method from = PaletteBrushTool.class.getDeclaredMethod("from");
            from.setAccessible(true);
            assertSame(bricks, from.invoke(tool));
            store.rename(bricks, "Panel test from, renamed");
            same = store.create("Panel test from", List.of(new PaletteEntry("minecraft:oak_planks", "")));
            assertSame(bricks, from.invoke(tool), "a new palette with the old name doesn't become From");
            store.delete(bricks);
            assertNull(from.invoke(tool), "a deleted From stays gone");
        } finally {
            store.delete(bricks);
            if (same != null) store.delete(same);
        }
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
}
