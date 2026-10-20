package dev.chromastudio.client.ui;

import java.util.EnumMap;
import java.util.Map;
import net.minecraft.client.Minecraft;
import org.lwjgl.glfw.GLFW;

/**
 * The mouse cursor, through GLFW: 1.21.1's GUI has no way to ask for one. A frame asks for a shape with
 * {@link #request} and the screen {@link #apply applies} it when it has drawn; a frame that asks for none gets the
 * arrow back. Where the system has no such shape, GLFW makes none and the arrow stays.
 */
final class Cursors {
    private Cursors() {}

    enum Shape {
        CROSSHAIR(GLFW.GLFW_CROSSHAIR_CURSOR), RESIZE_NS(GLFW.GLFW_RESIZE_NS_CURSOR), RESIZE_EW(GLFW.GLFW_RESIZE_EW_CURSOR),
        RESIZE_NWSE(GLFW.GLFW_RESIZE_NWSE_CURSOR), RESIZE_NESW(GLFW.GLFW_RESIZE_NESW_CURSOR);

        final int glfw;

        Shape(int glfw) { this.glfw = glfw; }
    }

    // Made on first use and kept, as the game keeps its own.
    private static final Map<Shape, Long> MADE = new EnumMap<>(Shape.class);
    private static Shape wanted, shown;

    static void request(Shape shape) { wanted = shape; }

    /** Shows the shape asked for this frame, or the arrow. */
    static void apply() {
        if (wanted != shown) {
            GLFW.glfwSetCursor(Minecraft.getInstance().getWindow().getWindow(), wanted == null ? 0 : MADE.computeIfAbsent(wanted, s -> GLFW.glfwCreateStandardCursor(s.glfw)));
            shown = wanted;
        }
        wanted = null;
    }

    /** The arrow again, for when the screen closes. */
    static void reset() {
        wanted = null;
        apply();
    }
}
