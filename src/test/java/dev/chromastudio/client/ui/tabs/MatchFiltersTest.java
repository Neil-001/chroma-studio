package dev.chromastudio.client.ui.tabs;

import dev.chromastudio.client.FaceChoice;
import dev.chromastudio.client.ModSettings;
import dev.chromastudio.client.index.Kind;
import dev.chromastudio.client.index.Query;
import dev.chromastudio.client.index.Query.Tri;
import dev.chromastudio.core.color.Badge;
import dev.chromastudio.core.color.Face;
import dev.chromastudio.core.color.Oklch;
import dev.chromastudio.core.color.Tone;
import dev.chromastudio.core.palette.Analyzer;
import java.util.Set;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class MatchFiltersTest {
    @BeforeEach
    @AfterEach
    void defaults() { ModSettings.reset(); }

    @Test
    void aFreshTabStartsFromTheSettings() {
        MatchFilters plain = MatchFilters.fromSettings();
        assertFalse(plain.fullCube || plain.solid || plain.opaque || plain.technical);
        assertEquals(Tri.ANY, plain.creativeOnly);
        assertEquals(0, plain.extras());

        ModSettings.filterFullCube = true;
        ModSettings.filterSolid = true;
        ModSettings.filterOpaque = true;
        ModSettings.filterSurvival = true;
        ModSettings.technicalBlocks = true;
        MatchFilters f = MatchFilters.fromSettings();
        assertTrue(f.fullCube && f.solid && f.opaque && f.technical);
        assertEquals(Tri.EXCLUDE, f.creativeOnly, "survival blocks only means leaving the creative-only ones out");
    }

    @Test
    void everyFilterReachesTheQuery() {
        MatchFilters f = new MatchFilters();
        f.kind = Kind.STAIRS;
        f.fullCube = f.solid = f.opaque = f.sameTexture = f.technical = true;
        f.tinted = Tri.ONLY;
        f.creativeOnly = Tri.EXCLUDE;
        f.lightSource = Tri.ONLY;
        f.namespaces.put("minecraft", Tri.ONLY);
        f.namespaces.put("create", Tri.EXCLUDE);
        f.namespaces.put("farmersdelight", Tri.ANY);
        f.tone = Tone.WARM;
        f.lightnessMin = 0.2;
        f.lightnessMax = 0.7;
        f.chromaMin = 0.05;
        f.chromaMax = 0.2;
        f.badges.add(Badge.FLAT);

        f.blockEntityRendered = true;

        Query q = new Query();
        q.kinds.add(Kind.SLAB);
        q.namespaces.add("stale");
        q.badges.add(Badge.BUSY);
        f.apply(q);
        assertEquals(Set.of(Kind.STAIRS), q.kinds);
        assertTrue(q.fullCube && q.solid && q.opaque && q.sameTexture && q.technical);
        assertEquals(Tri.ONLY, q.tinted);
        assertEquals(Tri.EXCLUDE, q.creativeOnly);
        assertEquals(Tri.ONLY, q.lightSource);
        assertEquals(Set.of("minecraft"), q.namespaces);
        assertEquals(Set.of("create"), q.namespacesExcluded);
        assertEquals(Tone.WARM, q.tone);
        assertEquals(0.2, q.lightnessMin);
        assertEquals(0.7, q.lightnessMax);
        assertEquals(0.05, q.chromaMin);
        assertEquals(0.2, q.chromaMax);
        assertEquals(Set.of(Badge.FLAT), q.badges);
        assertTrue(q.blockEntityRendered);
        assertEquals(12, f.extras(), "ten filters behind More and two mods, each counted once");
    }

    @Test
    void noFiltersLeaveTheQueryOpen() {
        Query q = new Query();
        new MatchFilters().apply(q);
        assertTrue(q.kinds.isEmpty() && q.namespaces.isEmpty() && q.namespacesExcluded.isEmpty() && q.badges.isEmpty());
        assertNull(q.tone);
        assertFalse(q.blockEntityRendered, "chests and the like stay out until asked for");
        assertEquals(Double.POSITIVE_INFINITY, q.chromaMax, "the chroma slider's top is no limit at all");
        assertEquals(1, q.lightnessMax);
    }

    @Test
    void anAnalyseFixNarrowsTheFilters() {
        Oklch dark = new Oklch(0.3, 0.02, 80);
        MatchFilters darker = new MatchFilters();
        darker.preset(Analyzer.DARKER, dark);
        assertEquals(0, darker.lightnessMin);
        assertEquals(0.4, darker.lightnessMax, 1e-9);

        MatchFilters lighter = new MatchFilters();
        lighter.preset(Analyzer.LIGHTER, new Oklch(0.8, 0.02, 80));
        assertEquals(0.7, lighter.lightnessMin, 1e-9);
        assertEquals(1, lighter.lightnessMax);

        MatchFilters accent = new MatchFilters();
        accent.preset(Analyzer.ACCENT, dark);
        assertEquals(MatchFilters.ACCENT_CHROMA, accent.chromaMin);

        MatchFilters grainy = new MatchFilters();
        grainy.badges.add(Badge.BUSY);
        grainy.preset(Analyzer.GRAINY, null);
        assertEquals(Set.of(Badge.GRAINY), grainy.badges);

        MatchFilters untouched = new MatchFilters();
        untouched.preset(Analyzer.NO_FILTER, dark);
        untouched.preset("something new", dark);
        untouched.preset(null, dark);
        assertEquals(0, untouched.extras());
    }

    @Test
    void theFaceControlBecomesTheFacesAQueryScores() {
        Query q = new Query();
        MatchState.face(q, FaceChoice.SIDE);
        assertEquals(Face.ANY, q.face);
        assertEquals(Set.of(Face.NORTH, Face.SOUTH, Face.WEST, Face.EAST), q.faces);
        MatchState.face(q, FaceChoice.ANY);
        assertEquals(Face.ANY, q.face);
        assertTrue(q.faces.isEmpty(), "any face means all six again");
        MatchState.face(q, FaceChoice.TOP);
        assertEquals(Face.UP, q.face);
        MatchState.face(q, FaceChoice.BOTTOM);
        assertEquals(Face.DOWN, q.face);
        MatchState.face(q, FaceChoice.WEST);
        assertEquals(Face.WEST, q.face);
    }
}
