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

/** The scripts' choice of blocks, which needs the game's blocks but not Axiom. */
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
        // The builder's "Tower": nine steps, the second a chain, which isn't a full block.
        List<PaletteEntry> tower = steps("brown_concrete_powder", "copper_chain", "terracotta", "jungle_planks", "stripped_oak_log", "birch_planks",
                "sandstone", "smooth_sandstone", "chest");
        AxiomBridge.Script script = AxiomScripts.gradient("Tower", tower, AxiomScript.Axis.Y, new BlockPos(68, -53, 6));
        assertFalse(script.text().contains("copper_chain"), script.text());
        assertFalse(script.text().contains("blocks.chest"), script.text());
        assertTrue(script.text().contains("blocks.terracotta or false"), script.text());
        // Seven steps, two blocks each, from where the player stands.
        assertTrue(script.text().contains("$int(Bottom, -53, -64, 319)$") && script.text().contains("$int(Top, -40, -64, 319)$"), script.text());
        List<String> keys = new ArrayList<>();
        keys(script.summary(), keys);
        assertTrue(keys.contains("axiom.chromastudio.gradient.done[7, y]"), keys.toString());
        assertTrue(keys.contains("axiom.chromastudio.gradient_tool.skipped[2]"), keys.toString());
        // Without the chest, as the saved gradient has it, the script fits what a server lets a player run.
        AxiomBridge.Script seven = AxiomScripts.gradient("Tower", tower.subList(0, 8), AxiomScript.Axis.Y, new BlockPos(68, -53, 6));
        assertTrue(AxiomScript.counted(seven.text()) < AxiomScript.SERVER_LIMIT, AxiomScript.counted(seven.text()) + " " + AxiomScripts.kept());
    }

    @Test
    void theScatterScriptPlacesWhatTheBrushsScatterPlaces() throws Exception {
        // Stairs go in as their full block, and a lantern, which can't stand alone, is left out, as the brush has it.
        Palette p = new Palette("Mixed");
        p.entries.addAll(steps("stone", "oak_stairs", "lantern"));
        AxiomBridge.Script script = AxiomScripts.scatter(p);
        assertTrue(script.text().contains("\"oak_planks\"") && script.text().contains("\"stone\""), script.text());
        assertFalse(script.text().contains("oak_stairs") || script.text().contains("lantern"), script.text());
        List<String> keys = new ArrayList<>();
        keys(script.summary(), keys);
        assertTrue(keys.stream().anyMatch(k -> k.startsWith("axiom.chromastudio.brush.left_out")), keys.toString());
        assertTrue(keys.contains("axiom.chromastudio.scatter.done[2]"), keys.toString());
    }

    @Test
    void theGradientScriptNamesTheFullBlocksThatHoldItemsWhichItsPropertiesMiss() {
        List<String> kept = AxiomScripts.kept();
        // A jukebox and a spawner have no way they face; a barrel, which faces a way, is left alone by that.
        assertTrue(kept.contains("minecraft:jukebox") && kept.contains("minecraft:spawner"), kept.toString());
        assertFalse(kept.contains("minecraft:barrel") || kept.contains("minecraft:stone"), kept.toString());
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
