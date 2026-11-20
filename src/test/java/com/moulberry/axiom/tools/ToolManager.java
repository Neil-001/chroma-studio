package com.moulberry.axiom.tools;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * A stand-in for one of Axiom's own classes, written for Chroma Studio's tests, with only the members Chroma Studio
 * reaches by reflection. It holds none of Axiom's code.
 */
public final class ToolManager {
    private ToolManager() {}

    public static List<Tool> tools = new ArrayList<>();
    /** Axiom keys its tools' key bindings by the tool's entry. It holds none of the tools added through its API. */
    public static Map<Tool, Object> keybindMap = new HashMap<>();

    public static List<Tool> getTools() { return tools; }
}
