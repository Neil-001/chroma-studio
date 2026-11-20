package dev.chromastudio.core.palette;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * The blocks a tool means to change, each with what it saw there and what goes there, and the one rule for whether
 * they may still be sent. A stroke or a preview queues its changes as it goes; when the player lets go or presses
 * Apply, {@link #check} says whether everything that allowed them still holds. If anything doesn't, nothing is sent:
 * a tool never sends part of what the player saw, nor anything that might have outlived what allowed it.
 *
 * @param <S> what a block is, compared with {@code equals}: the game's block states, which are each one object
 */
public final class QueuedChanges<S> {
    /** Why queued changes can't be sent, or {@link #NONE} when they can. */
    public enum Refusal {
        NONE,
        /** Nothing is queued. */
        EMPTY,
        /** The player is in another world or dimension than the one the changes were queued in. */
        WORLD,
        /** The colours of the blocks were read again since, which can change what the tool would pick. */
        INDEX,
        /** The player left the editor or the tool since, so the changes may be stale. */
        EDITOR,
        /** The tool masks couldn't be read, or failed partway. */
        MASK_FAILED,
        /** The tool masks now keep out a block that's queued. */
        MASK_DENIES,
        /** A queued block isn't what it was when it was queued. */
        BLOCK_CHANGED
    }

    /** Where the changes are to be made: the world, the index's version and the visit to the editor they were made in. */
    public record Origin(Object world, int index, long visit) {}

    /** A block in the world as it is now. */
    @FunctionalInterface
    public interface World<S> {
        S at(int x, int y, int z);
    }

    /** The player's tool masks as they are now. */
    public interface Mask {
        boolean allows(int x, int y, int z);

        /** Whether a call into the masks failed, in which case no answer they gave can be trusted. */
        boolean failed();
    }

    /** One queued change: what was there when it was queued, and what goes there. */
    public record Change<S>(int x, int y, int z, S was, S next) {}

    private final Origin origin;
    private final Map<Long, Change<S>> changes = new LinkedHashMap<>();

    public QueuedChanges(Origin origin) { this.origin = Objects.requireNonNull(origin); }

    /**
     * Queues a change. A position already queued keeps what was first seen there, so a stroke that comes back over
     * a block it has queued is still checked against the world as it was, and takes the newer block to put there.
     */
    public void add(int x, int y, int z, S was, S next) {
        changes.merge(BuildSearch.pack(x, y, z), new Change<>(x, y, z, was, next), (old, now) -> new Change<>(x, y, z, old.was(), now.next()));
    }

    public boolean isEmpty() { return changes.isEmpty(); }

    public int size() { return changes.size(); }

    /** The changes in the order they were queued. */
    public Iterable<Change<S>> changes() { return Collections.unmodifiableCollection(changes.values()); }

    /**
     * Whether the changes may be sent now, from {@code now}. Every queued block is tested against the masks as they
     * are now and compared with the world, so a mask that was changed, or a block that another player broke or filled
     * with a chest, refuses the lot. The masks are asked about every block before their failure is looked at, as
     * asking is what makes a failure show.
     */
    public Refusal check(Origin now, World<S> world, Mask mask) {
        if (changes.isEmpty()) return Refusal.EMPTY;
        if (now.world() != origin.world()) return Refusal.WORLD;
        if (now.index() != origin.index()) return Refusal.INDEX;
        if (now.visit() != origin.visit()) return Refusal.EDITOR;
        boolean denied = false, changed = false;
        for (Change<S> c : changes.values()) {
            denied |= !mask.allows(c.x(), c.y(), c.z());
            changed |= !Objects.equals(world.at(c.x(), c.y(), c.z()), c.was());
        }
        if (mask.failed()) return Refusal.MASK_FAILED;
        if (denied) return Refusal.MASK_DENIES;
        return changed ? Refusal.BLOCK_CHANGED : Refusal.NONE;
    }
}
