package dev.chromastudio.core.export;

public record LayoutOptions(Layout layout, int height, int patch, int width, boolean gap, long seed) {
    public static final int WALL_HEIGHT = 3, PATCH = 3, DIORAMA_WIDTH = 16, DIORAMA_HEIGHT = 9;

    public static LayoutOptions of(Layout layout, int patch, boolean gap, long seed) {
        return new LayoutOptions(layout, layout == Layout.WALL ? WALL_HEIGHT : DIORAMA_HEIGHT, patch, DIORAMA_WIDTH, gap, seed);
    }

    public static LayoutOptions of(Layout layout) {
        return of(layout, PATCH, false, 0);
    }
}
