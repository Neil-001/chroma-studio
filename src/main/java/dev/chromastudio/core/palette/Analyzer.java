package dev.chromastudio.core.palette;

import dev.chromastudio.core.color.Badge;
import dev.chromastudio.core.color.Harmony;
import dev.chromastudio.core.color.Oklab;
import dev.chromastudio.core.color.Oklch;
import dev.chromastudio.core.color.Tone;
import java.util.ArrayList;
import java.util.List;

public final class Analyzer {
    public enum Severity { INFO, WARNING }

    public record Finding(String key, Severity severity, Oklch suggestedTarget, String suggestedFilter, List<Integer> blocks) {}

    private static final String PREFIX = "chromastudio.analysis.";
    public static final String NARROW_RANGE = PREFIX + "narrow_range", LIGHTNESS_GAP = PREFIX + "lightness_gap",
            NO_DARKS = PREFIX + "no_darks", NO_HIGHLIGHTS = PREFIX + "no_highlights", HUES_STRAY = PREFIX + "hues_stray",
            NO_ACCENT = PREFIX + "no_accent", TOO_SATURATED = PREFIX + "too_saturated",
            ACCENT_CONTRAST = PREFIX + "accent_contrast", TOO_BUSY = PREFIX + "too_busy",
            TEXTURE_JUMP = PREFIX + "texture_jump", TOO_SMALL = PREFIX + "too_small", TOO_LARGE = PREFIX + "too_large";

    public static final List<String> KEYS = List.of(NARROW_RANGE, LIGHTNESS_GAP, NO_DARKS, NO_HIGHLIGHTS, HUES_STRAY,
            NO_ACCENT, TOO_SATURATED, ACCENT_CONTRAST, TOO_BUSY, TEXTURE_JUMP, TOO_SMALL, TOO_LARGE);

    public static final String DARKER = "darker", LIGHTER = "lighter", ACCENT = "accent", GRAINY = "grainy", NO_FILTER = "";

    private static final double MIN_L = Harmony.MIN_LIGHTNESS, MAX_L = Harmony.MAX_LIGHTNESS;
    private static final double NARROW = 0.25, WIDEN_BY = 0.3, GAP = 0.3;
    private static final double DARK_BELOW = 0.4, DARK = 0.25, HIGHLIGHT_ABOVE = 0.7, HIGHLIGHT = 0.85;
    private static final double HUE_TOLERANCE = 20, HUE_SEARCH_STEP = 0.5;
    private static final double ACCENT_FROM = 0.04, ACCENT_CHROMA = 0.15, SATURATED_FROM = 0.12, MUTED_CHROMA = 0.04;
    private static final double ACCENT_NEAR = 0.1, ACCENT_MOVE = 0.25;
    private static final int SMALL = 3, LARGE = 12;

    private Analyzer() {}

    public static List<Finding> analyse(List<Oklch> colours, List<Badge> badges) {
        if (colours.size() != badges.size()) throw new IllegalArgumentException("Each colour needs one badge");
        List<Finding> out = new ArrayList<>();
        int n = colours.size();
        if (n == 0) return List.of(new Finding(TOO_SMALL, Severity.INFO, null, NO_FILTER, List.of()));

        List<Integer> byLightness = new ArrayList<>();
        for (int i = 0; i < n; i++) byLightness.add(i);
        byLightness.sort((a, b) -> Double.compare(colours.get(a).l(), colours.get(b).l()));
        double[] lightness = byLightness.stream().mapToDouble(i -> colours.get(i).l()).toArray();
        double darkest = lightness[0], lightest = lightness[n - 1];
        Oklch cast = cast(colours);

        if (lightest - darkest < NARROW) {
            boolean lighter = cast.l() < (MIN_L + MAX_L) / 2;
            double l = Math.clamp(cast.l() + (lighter ? WIDEN_BY : -WIDEN_BY), MIN_L, MAX_L);
            out.add(new Finding(NARROW_RANGE, Severity.WARNING, cast.withL(l), lighter ? LIGHTER : DARKER, List.of()));
        }

        int widest = 1;
        for (int i = 2; i < n; i++) if (lightness[i] - lightness[i - 1] > lightness[widest] - lightness[widest - 1]) widest = i;
        if (n > 1 && lightness[widest] - lightness[widest - 1] > GAP) {
            out.add(new Finding(LIGHTNESS_GAP, Severity.INFO, cast.withL((lightness[widest] + lightness[widest - 1]) / 2), NO_FILTER,
                    List.of(byLightness.get(widest - 1), byLightness.get(widest))));
        }

        if (darkest > DARK_BELOW) out.add(new Finding(NO_DARKS, Severity.INFO, cast.withL(DARK), DARKER, List.of(byLightness.getFirst())));
        if (lightest < HIGHLIGHT_ABOVE) out.add(new Finding(NO_HIGHLIGHTS, Severity.INFO, cast.withL(HIGHLIGHT), LIGHTER, List.of(byLightness.getLast())));

        List<Oklch> chromatic = colours.stream().filter(c -> c.c() >= Tone.MIN_CHROMA).toList();
        if (!chromatic.isEmpty()) {
            Fit fit = nearestHarmony(chromatic);
            if (fit.worst > HUE_TOLERANCE) {
                out.add(new Finding(HUES_STRAY, Severity.INFO, cast.withHue(fit.missingHue), NO_FILTER, List.of()));
            }
        }

        int accentAt = 0;
        List<Integer> saturated = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            if (colours.get(i).c() > colours.get(accentAt).c()) accentAt = i;
            if (colours.get(i).c() > SATURATED_FROM) saturated.add(i);
        }
        Oklch accent = colours.get(accentAt);
        boolean hasAccent = accent.c() >= ACCENT_FROM;
        if (!hasAccent) out.add(new Finding(NO_ACCENT, Severity.INFO, cast.withC(ACCENT_CHROMA), ACCENT, List.of()));
        if (saturated.size() * 2 > n) out.add(new Finding(TOO_SATURATED, Severity.INFO, cast.withC(MUTED_CHROMA), NO_FILTER, List.copyOf(saturated)));
        if (hasAccent && n > 1 && Math.abs(accent.l() - cast.l()) < ACCENT_NEAR) {
            boolean lighter = accent.l() >= cast.l();
            if (lighter ? accent.l() + ACCENT_MOVE > MAX_L : accent.l() - ACCENT_MOVE < MIN_L) lighter = !lighter;
            double l = Math.clamp(accent.l() + (lighter ? ACCENT_MOVE : -ACCENT_MOVE), MIN_L, MAX_L);
            out.add(new Finding(ACCENT_CONTRAST, Severity.INFO, accent.withL(l), ACCENT, List.of(accentAt)));
        }

