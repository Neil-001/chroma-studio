package dev.chromastudio.client.ui.tabs;

public final class Debounce {
    private boolean wanted;
    private int generation;

    public void request() { wanted = true; }

    public boolean pending() { return wanted; }

    public int poll() {
        if (!wanted) return -1;
        wanted = false;
        return ++generation;
    }

    public boolean current(int run) { return run == generation; }
}
