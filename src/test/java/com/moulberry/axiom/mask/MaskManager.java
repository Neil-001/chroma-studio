package com.moulberry.axiom.mask;

/**
 * A stand-in for one of Axiom's own classes, written for Chroma Studio's tests, with only the members Chroma Studio
 * reaches by reflection. It holds none of Axiom's code.
 */
public final class MaskManager {
    private MaskManager() {}

    public static MaskElement mask = _ -> true;

    public static MaskElement getDestMask() { return mask; }
}
