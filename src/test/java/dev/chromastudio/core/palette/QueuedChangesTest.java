package dev.chromastudio.core.palette;

import static org.junit.jupiter.api.Assertions.assertEquals;

import dev.chromastudio.core.palette.QueuedChanges.Origin;
import dev.chromastudio.core.palette.QueuedChanges.Refusal;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

class QueuedChangesTest {
    private static final Object OVERWORLD = new Object(), NETHER = new Object();
    private static final Origin HERE = new Origin(OVERWORLD, 3, 7);

    /** A world of strings, with "air" wherever nothing was set. */
    private final Map<Long, String> blocks = new HashMap<>();
    private final QueuedChanges.World<String> world = (x, y, z) -> blocks.getOrDefault(BuildSearch.pack(x, y, z), "air");

    /** A mask that keeps out the positions listed, and fails once it's asked about one in {@code throwsAt}. */
    private static final class Mask implements QueuedChanges.Mask {
        final Set<Long> out = new HashSet<>(), throwsAt = new HashSet<>();
        boolean failed;

        @Override
        public boolean allows(int x, int y, int z) {
            long p = BuildSearch.pack(x, y, z);
            if (failed || throwsAt.contains(p)) {
                failed = true;
                return false;
            }
            return !out.contains(p);
        }

        @Override
        public boolean failed() { return failed; }
    }

    private void set(int x, int y, int z, String s) { blocks.put(BuildSearch.pack(x, y, z), s); }

    private QueuedChanges<String> twoBricks() {
        set(0, 0, 0, "stone");
        set(1, 0, 0, "stone");
        QueuedChanges<String> q = new QueuedChanges<>(HERE);
        q.add(0, 0, 0, "stone", "bricks");
        q.add(1, 0, 0, "stone", "bricks");
        return q;
    }

    @Test
    void untouchedChangesMayBeSent() {
        assertEquals(Refusal.NONE, twoBricks().check(HERE, world, new Mask()));
    }

    @Test
    void nothingQueuedIsNothingToSend() {
        assertEquals(Refusal.EMPTY, new QueuedChanges<String>(HERE).check(HERE, world, new Mask()));
    }

    @Test
    void anotherWorldIndexOrVisitRefuses() {
        QueuedChanges<String> q = twoBricks();
        // Another dimension has its own blocks at the same coordinates, which may well match.
        assertEquals(Refusal.WORLD, q.check(new Origin(NETHER, 3, 7), world, new Mask()));
        assertEquals(Refusal.INDEX, q.check(new Origin(OVERWORLD, 4, 7), world, new Mask()));
        // The editor closed and opened again while the button was held.
        assertEquals(Refusal.EDITOR, q.check(new Origin(OVERWORLD, 3, 8), world, new Mask()));
    }

    @Test
    void aBlockThatChangedRefusesTheLot() {
        QueuedChanges<String> q = twoBricks();
        set(1, 0, 0, "chest");
        assertEquals(Refusal.BLOCK_CHANGED, q.check(HERE, world, new Mask()));
    }

    @Test
    void aMaskThatNowKeepsABlockOutRefuses() {
        QueuedChanges<String> q = twoBricks();
        Mask mask = new Mask();
        mask.out.add(BuildSearch.pack(1, 0, 0));
        assertEquals(Refusal.MASK_DENIES, q.check(HERE, world, mask));
    }

    @Test
    void aMaskThatFailsOnAnyBlockRefusesEvenWhenTheOthersAreAllowed() {
        // The case a recomputed preview missed: the mask fails on a block that was never queued would be invisible,
        // so every queued block is asked, and a failure on any of them refuses all.
        QueuedChanges<String> q = twoBricks();
        Mask mask = new Mask();
        mask.throwsAt.add(BuildSearch.pack(1, 0, 0));
        assertEquals(Refusal.MASK_FAILED, q.check(HERE, world, mask));
        Mask broken = new Mask();
        broken.failed = true;
        assertEquals(Refusal.MASK_FAILED, q.check(HERE, world, broken));
    }

    @Test
    void aPositionQueuedTwiceKeepsWhatWasFirstSeen() {
        set(0, 0, 0, "stone");
        QueuedChanges<String> q = new QueuedChanges<>(HERE);
        q.add(0, 0, 0, "stone", "bricks");
        q.add(0, 0, 0, "bricks", "tuff");
        assertEquals(1, q.size());
        QueuedChanges.Change<String> c = q.changes().iterator().next();
        assertEquals("stone", c.was());
        assertEquals("tuff", c.next());
        assertEquals(Refusal.NONE, q.check(HERE, world, new Mask()));
    }
}
