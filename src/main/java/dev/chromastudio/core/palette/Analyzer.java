package dev.chromastudio.core.palette;

import dev.chromastudio.core.color.Badge;
import dev.chromastudio.core.color.Harmony;
import dev.chromastudio.core.color.Oklab;
import dev.chromastudio.core.color.Oklch;
import dev.chromastudio.core.color.Tone;
import java.util.ArrayList;
import java.util.List;

/** Looks over a palette's colours and textures for what a builder might want to fix. */
public final class Analyzer {
    public enum Severity { INFO, WARNING }

    /**
     * One thing found. {@code key} is a translation key from {@link #KEYS}. {@code suggestedTarget} is a colour
     * to look for that would mend it, or null when no one colour would. {@code suggestedFilter} is one of the
     * filter tokens listed at {@link #KEYS}.
     */
    public record Finding(String key, Severity severity, Oklch suggestedTarget, String suggestedFilter) {}

    private static final String PREFIX = "chromastudio.analysis.";
    public static final String NARROW_RANGE = PREFIX + "narrow_range", LIGHTNESS_GAP = PREFIX + "lightness_gap",
            NO_DARKS = PREFIX + "no_darks", NO_HIGHLIGHTS = PREFIX + "no_highlights", HUES_STRAY = PREFIX + "hues_stray",
            NO_ACCENT = PREFIX + "no_accent", TOO_SATURATED = PREFIX + "too_saturated",
            ACCENT_CONTRAST = PREFIX + "accent_contrast", TOO_BUSY = PREFIX + "too_busy",
            TEXTURE_JUMP = PREFIX + "texture_jump", TOO_SMALL = PREFIX + "too_small", TOO_LARGE = PREFIX + "too_large";

    /**
     * Every key a finding can have, in the order findings are given, so that a language file can be checked
     * against it.
     *
     * <p>The filter tokens, which the window maps to filter presets: {@link #DARKER} and {@link #LIGHTER} for
     * blocks on that side of the palette, {@link #ACCENT} for saturated blocks, {@link #GRAINY} for blocks with
     * the grainy texture badge, and the empty string for no filter.
     */
    public static final List<String> KEYS = List.of(NARROW_RANGE, LIGHTNESS_GAP, NO_DARKS, NO_HIGHLIGHTS, HUES_STRAY,
            NO_ACCENT, TOO_SATURATED, ACCENT_CONTRAST, TOO_BUSY, TEXTURE_JUMP, TOO_SMALL, TOO_LARGE);

    public static final String DARKER = "darker", LIGHTER = "lighter", ACCENT = "accent", GRAINY = "grainy", NO_FILTER = "";

    // Suggested lightnesses stay inside the range blocks can be found in.
    private static final double MIN_L = Harmony.MIN_LIGHTNESS, MAX_L = Harmony.MAX_LIGHTNESS;
    private static final double NARROW = 0.25, WIDEN_BY = 0.3, GAP = 0.3;
    private static final double DARK_BELOW = 0.4, DARK = 0.25, HIGHLIGHT_ABOVE = 0.7, HIGHLIGHT = 0.85;
    private static final double HUE_TOLERANCE = 20, HUE_SEARCH_STEP = 0.5;
    private static final double ACCENT_FROM = 0.04, ACCENT_CHROMA = 0.15, SATURATED_FROM = 0.12, MUTED_CHROMA = 0.04;
    private static final double ACCENT_NEAR = 0.1, ACCENT_MOVE = 0.25;
    private static final int SMALL = 3, LARGE = 12;

    private Analyzer() {}

