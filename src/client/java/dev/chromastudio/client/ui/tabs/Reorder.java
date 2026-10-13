package dev.chromastudio.client.ui.tabs;

import java.util.List;

/** Dragging one of a row of equally wide items to another place in it. */
final class Reorder {
    private Reorder() {}

    /**
     * The gap a drag is over: 0 before the first item, {@code count} after the last. {@code x} is measured from the
     * row's left edge, and {@code pitch} is the distance from one item's left edge to the next one's.
     */
    static int gap(double x, int pitch, int count) { return Math.clamp(Math.round(x / pitch), 0, count); }

    /** Where the item at {@code from} would end up if dropped in a gap. The gaps either side of it leave it where it is. */
    static int target(int from, int gap) { return gap > from ? gap - 1 : gap; }

    /** Moves an item to a gap, and returns its new place. */
    static <T> int move(List<T> items, int from, int gap) {
        int to = target(from, gap);
        if (to != from) items.add(to, items.remove(from));
        return to;
    }
}
