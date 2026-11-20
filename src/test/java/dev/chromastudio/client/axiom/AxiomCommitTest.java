package dev.chromastudio.client.axiom;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.moulberry.axiom.editor.EditorUI;
import com.moulberry.axiom.mask.MaskManager;
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
    private static final BlockState STONE = Blocks.STONE.defaultBlockState(), BRICKS = Blocks.STONE_BRICKS.defaultBlockState();

    private FakeAxiom axiom;

    @BeforeAll
    static void loadGame() {
        TestIndex.loadGame();
        Map<String, String> blocks = new LinkedHashMap<>();
        blocks.put("oak_planks", "block/oak_planks");
        blocks.put("oak_door", "block/oak_door_bottom");
        blocks.put("spruce_planks", "block/spruce_planks");
        blocks.put("spruce_door", "block/spruce_door_bottom");
        TestIndex.index(blocks);
    }

    @BeforeEach
    void install() {
        axiom = FakeAxiom.install();
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
        // Sending asked the masks afresh about every block the stroke reached, and so failed again.
        assertEquals(List.of(0, 1, 0, 1), asked);
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
        QueuedChanges<BlockState> made = plan.make(AxiomMask.now(axiom.level));
        preview.show(axiom.level, made, new long[0], plan);
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
        stroke.tick(false);
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
        stroke.tick(false);
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
        assertEquals(FakeAxiom.refusal("changed"), preview.apply().orElse(null));
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
        preview.show(axiom.level, plan.make(AxiomMask.now(axiom.level)), new long[0], plan);
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
    void anIconIsLeftAsThePencilWhenAxiomKeysTheToolsEntry() {
        Tool entry = new Tool() {
            @Override
            public char iconChar() { return 'o'; }

            @Override
            public String name() { return "Test tool"; }
        };
        ToolManager.tools.add(entry);
        ToolManager.keybindMap.put(entry, "bound");
        AxiomIcons.give(() -> "Test tool", 'x');
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
        AxiomIcons.give(() -> "Test tool", 'x');
        assertEquals('x', ToolManager.tools.getFirst().iconChar());
        assertEquals("Test tool", ToolManager.tools.getFirst().name());
    }
}
