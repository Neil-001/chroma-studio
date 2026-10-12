package dev.chromastudio.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.chromastudio.client.PaletteStore;
import dev.chromastudio.core.palette.Palette;
import dev.chromastudio.core.palette.PaletteEntry;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** The palette reads. The colour reads need the game's blocks, so {@code GameIndexTest} covers them. */
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
}
