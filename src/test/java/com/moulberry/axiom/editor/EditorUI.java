package com.moulberry.axiom.editor;

public final class EditorUI {
    private EditorUI() {}

    public static boolean enabled, active;
    public static RuntimeException throwOnDisable;

    public static boolean isEnabled() { return enabled; }

    public static boolean isActive() { return active; }

    public static void enable() { enabled = true; }

    public static long pushImGuiContext() { return 0; }

    public static void popImGuiContext(long previous) {}

    public static boolean isImGuiContextActive() { return false; }

    public static void disable() {
        if (throwOnDisable != null) throw throwOnDisable;
        enabled = false;
    }
}
