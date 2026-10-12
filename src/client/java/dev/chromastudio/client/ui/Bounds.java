package dev.chromastudio.client.ui;

/** A rectangle in GUI pixels. */
public record Bounds(int x, int y, int w, int h) {
    public int right() { return x + w; }
    public int bottom() { return y + h; }
    public boolean contains(double px, double py) { return px >= x && px < x + w && py >= y && py < y + h; }
}
