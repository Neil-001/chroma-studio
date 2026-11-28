package dev.chromastudio.client.index;

import dev.chromastudio.core.color.Badge;
import dev.chromastudio.core.color.Face;
import dev.chromastudio.core.color.Metric;
import dev.chromastudio.core.color.Scale;
import dev.chromastudio.core.color.Tone;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.Set;

public final class Query {
    public enum Tri {
        ANY, ONLY, EXCLUDE;

        boolean allows(boolean value) { return this == ANY || (this == ONLY) == value; }
    }

    public Face face = Face.ANY;
    public final Set<Face> faces = EnumSet.noneOf(Face.class);
    public Scale scale = Scale.T8;
    public Metric metric = Metric.OKLAB;
    public boolean shading = false;
    public TintContext tint = TintContext.HERE;
    public int limit = 12;
    public boolean statesSeparately = IndexOptions.current().statesSeparately();

    public final Set<Kind> kinds = EnumSet.noneOf(Kind.class);
    public boolean fullCube, solid, opaque, sameTexture;
    public Tri tinted = Tri.ANY, creativeOnly = Tri.ANY, lightSource = Tri.ANY;
    public final Set<String> namespaces = new HashSet<>();
    public final Set<String> namespacesExcluded = new HashSet<>();
    public boolean technical = IndexOptions.current().technical();
    public boolean blockEntityRendered = false;
    public Tone tone;
    public double lightnessMin = 0, lightnessMax = 1, chromaMin = 0, chromaMax = Double.POSITIVE_INFINITY;
    public final Set<Badge> badges = EnumSet.noneOf(Badge.class);

    public Query face(Face f) { face = f; return this; }

    public Query scale(Scale s) { scale = s; return this; }

    public Query metric(Metric m) { metric = m; return this; }

    public Query shading(boolean on) { shading = on; return this; }

    public Query tint(TintContext t) { tint = t; return this; }

    public Query limit(int n) { limit = n; return this; }

    boolean scores(Face f) { return face == Face.ANY ? faces.isEmpty() || faces.contains(f) : face == f; }

    boolean keeps(Entry e) {
        if (!kinds.isEmpty() && !kinds.contains(e.kind())) return false;
        if (fullCube && !e.fullCube() || solid && !e.solid() || opaque && !e.opaque() || sameTexture && !e.sameTextureAllRound()) return false;
        if (!tinted.allows(e.tinted()) || !creativeOnly.allows(e.creativeOnly()) || !lightSource.allows(e.lightSource())) return false;
        if (!technical && e.technical()) return false;
        if (!blockEntityRendered && e.blockEntityRendered()) return false;
        if (namespaces.isEmpty() && namespacesExcluded.isEmpty()) return true;
        String namespace = e.id().substring(0, e.id().indexOf(':'));
        return (namespaces.isEmpty() || namespaces.contains(namespace)) && !namespacesExcluded.contains(namespace);
    }

    boolean keeps(Sampled face) {
        if (!badges.isEmpty() && !badges.contains(face.stats.badge())) return false;
        if (tone == null && lightnessMin <= 0 && lightnessMax >= 1 && chromaMin <= 0 && chromaMax == Double.POSITIVE_INFINITY) return true;
        var lch = face.stats.mean(scale).oklch();
        if (tone != null && Tone.of(lch) != tone) return false;
        return lch.l() >= lightnessMin && lch.l() <= lightnessMax && lch.c() >= chromaMin && lch.c() <= chromaMax;
    }
}
