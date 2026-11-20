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

/**
 * The rule for when a tool may send its changes to Axiom, through the classes that send them, against a stand-in
 * Axiom. Each case is one a review found getting through: the changes a stroke or preview queued are sent only when
 * working them out again, from the world, masks and editor as they are at that moment, gives exactly the same.
 */
class AxiomCommitTest {
    /** Read once the game is loaded, as the game's blocks can't be made before. */
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
        // Stone bricks darken a step to tuff bricks, for the shade brush.
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

    /** A stroke that turns every block it reaches into stone bricks. */
    private BrushStroke bricks() throws Exception {
        BrushStroke stroke = new BrushStroke();
        stroke.start(axiom.level, AxiomChanges.queue(axiom.level), 1, _ -> (x, y, z, _, _, put) -> put.put(x, y, z, BRICKS));
        return stroke;
    }

    /** Draws one frame of a stroke, with the button held or not, reaching the positions given. */
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
        // The mask's script lets the first block through and fails on the second.
        MaskManager.mask = FakeAxiom.lua(asked, x -> x == 1);
        BrushStroke stroke = bricks();
        frame(stroke, true, at(0), at(1));
        frame(stroke, false);
        assertEquals(List.of(), axiom.tools.pushed);
        assertEquals(FakeAxiom.refusal("mask_failed"), stroke.refused());
        // The stroke ended the frame its mask failed, so nothing was left to check.
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
        // The panel says the masks aren't working, as Chroma Studio's tools now change nothing.
        assertTrue(AxiomMask.unavailable().isPresent());
    }

    /** Shows a preview of what {@code plan} works out with the masks as they are now. */
    private BuildPreview preview(QueuedChanges.Plan<BlockState> plan) {
        BuildPreview preview = new BuildPreview("test");
        AxiomMask.Test mask = AxiomMask.now(axiom.level);
        preview.show(plan.make(mask), mask, new long[0], plan);
        return preview;
    }

    /** Turns every block between x 0 and 2 that isn't air into stone bricks, where the masks let it. */
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
        // Axiom says it's active a frame late, and no tick has passed.
        EditorUI.enabled = false;
        assertEquals(Component.translatable("axiom.chromastudio.refused.editor"), preview.apply().orElse(null));
        assertEquals(List.of(), axiom.tools.pushed);
    }

    @Test
    void aStrokeHeldWhileAnotherToolWasPickedAndPutBackSendsNothing() throws Exception {
        axiom.level.put(0, 0, 0, STONE);
        BrushStroke stroke = bricks();
        frame(stroke, true, at(0));
        // Three frames draw another tool, quicker than a tick notices, and Axiom doesn't reset ours.
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
        // The game stalls for longer than a frame should take, with the same tool and editor open, and draws nothing.
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
        // Someone builds on, and the build the tool would find now is bigger than the one the player saw.
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
        // Axiom applies the masks only while its window is open, so closed, the mask no longer keeps the second block out.
        EditorWindowType.TOOL_MASKS.open = false;
        assertEquals(java.util.Optional.of(true), AxiomMask.windowClosed());
        // The masks answer otherwise for a block they were asked about, which the player is told.
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
        // Another player puts the door's top half in before the button is let go.
        axiom.level.put(0, 1, 0, lower.setValue(DoorBlock.HALF, DoubleBlockHalf.UPPER));
        frame(stroke, false);
        assertEquals(List.of(), axiom.tools.pushed);
        assertEquals(FakeAxiom.refusal("changed"), stroke.refused());
    }

    @Test
    void aLuaMaskThatFailsOnlyOnTheStrokesOwnPassStillSendsNothing() throws Exception {
        axiom.level.put(0, 0, 0, STONE);
        axiom.level.put(1, 0, 0, STONE);
        // The script fails at the second block only in the first context, as a script reading the world can: the
        // check's fresh context compiles it, which clears Axiom's error, and then it answers no without one.
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
        // Each context draws its own seed, as Axiom's does for a script's noise: the stroke's lets the block through,
        // the check's keeps it out.
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
        // The failure turned the features off, and putting the tool away let go of the world's look after all.
        assertTrue(AxiomBridge.whyNot().isPresent());
        assertEquals(java.util.Set.of(), axiom.tools.overriding);
        assertEquals(List.of(), axiom.tools.pushed);
    }

    /** A gradient saved under a name of its own, from one block to another. */
    private static void saveGradient(String name, String from, String to) {
        GradientStore.get().put(new StoredGradient(name, List.of(StoredGradient.Stop.colour(0), StoredGradient.Stop.colour(0xFFFFFF)), 2,
                "LINEAR", "SHORTEST", false, "", List.of(new StoredGradient.Step(0, new PaletteEntry(from, ""), false),
                new StoredGradient.Step(0xFFFFFF, new PaletteEntry(to, ""), false))));
    }

    /** A gradient tool with its ends set at x 0 and 5 of a row of oak planks, its preview made as a frame draws it. */
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
        // Swap ends, drawn in Tool Options before Axiom hands the tool Confirm in the same frame.
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
            // Another palette, clicked in Tool Options before Axiom hands the tool Confirm in the same frame.
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
            // The palette loses the shade the stroke has been putting in, as an edit through Chroma Studio's API can.
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
        // Another context compiles the script, as the check before sending does, and Axiom forgets the error.
        ToolMaskWindow.luaExecutionError = null;
        assertTrue(mask.failed());
    }

    @Test
    void aLuaErrorLeftFromBeforeIsntKeptOnceItsDismissed() {
        // A script failed earlier and the player has since switched Tool Masks to a mask with no script.
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
        // The player types a script that doesn't compile: Axiom keeps no script, and applies no mask at all.
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
        // The check's context keeps the second block out, where the stroke's let it in; that block holds bricks
        // already, so its answer changes nothing.
        Map<MaskContext, Integer> contexts = new IdentityHashMap<>();
        MaskManager.mask = c -> contexts.computeIfAbsent(c, _ -> contexts.size()) == 0 || c.x != 1;
        BrushStroke stroke = bricks();
        frame(stroke, true, at(0), at(1));
        // What changes the plan is another player's block at the first.
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
            // Radius lowered while the use key is held: Axiom's ball keeps the size the stroke started with.
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

    /** A tool with a preview, as the whole-build tools have, which it puts away when Axiom picks another tool. */
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
        // Axiom's own entry for the tool, which its tool grid holds and tells when another tool is picked.
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
        // With "Clear tool when swapping" off, Axiom calls only this as another tool is picked, which may take the
        // world's look straight away, as its Text tool does.
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
