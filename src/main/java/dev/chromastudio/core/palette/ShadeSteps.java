package dev.chromastudio.core.palette;

import dev.chromastudio.core.color.Color;
import dev.chromastudio.core.color.Oklch;
import dev.chromastudio.core.color.Tone;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.function.IntPredicate;

public final class ShadeSteps {
    public static final double MIN_STEP = 0.03, MAX_STEP = 0.12;

    private static final double MAX_HUE = 0.04, MIN_CHROMA_CHANGE = 0.02, CHROMA_SHARE = 0.3, MAX_SPREAD = 0.04;
    private static final double HUE_SCALE = 0.025, CHROMA_SCALE = 0.04, SPREAD_SCALE = 0.03;
    private static final double OTHER_FAMILY = 2.0, OTHER_SHAPES = 0.5;
    private static final double SHORTEST = 0.5, LONGEST = 2.5, VISIBLE = 0.025;
    private static final double PARTNER_SLACK = 0.75;
    private static final double PALETTE_MAX_HUE = 0.08, PALETTE_CHROMA_SHARE = 0.6;

    public enum Direction {
        LIGHTER, DARKER;

        public Direction opposite() { return this == LIGHTER ? DARKER : LIGHTER; }
    }

    public record Look(Color colour, double spread, String family, String material, boolean shaped, boolean decorated) {
        public Look(Color colour, double spread, String family, String material, boolean shaped) {
            this(colour, spread, family, material, shaped, false);
        }
    }

    private static final List<String> DECORATIONS = List.of("mossy_", "cracked_", "chiseled_", "infested_");

    public static boolean decorated(String id) {
        String path = id.substring(id.indexOf(':') + 1);
        for (String d : DECORATIONS) if (path.startsWith(d)) return true;
        return false;
    }

    private final List<Look> blocks;
    private final Oklch[] lch;
    private final double step;
    private final boolean palette;
    private final int[] lighter, darker;

    public ShadeSteps(List<Look> blocks, double step) { this(blocks, step, false); }

    public ShadeSteps(List<Look> blocks, double step, boolean palette) {
        this.blocks = List.copyOf(blocks);
        this.step = Math.clamp(step, MIN_STEP, MAX_STEP);
        this.palette = palette;
        int n = blocks.size();
        lch = new Oklch[n];
        for (int i = 0; i < n; i++) lch[i] = blocks.get(i).colour().oklch();
        lighter = new int[n];
        darker = new int[n];
        Arrays.fill(lighter, -1);
        Arrays.fill(darker, -1);

        record Pair(int dark, int light, double cost) {}
        List<Pair> pairs = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            for (int j = 0; j < n; j++) {
                if (lch[j].l() <= lch[i].l() || blocks.get(i).decorated() != blocks.get(j).decorated()) continue;
                double cost = cost(blocks.get(i), lch[i], blocks.get(j), lch[j]);
                if (cost < Double.POSITIVE_INFINITY) pairs.add(new Pair(i, j, cost));
            }
        }
        pairs.sort(Comparator.comparingDouble(Pair::cost).thenComparingInt(Pair::dark).thenComparingInt(Pair::light));
        for (Pair p : pairs) {
            if (lighter[p.dark] >= 0 || darker[p.light] >= 0) continue;
            lighter[p.dark] = p.light;
            darker[p.light] = p.dark;
        }
    }

    public double step() { return step; }

    public int next(int from, Direction d, IntPredicate allowed) {
        int partner = (d == Direction.LIGHTER ? lighter : darker)[from];
        int closest = closest(blocks.get(from), lch[from], d, i -> i != from && allowed.test(i) && reaches(blocks.get(from), i));
        if (partner < 0 || !allowed.test(partner) || closest < 0) return closest;
        return cost(from, partner, d) <= cost(from, closest, d) + PARTNER_SLACK ? partner : closest;
    }

    public int next(Look from, Direction d, IntPredicate allowed) {
        return closest(from, from.colour().oklch(), d, i -> allowed.test(i) && reaches(from, i));
    }

    private boolean reaches(Look from, int to) { return from.decorated() || !blocks.get(to).decorated(); }

    private int closest(Look from, Oklch at, Direction d, IntPredicate allowed) {
        int best = -1;
        double least = Double.POSITIVE_INFINITY;
        for (int i = 0; i < blocks.size(); i++) {
            if (!allowed.test(i)) continue;
            double cost = cost(from, at, i, d);
            if (cost < least) {
                least = cost;
                best = i;
            }
        }
        return best;
    }

    private double cost(int from, int to, Direction d) { return cost(blocks.get(from), lch[from], to, d); }

    private double cost(Look from, Oklch at, int to, Direction d) {
        return d == Direction.LIGHTER ? cost(from, at, blocks.get(to), lch[to]) : cost(blocks.get(to), lch[to], from, at);
    }

    private double cost(Look dark, Oklch d, Look light, Oklch l) {
        double rise = l.l() - d.l();
        if (rise < (palette ? VISIBLE : Math.max(VISIBLE, SHORTEST * step)) || !palette && rise > LONGEST * step) return Double.POSITIVE_INFINITY;
        double hue = hueDistance(d, l), chroma = Math.abs(l.c() - d.c()), spread = Math.abs(light.spread() - dark.spread());
        double maxChroma = Math.max(MIN_CHROMA_CHANGE, (palette ? PALETTE_CHROMA_SHARE : CHROMA_SHARE) * Math.max(d.c(), l.c()));
        if (hue > (palette ? PALETTE_MAX_HUE : MAX_HUE) || chroma > maxChroma || !palette && spread > MAX_SPREAD) return Double.POSITIVE_INFINITY;
        if (d.c() < Tone.MIN_CHROMA != l.c() < Tone.MIN_CHROMA || !dark.material().equals(light.material())) return Double.POSITIVE_INFINITY;
        double off = (rise - step) / step;
        boolean sameFamily = !dark.family().isEmpty() && dark.family().equals(light.family());
        return off * off + square(hue / HUE_SCALE) + square(chroma / CHROMA_SCALE) + square(spread / SPREAD_SCALE)
                + (sameFamily || palette ? 0 : OTHER_FAMILY) + (dark.shaped() == light.shaped() ? 0 : OTHER_SHAPES);
    }

    private static double hueDistance(Oklch a, Oklch b) {
        double angle = Math.toRadians(a.h() - b.h());
        return 2 * Math.sqrt(a.c() * b.c()) * Math.abs(Math.sin(angle / 2));
    }

    private static double square(double v) { return v * v; }
}
