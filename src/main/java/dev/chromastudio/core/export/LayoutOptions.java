package dev.chromastudio.core.export;

/**
 * A layout and its sizes. {@code height} is the wall's and the three diorama layouts' height, {@code patch} the side
 * of each square in the patch grid, {@code width} the diorama layouts' width, and {@code gap} leaves a block of air
 * between entries in the strip, wall and patch grid. {@code seed} makes the diorama layouts repeatable. A layout
 * ignores the fields that aren't its own, and {@link Arrangement} brings sizes into range.
 */
public record LayoutOptions(Layout layout, int height, int patch, int width, boolean gap, long seed) {
    public static final int WALL_HEIGHT = 3, PATCH = 3, DIORAMA_WIDTH = 16, DIORAMA_HEIGHT = 9;

    /** The layout at its usual sizes: a wall three high, patches three across, a diorama 16 by 9. */
    public static LayoutOptions of(Layout layout, int patch, boolean gap, long seed) {
        return new LayoutOptions(layout, layout == Layout.WALL ? WALL_HEIGHT : DIORAMA_HEIGHT, patch, DIORAMA_WIDTH, gap, seed);
    }

    public static LayoutOptions of(Layout layout) {
        return of(layout, PATCH, false, 0);
    }
}
