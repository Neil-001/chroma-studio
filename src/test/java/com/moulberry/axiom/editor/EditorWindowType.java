package com.moulberry.axiom.editor;

/**
 * A stand-in for one of Axiom's own classes, written for Chroma Studio's tests, with only the members Chroma Studio
 * reaches by reflection. It holds none of Axiom's code.
 */
public enum EditorWindowType {
    TOOL_MASKS;

    /** Whether the window is open, docked or minimised. */
    public boolean open = true;

    public boolean isOpen() { return open; }
}
