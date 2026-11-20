package dev.chromastudio.client.axiom;

import static dev.chromastudio.client.axiom.AxiomTools.tr;

import dev.chromastudio.client.ModSettings;
import dev.chromastudio.core.color.Color;
import imgui.moulberry92.ImGui;
import imgui.moulberry92.flag.ImGuiCol;
import imgui.moulberry92.flag.ImGuiColorEditFlags;
import imgui.moulberry92.flag.ImGuiSliderFlags;
import java.util.List;
import net.minecraft.network.chat.Component;
import org.jspecify.annotations.Nullable;

/**
 * The controls Chroma Studio's tools draw in Axiom's Tool Options panel, so that each looks, reads and sits the same in
 * every tool, and the one way their text reaches ImGui. ImGui reads the text of its tooltips and of {@code text} and
 * {@code textWrapped} as a printf format, so a % in a palette's or block's name would show as garbage; everything here
 * goes through {@code textUnformatted}. A control's own label, as on a button or checkbox, is drawn as it is.
 *
 * <p>Each tool's panel runs in {@link #draw}, which gives it its own scrolling area: Axiom's panel shows no scrollbar,
 * so a panel taller than the space left would hide its last lines without a sign. The order is the same in every tool:
 * what it paints with, how, its options, the masks line, then a separator, its buttons and what it has to say.
 */
final class AxiomPanel {
    private AxiomPanel() {}

    /** A swatch: a block's colour, and what hovering it says. */
    record Swatch(Color colour, String tip) {}

    /** The most rows a list shows before it scrolls, so it leaves room for the controls below it. */
    private static final int LIST_ROWS = 5;
    /** How wide a tooltip runs before it wraps, in the font's size. */
    private static final float TIP_WIDTH = 30f;

    /** Draws a tool's panel in a scrolling area the size of the space Axiom gives it. */
    static void draw(Runnable panel) {
        if (ImGui.beginChild("##chromastudio", 0, 0, false)) panel.run();
        ImGui.endChild();
    }

    /** Text as it is, on one line. */
    static void text(String text) { ImGui.textUnformatted(text); }

    /** Text as it is, wrapped at the panel's edge. */
    static void wrapped(String text) {
        ImGui.pushTextWrapPos(0f);
        ImGui.textUnformatted(text);
        ImGui.popTextWrapPos();
    }

    static void wrapped(Component text) { wrapped(text.getString()); }

    /** Text that says something about the panel rather than what to do, wrapped and greyed. */
    static void note(String text) {
        ImGui.pushStyleColor(ImGuiCol.Text, ImGui.getColorU32(ImGuiCol.TextDisabled));
        wrapped(text);
        ImGui.popStyleColor();
    }

    /** A tooltip for the last item, shown as it is and wrapped. It shows on a greyed-out item too, to say why. */
    static void tip(String text) {
        if (!ImGui.beginItemTooltip()) return;
        ImGui.pushTextWrapPos(ImGui.getFontSize() * TIP_WIDTH);
        ImGui.textUnformatted(text);
        ImGui.popTextWrapPos();
        ImGui.endTooltip();
    }

    /** A button, greyed out when it can't be used, and its tooltip, which says why when it's greyed out. */
    static boolean button(String label, boolean enabled, String tip) {
        ImGui.beginDisabled(!enabled);
        boolean pressed = ImGui.button(label);
        ImGui.endDisabled();
        tip(tip);
        return pressed && enabled;
    }

    /** A radio button and its tooltip. */
    static boolean radio(String label, boolean on, boolean enabled, String tip) {
        ImGui.beginDisabled(!enabled);
        boolean pressed = ImGui.radioButton(label, on);
        ImGui.endDisabled();
        tip(tip);
        return pressed && enabled;
    }

    /** A checkbox and its tooltip. Returns whether it was clicked. */
    static boolean checkbox(String label, boolean on, boolean enabled, String tip) {
        ImGui.beginDisabled(!enabled);
        boolean pressed = ImGui.checkbox(label, on);
        ImGui.endDisabled();
        tip(tip);
        return pressed && enabled;
    }

