package dev.chromastudio.core.palette;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;

public final class QueuedChanges<S> {
    public enum Refusal {
        NONE,
        EMPTY,
        WORLD,
        INDEX,
        EDITOR,
        MASK_FAILED,
        CHANGED
    }

    public record Origin(Object world, int index, long visit) {}

    public interface Mask {
        boolean allows(int x, int y, int z);

        boolean failed();
    }

    @FunctionalInterface
    public interface Plan<S> {
        QueuedChanges<S> make(Mask mask);
    }

    public record Change<S>(int x, int y, int z, S was, S next) {}

    private final Origin origin;
    private final Map<Long, Change<S>> changes = new LinkedHashMap<>();
    private final Map<Long, S> left = new LinkedHashMap<>();

    public QueuedChanges(Origin origin) { this.origin = Objects.requireNonNull(origin); }

    public Origin origin() { return origin; }

    public void add(int x, int y, int z, S was, S next) {
        long p = BuildSearch.pack(x, y, z);
        left.remove(p);
        changes.merge(p, new Change<>(x, y, z, was, next), (old, now) -> new Change<>(x, y, z, old.was(), now.next()));
    }

    public void leave(int x, int y, int z, S was) {
        long p = BuildSearch.pack(x, y, z);
        if (!changes.containsKey(p)) left.putIfAbsent(p, was);
    }

    public boolean isEmpty() { return changes.isEmpty(); }

    public int size() { return changes.size(); }

    public Iterable<Change<S>> changes() { return Collections.unmodifiableCollection(changes.values()); }

    public boolean sameAs(QueuedChanges<S> other) { return changes.equals(other.changes) && left.equals(other.left); }

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
