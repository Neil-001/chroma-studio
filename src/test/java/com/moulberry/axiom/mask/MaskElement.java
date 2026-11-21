package com.moulberry.axiom.mask;

@FunctionalInterface
public interface MaskElement {
    boolean test(MaskContext context);
}
