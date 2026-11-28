package com.moulberry.axiom.mask;

import com.moulberry.axiom.editor.EditorWindowType;

public final class MaskManager {
    private MaskManager() {}

    public static MaskElement mask = ignored -> true;
    public static int[] mode = {0};

    public static boolean useLuaScript;
    public static String luaScript;

    public static MaskElement getDestMask() {
        if (mode[0] == 1 || !EditorWindowType.TOOL_MASKS.isOpen() || useLuaScript && luaScript == null) return ignored -> true;
        return mask;
    }
}
