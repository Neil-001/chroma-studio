package dev.chromastudio.client.axiom;

/**
 * Tells when Axiom has stopped drawing a tool, by the frames it drew without it. Axiom draws only the tool in use, and
 * doesn't always tell a tool it was put away: with "Clear tool when swapping" off, a player can pick another tool and
 * come back with nothing said. Frames are counted rather than time, so a game that stalls, on a slow machine or in a
 * lag spike, never counts as having put the tool away. A frame that can't be told, given as a negative number, counts
 * as a frame without the tool, as nothing can be vouched for then.
 */
final class Drawn {
    private int last;
    private boolean on;

    /** Starts counting from {@code frame}, as a stroke or preview begins. */
    void start(int frame) {
        last = frame;
        on = true;
    }

    void stop() { on = false; }

    /**
     * Called each time the tool is drawn, in frame {@code frame}. False when a whole frame went by without it since it
     * was last drawn, as when another tool was in use in between.
     */
    boolean draw(int frame) {
        boolean kept = frame >= 0 && (!on || last >= 0 && frame - last <= 1);
        last = frame;
        on = true;
        return kept;
    }

    /**
     * Whether a whole frame has gone by without the tool, asked between frames. A frame drawn since the last one with
     * the tool may still be the frame it's drawn in next, so it takes two.
     */
    boolean missed(int frame) { return on && (frame < 0 || last < 0 || frame - last >= 2); }
}
