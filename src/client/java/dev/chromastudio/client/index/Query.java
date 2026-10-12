package dev.chromastudio.client.index;

import dev.chromastudio.core.color.Badge;
import dev.chromastudio.core.color.Face;
import dev.chromastudio.core.color.Metric;
import dev.chromastudio.core.color.Scale;
import dev.chromastudio.core.color.Tone;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.Set;

/**
 * How to search the index and what to leave out. Set the fields, or chain the setters for the common ones. A fresh
 * query filters nothing but technical blocks, which follow the setting.
 */
public final class Query {
    /** A filter on something an entry either is or isn't. */
    public enum Tri {
        ANY, ONLY, EXCLUDE;

        boolean allows(boolean value) { return this == ANY || (this == ONLY) == value; }
    }

    /** The face to match. {@link Face#ANY} scores all six and keeps the best. */
    public Face face = Face.ANY;
    public Scale scale = Scale.T8;
    public Metric metric = Metric.OKLAB;
    /** Whether faces are darkened as the game lights them before they are scored. */
    public boolean shading = false;
    public TintContext tint = TintContext.HERE;
    /** The most matches to return. */
    public int limit = 12;
    /** Whether every state of a block is matched on its own, where otherwise a block gives only its best state. */
    public boolean statesSeparately = IndexOptions.current().statesSeparately();

    /** The kinds to keep. Empty keeps all. */
    public final Set<Kind> kinds = EnumSet.noneOf(Kind.class);
    /** Each of these, when true, keeps only entries that are so. */
    public boolean fullCube, solid, opaque, sameTexture;
    public Tri tinted = Tri.ANY, creativeOnly = Tri.ANY, lightSource = Tri.ANY;
    /** The mod namespaces to keep, such as {@code minecraft}. Empty keeps all. */
    public final Set<String> namespaces = new HashSet<>();
    public final Set<String> namespacesExcluded = new HashSet<>();
    /** Whether blocks outside the creative tabs are kept. */
    public boolean technical = IndexOptions.current().technical();
    /** The tone of the matched face's average colour, or null for any. */
    public Tone tone;
    /** The OKLCH lightness and chroma the matched face's average colour must lie within. */
    public double lightnessMin = 0, lightnessMax = 1, chromaMin = 0, chromaMax = Double.POSITIVE_INFINITY;
    /** The texture badges to keep, by the matched face. Empty keeps all. */
    public final Set<Badge> badges = EnumSet.noneOf(Badge.class);

    public Query face(Face f) { face = f; return this; }

    public Query scale(Scale s) { scale = s; return this; }

    public Query metric(Metric m) { metric = m; return this; }

    public Query shading(boolean on) { shading = on; return this; }

    public Query tint(TintContext t) { tint = t; return this; }

    public Query limit(int n) { limit = n; return this; }

    /** Whether an entry passes the filters that don't depend on which face matched. */
    boolean keeps(Entry e) {
        if (!kinds.isEmpty() && !kinds.contains(e.kind())) return false;
        if (fullCube && !e.fullCube() || solid && !e.solid() || opaque && !e.opaque() || sameTexture && !e.sameTextureAllRound()) return false;
        if (!tinted.allows(e.tinted()) || !creativeOnly.allows(e.creativeOnly()) || !lightSource.allows(e.lightSource())) return false;
        if (!technical && e.technical()) return false;
        if (namespaces.isEmpty() && namespacesExcluded.isEmpty()) return true;
        String namespace = e.id().substring(0, e.id().indexOf(':'));
        return (namespaces.isEmpty() || namespaces.contains(namespace)) && !namespacesExcluded.contains(namespace);
    }

    /** Whether a face passes the filters on its colour and texture. */
    boolean keeps(Sampled face) {
        if (!badges.isEmpty() && !badges.contains(face.stats.badge())) return false;
        if (tone == null && lightnessMin <= 0 && lightnessMax >= 1 && chromaMin <= 0 && chromaMax == Double.POSITIVE_INFINITY) return true;
        var lch = face.stats.mean(scale).oklch();
        if (tone != null && Tone.of(lch) != tone) return false;
        return lch.l() >= lightnessMin && lch.l() <= lightnessMax && lch.c() >= chromaMin && lch.c() <= chromaMax;
    }
}
