package com.moulberry.axiom.editor.windows.global_mask;

/**
 * A stand-in for one of Axiom's own classes, written for Chroma Studio's tests, with only the members Chroma Studio
 * reaches by reflection. It holds none of Axiom's code.
 */
public final class ToolMaskWindow {
    private ToolMaskWindow() {}

    /** The last error a mask's Lua script raised, until a context compiles the script afresh or the player closes it. */
    public static String luaExecutionError;
}
