package dev.chromastudio.client.index;

import dev.chromastudio.client.ModSettings;

public record IndexOptions(boolean technical, boolean statesSeparately, int statesCap, boolean animatedAverage,
                           String cacheFolder, boolean rebuildOnReload) {
    public static IndexOptions current() {
        return new IndexOptions(ModSettings.technicalBlocks, ModSettings.statesSeparately, ModSettings.statesCap,
                ModSettings.animatedAveraging, ModSettings.cacheFolder, ModSettings.rebuildOnReload);
    }
}
