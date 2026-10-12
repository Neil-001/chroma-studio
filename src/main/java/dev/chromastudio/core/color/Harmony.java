package dev.chromastudio.core.color;

import java.util.ArrayList;
import java.util.List;

/** Colour harmonies as hue offsets in OKLCH, keeping lightness and chroma. */
public enum Harmony {
    SIMILAR(0, 15, -15),
    ANALOGOUS(0, 30, -30),
    COMPLEMENTARY(0, 180),
    SPLIT_COMPLEMENTARY(0, 150, 210),
    TRIADIC(0, 120, 240),
    TETRADIC(0, 60, 180, 240),
    SQUARE(0, 90, 180, 270),
    /** One hue, with lightness stepped instead. */
    MONOCHROMATIC(0);

    /** The lightness range monochromatic steps cover. Beyond it nearly every hue clips to black or white. */
    public static final double MIN_LIGHTNESS = 0.2, MAX_LIGHTNESS = 0.95;

    private final double[] offsets;

    Harmony(double... offsets) {
        this.offsets = offsets;
    }

    /** The hue offsets from the seed, in degrees, the seed's own 0 first. */
    public double[] offsets() {
        return offsets.clone();
    }

    /**
     * {@code count} colours from the seed. They take the model's hues in turn, starting again from the seed's
     * when there are more colours than hues. Monochromatic gives the seed for one colour, and otherwise even
     * lightness steps from dark to light at the seed's hue and chroma, each clipped to sRGB.
     */
    public List<Oklch> colours(Oklch seed, int count) {
        if (count < 1) throw new IllegalArgumentException("count must be at least 1");
        List<Oklch> out = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            if (this == MONOCHROMATIC && count > 1) {
                double l = MIN_LIGHTNESS + (MAX_LIGHTNESS - MIN_LIGHTNESS) * i / (count - 1);
                out.add(seed.withL(l).clipped());
            } else {
                out.add(seed.withHue(seed.h() + offsets[i % offsets.length]));
            }
        }
        return out;
    }
}