    /**
     * A slider for a whole number from {@code min} to {@code max}. A number typed in with Ctrl-click is held to those
     * too, as is the value it starts from, which a remembered setting could have put anywhere. Returns whether the
     * player has just finished changing it.
     */
    static boolean slider(String label, int[] value, int min, int max, String format, boolean enabled, String tip) {
        value[0] = Math.clamp(value[0], min, max);
        ImGui.beginDisabled(!enabled);
        ImGui.sliderInt(label, value, min, max, format, ImGuiSliderFlags.AlwaysClamp);
        boolean done = ImGui.isItemDeactivatedAfterEdit();
        ImGui.endDisabled();
        value[0] = Math.clamp(value[0], min, max);
        tip(tip);
        return done;
    }

    /** The brush's radius, as every brush has it. */
    static boolean radius(int[] radius, boolean enabled, String tip) {
        return slider(tr("axiom.chromastudio.radius"), radius, 0, ModSettings.BRUSH_MAX_RADIUS, "%d", enabled, tip);
    }

    /** A brush's radius held to what the slider offers, for one that's read without the panel being drawn. */
    static int radius(int[] radius) { return radius[0] = Math.clamp(radius[0], 0, ModSettings.BRUSH_MAX_RADIUS); }

    /**
     * A list to choose one from, under its heading, up to {@link #LIST_ROWS} rows tall. Returns the row clicked, or -1.
     * Labels may repeat, as a player's palette may share a preset's name.
     */
    static int list(String heading, String id, List<String> labels, int selected) {
        text(heading);
        int clicked = -1;
        float rows = Math.min(Math.max(labels.size(), 1), LIST_ROWS) * ImGui.getTextLineHeightWithSpacing() + ImGui.getStyle().getFramePaddingY() * 2;
        if (ImGui.beginListBox(id, -1, rows)) {
            // A label is also ImGui's id for its row, so each gets its place in the list as well.
            for (int i = 0; i < labels.size(); i++) if (ImGui.selectable(labels.get(i) + "##" + i, i == selected)) clicked = i;
            ImGui.endListBox();
        }
        return clicked;
    }

    /** Swatches in rows, as many to a row as the panel is wide, each named when hovered. */
    static void swatches(String id, List<Swatch> shown) {
        float size = ImGui.getFrameHeight(), gap = ImGui.getStyle().getItemSpacingX();
        int perRow = Math.max(1, (int) ((ImGui.getContentRegionAvailX() + gap) / (size + gap)));
        for (int i = 0; i < shown.size(); i++) {
            if (i % perRow != 0) ImGui.sameLine();
            swatch(id + i, shown.get(i));
        }
    }

    /** One swatch, named when hovered. */
    static void swatch(String id, Swatch s) {
        Color c = s.colour();
        ImGui.colorButton("##" + id, c.red() / 255f, c.green() / 255f, c.blue() / 255f, 1f, ImGuiColorEditFlags.NoTooltip);
        tip(s.tip());
    }

    /**
     * The line that says the tool keeps to Axiom's tool masks, or why it doesn't. Its tooltip says how to set one,
     * and that Axiom applies them only while its Tool Masks window is open, which would surprise a player otherwise.
     */
    static void masks() {
        AxiomMask.unavailable().ifPresentOrElse(why -> wrapped(why), () -> {
            note(tr("axiom.chromastudio.masks"));
            tip(tr("axiom.chromastudio.masks.tip"));
        });
    }

    /** Apply and Cancel, as a whole-build tool has them, with {@code between} drawn between the two. */
    static void applyCancel(boolean canApply, String applyWhyNot, boolean canCancel, Runnable apply, Runnable cancel, @Nullable Runnable between) {
        if (button(tr("axiom.chromastudio.apply"), canApply, canApply ? tr("axiom.chromastudio.apply.tip") : applyWhyNot)) apply.run();
        if (between != null) {
            ImGui.sameLine();
            between.run();
        }
        ImGui.sameLine();
        if (button(tr("axiom.chromastudio.cancel"), canCancel, tr(canCancel ? "axiom.chromastudio.cancel.tip" : "axiom.chromastudio.cancel.none"))) cancel.run();
    }

    /** A separator between a panel's choices and its buttons. */
    static void separator() { ImGui.separator(); }

    static void sameLine() { ImGui.sameLine(); }
}
