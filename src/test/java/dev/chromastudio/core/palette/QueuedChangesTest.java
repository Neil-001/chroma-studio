package dev.chromastudio.core.palette;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.chromastudio.core.palette.QueuedChanges.Origin;
import dev.chromastudio.core.palette.QueuedChanges.Refusal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

class QueuedChangesTest {
    private static final Object OVERWORLD = new Object(), NETHER = new Object();
    private static final Origin HERE = new Origin(OVERWORLD, 3, 7);

    /** A world of strings, with "air" wherever nothing was set. */
    private final Map<Long, String> blocks = new HashMap<>();
    /** The masks as they're set now: a new {@link Mask} is made from these each time they're read. */
    private final Set<Long> out = new HashSet<>(), failsAt = new HashSet<>();
    /** Every position the masks were asked about, in order. */
    private final List<Integer> asked = new ArrayList<>();
    private Origin now = HERE;
    private boolean editorOn = true;

    /** The masks read once: keeps out {@link #out}, and fails once it's asked about a position in {@link #failsAt}. */
    private final class Mask implements QueuedChanges.Mask {
        boolean failed;

        @Override
        public boolean allows(int x, int y, int z) {
            long p = BuildSearch.pack(x, y, z);
            asked.add(x);
            if (failed || failsAt.contains(p)) {
                failed = true;
                return false;
            }
            return !out.contains(p);
        }

        @Override
        public boolean failed() { return failed; }
    }

    private String at(int x) { return blocks.getOrDefault(BuildSearch.pack(x, 0, 0), "air"); }

    private void set(int x, String s) { blocks.put(BuildSearch.pack(x, 0, 0), s); }

    /**
     * A row of blocks along x from 0, as a tool would see it: the row runs on while blocks aren't air, each stone the
     * masks let through becomes bricks, and the rest stay. A block that joins the row joins the plan.
     */
    private final QueuedChanges.Plan<String> row = mask -> {
        QueuedChanges<String> q = new QueuedChanges<>(now);
        for (int x = 0; !at(x).equals("air"); x++) {
            boolean allowed = mask.allows(x, 0, 0);
            if (allowed && at(x).equals("stone")) q.add(x, 0, 0, "stone", "bricks");
            else q.leave(x, 0, 0, at(x));
        }
        return q;
    };

    private QueuedChanges<String> made() {
        QueuedChanges<String> q = row.make(new Mask());
        asked.clear();
        return q;
    }

    private Refusal check(QueuedChanges<String> q) { return q.check(() -> now, () -> editorOn, Mask::new, row); }

    private QueuedChanges<String> twoBricks() {
        set(0, "stone");
        set(1, "stone");
        return made();
    }

    @Test
    void untouchedChangesMayBeSent() {
        QueuedChanges<String> q = twoBricks();
        assertEquals(2, q.size());
        assertEquals(Refusal.NONE, check(q));
    }

    @Test
    void nothingQueuedIsNothingToSend() {
        assertEquals(Refusal.EMPTY, new QueuedChanges<String>(HERE).check(() -> now, () -> true, Mask::new, row));
    }

    @Test
    void anotherWorldIndexOrVisitRefuses() {
        QueuedChanges<String> q = twoBricks();
        // Another dimension has its own blocks at the same coordinates, which may well match.
        now = new Origin(NETHER, 3, 7);
        assertEquals(Refusal.WORLD, check(q));
        now = new Origin(OVERWORLD, 4, 7);
        assertEquals(Refusal.INDEX, check(q));
        // The editor closed and opened again while the button was held.
        now = new Origin(OVERWORLD, 3, 8);
        assertEquals(Refusal.EDITOR, check(q));
    }

    @Test
    void anEditorSwitchedOffRefusesThoughNoVisitWasCounted() {
        QueuedChanges<String> q = twoBricks();
        editorOn = false;
        assertEquals(Refusal.EDITOR, check(q));
        assertEquals(List.of(), asked, "nothing is worked out for a closed editor");
    }

    @Test
    void whereThePlayerIsIsReadAgainAfterThePlanIsWorkedOut() {
        QueuedChanges<String> q = twoBricks();
        // The index is rebuilt on its own thread, and may be published while a big build is worked out again.
        QueuedChanges.Plan<String> slow = mask -> {
            QueuedChanges<String> redone = row.make(mask);
            now = new Origin(OVERWORLD, 4, 7);
            return redone;
        };
        assertEquals(Refusal.INDEX, q.check(() -> now, () -> true, Mask::new, slow));
    }

    @Test
    void aBlockThatChangedRefusesTheLot() {
        QueuedChanges<String> q = twoBricks();
        set(1, "chest");
        assertEquals(Refusal.CHANGED, check(q));
    }

