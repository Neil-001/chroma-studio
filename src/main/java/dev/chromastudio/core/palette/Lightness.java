package dev.chromastudio.core.palette;

import dev.chromastudio.core.color.Harmony;
import dev.chromastudio.core.color.Oklch;
import java.util.List;

public final class Lightness {
    private Lightness() {}

    public static List<Oklch> shades(Oklch seed, int count) {
        if (count == 1) return List.of(seed.clipped());
        return Harmony.MONOCHROMATIC.colours(seed, count);
    }
}
