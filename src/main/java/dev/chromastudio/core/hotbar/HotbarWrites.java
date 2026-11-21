package dev.chromastudio.core.hotbar;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public final class HotbarWrites {
    private HotbarWrites() {}

    public interface Hotbars<T> {
        T get(int hotbar, int slot) throws Throwable;

        void set(int hotbar, int slot, T item) throws Throwable;

        boolean same(T a, T b);
    }

    public record Slot(int hotbar, int slot) {}

    public record Was<T>(int hotbar, int slot, T item) {}

    public record Result<T>(List<Was<T>> undo, Throwable failure) {
        public boolean worked() { return failure == null; }
    }

    public record Restored<T>(List<Was<T>> left, Throwable failure) {
        public Restored {
            left = List.copyOf(left);
        }

        public boolean done() { return left.isEmpty(); }
    }

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
            Restored<T> back = restore(hotbars, before.subList(0, Math.min(written + 1, before.size())));
            return new Result<>(back.left(), together(t, back.failure()));
        }
        return new Result<>(List.copyOf(before), null);
    }

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

    private static Throwable together(Throwable first, Throwable then) {
        if (first == null) return then;
        if (then != null && then != first) first.addSuppressed(then);
        return first;
    }
}
