package dev.chromastudio.client.ui.tabs;

/**
 * Lets many requests in one frame become one piece of work, and tells a finished piece of work whether a newer one
 * has been started since. Dragging a slider asks for a search with every mouse event; only one starts a frame, and
 * an answer that comes back after a later search began is dropped.
 */
public final class Debounce {
    private boolean wanted;
    private int generation;

    /** Asks for the work to run at the next {@link #poll}. */
    public void request() { wanted = true; }

    public boolean pending() { return wanted; }

    /** Call once a frame. Returns the number of the run to start now, or -1 when nothing was asked for since the last one. */
    public int poll() {
        if (!wanted) return -1;
        wanted = false;
        return ++generation;
    }

    /** Whether a run is still the latest started, so its answer is the one wanted. */
    public boolean current(int run) { return run == generation; }
}
