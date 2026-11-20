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

    /** As in Axiom, the mask set applies only while the Tool Masks window is open; otherwise every block passes. */
    public static MaskElement getDestMask() { return mode[0] != 1 && EditorWindowType.TOOL_MASKS.isOpen() ? mask : _ -> true; }
}
