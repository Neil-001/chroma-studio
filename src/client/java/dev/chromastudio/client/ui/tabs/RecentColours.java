package dev.chromastudio.client.ui.tabs;

import java.util.ArrayList;
import java.util.List;

/** The last few colours the player matched, newest first, each listed once. They last for the session. */
public final class RecentColours {
    public static final int KEPT = 12;

    private final List<Integer> colours = new ArrayList<>();

    /** Puts a colour, packed 0xRRGGBB, at the front. One already listed moves there. */
    public void push(int rgb) {
        colours.remove((Integer) rgb);
        colours.addFirst(rgb);
        while (colours.size() > KEPT) colours.removeLast();
    }

    public List<Integer> list() { return List.copyOf(colours); }
}
