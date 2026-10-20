package dev.chromastudio.client.input;

/** A key press or release. */
public record KeyEvent(int key, int scancode, int modifiers) implements InputWithModifiers {
    @Override public int input() { return key; }
}
