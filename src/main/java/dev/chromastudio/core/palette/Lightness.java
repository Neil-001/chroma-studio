package dev.chromastudio.core.palette;

import dev.chromastudio.core.color.Harmony;
import dev.chromastudio.core.color.Oklch;
import java.util.List;

/** Shades of one colour. */
public final class Lightness {
    private Lightness() {}

    /**
     * {@code count} colours with the seed's hue and chroma, from dark to light in even steps of lightness between
     * {@link Harmony#MIN_LIGHTNESS} and {@link Harmony#MAX_LIGHTNESS}, each clipped to sRGB. One colour alone is
     * the seed itself, clipped.
     */
    public static List<Oklch> shades(Oklch seed, int count) {
        if (count == 1) return List.of(seed.clipped());
        return Harmony.MONOCHROMATIC.colours(seed, count);
    }
}
