package com.moulberry.axiom.mask;

import com.moulberry.axiom.editor.EditorWindowType;

/**
 * A stand-in for one of Axiom's own classes, written for Chroma Studio's tests, with only the members Chroma Studio
 * reaches by reflection. It holds none of Axiom's code.
 */
public final class MaskManager {
    private MaskManager() {}

    /** The mask set in the Tool Masks window. */
    public static MaskElement mask = _ -> true;
    public static int[] mode = {0};

    /** Whether the Scripting tab is in use, and the script it last compiled, or null when its last compile failed. */
    public static boolean useLuaScript;
    public static String luaScript;

    /**
     * As in Axiom, the mask set applies only while the Tool Masks window is open; otherwise every block passes. With
     * the Scripting tab in use and no script compiled, Axiom applies no mask either.
     */
    public static MaskElement getDestMask() {
        if (mode[0] == 1 || !EditorWindowType.TOOL_MASKS.isOpen() || useLuaScript && luaScript == null) return _ -> true;
        return mask;
    }
}
