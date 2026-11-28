package dev.chromastudio.client.input;

import net.minecraft.client.gui.screens.Screen;

public record KeyEvent(int key, int scancode, int modifiers) implements InputWithModifiers {
    @Override public int input() { return key; }

    public boolean isCopy() { return Screen.isCopy(key); }

    public boolean isCut() { return Screen.isCut(key); }
}
