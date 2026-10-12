package dev.chromastudio.client.ui;

import dev.chromastudio.core.palette.PaletteEntry;
import java.util.List;

/**
 * What one tab leaves for another to pick up the next time it is selected. A consumer reads its field in
 * {@code Tab.init} or on select, acts on it and sets it back to null, so nothing is applied twice.
 */
public final class Handoff {
    private Handoff() {}

    /** A target for the Match tab: a {@code core.color.Color} or a {@code client.index.Entry}. Set by the Analyse panel's fixes. */
    public static Object matchTarget;

    /** A filter preset token from {@code Analyzer} findings: "darker", "lighter", "accent", "grainy" or "". Read with {@link #matchTarget}. */
    public static String matchFilter;

    /** An {@code Entry} the Gradient tab adds as a stop. Set by a right-click on a Match result. */
    public static Object gradientSeed;

    /** Entries the Explore tab highlights. Set by the Analyse panel; the Explore tab keeps them until a new list arrives or the player clears them. */
    public static List<PaletteEntry> exploreHighlight;
}
