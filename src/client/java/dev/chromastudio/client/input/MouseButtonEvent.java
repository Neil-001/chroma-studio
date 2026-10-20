package dev.chromastudio.client.input;

import net.minecraft.Util;
import net.minecraft.client.gui.screens.Screen;
import org.lwjgl.glfw.GLFW;

/** A mouse button pressed, dragged or released at a point, in GUI pixels. */
public record MouseButtonEvent(double x, double y, int button, int modifiers) implements InputWithModifiers {
    @Override public int input() { return button; }

    /** The event for what the game reports, with the modifier keys as they are now. */
    public static MouseButtonEvent of(double x, double y, int button) {
        int modifiers = (Screen.hasShiftDown() ? GLFW.GLFW_MOD_SHIFT : 0) | (Screen.hasControlDown() ? GLFW.GLFW_MOD_CONTROL : 0)
                | (Screen.hasAltDown() ? GLFW.GLFW_MOD_ALT : 0);
        return new MouseButtonEvent(x, y, button, modifiers);
    }

    // A second press of the same button this soon and this close is a double click. The game doesn't count them on 1.21.1.
    private static final long DOUBLE_MS = 250;
    private static final double DOUBLE_DISTANCE = 4;
    private static long lastTime;
    private static double lastX, lastY;
    private static int lastButton = -1;

    /** Whether a press is the second of a double click. Call it once for each press. */
    public static boolean doubled(double x, double y, int button) {
        long now = Util.getMillis();
        boolean doubled = button == lastButton && now - lastTime <= DOUBLE_MS
                && Math.abs(x - lastX) <= DOUBLE_DISTANCE && Math.abs(y - lastY) <= DOUBLE_DISTANCE;
        lastTime = doubled ? 0 : now;   // a third press starts over
        lastX = x;
        lastY = y;
        lastButton = button;
        return doubled;
    }
}
