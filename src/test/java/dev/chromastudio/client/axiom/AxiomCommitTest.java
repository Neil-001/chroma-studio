package dev.chromastudio.client.axiom;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.moulberry.axiom.editor.EditorUI;
import com.moulberry.axiom.editor.EditorWindowType;
import com.moulberry.axiom.mask.MaskManager;
import com.moulberry.axiom.editor.windows.global_mask.ToolMaskWindow;
import com.moulberry.axiom.mask.MaskContext;
import dev.chromastudio.client.GradientStore;
import dev.chromastudio.client.PaletteStore;
import dev.chromastudio.core.palette.StoredGradient;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.IdentityHashMap;
import com.moulberry.axiom.tools.Tool;
import com.moulberry.axiom.tools.ToolManager;
import com.moulberry.axiomclientapi.CustomTool;
import dev.chromastudio.client.index.TestIndex;
import dev.chromastudio.client.index.TintContext;
import dev.chromastudio.core.color.Face;
import dev.chromastudio.core.palette.BuildSearch;
import dev.chromastudio.core.palette.Palette;
import dev.chromastudio.core.palette.PaletteEntry;
import dev.chromastudio.core.palette.QueuedChanges;
import dev.chromastudio.core.palette.Recolour;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class AxiomCommitTest {
    private BlockState STONE, BRICKS;

    private FakeAxiom axiom;

    @BeforeAll
    static void loadGame() {
        TestIndex.loadGame();
        Map<String, String> blocks = new LinkedHashMap<>();
        blocks.put("oak_planks", "block/oak_planks");
        blocks.put("oak_door", "block/oak_door_bottom");
        blocks.put("spruce_planks", "block/spruce_planks");
        blocks.put("spruce_door", "block/spruce_door_bottom");
        blocks.put("stone_bricks", "block/stone_bricks");
        blocks.put("tuff_bricks", "block/tuff_bricks");
        TestIndex.index(blocks);
    }

    @BeforeEach
    void install() {
        axiom = FakeAxiom.install();
        STONE = Blocks.STONE.defaultBlockState();
        BRICKS = Blocks.STONE_BRICKS.defaultBlockState();
        FakeAxiom.clearReadout();
    }

    @AfterEach
    void uninstall() { FakeAxiom.uninstall(); }

    private BrushStroke bricks() throws Exception {
        BrushStroke stroke = new BrushStroke();
        stroke.start(axiom.level, AxiomChanges.queue(axiom.level), 1, _ -> (x, y, z, _, _, put) -> put.put(x, y, z, BRICKS));
        return stroke;
    }

    private void frame(BrushStroke stroke, boolean held, BlockPos... reached) {
        axiom.frame++;
        axiom.tools.down = held;
        axiom.reach.addAll(List.of(reached));
        stroke.render(axiom.rc);
    }

    private static BlockPos at(int x) { return new BlockPos(x, 0, 0); }

    @Test
    void aLuaMaskThatFailsPartwayThroughAStrokeSendsNothing() throws Exception {
        axiom.level.put(0, 0, 0, STONE);
        axiom.level.put(1, 0, 0, STONE);
        List<Integer> asked = new ArrayList<>();
        MaskManager.mask = FakeAxiom.lua(asked, x -> x == 1);
        BrushStroke stroke = bricks();
        frame(stroke, true, at(0), at(1));
        frame(stroke, false);
        assertEquals(List.of(), axiom.tools.pushed);
        assertEquals(FakeAxiom.refusal("mask_failed"), stroke.refused());
        assertEquals(List.of(0, 1), asked);
        assertFalse(stroke.active());
    }

    @Test
    void aLuaMaskErrorShowsInThePanel() {
        MaskManager.mask = FakeAxiom.lua(new ArrayList<>(), x -> x == 1);
        AxiomMask.Test mask = AxiomMask.now(axiom.level);
        assertTrue(mask.allows(0, 0, 0));
        assertFalse(mask.allows(1, 0, 0));
        assertTrue(mask.failed());
        assertTrue(AxiomMask.unavailable().isPresent());
    }

    private BuildPreview preview(QueuedChanges.Plan<BlockState> plan) {
        BuildPreview preview = new BuildPreview("test");
        AxiomMask.Test mask = AxiomMask.now(axiom.level);
        preview.show(plan.make(mask), mask, new long[0], plan);
        return preview;
    }

    private QueuedChanges.Plan<BlockState> row() {
        return mask -> {
            QueuedChanges<BlockState> q = AxiomChanges.queue(axiom.level);
            BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
            BuildSearch.Found found = BuildSearch.from(0, 0, 0, (x, y, z) -> y == 0 && z == 0 && x >= 0 && x <= 2
                    && !axiom.level.getBlockState(pos.set(x, y, z)).isAir(), 100);
            for (long p : found.blocks()) {
                int x = BuildSearch.x(p);
                BlockState now = axiom.level.getBlockState(pos.set(x, 0, 0));
                if (mask.allows(x, 0, 0)) q.add(x, 0, 0, now, BRICKS);
                else q.leave(x, 0, 0, now);
            }
            return q;
        };
    }

    @Test
    void anEditorSwitchedOffSinceSendsNothingThoughAxiomStillSaysItsActive() {
        axiom.level.put(0, 0, 0, STONE);
        BuildPreview preview = preview(row());
        EditorUI.enabled = false;
        assertEquals(Component.translatable("axiom.chromastudio.refused.editor"), preview.apply().orElse(null));
        assertEquals(List.of(), axiom.tools.pushed);
    }

    @Test
    void aStrokeHeldWhileAnotherToolWasPickedAndPutBackSendsNothing() throws Exception {
        axiom.level.put(0, 0, 0, STONE);
        BrushStroke stroke = bricks();
        frame(stroke, true, at(0));
        axiom.frame += 3;
        stroke.tick();
        frame(stroke, false);
        assertEquals(List.of(), axiom.tools.pushed);
        assertNotNull(stroke.refused());
    }

    @Test
    void aStrokeHeldThroughALagSpikeIsStillMade() throws Exception {
        axiom.level.put(0, 0, 0, STONE);
        BrushStroke stroke = bricks();
        frame(stroke, true, at(0));
        Thread.sleep(300);
        stroke.tick();
        frame(stroke, true);
        frame(stroke, false);
        assertEquals(List.of(Map.of(at(0), BRICKS)), axiom.tools.pushed);
        assertNull(stroke.refused());
    }

    @Test
    void aBlockThatJoinsTheBuildRefusesApplyAndASecondApplySendsNothing() {
        axiom.level.put(0, 0, 0, STONE);
        axiom.level.put(1, 0, 0, STONE);
        BuildPreview preview = preview(row());
        assertEquals(2, preview.count());
        axiom.level.put(2, 0, 0, STONE);
        assertEquals(FakeAxiom.refusal("changed"), preview.apply().orElse(null));
        assertEquals(List.of(), axiom.tools.pushed);
        assertTrue(preview.isEmpty());
        assertEquals(java.util.Set.of(), axiom.tools.overriding);
        assertTrue(preview.apply().isEmpty());
        assertEquals(List.of(), axiom.tools.pushed);
    }

    @Test
    void aMaskThatNowLetsInABlockItKeptOutRefusesApply() {
        axiom.level.put(0, 0, 0, STONE);
        axiom.level.put(1, 0, 0, STONE);
        MaskManager.mask = c -> c.x == 0;
        BuildPreview preview = preview(row());
        assertEquals(1, preview.count());
        MaskManager.mask = _ -> true;
        assertEquals(Component.translatable("axiom.chromastudio.refused.masks_differ"), preview.apply().orElse(null));
        assertEquals(List.of(), axiom.tools.pushed);
    }

    @Test
    void closingTheToolMasksWindowSinceRefusesApplyAndThePanelSaysNoMaskApplies() {
        axiom.level.put(0, 0, 0, STONE);
        axiom.level.put(1, 0, 0, STONE);
        MaskManager.mask = c -> c.x == 0;
        assertEquals(java.util.Optional.of(false), AxiomMask.windowClosed());
        BuildPreview preview = preview(row());
        assertEquals(1, preview.count());
        EditorWindowType.TOOL_MASKS.open = false;
        assertEquals(java.util.Optional.of(true), AxiomMask.windowClosed());
        assertEquals(Component.translatable("axiom.chromastudio.refused.masks_differ"), preview.apply().orElse(null));
        assertEquals(List.of(), axiom.tools.pushed);
    }

    @Test
    void aRefusedApplyLetsGoOfThePreviewSoNothingIsSentLater() {
        axiom.level.put(0, 0, 0, STONE);
        BuildPreview preview = preview(row());
        assertEquals(java.util.Set.of("test"), axiom.tools.overriding);
        MaskManager.mask = _ -> false;
        assertTrue(preview.apply().isPresent());
        assertEquals(java.util.Set.of(), axiom.tools.overriding);
        MaskManager.mask = _ -> true;
        assertTrue(preview.apply().isEmpty());
        assertEquals(List.of(), axiom.tools.pushed);
    }

    @Test
    void anAxiomThatThrowsWhenAChangeIsSentTurnsTheFeaturesOffAndLetsGoOfThePreview() {
        axiom.level.put(0, 0, 0, STONE);
        BuildPreview preview = new BuildPreview("test");
        QueuedChanges.Plan<BlockState> plan = row();
        CustomTool registered = axiom.register(new CustomTool() {
            @Override
            public String name() { return "Test tool"; }

            @Override
            public void reset() { preview.clear(); }

            @Override
            public boolean callConfirm() { return preview.apply().isEmpty(); }
        }, 'x');
        preview.show(plan.make(AxiomMask.now(axiom.level)), AxiomMask.now(axiom.level), new long[0], plan);
        axiom.tools.failPush = new AbstractMethodError("pushBlockRegionChange");
        assertDoesNotThrow(registered::callConfirm);
        assertEquals(java.util.Set.of(), axiom.tools.overriding);
        assertTrue(AxiomBridge.whyNot().isPresent());
    }

    @Test
    void anEditorThatThrowsWhenLeftTellsThePlayer() {
        EditorUI.throwOnDisable = new IllegalStateException("disable");
        AxiomEditor.leaveForWindow();
        assertEquals(Component.translatable("axiom.chromastudio.editor.unknown"), FakeAxiom.readout());
    }

    @Test
    void aLoneDoorHalfWhoseOtherHalfIsBuiltBeforeReleaseSendsNothing() throws Exception {
        BlockState lower = TestIndex.block("oak_door").defaultBlockState().setValue(DoorBlock.FACING, Direction.EAST)
                .setValue(DoorBlock.HALF, DoubleBlockHalf.LOWER);
        axiom.level.put(0, 0, 0, lower);
        Palette spruce = new Palette("Spruce");
        spruce.entries.add(new PaletteEntry("minecraft:spruce_planks", ""));
        BrushStroke stroke = new BrushStroke();
        stroke.start(axiom.level, AxiomChanges.queue(axiom.level), 1, level -> {
            RecolourPlan plan = RecolourPlan.of(spruce, null, Recolour.Mode.SHADING, Face.NORTH, TintContext.none());
            return (x, y, z, _, admit, put) -> plan.change(level, x, y, z, admit, put);
        });
        frame(stroke, true, at(0));
        axiom.level.put(0, 1, 0, lower.setValue(DoorBlock.HALF, DoubleBlockHalf.UPPER));
        frame(stroke, false);
        assertEquals(List.of(), axiom.tools.pushed);
        assertEquals(FakeAxiom.refusal("changed"), stroke.refused());
    }

    @Test
    void aLuaMaskThatFailsOnlyOnTheStrokesOwnPassStillSendsNothing() throws Exception {
        axiom.level.put(0, 0, 0, STONE);
        axiom.level.put(1, 0, 0, STONE);
        int[] contexts = {0};
        MaskManager.mask = c -> {
            if (!c.compiled) {
                c.compiled = true;
                contexts[0]++;
                ToolMaskWindow.luaExecutionError = null;
            }
            if (c.broken) return false;
            if (c.x == 1 && contexts[0] == 1) {
                ToolMaskWindow.luaExecutionError = "attempt to compare nil with number";
                c.broken = true;
                return false;
            }
            return c.x != 1;
        };
        BrushStroke stroke = bricks();
        frame(stroke, true, at(0));
        frame(stroke, true, at(1));
        frame(stroke, false);
        assertEquals(List.of(), axiom.tools.pushed);
        assertEquals(FakeAxiom.refusal("mask_failed"), stroke.refused());
    }

    @Test
    void aMaskThatAnswersOtherwiseWhenCheckedSaysToSeedItsNoise() throws Exception {
        axiom.level.put(0, 0, 0, STONE);
        Map<MaskContext, Integer> seeds = new IdentityHashMap<>();
        MaskManager.mask = c -> seeds.computeIfAbsent(c, _ -> seeds.size()) == 0;
        BrushStroke stroke = bricks();
        frame(stroke, true, at(0));
        frame(stroke, false);
        assertEquals(List.of(), axiom.tools.pushed);
        assertEquals(Component.translatable("axiom.chromastudio.refused.masks_differ"), stroke.refused());
    }

    @Test
    void anAxiomThatThrowsWhileLettingGoOfThePreviewStillLetsGoOfIt() {
        axiom.level.put(0, 0, 0, STONE);
        PreviewTool tool = new PreviewTool();
        CustomTool registered = axiom.register(new CustomTool() {
            @Override
            public String name() { return "Test tool"; }

            @Override
            public void reset() { tool.preview.clear(); }

            @Override
            public boolean callConfirm() { return tool.preview.apply().isEmpty(); }
        }, 'x');
        QueuedChanges.Plan<BlockState> plan = row();
        AxiomMask.Test mask = AxiomMask.now(axiom.level);
        assertTrue(tool.preview.show(plan.make(mask), mask, new long[0], plan));
        axiom.tools.failRelease = new IllegalStateException("release");
        assertDoesNotThrow(registered::callConfirm);
        assertTrue(AxiomBridge.whyNot().isPresent());
        assertEquals(java.util.Set.of(), axiom.tools.overriding);
        assertEquals(List.of(), axiom.tools.pushed);
    }

    private static void saveGradient(String name, String from, String to) {
        GradientStore.get().put(new StoredGradient(name, List.of(StoredGradient.Stop.colour(0), StoredGradient.Stop.colour(0xFFFFFF)), 2,
                "LINEAR", "SHORTEST", false, "", List.of(new StoredGradient.Step(0, new PaletteEntry(from, ""), false),
                new StoredGradient.Step(0xFFFFFF, new PaletteEntry(to, ""), false))));
    }

    private GradientTool gradientAcrossARow() {
        axiom.client();
        BlockState oak = TestIndex.block("oak_planks").defaultBlockState();
        for (int x = 0; x <= 5; x++) axiom.level.put(x, 0, 0, oak);
        saveGradient("Commit test", "minecraft:spruce_planks", "minecraft:oak_planks");
        GradientTool tool = new GradientTool();
        FakeAxiom.set(GradientTool.class, tool, "picked", "Commit test");
        axiom.tools.hit = at(0);
        assertTrue(tool.callUseTool());
        axiom.tools.hit = at(5);
        assertTrue(tool.callUseTool());
        axiom.frame++;
        tool.render(axiom.rc);
        assertEquals(TestIndex.block("spruce_planks").defaultBlockState(), axiom.tools.overrides.get(at(0)));
        return tool;
    }

    @Test
    void swappingTheEndsAndConfirmingInTheSameFrameSendsNothing() {
        GradientTool tool = gradientAcrossARow();
        FakeAxiom.set(GradientTool.class, tool, "reversed", true);
        assertFalse(tool.callConfirm());
        assertEquals(List.of(), axiom.tools.pushed);
        assertEquals(FakeAxiom.refusal("changed"), FakeAxiom.get(GradientTool.class, tool, "refused"));
    }

    @Test
    void swappingTheEndsThenConfirmingOnceThePreviewShowsItSendsWhatItShows() {
        GradientTool tool = gradientAcrossARow();
        FakeAxiom.set(GradientTool.class, tool, "reversed", true);
        axiom.frame++;
        tool.render(axiom.rc);
        Map<BlockPos, BlockState> shown = new LinkedHashMap<>(axiom.tools.overrides);
        assertEquals(TestIndex.block("spruce_planks").defaultBlockState(), shown.get(at(5)));
        assertTrue(tool.callConfirm());
        assertEquals(List.of(shown), axiom.tools.pushed);
    }

    @Test
    void choosingAnotherPaletteAndApplyingInTheSameFrameSendsNothing() throws Exception {
        axiom.client();
        BlockState oak = TestIndex.block("oak_planks").defaultBlockState();
        for (int x = 0; x <= 2; x++) axiom.level.put(x, 0, 0, oak);
        Palette spruce = PaletteStore.get().create("Commit test spruce", List.of(new PaletteEntry("minecraft:spruce_planks", "")));
        Palette other = PaletteStore.get().create("Commit test oak", List.of(new PaletteEntry("minecraft:oak_planks", "")));
        try {
            PaletteBrushTool tool = new PaletteBrushTool();
            Method keys = PaletteBrushTool.class.getDeclaredMethod("keys", List.class);
            keys.setAccessible(true);
            List<?> key = (List<?>) keys.invoke(null, List.of(spruce, other));
            Field mode = PaletteBrushTool.class.getDeclaredField("mode");
            mode.setAccessible(true);
            FakeAxiom.set(PaletteBrushTool.class, tool, "mode", mode.getType().getEnumConstants()[1]);
            FakeAxiom.set(PaletteBrushTool.class, tool, "wholeBuild", true);
            FakeAxiom.set(PaletteBrushTool.class, tool, "fromKey", "build");
            FakeAxiom.set(PaletteBrushTool.class, tool, "paletteKey", key.get(0));
            axiom.tools.hit = at(0);
            assertTrue(tool.callUseTool());
            axiom.frame++;
            tool.render(axiom.rc);
            assertEquals(TestIndex.block("spruce_planks").defaultBlockState(), axiom.tools.overrides.get(at(0)));
            FakeAxiom.set(PaletteBrushTool.class, tool, "paletteKey", key.get(1));
            assertFalse(tool.callConfirm());
            assertEquals(List.of(), axiom.tools.pushed);
            assertEquals(FakeAxiom.refusal("changed"), FakeAxiom.get(PaletteBrushTool.class, tool, "refused"));
        } finally {
            PaletteStore.get().delete(spruce);
            PaletteStore.get().delete(other);
        }
    }

    @Test
    void aShadeStrokeWorksItsShadesOutAgainWhenLetGo() {
        axiom.client();
        axiom.level.put(0, 0, 0, BRICKS);
        Palette shades = PaletteStore.get().create("Commit test shades",
                new ArrayList<>(List.of(new PaletteEntry("minecraft:stone_bricks", ""), new PaletteEntry("minecraft:tuff_bricks", ""))));
        try {
            ShadeBrushTool tool = new ShadeBrushTool();
            FakeAxiom.set(ShadeBrushTool.class, tool, "from", shades);
            assertTrue(tool.callUseTool());
            axiom.frame++;
            axiom.tools.down = true;
            axiom.reach.add(at(0));
            tool.render(axiom.rc);
            shades.entries.removeLast();
            axiom.frame++;
            axiom.tools.down = false;
            tool.render(axiom.rc);
            assertEquals(List.of(), axiom.tools.pushed);
            BrushStroke stroke = (BrushStroke) FakeAxiom.get(ShadeBrushTool.class, tool, "stroke");
            assertEquals(FakeAxiom.refusal("changed"), stroke.refused());
        } finally {
            PaletteStore.get().delete(shades);
        }
    }

    @Test
    void anOrdinaryShadeStrokeIsSent() {
        axiom.client();
        axiom.level.put(0, 0, 0, BRICKS);
        Palette shades = PaletteStore.get().create("Commit test shades",
                new ArrayList<>(List.of(new PaletteEntry("minecraft:stone_bricks", ""), new PaletteEntry("minecraft:tuff_bricks", ""))));
        try {
            ShadeBrushTool tool = new ShadeBrushTool();
            FakeAxiom.set(ShadeBrushTool.class, tool, "from", shades);
            assertTrue(tool.callUseTool());
            axiom.frame++;
            axiom.tools.down = true;
            axiom.reach.add(at(0));
            tool.render(axiom.rc);
            axiom.frame++;
            axiom.tools.down = false;
            tool.render(axiom.rc);
            assertEquals(List.of(Map.of(at(0), TestIndex.block("tuff_bricks").defaultBlockState())), axiom.tools.pushed);
        } finally {
            PaletteStore.get().delete(shades);
        }
    }

    @Test
    void aMaskTestThatRaisedALuaErrorStaysFailedOnceAxiomClearsIt() {
        MaskManager.mask = FakeAxiom.lua(new ArrayList<>(), x -> x == 1);
        AxiomMask.Test mask = AxiomMask.now(axiom.level);
        assertTrue(mask.allows(0, 0, 0));
        assertFalse(mask.allows(1, 0, 0));
        ToolMaskWindow.luaExecutionError = null;
        assertTrue(mask.failed());
    }

    @Test
    void aLuaErrorLeftFromBeforeIsntKeptOnceItsDismissed() {
        ToolMaskWindow.luaExecutionError = "attempt to compare nil with number";
        AxiomMask.Test mask = AxiomMask.now(axiom.level);
        assertTrue(mask.allows(0, 0, 0));
        assertTrue(mask.failed());
        ToolMaskWindow.luaExecutionError = null;
        assertFalse(mask.failed());
    }

    @Test
    void aMaskScriptThatNoLongerCompilesRefusesApply() {
        axiom.level.put(0, 0, 0, STONE);
        axiom.level.put(1, 0, 0, STONE);
        MaskManager.useLuaScript = true;
        MaskManager.luaScript = "return true";
        FakeAxiom.set(ToolMaskWindow.class, null, "lastLuaCompile", 1L);
        BuildPreview preview = preview(row());
        assertEquals(2, preview.count());
        MaskManager.luaScript = null;
        assertEquals(FakeAxiom.refusal("mask_failed"), preview.apply().orElse(null));
        assertEquals(List.of(), axiom.tools.pushed);
        assertEquals(java.util.Optional.of(Component.translatable("axiom.chromastudio.mask.lua_invalid")), AxiomMask.unavailable());
    }

    @Test
    void theScriptingTabBeforeItHasCompiledAnythingKeepsNothingOut() {
        axiom.level.put(0, 0, 0, STONE);
        MaskManager.useLuaScript = true;
        BuildPreview preview = preview(row());
        assertTrue(preview.apply().isEmpty());
        assertEquals(1, axiom.tools.pushed.size());
    }

    @Test
    void aBlockChangedUnderAStrokeIsntPutDownToTheMasks() throws Exception {
        axiom.level.put(0, 0, 0, STONE);
        axiom.level.put(1, 0, 0, BRICKS);
        Map<MaskContext, Integer> contexts = new IdentityHashMap<>();
        MaskManager.mask = c -> contexts.computeIfAbsent(c, _ -> contexts.size()) == 0 || c.x != 1;
        BrushStroke stroke = bricks();
        frame(stroke, true, at(0), at(1));
        axiom.level.put(0, 0, 0, Blocks.ANDESITE.defaultBlockState());
        frame(stroke, false);
        assertEquals(List.of(), axiom.tools.pushed);
        assertEquals(FakeAxiom.refusal("changed"), stroke.refused());
    }

    @Test
    void aShadeStrokeWhoseRadiusWasChangedWhileHeldSendsNothing() {
        axiom.client();
        axiom.level.put(0, 0, 0, BRICKS);
        Palette shades = PaletteStore.get().create("Commit test shades",
                new ArrayList<>(List.of(new PaletteEntry("minecraft:stone_bricks", ""), new PaletteEntry("minecraft:tuff_bricks", ""))));
        try {
            ShadeBrushTool tool = new ShadeBrushTool();
            FakeAxiom.set(ShadeBrushTool.class, tool, "from", shades);
            assertTrue(tool.callUseTool());
            axiom.frame++;
            axiom.tools.down = true;
            axiom.reach.add(at(0));
            tool.render(axiom.rc);
            ((int[]) FakeAxiom.get(ShadeBrushTool.class, tool, "radius"))[0] = 0;
            axiom.frame++;
            axiom.tools.down = false;
            tool.render(axiom.rc);
            assertEquals(List.of(), axiom.tools.pushed);
            BrushStroke stroke = (BrushStroke) FakeAxiom.get(ShadeBrushTool.class, tool, "stroke");
            assertEquals(FakeAxiom.refusal("changed"), stroke.refused());
        } finally {
            PaletteStore.get().delete(shades);
        }
    }

    @Test
    void tellingThePlayerWhyNeverRunsTheMasksAgain() throws Exception {
        BlockState lower = TestIndex.block("oak_door").defaultBlockState().setValue(DoorBlock.FACING, Direction.EAST)
                .setValue(DoorBlock.HALF, DoubleBlockHalf.LOWER);
        axiom.level.put(0, 0, 0, lower);
        Palette spruce = new Palette("Spruce");
        spruce.entries.add(new PaletteEntry("minecraft:spruce_planks", ""));
        Map<MaskContext, Integer> contexts = new IdentityHashMap<>();
        MaskManager.mask = c -> {
            if (contexts.computeIfAbsent(c, _ -> contexts.size()) == 0) return true;
            if (c.y == 1) ToolMaskWindow.luaExecutionError = "attempt to compare nil with number";
            return false;
        };
        BrushStroke stroke = new BrushStroke();
        stroke.start(axiom.level, AxiomChanges.queue(axiom.level), 1, level -> {
            RecolourPlan plan = RecolourPlan.of(spruce, null, Recolour.Mode.SHADING, Face.NORTH, TintContext.none());
            return (x, y, z, _, admit, put) -> plan.change(level, x, y, z, admit, put);
        });
        frame(stroke, true, at(0));
        axiom.level.put(0, 1, 0, lower.setValue(DoorBlock.HALF, DoubleBlockHalf.UPPER));
        frame(stroke, false);
        assertEquals(List.of(), axiom.tools.pushed);
        assertEquals(FakeAxiom.refusal("changed"), stroke.refused());
        assertNull(ToolMaskWindow.luaExecutionError);
    }

    private static final class PreviewTool implements CustomTool, GuardedTool.PutAway {
        final BuildPreview preview = new BuildPreview("test");

        @Override
        public String name() { return "Test tool"; }

        @Override
        public void putAway() { preview.clear(); }
    }

    @Test
    void pickingAnotherToolLetsGoOfThePreviewAtOnceThoughAxiomDoesntResetIt() {
        axiom.level.put(0, 0, 0, STONE);
        ToolManager.tools.add(new Tool() {
            @Override
            public char iconChar() { return 'o'; }

            @Override
            public String name() { return "Test tool"; }
        });
        PreviewTool tool = new PreviewTool();
        axiom.register(tool, 'x');
        QueuedChanges.Plan<BlockState> plan = row();
        assertTrue(tool.preview.show(plan.make(AxiomMask.now(axiom.level)), AxiomMask.now(axiom.level), new long[0], plan));
        ToolManager.tools.getFirst().toolDeselected();
        assertEquals(java.util.Set.of(), axiom.tools.overriding);
        assertTrue(tool.preview.isEmpty());
        assertDoesNotThrow(() -> axiom.tools.acquireChunkRenderOverrider("axiom_text"));
    }

    @Test
    void aPreviewWhileAnotherToolHoldsTheWorldsLookShowsNothingAndKeepsTheFeaturesOn() {
        axiom.level.put(0, 0, 0, STONE);
        axiom.tools.acquireChunkRenderOverrider("axiom_text");
        QueuedChanges.Plan<BlockState> plan = row();
        BuildPreview preview = new BuildPreview("test");
        assertFalse(preview.show(plan.make(AxiomMask.now(axiom.level)), AxiomMask.now(axiom.level), new long[0], plan));
        assertTrue(preview.isEmpty());
        assertTrue(preview.apply().isEmpty());
        assertEquals(List.of(), axiom.tools.pushed);
        assertTrue(AxiomBridge.whyNot().isEmpty());
    }

    @Test
    void anIconIsLeftAsThePencilWhenAxiomKeysTheToolsEntry() {
        Tool entry = new Tool() {
            @Override
            public char iconChar() { return 'o'; }

            @Override
            public String name() { return "Test tool"; }
        };
        ToolManager.tools.add(entry);
        ToolManager.keybindMap.put(entry, "bound");
        AxiomIcons.give(new GuardedTool(() -> "Test tool"), 'x');
        assertTrue(ToolManager.tools.getFirst() == entry);
    }

    @Test
    void anIconIsSwappedWhenNothingKeysTheToolsEntry() {
        Tool entry = new Tool() {
            @Override
            public char iconChar() { return 'o'; }

            @Override
            public String name() { return "Test tool"; }
        };
        ToolManager.tools.add(entry);
        AxiomIcons.give(new GuardedTool(() -> "Test tool"), 'x');
        assertEquals('x', ToolManager.tools.getFirst().iconChar());
        assertEquals("Test tool", ToolManager.tools.getFirst().name());
    }
}