        List<Integer> flat = new ArrayList<>(), busy = new ArrayList<>();
        int grainy = 0;
        for (int i = 0; i < n; i++) {
            switch (badges.get(i)) {
                case FLAT -> flat.add(i);
                case GRAINY -> grainy++;
                case BUSY -> busy.add(i);
            }
        }
        if (busy.size() * 2 > n) out.add(new Finding(TOO_BUSY, Severity.INFO, cast, GRAINY, List.copyOf(busy)));
        if (!flat.isEmpty() && !busy.isEmpty() && grainy == 0) {
            List<Integer> both = new ArrayList<>(flat);
            both.addAll(busy);
            both.sort(null);
            out.add(new Finding(TEXTURE_JUMP, Severity.INFO, cast, GRAINY, List.copyOf(both)));
        }

        if (n < SMALL) out.add(new Finding(TOO_SMALL, Severity.INFO, null, NO_FILTER, List.of()));
        if (n > LARGE) out.add(new Finding(TOO_LARGE, Severity.INFO, null, NO_FILTER, List.of()));
        return List.copyOf(out);
    }

    private static Oklch cast(List<Oklch> colours) {
        double l = 0, c = 0, a = 0, b = 0;
        Oklch strongest = colours.getFirst();
        for (Oklch colour : colours) {
            Oklab lab = colour.oklab();
            l += colour.l();
            c += colour.c();
            a += lab.a();
            b += lab.b();
            if (colour.c() > strongest.c()) strongest = colour;
        }
        int n = colours.size();
        Oklch lean = new Oklab(0, a / n, b / n).oklch();
        return new Oklch(l / n, c / n, lean.c() >= 1e-3 ? lean.h() : strongest.h());
    }

    private record Fit(double worst, double missingHue) {}

    private static Fit nearestHarmony(List<Oklch> chromatic) {
        double best = Double.POSITIVE_INFINITY, bestSeed = 0;
        Harmony bestHarmony = Harmony.MONOCHROMATIC;
        for (Harmony harmony : Harmony.values()) {
            double[] offsets = harmony.offsets();
            for (double seed = 0; seed < 360; seed += HUE_SEARCH_STEP) {
                double worst = 0;
                for (Oklch c : chromatic) {
                    double nearest = 180;
                    for (double offset : offsets) nearest = Math.min(nearest, apart(c.h(), seed + offset));
                    worst = Math.max(worst, nearest);
                    if (worst >= best) break;
                }
                if (worst < best) {
                    best = worst;
                    bestSeed = seed;
                    bestHarmony = harmony;
                }
            }
        }
        double missing = bestSeed, furthest = -1;
        for (double offset : bestHarmony.offsets()) {
            double nearest = 180;
            for (Oklch c : chromatic) nearest = Math.min(nearest, apart(c.h(), bestSeed + offset));
            if (nearest > furthest) {
                furthest = nearest;
                missing = bestSeed + offset;
            }
        }
        return new Fit(best, missing);
    }

    private static double apart(double a, double b) {
        return Math.abs(HueArc.SHORTEST.turn(a, b));
    }
}
