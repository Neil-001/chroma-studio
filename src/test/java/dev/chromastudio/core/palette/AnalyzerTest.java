package dev.chromastudio.core.palette;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.chromastudio.core.color.Badge;
import dev.chromastudio.core.color.Oklch;
import dev.chromastudio.core.palette.Analyzer.Finding;
import dev.chromastudio.core.palette.Analyzer.Severity;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import org.junit.jupiter.api.Test;

class AnalyzerTest {
    // A palette with nothing to fix: lightness from dark to light without gaps, three muted oranges and their
    // complement as a light accent.
    private static final List<Oklch> GOOD = List.of(
            new Oklch(0.30, 0.05, 30), new Oklch(0.50, 0.06, 40), new Oklch(0.65, 0.05, 35), new Oklch(0.85, 0.14, 215));

    private static List<Badge> grainy(int count) {
        return Collections.nCopies(count, Badge.GRAINY);
    }

    private static List<Finding> analyse(List<Oklch> colours) {
        return Analyzer.analyse(colours, grainy(colours.size()));
    }

    private static List<String> keys(List<Finding> findings) {
        return findings.stream().map(Finding::key).toList();
    }

    /** The good palette with new lightnesses, in the same order. */
    private static List<Oklch> withLightness(double... lightness) {
        List<Oklch> out = new ArrayList<>();
        for (int i = 0; i < lightness.length; i++) out.add(GOOD.get(i).withL(lightness[i]));
        return out;
    }

    private static List<Oklch> withHues(double... hues) {
        List<Oklch> out = new ArrayList<>();
        for (int i = 0; i < hues.length; i++) out.add(GOOD.get(i).withHue(hues[i]));
        return out;
    }

    private static List<Oklch> withChroma(double... chroma) {
        List<Oklch> out = new ArrayList<>();
        for (int i = 0; i < chroma.length; i++) out.add(GOOD.get(i).withC(chroma[i]));
        return out;
    }

    private static Finding only(List<Finding> findings, String key) {
        assertEquals(List.of(key), keys(findings));
        return findings.getFirst();
    }

    @Test
    void aGoodPaletteHasNoFindings() {
        assertEquals(List.of(), analyse(GOOD));
    }

    @Test
    void theKeysAreDistinctTranslationKeys() {
        assertEquals(12, Analyzer.KEYS.size());
        assertEquals(12, new HashSet<>(Analyzer.KEYS).size());
        for (String key : Analyzer.KEYS) assertTrue(key.startsWith("chromastudio.analysis."), key);
    }

    @Test
    void aNarrowLightnessRangeIsAWarningThatPointsToTheRoomierSide() {
        // The mean is 0.5875, over the middle of the usable range, so there is more room below.
        List<Finding> findings = analyse(withLightness(0.5, 0.55, 0.6, 0.7));
        assertEquals(List.of(Analyzer.NARROW_RANGE, Analyzer.NO_DARKS), keys(findings));
        Finding narrow = findings.getFirst();
        assertEquals(Severity.WARNING, narrow.severity());
        assertEquals(0.2875, narrow.suggestedTarget().l(), 1e-9);
        assertEquals(Analyzer.DARKER, narrow.suggestedFilter());

        findings = analyse(withLightness(0.3, 0.35, 0.4, 0.5));
        assertEquals(List.of(Analyzer.NARROW_RANGE, Analyzer.NO_HIGHLIGHTS), keys(findings));
        assertEquals(0.6875, findings.getFirst().suggestedTarget().l(), 1e-9);
        assertEquals(Analyzer.LIGHTER, findings.getFirst().suggestedFilter());
    }

    @Test
    void aGapInLightnessSuggestsItsMidpoint() {
        Finding gap = only(analyse(withLightness(0.25, 0.3, 0.7, 0.9)), Analyzer.LIGHTNESS_GAP);
        assertEquals(Severity.INFO, gap.severity());
        assertEquals(0.5, gap.suggestedTarget().l(), 1e-9);
        assertEquals("", gap.suggestedFilter());

        // The order the colours come in doesn't matter, and of two gaps the wider is the one reported.
        gap = only(analyse(withLightness(0.95, 0.2, 0.55, 0.21)), Analyzer.LIGHTNESS_GAP);
        assertEquals(0.75, gap.suggestedTarget().l(), 1e-9);
    }

    @Test
    void noDarksAndNoHighlightsEachSuggestOne() {
        Finding dark = only(analyse(withLightness(0.45, 0.6, 0.75, 0.9)), Analyzer.NO_DARKS);
        assertEquals(0.25, dark.suggestedTarget().l(), 1e-9);
        assertEquals(Analyzer.DARKER, dark.suggestedFilter());

        // The accent is the darkest here, to keep it clear of the mean.
        Finding light = only(analyse(withLightness(0.65, 0.5, 0.35, 0.2)), Analyzer.NO_HIGHLIGHTS);
        assertEquals(0.85, light.suggestedTarget().l(), 1e-9);
        assertEquals(Analyzer.LIGHTER, light.suggestedFilter());
    }

