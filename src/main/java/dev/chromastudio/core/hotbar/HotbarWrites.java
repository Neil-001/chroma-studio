package dev.chromastudio.core.hotbar;

import java.util.ArrayList;
import java.util.List;

/**
 * Writes items into numbered hotbars all or nothing, and puts back what was there. The hotbars are another mod's, read
 * and written through calls that may throw or quietly not take, so every slot is read back after the writes, and on
 * any failure what was written is put back before the caller hears of it. It is generic over the item so it can be
 * tested without the game.
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
     * nothing when it failed and was put back, and what it touched when putting back failed too. {@code failure} is
     * null when the write worked.
     */
    public record Result<T>(List<Was<T>> undo, Throwable failure) {
        public boolean worked() { return failure == null; }
    }

    /**
     * Puts each item in its slot, the two lists in step. Every slot is read before anything is written, so a failed
     * read changes nothing.
     */
    public static <T> Result<T> write(Hotbars<T> hotbars, List<Slot> slots, List<T> items) {
        if (slots.size() != items.size()) throw new IllegalArgumentException(slots.size() + " slots for " + items.size() + " items");
        List<Was<T>> before = new ArrayList<>();
        try {
            for (Slot s : slots) before.add(new Was<>(s.hotbar(), s.slot(), hotbars.get(s.hotbar(), s.slot())));
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
            List<Was<T>> touched = List.copyOf(before.subList(0, Math.min(written + 1, before.size())));
            Throwable undo = restore(hotbars, touched);
            if (undo == null) return new Result<>(List.of(), t);
            t.addSuppressed(undo);
            return new Result<>(touched, t);
        }
        return new Result<>(List.copyOf(before), null);
    }

    /**
     * Puts back what a write replaced, last first, so a slot named twice ends as it began. Every slot is tried. Returns
     * what went wrong, or null when all were put back.
     */
    public static <T> Throwable restore(Hotbars<T> hotbars, List<Was<T>> was) {
        Throwable failed = null;
        for (int i = was.size() - 1; i >= 0; i--) {
            Was<T> w = was.get(i);
            try {
                hotbars.set(w.hotbar(), w.slot(), w.item());
            } catch (Throwable t) {
                if (failed == null) failed = t;
                else failed.addSuppressed(t);
            }
        }
        return failed;
    }
}
