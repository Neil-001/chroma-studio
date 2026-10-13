package dev.chromastudio.client.ui.tabs;

import dev.chromastudio.client.ModSettings;
import dev.chromastudio.client.index.Kind;
import dev.chromastudio.client.index.Query;
import dev.chromastudio.client.index.Query.Tri;
import dev.chromastudio.core.color.Badge;
import dev.chromastudio.core.color.Oklch;
import dev.chromastudio.core.color.Tone;
import dev.chromastudio.core.palette.Analyzer;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/** The filters the Match tab shows, which the Explore tab follows too. {@link #apply} writes them into a query. */
public final class MatchFilters {
    /** How far either side of a suggested lightness "darker" and "lighter" still accept. */
    static final double LIGHTNESS_ROOM = 0.1;
    /** The least chroma an accent has. */
    static final double ACCENT_CHROMA = 0.1;

    /** The one kind to keep, or null for every kind. */
    public Kind kind;
    public boolean fullCube, solid, opaque, sameTexture;
    public Tri tinted = Tri.ANY, creativeOnly = Tri.ANY, lightSource = Tri.ANY;
    /** Whether blocks outside the creative tabs are kept. */
    public boolean technical;
    /** Whether blocks a block entity draws, such as chests and banners, are kept. */
    public boolean blockEntityRendered;
    /** Mod namespaces that are kept alone ({@link Tri#ONLY}) or left out ({@link Tri#EXCLUDE}). One not listed is {@link Tri#ANY}. */
    public final Map<String, Tri> namespaces = new TreeMap<>();
    /** The tone to keep, or null for any. */
    public Tone tone;
    public double lightnessMin = 0, lightnessMax = 1, chromaMin = 0, chromaMax = TargetModel.CHROMA_MAX;
    /** The texture badges to keep. Empty keeps all. */
    public final Set<Badge> badges = EnumSet.noneOf(Badge.class);

    /** The settings the filters start from, as they were when last taken. */
    private List<Boolean> seen = List.of();

    /** The filters a fresh Match tab starts with, from the settings. */
    public static MatchFilters fromSettings() {
        MatchFilters f = new MatchFilters();
        f.followSettings();
        return f;
    }

    /**
     * Takes each setting a filter starts from that has changed since it was last taken, and says whether any had.
     * The settings screen changes and resets them behind the tab's back, and a filter left at the old value would
     * go on showing, say, technical blocks after the setting was reset. A filter whose setting hasn't moved keeps
     * what the player made it.
     */
    public boolean followSettings() {
        List<Boolean> now = List.of(ModSettings.filterFullCube, ModSettings.filterSolid, ModSettings.filterOpaque, ModSettings.filterSurvival,
                ModSettings.technicalBlocks);
        if (now.equals(seen)) return false;
        boolean first = seen.isEmpty();
        if (first || now.get(0) != seen.get(0)) fullCube = now.get(0);
        if (first || now.get(1) != seen.get(1)) solid = now.get(1);
        if (first || now.get(2) != seen.get(2)) opaque = now.get(2);
        if (first || now.get(3) != seen.get(3)) creativeOnly = now.get(3) ? Tri.EXCLUDE : Tri.ANY;
        if (first || now.get(4) != seen.get(4)) technical = now.get(4);
        seen = now;
        return true;
    }

    /**
     * Narrows the filters as an Analyse finding asks: one of {@link Analyzer}'s tokens, with the colour it suggests
     * looking for. An unknown token changes nothing.
     */
    public void preset(String token, Oklch around) {
        if (token == null) return;
        switch (token) {
            case Analyzer.DARKER -> {
                lightnessMin = 0;
                lightnessMax = around == null ? 0.5 : Math.min(1, around.l() + LIGHTNESS_ROOM);
            }
            case Analyzer.LIGHTER -> {
                lightnessMin = around == null ? 0.5 : Math.max(0, around.l() - LIGHTNESS_ROOM);
                lightnessMax = 1;
            }
            case Analyzer.ACCENT -> {
                chromaMin = ACCENT_CHROMA;
                chromaMax = TargetModel.CHROMA_MAX;
            }
            case Analyzer.GRAINY -> {
                badges.clear();
                badges.add(Badge.GRAINY);
            }
            default -> { }
        }
    }

    /** Writes the filters into a query, replacing the ones it had. */
    public void apply(Query q) {
        q.kinds.clear();
        if (kind != null) q.kinds.add(kind);
        q.fullCube = fullCube;
        q.solid = solid;
        q.opaque = opaque;
        q.sameTexture = sameTexture;
        q.tinted = tinted;
        q.creativeOnly = creativeOnly;
        q.lightSource = lightSource;
        q.technical = technical;
        q.blockEntityRendered = blockEntityRendered;
        q.namespaces.clear();
        q.namespacesExcluded.clear();
        namespaces.forEach((namespace, how) -> {
            if (how == Tri.ONLY) q.namespaces.add(namespace);
            else if (how == Tri.EXCLUDE) q.namespacesExcluded.add(namespace);
        });
        q.tone = tone;
        q.lightnessMin = lightnessMin;
        q.lightnessMax = lightnessMax;
        q.chromaMin = chromaMin;
        // The slider's top means no limit: a few textures are more vivid than it reaches.
        q.chromaMax = chromaMax >= TargetModel.CHROMA_MAX ? Double.POSITIVE_INFINITY : chromaMax;
        q.badges.clear();
        q.badges.addAll(badges);
    }

    /** How many of the filters behind "More…" are narrowing the results, for its label. */
    public int extras() {
        int n = 0;
        if (sameTexture) n++;
        if (tinted != Tri.ANY) n++;
        if (creativeOnly != Tri.ANY) n++;
        if (lightSource != Tri.ANY) n++;
        if (technical != ModSettings.technicalBlocks) n++;
        if (blockEntityRendered) n++;
        if (tone != null) n++;
        if (lightnessMin > 0 || lightnessMax < 1) n++;
        if (chromaMin > 0 || chromaMax < TargetModel.CHROMA_MAX) n++;
        if (!badges.isEmpty()) n++;
        n += (int) namespaces.values().stream().filter(t -> t != Tri.ANY).count();
        return n;
    }
}
