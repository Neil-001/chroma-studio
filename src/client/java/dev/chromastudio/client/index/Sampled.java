package dev.chromastudio.client.index;

import dev.chromastudio.core.color.Color;
import dev.chromastudio.core.color.Distribution;
import dev.chromastudio.core.color.Face;
import dev.chromastudio.core.color.Scale;
import dev.chromastudio.core.color.Shading;
import dev.chromastudio.core.color.TextureStats;
import java.util.ArrayList;
import java.util.List;

/**
 * One face's stats with its tint applied, plus what a search needs worked out ahead. Faces that look the same share
 * one of these, so a search scores each look once.
 */
final class Sampled {
    private static final Face[] FACES = Face.values();

    final TextureStats stats;
    final boolean animated;
    /** The frame the stats came from, or null for one that is another's stats tinted. */
    final Textures.Frame frame;
    // By scale, then by face. Filled on first use; a race only repeats the work.
    private final Distribution[][] shaded = new Distribution[Scale.values().length][FACES.length];

    Sampled(Textures.Frame frame) {
        this.stats = frame.stats();
        this.animated = frame.animated();
        this.frame = frame;
    }

    Sampled(TextureStats stats, boolean animated) {
        this.stats = stats;
        this.animated = animated;
        this.frame = null;
    }

    /** The colours at a scale, darkened as the game lights that face, or as they are when {@code shadeAs} is null. */
    Distribution distribution(Scale scale, Face shadeAs) {
        Distribution plain = stats.distribution(scale);
        if (shadeAs == null || Shading.factor(shadeAs) == 1) return plain;
        Distribution d = shaded[scale.ordinal()][shadeAs.ordinal()];
        if (d == null) {
            List<Color> colours = new ArrayList<>();
            for (Color c : plain.colours()) colours.add(Shading.apply(c, shadeAs));
            d = Distribution.of(colours, plain.weights());
            shaded[scale.ordinal()][shadeAs.ordinal()] = d;
        }
        return d;
    }
}
