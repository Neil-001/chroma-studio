package dev.chromastudio.client.input;

import net.minecraft.Util;
import net.minecraft.client.gui.screens.Screen;
import org.lwjgl.glfw.GLFW;

public record MouseButtonEvent(double x, double y, int button, int modifiers) implements InputWithModifiers {
    @Override public int input() { return button; }

    public static MouseButtonEvent of(double x, double y, int button) {
        int modifiers = (Screen.hasShiftDown() ? GLFW.GLFW_MOD_SHIFT : 0) | (Screen.hasControlDown() ? GLFW.GLFW_MOD_CONTROL : 0)
                | (Screen.hasAltDown() ? GLFW.GLFW_MOD_ALT : 0);
        return new MouseButtonEvent(x, y, button, modifiers);
    }

    private static final long DOUBLE_MS = 250;
    private static final double DOUBLE_DISTANCE = 4;
    private static long lastTime;
    private static double lastX, lastY;
    private static int lastButton = -1;

    public static boolean doubled(double x, double y, int button) {
        long now = Util.getMillis();
        boolean doubled = button == lastButton && now - lastTime <= DOUBLE_MS
                && Math.abs(x - lastX) <= DOUBLE_DISTANCE && Math.abs(y - lastY) <= DOUBLE_DISTANCE;
        lastTime = doubled ? 0 : now;
        lastX = x;
        lastY = y;
        lastButton = button;
        return doubled;
    }
}
