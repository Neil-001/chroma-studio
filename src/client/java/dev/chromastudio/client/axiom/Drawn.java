package dev.chromastudio.client.axiom;

final class Drawn {
    private int last;
    private boolean on;

    void start(int frame) {
        last = frame;
        on = true;
    }

    void stop() { on = false; }

    boolean draw(int frame) {
        boolean kept = frame >= 0 && (!on || last >= 0 && frame - last <= 1);
        last = frame;
        on = true;
        return kept;
    }

    boolean missed(int frame) { return on && (frame < 0 || last < 0 || frame - last >= 2); }
}
