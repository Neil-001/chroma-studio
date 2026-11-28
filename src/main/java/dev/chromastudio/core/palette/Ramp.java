package dev.chromastudio.core.palette;

import dev.chromastudio.core.color.Oklch;
import java.util.ArrayList;
import java.util.List;

public final class Ramp {
    public static final double ACHROMATIC = 0.01;

    private Ramp() {}

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
        if (t <= 0) return a;
        if (t >= 1) return b;
        boolean greyA = a.c() < ACHROMATIC, greyB = b.c() < ACHROMATIC;
        double from = greyA && !greyB ? b.h() : a.h();
        double turn = greyA || greyB ? 0 : arc.turn(a.h(), b.h());
        return new Oklch(a.l() + (b.l() - a.l()) * t, a.c() + (b.c() - a.c()) * t, from + turn * t);
    }
}