    @Test
    void lightnessSuggestionsKeepThePalettesHueAndChroma() {
        Finding dark = only(analyse(List.of(
                new Oklch(0.45, 0.06, 30), new Oklch(0.6, 0.06, 30), new Oklch(0.75, 0.06, 30), new Oklch(0.9, 0.06, 30))), Analyzer.NO_DARKS);
        assertEquals(30, dark.suggestedTarget().h(), 1e-9);
        assertEquals(0.06, dark.suggestedTarget().c(), 1e-9);
    }

    @Test
    void huesThatFitAHarmonyAreLeftAlone() {
        // Triadic from 350, each hue a few degrees out, across the seam.
        assertEquals(List.of(), analyse(withHues(355, 345, 105, 238)));
        // Analogous: 15 either side of a seed at 45.
        assertEquals(List.of(), analyse(withHues(30, 45, 60, 45)));
        // One hue throughout.
        assertEquals(List.of(), analyse(withHues(200, 200, 200, 200)));
        // Just inside the 20 degrees: complementary from 20, with 0 and 40 on one side.
        assertEquals(List.of(), analyse(withHues(1, 39, 20, 200)));
    }

    @Test
    void greysDoNotCountAsHues() {
        List<Oklch> colours = new ArrayList<>(GOOD);
        colours.add(new Oklch(0.4, 0.02, 120));
        colours.add(new Oklch(0.75, 0.0, 300));
        assertEquals(List.of(), analyse(colours));
    }

    @Test
    void strayHuesSuggestTheHarmonyHueThePaletteLacks() {
        // 0, 45, 90 and 135 are 45 apart, so a harmony hue within 22.5 of one reaches at most one more. Covering
        // all four that closely takes two harmony hues 90 apart, at 22.5 and 112.5, and only a square has those.
        // Its other corners are 202.5 and 292.5; 300 sits by the second, which leaves 202.5 the one missing.
        List<Oklch> colours = List.of(new Oklch(0.30, 0.05, 0), new Oklch(0.45, 0.06, 45), new Oklch(0.60, 0.05, 90),
                new Oklch(0.70, 0.04, 135), new Oklch(0.85, 0.15, 300));
        Finding stray = only(analyse(colours), Analyzer.HUES_STRAY);
        assertEquals(Severity.INFO, stray.severity());
        assertEquals("", stray.suggestedFilter());
        assertEquals(202.5, stray.suggestedTarget().h(), 1e-9);
        assertEquals(0.58, stray.suggestedTarget().l(), 1e-9);
        assertEquals(0.07, stray.suggestedTarget().c(), 1e-9);

        // A grey has no hue to stray, but it still counts towards the lightness and chroma suggested.
        List<Oklch> withGrey = new ArrayList<>(colours);
        withGrey.add(new Oklch(0.20, 0, 0));
        stray = only(analyse(withGrey), Analyzer.HUES_STRAY);
        assertEquals(202.5, stray.suggestedTarget().h(), 1e-9);
        assertEquals(3.1 / 6, stray.suggestedTarget().l(), 1e-9);
        assertEquals(0.35 / 6, stray.suggestedTarget().c(), 1e-9);

        // Closing the hues up to 40 apart lets the same square fit within 20.
        assertEquals(List.of(), analyse(List.of(new Oklch(0.30, 0.05, 2.5), new Oklch(0.45, 0.06, 42.5),
                new Oklch(0.60, 0.05, 92.5), new Oklch(0.70, 0.04, 132.5), new Oklch(0.85, 0.15, 300))));
    }

    @Test
    void aPaletteOfGreysHasNoAccent() {
        Finding accent = only(analyse(withChroma(0.02, 0.01, 0.035, 0.0)), Analyzer.NO_ACCENT);
        assertEquals(0.15, accent.suggestedTarget().c(), 1e-9);
        assertEquals(0.575, accent.suggestedTarget().l(), 1e-9);
        assertEquals(Analyzer.ACCENT, accent.suggestedFilter());
    }

    @Test
    void mostlySaturatedColoursAreTooMuch() {
        Finding saturated = only(analyse(withChroma(0.15, 0.13, 0.14, 0.05)), Analyzer.TOO_SATURATED);
        assertEquals(0.04, saturated.suggestedTarget().c(), 1e-9);
        // Exactly half is not more than half.
        assertEquals(List.of(), analyse(withChroma(0.15, 0.05, 0.05, 0.14)));
    }

