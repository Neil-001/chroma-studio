package dev.chromastudio.client.ui.tabs;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;

import dev.chromastudio.core.palette.PaletteEntry;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;

class PalettesTabTest {
    private static List<PaletteEntry> weights(int... weights) {
        return Arrays.stream(weights).mapToObj(w -> new PaletteEntry("minecraft:stone", "", w)).toList();
    }

    @Test
    void sharesAlwaysAddUpToOneHundred() {
        assertArrayEquals(new int[] {38, 25, 12, 25}, PalettesTab.shares(weights(3, 2, 1, 2)));
        assertArrayEquals(new int[] {34, 33, 33}, PalettesTab.shares(weights(1, 1, 1)));
        assertArrayEquals(new int[] {100}, PalettesTab.shares(weights(7)));
        assertArrayEquals(new int[] {}, PalettesTab.shares(List.of()));
    }
}