    /**
     * The findings for a palette, in the order of {@link #KEYS}, each key at most once. {@code colours} holds one
     * colour for each entry and {@code badges} its texture badge, in the same order. Hue is only read from
     * colours with a chroma of {@link Tone#MIN_CHROMA} or more. An empty palette is only too small.
     */
    public static List<Finding> analyse(List<Oklch> colours, List<Badge> badges) {
        if (colours.size() != badges.size()) throw new IllegalArgumentException("Each colour needs one badge");
        List<Finding> out = new ArrayList<>();
        int n = colours.size();
        if (n == 0) return List.of(new Finding(TOO_SMALL, Severity.INFO, null, NO_FILTER));

        double[] lightness = colours.stream().mapToDouble(Oklch::l).sorted().toArray();
        double darkest = lightness[0], lightest = lightness[n - 1];
        // What a suggestion starts from: the palette's mean lightness and chroma, at the hue it leans to.
        Oklch cast = cast(colours);

        if (lightest - darkest < NARROW) {
            // Towards whichever end has more room.
            boolean lighter = cast.l() < (MIN_L + MAX_L) / 2;
            double l = Math.clamp(cast.l() + (lighter ? WIDEN_BY : -WIDEN_BY), MIN_L, MAX_L);
            out.add(new Finding(NARROW_RANGE, Severity.WARNING, cast.withL(l), lighter ? LIGHTER : DARKER));
        }

        // Only the widest gap is reported; filling it may be all the palette needs.
        int widest = 1;
        for (int i = 2; i < n; i++) if (lightness[i] - lightness[i - 1] > lightness[widest] - lightness[widest - 1]) widest = i;
        if (n > 1 && lightness[widest] - lightness[widest - 1] > GAP) {
            out.add(new Finding(LIGHTNESS_GAP, Severity.INFO, cast.withL((lightness[widest] + lightness[widest - 1]) / 2), NO_FILTER));
        }

        if (darkest > DARK_BELOW) out.add(new Finding(NO_DARKS, Severity.INFO, cast.withL(DARK), DARKER));
        if (lightest < HIGHLIGHT_ABOVE) out.add(new Finding(NO_HIGHLIGHTS, Severity.INFO, cast.withL(HIGHLIGHT), LIGHTER));

        List<Oklch> chromatic = colours.stream().filter(c -> c.c() >= Tone.MIN_CHROMA).toList();
        if (!chromatic.isEmpty()) {
            Fit fit = nearestHarmony(chromatic);
            if (fit.worst > HUE_TOLERANCE) {
                out.add(new Finding(HUES_STRAY, Severity.INFO, cast.withHue(fit.missingHue), NO_FILTER));
            }
        }

        Oklch accent = colours.getFirst();
        int saturated = 0;
        for (Oklch c : colours) {
            if (c.c() > accent.c()) accent = c;
            if (c.c() > SATURATED_FROM) saturated++;
        }
        boolean hasAccent = accent.c() >= ACCENT_FROM;
        if (!hasAccent) out.add(new Finding(NO_ACCENT, Severity.INFO, cast.withC(ACCENT_CHROMA), ACCENT));
        if (saturated * 2 > n) out.add(new Finding(TOO_SATURATED, Severity.INFO, cast.withC(MUTED_CHROMA), NO_FILTER));
        if (hasAccent && n > 1 && Math.abs(accent.l() - cast.l()) < ACCENT_NEAR) {
            // Away from the mean, unless that runs out of lightness to move into.
            boolean lighter = accent.l() >= cast.l();
            if (lighter ? accent.l() + ACCENT_MOVE > MAX_L : accent.l() - ACCENT_MOVE < MIN_L) lighter = !lighter;
            double l = Math.clamp(accent.l() + (lighter ? ACCENT_MOVE : -ACCENT_MOVE), MIN_L, MAX_L);
            out.add(new Finding(ACCENT_CONTRAST, Severity.INFO, accent.withL(l), ACCENT));
        }

        int flat = 0, grainy = 0, busy = 0;
        for (Badge b : badges) {
            switch (b) {
                case FLAT -> flat++;
                case GRAINY -> grainy++;
                case BUSY -> busy++;
            }
        }
        if (busy * 2 > n) out.add(new Finding(TOO_BUSY, Severity.INFO, cast, GRAINY));
        if (flat > 0 && busy > 0 && grainy == 0) out.add(new Finding(TEXTURE_JUMP, Severity.INFO, cast, GRAINY));

        if (n < SMALL) out.add(new Finding(TOO_SMALL, Severity.INFO, null, NO_FILTER));
        if (n > LARGE) out.add(new Finding(TOO_LARGE, Severity.INFO, null, NO_FILTER));
        return List.copyOf(out);
    }

    /**
     * The colours' mean lightness and mean chroma, at the hue they lean to. That hue is the one their OKLab mean
     * has, in which a colour counts by its chroma; when the hues cancel out, it is the most saturated colour's.
     */
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

    /** How far the worst hue is from the best-fitting harmony, and that harmony's hue the palette covers least. */
    private record Fit(double worst, double missingHue) {}

    /**
     * Tries every harmony at every seed hue, half a degree apart, and keeps the one whose furthest palette hue is
     * nearest. Ties go to the harmony listed first, which is the one with fewer hues to fill.
     */
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

    /** The angle between two hues, 0 to 180. */
    private static double apart(double a, double b) {
        return Math.abs(HueArc.SHORTEST.turn(a, b));
    }
}
