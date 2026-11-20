package com.moulberry.axiom.mask;

/**
 * A stand-in for one of Axiom's own classes, written for Chroma Studio's tests, with only the members Chroma Studio
 * reaches by reflection. It holds none of Axiom's code.
 */
@FunctionalInterface
public interface MaskElement {
    boolean test(MaskContext context);
}
