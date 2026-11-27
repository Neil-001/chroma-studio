package dev.chromastudio.client.ui.tabs;

import java.util.ArrayList;
import java.util.List;

public final class RecentColours {
    public static final int KEPT = 12;

    private final List<Integer> colours = new ArrayList<>();

    public void push(int rgb) {
        colours.remove((Integer) rgb);
        colours.addFirst(rgb);
        while (colours.size() > KEPT) colours.removeLast();
    }

    public List<Integer> list() { return List.copyOf(colours); }
}
