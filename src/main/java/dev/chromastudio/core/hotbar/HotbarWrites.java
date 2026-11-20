package dev.chromastudio.core.hotbar;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Writes items into numbered hotbars all or nothing, as far as that can be promised, and puts back what was there. The
 * hotbars are another mod's, read and written through calls that may throw or quietly not take, so every slot is
 * tried before anything changes and read back after the writes, and on any failure what was written is put back
 * before the caller hears of it. Calls that stop working partway, and stay broken, can't be put right at once, so
 * the caller is told exactly which slots were left changed. It is generic over the item so it can be tested without
 * the game.
 */
public final class HotbarWrites {
    private HotbarWrites() {}

    /** Hotbars numbered from 0, with slots numbered from 0 in each. Any call may throw. */
    public interface Hotbars<T> {
        T get(int hotbar, int slot) throws Throwable;

        void set(int hotbar, int slot, T item) throws Throwable;

        /** Whether two items are the same, as a slot read back should be what was written. */
        boolean same(T a, T b);
    }

    /** One slot of one hotbar. */
    public record Slot(int hotbar, int slot) {}

    /** One slot and what it held before a write. */
    public record Was<T>(int hotbar, int slot, T item) {}

    /**
     * How a write went. {@code undo} is what an Undo should put back: everything the write replaced when it worked,
     * nothing when it failed and was put back, and the slots still changed, by reading them back, when putting back
     * failed too. {@code failure} is null when the write worked.
     */
    public record Result<T>(List<Was<T>> undo, Throwable failure) {
        public boolean worked() { return failure == null; }
    }

    /**
     * How putting back went: the slots that still don't hold what they held before, each once with what it held, and
     * what went wrong, null when nothing did. A slot that couldn't be read back counts as still changed.
     */
    public record Restored<T>(List<Was<T>> left, Throwable failure) {
        public Restored {
            left = List.copyOf(left);
        }

        public boolean done() { return left.isEmpty(); }
    }

    /**
     * Puts each item in its slot, the two lists in step. Every slot is read before anything is written, and then set
     * to what it already holds, so a failed read, or a slot that refuses to be set, changes nothing. A set that stops
     * working only after that can still leave slots changed, which the result names for Undo.
     */
    public static <T> Result<T> write(Hotbars<T> hotbars, List<Slot> slots, List<T> items) {
        if (slots.size() != items.size()) throw new IllegalArgumentException(slots.size() + " slots for " + items.size() + " items");
        List<Was<T>> before = new ArrayList<>();
        try {
            for (Slot s : slots) before.add(new Was<>(s.hotbar(), s.slot(), hotbars.get(s.hotbar(), s.slot())));
            Set<Slot> tried = new HashSet<>();
            for (Was<T> w : before) if (tried.add(new Slot(w.hotbar(), w.slot()))) hotbars.set(w.hotbar(), w.slot(), w.item());
        } catch (Throwable t) {
            return new Result<>(List.of(), t);
        }
        int written = 0;
        try {
            for (; written < slots.size(); written++) hotbars.set(slots.get(written).hotbar(), slots.get(written).slot(), items.get(written));
            for (int i = 0; i < slots.size(); i++) {
                Slot s = slots.get(i);
                if (!hotbars.same(hotbars.get(s.hotbar(), s.slot()), items.get(i)))
                    throw new IllegalStateException("hotbar " + s.hotbar() + " slot " + s.slot() + " didn't take its item");
            }
        } catch (Throwable t) {
            // A set that threw may still have taken, so its slot is put back too.
            Restored<T> back = restore(hotbars, before.subList(0, Math.min(written + 1, before.size())));
            return new Result<>(back.left(), together(t, back.failure()));
        }
        return new Result<>(List.copyOf(before), null);
    }

    /**
     * Puts back what a write replaced, last first, so a slot named twice ends as it began, and reads every slot back,
     * since a set can return without having done anything. Every slot is tried.
     */
    public static <T> Restored<T> restore(Hotbars<T> hotbars, List<Was<T>> was) {
        Throwable failed = null;
        for (int i = was.size() - 1; i >= 0; i--) {
            Was<T> w = was.get(i);
            try {
                hotbars.set(w.hotbar(), w.slot(), w.item());
            } catch (Throwable t) {
                failed = together(failed, t);
            }
        }
        // Last first means a slot named twice ends as its first entry has it.
        List<Was<T>> left = new ArrayList<>();
        Set<Slot> checked = new HashSet<>();
        for (Was<T> w : was) {
            if (!checked.add(new Slot(w.hotbar(), w.slot()))) continue;
            try {
                if (hotbars.same(hotbars.get(w.hotbar(), w.slot()), w.item())) continue;
                failed = together(failed, new IllegalStateException("hotbar " + w.hotbar() + " slot " + w.slot() + " wasn't put back"));
            } catch (Throwable t) {
                failed = together(failed, t);
            }
            left.add(w);
        }
        return new Restored<>(left, failed);
    }

    /** One failure carrying another, which may be null or, from a mod that reuses its exceptions, the same one. */
    private static Throwable together(Throwable first, Throwable then) {
        if (first == null) return then;
        if (then != null && then != first) first.addSuppressed(then);
        return first;
    }
}
