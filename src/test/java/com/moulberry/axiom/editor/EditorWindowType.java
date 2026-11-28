package com.moulberry.axiom.editor;

public enum EditorWindowType {
    TOOL_MASKS;

    @SuppressWarnings("ImmutableEnumChecker")
    public boolean open = true;

    public boolean isOpen() { return open; }
}
