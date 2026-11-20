package dev.chromastudio.client.axiom;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.chromastudio.client.index.TestIndex;
import dev.chromastudio.core.palette.Palette;
import dev.chromastudio.core.palette.PaletteEntry;
import dev.chromastudio.core.palette.QueuedChanges;
import dev.chromastudio.core.palette.Recolour;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

/**
 * The plans the whole-build tools make, checked by the same steps at Apply: whole-build recolour and the gradient
 * tool, against a stand-in Axiom. Also how long that takes on a big build.
 */
class AxiomPlanTest {
    private FakeAxiom axiom;
    private BlockState oak, spruce;

    @BeforeAll
    static void loadGame() {
        TestIndex.loadGame();
        Map<String, String> blocks = new LinkedHashMap<>();
        blocks.put("oak_planks", "block/oak_planks");
        blocks.put("spruce_planks", "block/spruce_planks");
        blocks.put("birch_planks", "block/birch_planks");
        blocks.put("dark_oak_planks", "block/dark_oak_planks");
        TestIndex.index(blocks);
    }

    @BeforeEach
    void install() {
        axiom = FakeAxiom.install();
        oak = TestIndex.block("oak_planks").defaultBlockState();
        spruce = TestIndex.block("spruce_planks").defaultBlockState();
    }

    @AfterEach
    void uninstall() { FakeAxiom.uninstall(); }

    private static Palette palette(String... ids) {
        Palette p = new Palette("Test");
        for (String id : ids) p.entries.add(new PaletteEntry("minecraft:" + id, ""));
        return p;
    }

    /** Fills a box of oak planks from 0 0 0, {@code x} by {@code y} by {@code z}. */
    private void box(int x, int y, int z) {
        for (int i = 0; i < x; i++) for (int j = 0; j < y; j++) for (int k = 0; k < z; k++) axiom.level.put(i, j, k, oak);
    }

    /** Shows what recolouring the build at 0 0 0 into {@code to} does, as the palette brush makes it. */
    private BuildPreview recolour(Palette to) throws Exception {
        QueuedChanges<BlockState> made = AxiomChanges.queue(axiom.level);
        PaletteBrushTool.WholeBuild w = PaletteBrushTool.wholeBuild(axiom.level, BlockPos.ZERO, to, null, true, Recolour.Mode.CLOSEST,
                AxiomMask.now(axiom.level), made);
        BuildPreview preview = new BuildPreview("test");
        preview.show(made, w.outlined(), mask -> {
            QueuedChanges<BlockState> redone = AxiomChanges.queue(axiom.level);
            try {
                PaletteBrushTool.wholeBuild(axiom.level, BlockPos.ZERO, to, null, true, Recolour.Mode.CLOSEST, mask, redone);
            } catch (AxiomBridge.Refused _) {
                // Nothing to recolour, which is never the same.
            }
            return redone;
        });
        return preview;
    }

    @Test
    void aPlankThatJoinsTheBuildRefusesItsRecolour() throws Exception {
        box(3, 2, 1);
        BuildPreview preview = recolour(palette("spruce_planks"));
        assertEquals(6, preview.count());
        axiom.level.put(3, 0, 0, oak);
        assertEquals(FakeAxiom.refusal("changed"), preview.apply().orElse(null));
        assertEquals(List.of(), axiom.tools.pushed);
    }

    @Test
    void anUntouchedBuildIsRecolouredAsShown() throws Exception {
        box(3, 2, 1);
        BuildPreview preview = recolour(palette("spruce_planks"));
        assertTrue(preview.apply().isEmpty());
        assertEquals(1, axiom.tools.pushed.size());
        assertEquals(6, axiom.tools.pushed.getFirst().size());
        assertTrue(axiom.tools.pushed.getFirst().values().stream().allMatch(s -> s == spruce));
    }

    /** Shows a gradient from 0 0 0 to the far end of the box along x, as the gradient tool makes it. */
    private BuildPreview gradient(int length) {
        BlockPos first = BlockPos.ZERO, second = new BlockPos(length - 1, 0, 0);
        List<BlockState> steps = List.of(oak, spruce, TestIndex.block("dark_oak_planks").defaultBlockState());
        QueuedChanges<BlockState> made = AxiomChanges.queue(axiom.level);
        GradientTool.Laid laid = GradientTool.lay(axiom.level, GradientTool.search(axiom.level, first, second, first, true, 64), steps, false, 75,
                AxiomMask.now(axiom.level), made);
        BuildPreview preview = new BuildPreview("test");
        preview.show(made, laid.outlined(), mask -> {
            QueuedChanges<BlockState> redone = AxiomChanges.queue(axiom.level);
            GradientTool.lay(axiom.level, GradientTool.search(axiom.level, first, second, first, true, 64), steps, false, 75, mask, redone);
            return redone;
        });
        return preview;
    }

    @Test
    void aBlockThatJoinsTheBuildRefusesItsGradient() {
        box(6, 2, 1);
        BuildPreview preview = gradient(6);
        assertTrue(preview.count() > 0);
        axiom.level.put(2, 2, 0, oak);
        assertEquals(FakeAxiom.refusal("changed"), preview.apply().orElse(null));
        assertEquals(List.of(), axiom.tools.pushed);
        box(6, 2, 1);
        assertTrue(gradient(6).apply().isEmpty());
        assertEquals(1, axiom.tools.pushed.size());
    }

    /**
     * How long making a whole-build plan and checking it at Apply take on a build of 50,000 blocks, with the tool masks
     * asked about every block. Prints the times; run with {@code -Dchroma.timing=true}.
     */
    @Test
    @EnabledIfSystemProperty(named = "chroma.timing", matches = "true")
    void timeABigBuild() throws Exception {
        box(50, 40, 25);
        Palette to = palette("spruce_planks", "birch_planks", "dark_oak_planks");
        for (int round = 0; round < 3; round++) {
            long t0 = System.nanoTime();
            BuildPreview preview = recolour(to);
            long t1 = System.nanoTime();
            assertTrue(preview.apply().isEmpty());
            long t2 = System.nanoTime();
            BuildPreview g = gradient(50);
            long t3 = System.nanoTime();
            assertTrue(g.apply().isEmpty());
            long t4 = System.nanoTime();
            System.out.printf("50,000 blocks, round %d: recolour preview %d ms, Apply %d ms; gradient preview %d ms, Apply %d ms%n", round,
                    (t1 - t0) / 1_000_000, (t2 - t1) / 1_000_000, (t3 - t2) / 1_000_000, (t4 - t3) / 1_000_000);
        }
    }
}
