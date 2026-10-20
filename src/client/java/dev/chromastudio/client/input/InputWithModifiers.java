package dev.chromastudio.client.input;

import org.lwjgl.glfw.GLFW;

/**
 * A key or mouse button with the modifier keys held with it. 1.21.9 gave the game events like these; 1.21.1 hands
 * input over as plain numbers, so the window makes its own and the tabs read them as on newer versions.
 */
public interface InputWithModifiers {
    /** The key code or mouse button. */
    int input();

    int modifiers();

    default boolean hasShiftDown() { return (modifiers() & GLFW.GLFW_MOD_SHIFT) != 0; }

    default boolean hasControlDown() { return (modifiers() & GLFW.GLFW_MOD_CONTROL) != 0; }

    default boolean hasAltDown() { return (modifiers() & GLFW.GLFW_MOD_ALT) != 0; }

    default boolean isEscape() { return input() == GLFW.GLFW_KEY_ESCAPE; }

    default boolean isConfirmation() { return input() == GLFW.GLFW_KEY_ENTER || input() == GLFW.GLFW_KEY_KP_ENTER; }
}
