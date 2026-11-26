package imgui.moulberry92;

import imgui.moulberry92.type.ImString;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public final class ImGui {
    private ImGui() {}

    public static final Set<String> press = new HashSet<>();
    public static final List<String> drawn = new ArrayList<>();
    public static final List<String> tips = new ArrayList<>();
    public static final List<String> disabledButtons = new ArrayList<>();
    private static final ImGuiStyle STYLE = new ImGuiStyle();
    private static int disabled;
    private static boolean inTooltip;
    private static float cursorY;

    public static void reset() {
        press.clear();
        drawn.clear();
        tips.clear();
        disabledButtons.clear();
        disabled = 0;
        inTooltip = false;
    }

    private static String label(String label) {
        int id = label.indexOf("##");
        return id < 0 ? label : label.substring(0, id);
    }

    private static boolean pressed(String label) {
        String shown = label(label);
        if (disabled > 0) {
            disabledButtons.add(shown);
            return false;
        }
        return press.remove(shown);
    }

    private static void line() { cursorY += 24; }

    public static ImGuiStyle getStyle() { return STYLE; }

    public static boolean beginChild(String id, float width, float height, boolean border) { return true; }

    public static void endChild() {}

    public static float getWindowPosX() { return 0; }

    public static void setWindowFocus(String name) {}

    public static float getContentRegionAvailX() { return 284; }

    public static float getContentRegionAvailY() { return 600; }

    public static float getContentRegionMaxX() { return 292; }

    public static float getWindowContentRegionMaxX() { return 292; }

    public static void pushStyleColor(int what, int colour) {}

    public static void popStyleColor() {}

    public static void popStyleColor(int count) {}

    public static void pushStyleVar(int what, float value) {}

    public static void pushStyleVar(int what, float x, float y) {}

    public static void popStyleVar() {}

    public static void popStyleVar(int count) {}

    public static void setNextItemWidth(float width) {}

    public static float calcItemWidth() { return 180; }

    public static void pushTextWrapPos(float at) {}

    public static void popTextWrapPos() {}

    public static int getFontSize() { return 16; }

    public static int getColorU32(int what) { return 0; }

    public static int getColorU32(int what, float alpha) { return 0; }

    public static float getCursorPosX() { return 8; }

    public static float getCursorPosY() { return cursorY; }

    public static void setCursorPosX(float x) {}

    public static void setCursorPosY(float y) { cursorY = y; }

    public static void pushStyleVar(int what, float x, float y) {}

    public static void pushStyleVar(int what, float value) {}

    public static void popStyleVar() {}

    public static void separator() {}

    public static void separatorText(String label) { drawn.add(label); }

    public static void sameLine() {}

    public static void sameLine(float offset) {}

    public static void sameLine(float offset, float spacing) {}

    public static void newLine() { line(); }

    public static void dummy(float width, float height) {}

    public static void beginGroup() {}

    public static void endGroup() {}

    public static void alignTextToFramePadding() {}

    public static float getTextLineHeight() { return 16; }

    public static float getTextLineHeightWithSpacing() { return 20; }

    public static float getFrameHeight() { return 22; }

    public static float getFrameHeightWithSpacing() { return 26; }

    public static void textUnformatted(String text) {
        (inTooltip ? tips : drawn).add(text);
        line();
    }

    public static boolean button(String label) {
        line();
        return pressed(label);
    }

    public static boolean button(String label, float width, float height) { return button(label); }

    public static boolean smallButton(String label) { return button(label); }

    public static boolean checkbox(String label, boolean on) { return button(label); }

    public static boolean radioButton(String label, boolean on) { return button(label); }

    public static boolean beginCombo(String label, String preview) {
        drawn.add(preview);
        return false;
    }

    public static void endCombo() {}

    public static boolean sliderInt(String label, int[] value, int min, int max, String format, int flags) {
        line();
        return false;
    }

    public static boolean inputText(String label, ImString text) {
        line();
        return false;
    }

    public static boolean colorButton(String id, float r, float g, float b, float a, int flags) { return pressed(id); }

    public static boolean colorButton(String id, float r, float g, float b, float a, int flags, float width, float height) { return pressed(id); }

    public static boolean collapsingHeader(String label, int flags) {
        line();
        return press.remove(label(label));
    }

    public static boolean selectable(String label, boolean selected) {
        line();
        return pressed(label);
    }

    public static boolean beginListBox(String id, float width, float height) { return true; }

    public static void endListBox() {}

    public static boolean beginItemTooltip() {
        inTooltip = true;
        return true;
    }

    public static void endTooltip() { inTooltip = false; }

    public static boolean beginTable(String id, int columns, int flags, float width, float height) { return true; }

    public static void endTable() {}

    public static void tableSetupColumn(String label, int flags, float width) {}

    public static void tableNextRow() { line(); }

    public static boolean tableNextColumn() { return true; }

    public static void beginDisabled() { disabled++; }

    public static void beginDisabled(boolean on) {
        if (on) disabled++;
    }

    public static void endDisabled() { if (disabled > 0) disabled--; }

    public static boolean isItemHovered() { return false; }

    public static boolean isWindowHovered(int flags) { return false; }

    public static boolean isItemDeactivatedAfterEdit() { return false; }

    public static float getItemRectMinX() { return 8; }

    public static float getItemRectMaxX() { return 100; }

    public static int getFrameCount() { return 0; }

    public static ImVec2 calcTextSize(String text) { return new ImVec2(calcTextSizeX(text), 16); }

    public static float calcTextSizeX(String text) { return text.length() * 7f; }

    public static float calcTextSizeX(String text, boolean hideAfterId) { return calcTextSizeX(hideAfterId ? label(text) : text); }
}
