package com.moulberry.axiom.tools;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class ToolManager {
    private ToolManager() {}

    public static List<Tool> tools = new ArrayList<>();
    public static Map<Tool, Object> keybindMap = new HashMap<>();

    public static List<Tool> getTools() { return tools; }
}
