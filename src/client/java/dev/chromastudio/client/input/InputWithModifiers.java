package dev.chromastudio.client.input;

import org.lwjgl.glfw.GLFW;

public interface InputWithModifiers {
    int input();

    int modifiers();

    default boolean hasShiftDown() { return (modifiers() & GLFW.GLFW_MOD_SHIFT) != 0; }

    default boolean hasControlDown() { return (modifiers() & GLFW.GLFW_MOD_CONTROL) != 0; }

    default boolean hasAltDown() { return (modifiers() & GLFW.GLFW_MOD_ALT) != 0; }

    default boolean isEscape() { return input() == GLFW.GLFW_KEY_ESCAPE; }

    default boolean isConfirmation() { return input() == GLFW.GLFW_KEY_ENTER || input() == GLFW.GLFW_KEY_KP_ENTER; }

    default boolean isCycleFocus() { return input() == GLFW.GLFW_KEY_TAB; }

    default boolean isLeft() { return input() == GLFW.GLFW_KEY_LEFT; }

    default boolean isRight() { return input() == GLFW.GLFW_KEY_RIGHT; }

    default boolean isUp() { return input() == GLFW.GLFW_KEY_UP; }

    default boolean isDown() { return input() == GLFW.GLFW_KEY_DOWN; }
}
