package dev.chromastudio.core.palette;

import dev.chromastudio.core.color.Oklch;
import java.util.ArrayList;
import java.util.List;

/** The ideal colours of a gradient, before any blocks are matched to them. */
public final class Ramp {
    /** Below this chroma a stop is a grey, and its hue says nothing. */
    public static final double ACHROMATIC = 0.01;

    private Ramp() {}

    /**
     * {@code steps} colours from the first stop to the last. The easing paces the whole ramp, not each stretch
     * between stops; the stops sit at equal intervals along it. Lightness and chroma go in a straight line and
     * the hue turns the chosen way. A grey stop takes the hue of the stop at the other end of its stretch, so
     * that fading to grey doesn't sweep through other hues on the way. The colours are not clipped to sRGB.
     */
    public static List<Oklch> of(List<Oklch> stops, int steps, Easing e, HueArc arc) {
        if (stops.size() < 2) throw new IllegalArgumentException("A gradient needs at least two stops");
        if (steps < 2) throw new IllegalArgumentException("A gradient needs at least two steps");
        int stretches = stops.size() - 1;
        List<Oklch> out = new ArrayList<>(steps);
        for (int i = 0; i < steps; i++) {
            double along = Math.clamp(e.apply((double) i / (steps - 1)), 0.0, 1.0) * stretches;
            int stretch = Math.min((int) along, stretches - 1);
            out.add(between(stops.get(stretch), stops.get(stretch + 1), along - stretch, arc));
        }
        return out;
    }

    private static Oklch between(Oklch a, Oklch b, double t, HueArc arc) {
        // The ends are the stops as given, greys with their own hue included.
        if (t <= 0) return a;
        if (t >= 1) return b;
        boolean greyA = a.c() < ACHROMATIC, greyB = b.c() < ACHROMATIC;
        double from = greyA && !greyB ? b.h() : a.h();
        double turn = greyA || greyB ? 0 : arc.turn(a.h(), b.h());
        return new Oklch(a.l() + (b.l() - a.l()) * t, a.c() + (b.c() - a.c()) * t, from + turn * t);
    }
}
