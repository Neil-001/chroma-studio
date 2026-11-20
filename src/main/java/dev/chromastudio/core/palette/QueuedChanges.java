package dev.chromastudio.core.palette;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;

/**
 * What a tool worked out to do, and the one rule for whether it may still be sent. A stroke or a preview is a plan:
 * the positions it decided about, the brush's positions or the build's blocks, the ones the masks kept out included,
 * each with the block it saw there, and for those it changes, what goes there instead. When the player lets go or
 * presses Apply, {@link #check} works the plan out again, by the same steps, from the world, the tool masks and the
 * tool's own choices as they are at that moment, and the plan may be sent only if that gives exactly the same. So
 * whatever could change a decision, a block built or broken, a mask set otherwise, an option changed, a build grown
 * or a door completed, refuses the lot without having to be named here. Nothing is ever sent in part.
 *
 * <p>Positions a tool only looked at on the way, such as the edge of a build's search or a door's other half that it
 * didn't change, aren't in the plan. One that would change the result, a new member of the build or a door's half
 * that now goes too, changes the plan, and so is caught that way.
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
        /** The editor is closed, or was left since the changes were queued, so they may be stale. */
        EDITOR,
        /** The tool masks couldn't be read, or failed partway, as a mask's script with an error does. */
        MASK_FAILED,
        /** Worked out again now, with the world, masks and the tool's choices as they are, the plan isn't the same. */
        CHANGED
    }

    /** Where the changes are to be made: the world, the index's version and the visit to the editor they were made in. */
    public record Origin(Object world, int index, long visit) {}

    /** The player's tool masks as they are now. */
    public interface Mask {
        boolean allows(int x, int y, int z);

        /** Whether the masks failed while they were asked, in which case no answer they gave can be trusted. */
        boolean failed();
    }

    /** Works out a tool's plan again, by the steps that made it, from the world as it is now and these masks. */
    @FunctionalInterface
    public interface Plan<S> {
        QueuedChanges<S> make(Mask mask);
    }

    /** One queued change: what was there when it was queued, and what goes there. */
    public record Change<S>(int x, int y, int z, S was, S next) {}

    private final Origin origin;
    private final Map<Long, Change<S>> changes = new LinkedHashMap<>();
    /** The positions the plan decided to leave as they are, each with what it saw there. */
    private final Map<Long, S> left = new LinkedHashMap<>();

    public QueuedChanges(Origin origin) { this.origin = Objects.requireNonNull(origin); }

    public Origin origin() { return origin; }

    /**
     * Queues a change. A position already queued keeps what was first seen there, so a stroke that comes back over
     * a block it has queued still records the world as it was, and takes the newer block to put there.
     */
    public void add(int x, int y, int z, S was, S next) {
        long p = BuildSearch.pack(x, y, z);
        left.remove(p);
        changes.merge(p, new Change<>(x, y, z, was, next), (old, now) -> new Change<>(x, y, z, old.was(), now.next()));
    }

    /** Records a position the plan decided to leave as it is. A queued change there wins. */
    public void leave(int x, int y, int z, S was) {
        long p = BuildSearch.pack(x, y, z);
        if (!changes.containsKey(p)) left.putIfAbsent(p, was);
    }

    public boolean isEmpty() { return changes.isEmpty(); }

    public int size() { return changes.size(); }

    /** The changes in the order they were queued. */
    public Iterable<Change<S>> changes() { return Collections.unmodifiableCollection(changes.values()); }

    /**
     * Whether two plans are the same: the same positions changed, each from the same block to the same block, and the
     * same positions left alone, each seen holding the same block. Where they were made doesn't count.
     */
    public boolean sameAs(QueuedChanges<S> other) { return changes.equals(other.changes) && left.equals(other.left); }

    /**
     * Whether the changes may be sent now. Refuses when nothing is queued, when the world, the index or the visit to
     * the editor isn't the one the plan was made in, or when the editor is closed; then works the plan out again with
     * {@code again} and fresh masks, and refuses when the masks failed while they were asked or the plan isn't the same.
     * Where the player is and whether the editor is on are read again once the plan is made, as making it can take a
     * while on a big build.
     *
     * @param now      where the changes would be made now, read at the moment it's asked
     * @param editorOn whether the editor is switched on now
     * @param masks    the tool masks as they're set now, asked afresh, so no earlier answer stands in for them
     */
    public Refusal check(Supplier<Origin> now, BooleanSupplier editorOn, Supplier<? extends Mask> masks, Plan<S> again) {
        if (changes.isEmpty()) return Refusal.EMPTY;
        Refusal where = where(now.get(), editorOn.getAsBoolean());
        if (where != Refusal.NONE) return where;
        Mask mask = masks.get();
        QueuedChanges<S> redone = again.make(mask);
        if (mask.failed()) return Refusal.MASK_FAILED;
        where = where(now.get(), editorOn.getAsBoolean());
        if (where != Refusal.NONE) return where;
        return sameAs(redone) ? Refusal.NONE : Refusal.CHANGED;
    }

    private Refusal where(Origin now, boolean editorOn) {
        if (now.world() != origin.world()) return Refusal.WORLD;
        if (now.index() != origin.index()) return Refusal.INDEX;
        if (now.visit() != origin.visit() || !editorOn) return Refusal.EDITOR;
        return Refusal.NONE;
    }
}
