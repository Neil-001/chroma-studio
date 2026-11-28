package dev.chromastudio.client.ui.tabs;

import java.util.List;

final class Reorder {
    private Reorder() {}

    static int gap(double x, int pitch, int count) { return Math.clamp(Math.round(x / pitch), 0, count); }

    static int target(int from, int gap) { return gap > from ? gap - 1 : gap; }

    static <T> int move(List<T> items, int from, int gap) {
        int to = target(from, gap);
        if (to != from) items.add(to, items.remove(from));
        return to;
    }
}
