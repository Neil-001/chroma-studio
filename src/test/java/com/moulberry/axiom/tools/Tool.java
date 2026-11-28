package com.moulberry.axiom.tools;

public interface Tool {
    char iconChar();

    String name();

    default void toolDeselected() {}
}
