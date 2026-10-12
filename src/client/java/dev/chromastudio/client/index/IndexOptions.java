package dev.chromastudio.client.index;

import dev.chromastudio.client.ModSettings;

/**
 * The index's settings as they stood at one moment, so a build on its own thread sees them all from the same one.
 *
 * @param technical whether blocks outside the creative tabs are listed
 * @param statesSeparately whether each state of a block gets its own row
 * @param statesCap the most states kept per block
 * @param animatedAverage whether an animated texture is averaged over its frames, not read from the first alone
 * @param cacheFolder where the cache is kept; empty means {@code config/chromastudio/cache}
 * @param rebuildOnReload whether a resource reload rebuilds an index that is already built
 */
public record IndexOptions(boolean technical, boolean statesSeparately, int statesCap, boolean animatedAverage,
                           String cacheFolder, boolean rebuildOnReload) {
    /** The settings as they are now. What is listed follows them at once; what is indexed, at the next build. */
    public static IndexOptions current() {
        return new IndexOptions(ModSettings.technicalBlocks, ModSettings.statesSeparately, ModSettings.statesCap,
                ModSettings.animatedAveraging, ModSettings.cacheFolder, ModSettings.rebuildOnReload);
    }
}
