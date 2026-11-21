package dev.chromastudio.client.axiom;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.chromastudio.client.PaletteStore;
import dev.chromastudio.client.index.TestIndex;
import dev.chromastudio.core.palette.Palette;
import imgui.moulberry92.ImGui;
import java.util.LinkedHashMap;
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
        frame(tool, "axiom.chromastudio.whole_build");
        assertTrue(tool.callUseTool());
        frame(tool, "axiom.chromastudio.sampler.save");
        Palette saved = PaletteStore.get().selected();
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
}
