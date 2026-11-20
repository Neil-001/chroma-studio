package com.moulberry.axiom.editor;

/**
 * A stand-in for one of Axiom's own classes, written for Chroma Studio's tests, with only the members Chroma Studio
 * reaches by reflection. It holds none of Axiom's code.
 */
public final class EditorUI {
    private EditorUI() {}

    /** Whether the editor is switched on, and whether it was active last frame, which Axiom reports a frame late. */
    public static boolean enabled, active;
    public static RuntimeException throwOnDisable;

    public static boolean isEnabled() { return enabled; }

    public static boolean isActive() { return active; }

    public static void enable() { enabled = true; }

    public static void disable() {
        if (throwOnDisable != null) throw throwOnDisable;
        enabled = false;
    }
}
