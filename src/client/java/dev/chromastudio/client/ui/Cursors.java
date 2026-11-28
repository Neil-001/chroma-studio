package dev.chromastudio.client.ui;

import java.util.EnumMap;
import java.util.Map;
import net.minecraft.client.Minecraft;
import org.lwjgl.glfw.GLFW;

public final class Cursors {
    private Cursors() {}

    public enum Shape {
        CROSSHAIR(GLFW.GLFW_CROSSHAIR_CURSOR), RESIZE_NS(GLFW.GLFW_RESIZE_NS_CURSOR), RESIZE_EW(GLFW.GLFW_RESIZE_EW_CURSOR),
        RESIZE_NWSE(GLFW.GLFW_RESIZE_NWSE_CURSOR), RESIZE_NESW(GLFW.GLFW_RESIZE_NESW_CURSOR), RESIZE_ALL(GLFW.GLFW_RESIZE_ALL_CURSOR), POINTING_HAND(GLFW.GLFW_POINTING_HAND_CURSOR);

        final int glfw;

        Shape(int glfw) { this.glfw = glfw; }
    }

    private static final Map<Shape, Long> MADE = new EnumMap<>(Shape.class);
    private static Shape wanted, shown;

    public static void request(Shape shape) { wanted = shape; }

    static void apply() {
        if (wanted != shown) {
            GLFW.glfwSetCursor(Minecraft.getInstance().getWindow().getWindow(), wanted == null ? 0 : MADE.computeIfAbsent(wanted, s -> GLFW.glfwCreateStandardCursor(s.glfw)));
            shown = wanted;
        }
        wanted = null;
    }

    static void reset() {
        wanted = null;
        apply();
    }
}
