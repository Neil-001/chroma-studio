package dev.chromastudio.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.chromastudio.client.PaletteStore;
import dev.chromastudio.core.palette.Palette;
import dev.chromastudio.core.palette.PaletteEntry;
import dev.chromastudio.core.palette.StoredGradient;
import java.nio.file.Path;
import java.util.List;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.core.Direction;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** The palette and gradient reads. The colour reads need the block index, so {@code GameIndexTest} covers them. */
class ChromaStudioApiTest {
    @Test
    void palettesAreThePlayersOwnThenThePresetsAsCopies(@TempDir Path dir) {
        PaletteStore store = new PaletteStore(dir);
        store.create("Castle walls", List.of(new PaletteEntry("minecraft:stone", ""), new PaletteEntry("minecraft:andesite", "", 3)));

        List<Palette> palettes = ChromaStudioApi.copies(store);
        assertEquals(1 + PaletteStore.PRESETS.size(), palettes.size());
        Palette own = palettes.getFirst();
        assertEquals("Castle walls", own.name);
        assertFalse(own.preset);
        assertEquals(store.own().getFirst().entries, own.entries);
        assertTrue(palettes.subList(1, palettes.size()).stream().allMatch(p -> p.preset && !p.entries.isEmpty()));

        // Another mod's changes stay its own.
        own.name = "Changed";
        own.entries.clear();
        assertEquals("Castle walls", store.own().getFirst().name);
        assertEquals(2, store.own().getFirst().entries.size());
    }

    @Test
    void savedGradientsGiveTheirBlocksAsTheGamesOwn() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
        assertEquals(2, ChromaStudioApi.apiVersion());
        StoredGradient stored = new StoredGradient("Sunset", List.of(StoredGradient.Stop.colour(0x1F2A44), new StoredGradient.Stop(0xE9D8A6, new PaletteEntry("minecraft:oak_log", "axis=x"))),
                3, "SMOOTH", "SHORTEST", false, "", List.of(
                new StoredGradient.Step(0x1F2A44, new PaletteEntry("minecraft:stone", ""), false),
                new StoredGradient.Step(0x808080, new PaletteEntry("somemod:gone", ""), true),
                new StoredGradient.Step(0xE9D8A6, new PaletteEntry("minecraft:oak_log", "axis=x"), false)));
        SavedGradient g = ChromaStudioApi.copy(stored);
        assertEquals("Sunset", g.name());
        assertEquals(3, g.steps());
        assertEquals(2, g.stops().size());
        assertFalse(g.stops().getFirst().isBlock());
        assertNull(g.stops().getFirst().block());
        assertEquals(0x1F2A44, g.stops().getFirst().rgb());
        assertTrue(g.stops().get(1).isBlock());
        assertEquals(Blocks.OAK_LOG.defaultBlockState().setValue(BlockStateProperties.AXIS, Direction.Axis.X), g.stops().get(1).block());
        assertEquals("minecraft:oak_log[axis=x]", g.stops().get(1).blockId());
        assertEquals(List.of(0x1F2A44, 0x808080, 0xE9D8A6), g.blocks().stream().map(SavedGradient.Step::idealRgb).toList());
        assertEquals(Blocks.STONE.defaultBlockState(), g.blocks().getFirst().block());
        assertNull(g.blocks().get(1).block(), "a block this game doesn't have");
        assertEquals("somemod:gone", g.blocks().get(1).blockId());
        assertTrue(g.blocks().get(1).chosenByHand());
        assertFalse(g.blocks().getFirst().chosenByHand());
        assertEquals(Blocks.STONE.defaultBlockState(), ChromaStudioApi.blockState(new PaletteEntry("minecraft:stone", "")));
        assertNull(ChromaStudioApi.blockState(new PaletteEntry("minecraft:stone", "no_such=1")));
    }
}
