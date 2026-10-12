package dev.chromastudio.core.palette;

import dev.chromastudio.core.color.Harmony;
import dev.chromastudio.core.color.Oklch;
import java.util.ArrayList;
import java.util.List;
import java.util.random.RandomGenerator;

/** Makes the target colours of a generated palette. */
public final class Generator {
    /** How much lightness the targets cover, centred on the seed's. */
    public static final double LIGHTNESS_RANGE = 0.5;
    /** The most a hue is nudged either way, in degrees, so that two rolls of one harmony differ. */
    public static final double HUE_JITTER = 5;

    private Generator() {}

    /**
     * {@code count} targets from a seed. The hues go round the harmony's; the lightnesses are spread evenly over
     * {@link #LIGHTNESS_RANGE} around the seed's, kept within {@link Harmony#MIN_LIGHTNESS} and
     * {@link Harmony#MAX_LIGHTNESS}; the chroma is the seed's, lowered where sRGB can't show it. The random
     * generator pairs hues with lightnesses and nudges each hue, so the same generator state gives the same
     * targets. They come out in the harmony's hue order.
     */
    public static List<Oklch> targets(Oklch seed, Harmony h, int count, RandomGenerator r) {
        List<Oklch> hues = h.colours(seed, count);
        double low = Math.clamp(seed.l() - LIGHTNESS_RANGE / 2, Harmony.MIN_LIGHTNESS, Harmony.MAX_LIGHTNESS);
        double high = Math.clamp(seed.l() + LIGHTNESS_RANGE / 2, Harmony.MIN_LIGHTNESS, Harmony.MAX_LIGHTNESS);
        double[] lightness = new double[count];
        for (int i = 0; i < count; i++) {
            lightness[i] = count == 1 ? (low + high) / 2 : low + (high - low) * i / (count - 1);
        }
        // A Fisher-Yates shuffle, so every pairing of hue and lightness is as likely as any other.
        for (int i = count - 1; i > 0; i--) {
            int j = r.nextInt(i + 1);
            double swap = lightness[i];
            lightness[i] = lightness[j];
            lightness[j] = swap;
        }
        List<Oklch> out = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            double hue = hues.get(i).h() + r.nextDouble(-HUE_JITTER, HUE_JITTER);
            out.add(new Oklch(lightness[i], seed.c(), hue).clipped());
        }
        return out;
    }
}