    @Test
    void anAccentAtTheMeanLightnessIsMovedAwayFromIt() {
        // The mean is 0.575. The accent sits just above it, so it goes lighter.
        List<Oklch> colours = List.of(
                new Oklch(0.30, 0.05, 30), new Oklch(0.50, 0.06, 40), new Oklch(0.85, 0.05, 35), new Oklch(0.65, 0.14, 215));
        Finding contrast = only(analyse(colours), Analyzer.ACCENT_CONTRAST);
        assertEquals(new Oklch(0.9, 0.14, 215), contrast.suggestedTarget());
        assertEquals(Analyzer.ACCENT, contrast.suggestedFilter());

        // Just below the mean of 0.5625, it goes darker.
        colours = List.of(
                new Oklch(0.30, 0.05, 30), new Oklch(0.50, 0.14, 215), new Oklch(0.60, 0.05, 35), new Oklch(0.85, 0.05, 35));
        contrast = only(analyse(colours), Analyzer.ACCENT_CONTRAST);
        assertEquals(0.25, contrast.suggestedTarget().l(), 1e-9);

        // With no room above, it goes darker even from over the mean.
        colours = List.of(
                new Oklch(0.40, 0.05, 30), new Oklch(0.70, 0.06, 40), new Oklch(0.95, 0.05, 35), new Oklch(0.75, 0.14, 215));
        contrast = only(analyse(colours), Analyzer.ACCENT_CONTRAST);
        assertEquals(0.5, contrast.suggestedTarget().l(), 1e-9);
    }

    @Test
    void textureFindings() {
        Finding busy = only(Analyzer.analyse(GOOD, List.of(Badge.BUSY, Badge.BUSY, Badge.BUSY, Badge.GRAINY)), Analyzer.TOO_BUSY);
        assertEquals(Analyzer.GRAINY, busy.suggestedFilter());
        assertEquals(0.575, busy.suggestedTarget().l(), 1e-9);

        Finding jump = only(Analyzer.analyse(GOOD, List.of(Badge.FLAT, Badge.FLAT, Badge.BUSY, Badge.FLAT)), Analyzer.TEXTURE_JUMP);
        assertEquals(Analyzer.GRAINY, jump.suggestedFilter());

        assertEquals(List.of(Analyzer.TOO_BUSY, Analyzer.TEXTURE_JUMP),
                keys(Analyzer.analyse(GOOD, List.of(Badge.BUSY, Badge.BUSY, Badge.BUSY, Badge.FLAT))));
        // Half busy is not more than half, and a grainy block bridges flat and busy.
        assertEquals(List.of(), Analyzer.analyse(GOOD, List.of(Badge.BUSY, Badge.BUSY, Badge.GRAINY, Badge.FLAT)));
        assertEquals(List.of(), Analyzer.analyse(GOOD, List.of(Badge.FLAT, Badge.FLAT, Badge.FLAT, Badge.FLAT)));
    }

    @Test
    void sizeFindings() {
        List<Finding> empty = Analyzer.analyse(List.of(), List.of());
        Finding small = only(empty, Analyzer.TOO_SMALL);
        assertNull(small.suggestedTarget());
        assertEquals("", small.suggestedFilter());

        assertEquals(Analyzer.TOO_SMALL, keys(analyse(GOOD.subList(0, 2))).getLast());
        assertEquals(Analyzer.TOO_SMALL, keys(analyse(GOOD.subList(0, 1))).getLast());
        assertEquals(List.of(), analyse(List.of(new Oklch(0.3, 0.14, 215), new Oklch(0.55, 0.05, 30), new Oklch(0.8, 0.05, 40))));

        List<Oklch> many = new ArrayList<>();
        for (int i = 0; i < 13; i++) many.add(new Oklch(0.25 + 0.05 * i, i == 12 ? 0.14 : 0.05, 30));
        Finding large = only(analyse(many), Analyzer.TOO_LARGE);
        assertNull(large.suggestedTarget());
        assertEquals(List.of(), analyse(many.subList(1, 13)));
    }

    @Test
    void findingsComeInTheOrderOfTheKeys() {
        // Two mid greys with busy and flat textures: nearly everything is wrong at once.
        List<Finding> findings = Analyzer.analyse(
                List.of(new Oklch(0.5, 0.0, 0), new Oklch(0.55, 0.01, 0)), List.of(Badge.BUSY, Badge.FLAT));
        assertEquals(List.of(Analyzer.NARROW_RANGE, Analyzer.NO_DARKS, Analyzer.NO_HIGHLIGHTS, Analyzer.NO_ACCENT,
                Analyzer.TEXTURE_JUMP, Analyzer.TOO_SMALL), keys(findings));

        // Fourteen vivid colours of every hue, all mid-light and busy.
        List<Oklch> colours = new ArrayList<>();
        for (int i = 0; i < 14; i++) colours.add(new Oklch(0.6 + 0.005 * i, 0.2, 25 * i));
        findings = Analyzer.analyse(colours, Collections.nCopies(14, Badge.BUSY));
        assertEquals(List.of(Analyzer.NARROW_RANGE, Analyzer.NO_DARKS, Analyzer.NO_HIGHLIGHTS, Analyzer.HUES_STRAY,
                Analyzer.TOO_SATURATED, Analyzer.ACCENT_CONTRAST, Analyzer.TOO_BUSY, Analyzer.TOO_LARGE), keys(findings));
        for (Finding f : findings) assertTrue(Analyzer.KEYS.contains(f.key()));
    }

    @Test
    void eachColourNeedsItsBadge() {
        assertThrows(IllegalArgumentException.class, () -> Analyzer.analyse(GOOD, grainy(3)));
    }
}
