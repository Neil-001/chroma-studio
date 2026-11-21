package dev.chromastudio.client.ui;

import dev.chromastudio.core.palette.PaletteEntry;
import dev.chromastudio.core.palette.StoredGradient;
import java.util.List;

public final class Handoff {
    private Handoff() {}

    public static Object matchTarget;

    public static String matchFilter;

    public static Object gradientSeed;

    public static StoredGradient gradientLoad;

    public static List<PaletteEntry> exploreHighlight;
}
