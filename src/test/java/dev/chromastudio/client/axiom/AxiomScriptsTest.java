package dev.chromastudio.client.axiom;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.chromastudio.client.index.TestIndex;
import dev.chromastudio.core.palette.AxiomScript;
import dev.chromastudio.core.palette.PaletteEntry;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/** The scripts' choice of blocks, which needs the game's blocks but not Axiom. */
class AxiomScriptsTest {
    @BeforeAll
    static void loadGame() { TestIndex.loadGame(); }

    private static List<PaletteEntry> steps(String... ids) {
        List<PaletteEntry> out = new ArrayList<>();
        for (String id : ids) out.add(new PaletteEntry("minecraft:" + id, ""));
        return out;
    }

    @Test
    void aGradientScriptLeavesOutWhatTheGradientToolLeavesOutAndSaysSo() throws Exception {
        // The builder's "Tower": nine steps, the second a chain, which isn't a full block.
        List<PaletteEntry> tower = steps("brown_concrete_powder", "copper_chain", "terracotta", "jungle_planks", "stripped_oak_log", "birch_planks",
                "sandstone", "smooth_sandstone", "chest");
        AxiomBridge.Script script = AxiomScripts.gradient("Tower", tower, AxiomScript.Axis.Y, new BlockPos(68, -53, 6));
        assertFalse(script.text().contains("copper_chain"), script.text());
        assertFalse(script.text().contains("\"chest\""), script.text());
        assertTrue(script.text().contains("\"terracotta\""), script.text());
        // Seven steps, two blocks each, from where the player stands.
        assertTrue(script.text().contains("$int(Bottom, -53, -64, 319)$") && script.text().contains("$int(Top, -40, -64, 319)$"), script.text());
        List<String> keys = new ArrayList<>();
        keys(script.summary(), keys);
        assertTrue(keys.contains("axiom.chromastudio.gradient.done[7, y]"), keys.toString());
        assertTrue(keys.contains("axiom.chromastudio.gradient_tool.skipped[2]"), keys.toString());
    }

    /** The translation keys in a message, with their arguments. */
    private static void keys(Component c, List<String> out) {
        if (c.getContents() instanceof TranslatableContents t) out.add(t.getKey() + List.of(t.getArgs()));
        for (Component sibling : c.getSiblings()) keys(sibling, out);
    }

    @Test
    void aGradientWithFewerThanTwoFullBlocksIsRefused() {
        assertThrows(AxiomBridge.Refused.class, () -> AxiomScripts.gradient("Chains", steps("copper_chain", "torch", "stone"), AxiomScript.Axis.Y, BlockPos.ZERO));
    }
}
