package dev.chromastudio.client.axiom;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.chromastudio.client.index.TestIndex;
import dev.chromastudio.core.palette.AxiomScript;
import dev.chromastudio.core.palette.Palette;
import dev.chromastudio.core.palette.PaletteEntry;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class AxiomScriptsTest {
    @BeforeAll
    static void loadGame() {
        TestIndex.loadGame();
        TestIndex.index(Map.of("stone", "block/stone", "oak_planks", "block/oak_planks", "oak_stairs", "block/oak_planks", "lantern", "block/lantern"));
    }

    private static List<PaletteEntry> steps(String... ids) {
        List<PaletteEntry> out = new ArrayList<>();
        for (String id : ids) out.add(new PaletteEntry("minecraft:" + id, ""));
        return out;
    }

    @Test
    void aGradientScriptLeavesOutWhatTheGradientToolLeavesOutAndSaysSo() throws Exception {
        List<PaletteEntry> tower = steps("brown_concrete_powder", "copper_chain", "terracotta", "jungle_planks", "stripped_oak_log", "birch_planks",
                "sandstone", "smooth_sandstone", "chest");
        AxiomBridge.Script script = AxiomScripts.gradient("Tower", tower, AxiomScript.Axis.Y, new BlockPos(68, -53, 6));
        assertFalse(script.text().contains("copper_chain"), script.text());
        assertFalse(script.text().contains("blocks.chest"), script.text());
        assertTrue(script.text().contains("blocks.terracotta or false"), script.text());
        assertTrue(script.text().contains("$int(Bottom, -53, -64, 319)$") && script.text().contains("$int(Top, -40, -64, 319)$"), script.text());
        List<String> keys = new ArrayList<>();
        keys(script.summary(), keys);
        assertTrue(keys.contains("axiom.chromastudio.gradient.done[7, y]"), keys.toString());
        assertTrue(keys.contains("axiom.chromastudio.gradient_tool.skipped[2]"), keys.toString());
        keys.clear();
        keys(AxiomScripts.gradient("Tower", tower.subList(0, 3), AxiomScript.Axis.Y, BlockPos.ZERO).summary(), keys);
        assertTrue(keys.contains("axiom.chromastudio.gradient_tool.skipped.one[1]"), keys.toString());
        AxiomBridge.Script two = AxiomScripts.gradient("Tower", tower.subList(2, 4), AxiomScript.Axis.Y, new BlockPos(68, -53, 6));
        for (AxiomBridge.Script made : List.of(script, two)) {
            keys.clear();
            keys(made.summary(), keys);
            assertTrue(keys.contains("axiom.chromastudio.long[" + AxiomScript.SERVER_LIMIT + "]"), keys.toString());
        }
    }

    @Test
    void theScatterScriptPlacesWhatTheBrushsScatterPlaces() throws Exception {
        Palette p = new Palette("Mixed");
        p.entries.addAll(steps("stone", "oak_stairs", "lantern"));
        AxiomBridge.Script script = AxiomScripts.scatter(p);
        assertTrue(script.text().contains("blocks.oak_planks") && script.text().contains("blocks.stone"), script.text());
        assertFalse(script.text().contains("oak_stairs") || script.text().contains("lantern"), script.text());
        List<String> keys = new ArrayList<>();
        keys(script.summary(), keys);
        assertTrue(keys.stream().anyMatch(k -> k.startsWith("axiom.chromastudio.brush.left_out")), keys.toString());
        assertTrue(keys.contains("axiom.chromastudio.scatter.done[2]"), keys.toString());
    }

    @Test
    void theGradientScriptNamesTheBlocksThatHoldItemsWhichItsOtherChecksMiss() {
        AxiomScript.Kept kept = AxiomScripts.gradientKept();
        assertTrue(kept.blocks().contains("minecraft:jukebox") && kept.blocks().contains("minecraft:spawner"), kept.toString());
        assertFalse(kept.blocks().contains("minecraft:barrel") || kept.blocks().contains("minecraft:player_head") || kept.blocks().contains("minecraft:stone"), kept.toString());
        assertTrue(kept.properties().contains("has_bottle_0"), kept.toString());
    }

    @Test
    void theGradientScriptLeavesBedrockAsTheGround() {
        assertTrue(AxiomScripts.gradientKept().blocks().contains("minecraft:bedrock"));
    }

    private static void keys(Component c, List<String> out) {
        if (c.getContents() instanceof TranslatableContents t) out.add(t.getKey() + List.of(t.getArgs()));
        for (Component sibling : c.getSiblings()) keys(sibling, out);
    }

    @Test
    void aGradientWithFewerThanTwoFullBlocksIsRefused() {
        assertThrows(AxiomBridge.Refused.class, () -> AxiomScripts.gradient("Chains", steps("copper_chain", "torch", "stone"), AxiomScript.Axis.Y, BlockPos.ZERO));
    }
}