    @Test
    void aBlockThatChangedToOneThatWouldBeChangedTheSameStillRefuses() {
        // A plan that doesn't look at the block it replaces, as Scatter doesn't, would put the same there: what was
        // seen counts too, so a chest placed under a held stroke is never painted over.
        set(0, "stone");
        QueuedChanges<String> q = new QueuedChanges<>(HERE);
        q.add(0, 0, 0, "stone", "bricks");
        QueuedChanges.Plan<String> scatter = _ -> {
            QueuedChanges<String> redone = new QueuedChanges<>(now);
            redone.add(0, 0, 0, at(0), "bricks");
            return redone;
        };
        assertEquals(Refusal.NONE, q.check(() -> now, () -> true, Mask::new, scatter));
        set(0, "chest");
        assertEquals(Refusal.CHANGED, q.check(() -> now, () -> true, Mask::new, scatter));
    }

    @Test
    void aMaskThatNowKeepsABlockOutRefuses() {
        QueuedChanges<String> q = twoBricks();
        out.add(BuildSearch.pack(1, 0, 0));
        assertEquals(Refusal.CHANGED, check(q));
    }

    @Test
    void aMaskThatNowLetsInABlockItKeptOutRefuses() {
        set(0, "stone");
        set(1, "stone");
        out.add(BuildSearch.pack(1, 0, 0));
        QueuedChanges<String> q = made();
        assertEquals(1, q.size());
        out.clear();
        assertEquals(Refusal.CHANGED, check(q));
    }

    @Test
    void aBlockThatJoinsTheBuildRefuses() {
        QueuedChanges<String> q = twoBricks();
        set(2, "stone");
        assertEquals(Refusal.CHANGED, check(q));
    }

    @Test
    void aBlockLeftAloneThatChangedRefusesTheLot() {
        // A stone bridge joins two wings: the tool leaves it as it is, but without it the far wing is no longer part
        // of the build the player saw.
        set(0, "stone");
        set(1, "bridge");
        set(2, "stone");
        QueuedChanges<String> q = made();
        assertEquals(2, q.size());
        set(1, "cobble");
        assertEquals(Refusal.CHANGED, check(q));
    }

    @Test
    void aMaskThatFailsOnAnyBlockRefusesEvenOneThatIsNeverChanged() {
        // The masks are asked about every position the plan looks at, so a failure at a block that stays is seen too.
        set(0, "stone");
        set(1, "bridge");
        QueuedChanges<String> q = made();
        failsAt.add(BuildSearch.pack(1, 0, 0));
        assertEquals(Refusal.MASK_FAILED, check(q));
        assertEquals(List.of(0, 1), asked);
    }

    @Test
    void theMasksAreAskedAfreshWhenTheChangesAreChecked() {
        QueuedChanges<String> q = twoBricks();
        // The answers the plan was made with stand for nothing: each check reads the masks again.
        assertEquals(Refusal.NONE, check(q));
        assertEquals(List.of(0, 1), asked);
        assertEquals(Refusal.NONE, check(q));
        assertEquals(List.of(0, 1, 0, 1), asked);
    }

    @Test
    void aPositionQueuedTwiceKeepsWhatWasFirstSeen() {
        QueuedChanges<String> q = new QueuedChanges<>(HERE);
        q.add(0, 0, 0, "stone", "bricks");
        q.add(0, 0, 0, "bricks", "tuff");
        assertEquals(1, q.size());
        QueuedChanges.Change<String> c = q.changes().iterator().next();
        assertEquals("stone", c.was());
        assertEquals("tuff", c.next());
    }

    @Test
    void plansAreTheSameOnlyWhenEveryPositionIsTheSame() {
        QueuedChanges<String> a = new QueuedChanges<>(HERE), b = new QueuedChanges<>(new Origin(NETHER, 0, 0));
        a.add(0, 0, 0, "stone", "bricks");
        b.add(0, 0, 0, "stone", "bricks");
        assertTrue(a.sameAs(b), "where a plan was made isn't part of it");
        a.leave(1, 0, 0, "stone");
        assertFalse(a.sameAs(b));
        b.leave(1, 0, 0, "stone");
        assertTrue(a.sameAs(b));
        // A change queued at a position left alone wins, as a door's other half put after its own visit.
        a.leave(2, 0, 0, "door");
        a.add(2, 0, 0, "door", "spruce door");
        b.add(2, 0, 0, "door", "spruce door");
        b.leave(2, 0, 0, "door");
        assertTrue(a.sameAs(b));
    }
}
