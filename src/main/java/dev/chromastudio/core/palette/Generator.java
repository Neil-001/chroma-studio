package dev.chromastudio.core.palette;

import dev.chromastudio.core.color.Harmony;
import dev.chromastudio.core.color.Oklch;
import java.util.ArrayList;
import java.util.List;
import java.util.random.RandomGenerator;

public final class Generator {
    public static final double LIGHTNESS_RANGE = 0.5;
    public static final double HUE_JITTER = 5;

    private Generator() {}

    public static List<Oklch> targets(Oklch seed, Harmony h, int count, RandomGenerator r) {
        List<Oklch> hues = h.colours(seed, count);
        double low = Math.clamp(seed.l() - LIGHTNESS_RANGE / 2, Harmony.MIN_LIGHTNESS, Harmony.MAX_LIGHTNESS);
        double high = Math.clamp(seed.l() + LIGHTNESS_RANGE / 2, Harmony.MIN_LIGHTNESS, Harmony.MAX_LIGHTNESS);
        double[] lightness = new double[count];
        for (int i = 0; i < count; i++) {
            lightness[i] = count == 1 ? (low + high) / 2 : low + (high - low) * i / (count - 1);
        }
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
