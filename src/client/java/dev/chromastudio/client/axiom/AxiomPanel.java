package dev.chromastudio.client.axiom;

import static dev.chromastudio.client.axiom.AxiomTools.tr;

import dev.chromastudio.client.ModSettings;
import dev.chromastudio.client.action.PaletteBlocks;
import dev.chromastudio.core.color.Color;
import dev.chromastudio.core.palette.PaletteEntry;
import dev.chromastudio.core.text.LineBreaks;
import imgui.moulberry92.ImGui;
import imgui.moulberry92.flag.ImGuiCol;
import imgui.moulberry92.flag.ImGuiColorEditFlags;
import imgui.moulberry92.flag.ImGuiSliderFlags;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.minecraft.network.chat.Component;
import org.jspecify.annotations.Nullable;

/**
 * The controls Chroma Studio's tools draw in Axiom's Tool Options panel, so that each looks, reads and sits the same in
 * every tool, and the one way their text reaches ImGui. ImGui reads the text of its tooltips and of {@code text} and
 * {@code textWrapped} as a printf format, so a % in a palette's or block's name would show as garbage; everything here
 * goes through {@code textUnformatted}. A control's own label, as on a button or checkbox, is drawn as it is.
 *
 * <p>Each tool's panel is drawn through its {@link Layout}, in two parts. Its options scroll in their own area, as
 * Axiom's panel shows no scrollbar and a panel taller than the space left would hide its last lines without a sign.
 * Under them, held to the bottom of the panel, is what the player must read before acting and the buttons to act
 * with, so neither is ever scrolled out of sight. The order is the same in every tool: what it paints with, how, its
 * options, the masks line and its notes, scrolling; then a separator, what it has to say, and its buttons at the very
 * bottom. A line that comes and goes with a mode or a result sits below every control, so no control moves.
 *
 * <p>ImGui wraps text only at spaces. Chinese and Japanese have none between words, and their translations space only
 * around numbers, so ImGui would leave a number on one line and its counter on the next, or a full stop alone; their
 * text is broken into lines here by {@link LineBreaks} instead.
 */
final class AxiomPanel {
    private AxiomPanel() {}

    /** A swatch: a block's colour, and what hovering it says. */
    record Swatch(Color colour, String tip) {
        /** Swatches for blocks, each named when hovered, leaving out those whose colour can't be read. */
        static List<Swatch> of(List<PaletteEntry> blocks) {
            List<Swatch> out = new ArrayList<>();
            for (PaletteEntry e : blocks) {
                Color c = PaletteBlocks.colour(e);
                if (c != null) out.add(new Swatch(c, PaletteBlocks.name(e).getString()));
            }
            return out;
        }
    }

    /** The most rows a list shows before it scrolls, so it leaves room for the controls below it. */
    private static final int LIST_ROWS = 5;
    /** How wide a tooltip runs before it wraps, in the font's size. */
    private static final float TIP_WIDTH = 30f;

    /**
     * Where a tool's panel draws its two parts. It keeps how tall the bottom part was last frame, as ImGui lays a
     * window out top to bottom and the options' area has to know where the bottom part starts before it is drawn.
     */
    static final class Layout {
        /** The height the bottom part needed last frame, or -1 before it has been drawn. */
        private float bottom = -1;

        /** Draws the options, scrolling, and under them {@code bottom}, held to the panel's bottom edge. */
        void draw(Runnable options, Runnable bottom) {
            // A tool Axiom still holds after Chroma Studio's features were turned off says why, and shows nothing else.
            Optional<Component> off = AxiomBridge.whyNot();
            if (off.isPresent()) {
                wrapped(off.get());
                return;
            }
            // The bottom part has the first claim on the space, as it holds what must be read before acting; the options
            // scroll in what's left. Until it has been measured, it takes the whole panel for that one frame. In a panel
            // too short for both, the two scroll together, so nothing is out of reach.
            float room = ImGui.getContentRegionAvailY();
            float above = this.bottom < 0 ? 0 : room - this.bottom - ImGui.getStyle().getItemSpacingY();
            if (this.bottom >= 0 && above < ImGui.getFrameHeightWithSpacing() * 2) {
                child("##options", 0, () -> {
                    options.run();
                    float top = ImGui.getCursorPosY();
                    ImGui.separator();
                    bottom.run();
                    this.bottom = ImGui.getCursorPosY() - top;
                });
                return;
            }
            if (above >= 1) child("##options", above, options);
            child("##bottom", 0, () -> {
                ImGui.separator();
                bottom.run();
                this.bottom = ImGui.getCursorPosY();
            });
        }

        /** A scrolling area, closed whatever {@code body} throws, as ImGui left with an open child would fail on Axiom's next call into it. */
        private static void child(String id, float height, Runnable body) {
            try {
                if (ImGui.beginChild(id, 0, height, false)) body.run();
            } finally {
                ImGui.endChild();
            }
        }
    }

    /** Text as it is, on one line. */
    static void text(String text) { ImGui.textUnformatted(text); }

    /** Text as it is, wrapped at the panel's edge. */
    static void wrapped(String text) { wrapped(text, ImGui.getContentRegionAvailX(), 0f); }

    /**
     * Text wrapped {@code width} wide, which ImGui's own wrapping does at {@code wrapAt} unless the text is Chinese or
     * Japanese. As one item, so a tooltip set after it belongs to all of its lines.
     */
    private static void wrapped(String text, float width, float wrapAt) {
        if (!LineBreaks.needed(text)) {
            ImGui.pushTextWrapPos(wrapAt);
            ImGui.textUnformatted(text);
            ImGui.popTextWrapPos();
            return;
        }
        ImGui.beginGroup();
        for (String line : lines(text, width)) ImGui.textUnformatted(line);
        ImGui.endGroup();
    }

    /** Lines broken lately, by text, width and font size, as a panel is drawn every frame and says the same for a while. */
    private static final Map<List<Object>, List<String>> BROKEN = new LinkedHashMap<>(16, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<List<Object>, List<String>> eldest) { return size() > 64; }
    };

    private static List<String> lines(String text, float width) {
        return BROKEN.computeIfAbsent(List.of(text, width, ImGui.getFontSize()), _ -> LineBreaks.split(text, width, ImGui::calcTextSizeX));
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
        float width = ImGui.getFontSize() * TIP_WIDTH;
        wrapped(text, width, width);
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

    /**
     * A slider's value held to what it offers, for a tool that reads it: the panel may not have been drawn since it was
     * set, and a value typed in or remembered could be anything.
     */
    static int held(int[] value, int min, int max) { return value[0] = Math.clamp(value[0], min, max); }

    /** A brush's radius held to what the slider offers. */
    static int radius(int[] radius) { return held(radius, 0, ModSettings.BRUSH_MAX_RADIUS); }

    /**
     * A list to choose one from, under its heading, up to {@link #LIST_ROWS} rows tall. Returns the row clicked, or -1.
     * Labels may repeat, as a player's palette may share a preset's name.
     */
    static int list(String heading, String id, List<String> labels, int selected) {
        text(heading);
        int clicked = -1;
        float rows = Math.min(Math.max(labels.size(), 1), LIST_ROWS) * ImGui.getTextLineHeightWithSpacing() + ImGui.getStyle().getFramePaddingY() * 2;
        if (ImGui.beginListBox(id, -1, rows)) {
            // A selectable's own width grows to fit its label, so a label is measured against the list's.
            float width = ImGui.getContentRegionAvailX();
            // A label is also ImGui's id for its row, so each gets its place in the list as well.
            for (int i = 0; i < labels.size(); i++) {
                String label = labels.get(i);
                if (ImGui.selectable(label + "##" + i, i == selected)) clicked = i;
                // A label too long for the list, as a long translation or palette name can be, is shown whole when hovered.
                if (ImGui.calcTextSizeX(label) > width) tip(label);
            }
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
        // Said before the player starts, as these tools then change nothing.
        if (!AxiomEditor.known()) wrapped(tr("axiom.chromastudio.editor.unknown.tools"));
        AxiomMask.unavailable().ifPresentOrElse(why -> wrapped(why), () -> {
            // Read each frame, so the line changes as the Tool Masks window is opened or closed.
            if (AxiomMask.windowClosed().orElse(false)) {
                wrapped(tr("axiom.chromastudio.masks.off"));
                return;
            }
            note(tr("axiom.chromastudio.masks"));
            tip(tr("axiom.chromastudio.masks.tip"));
        });
    }

    /** Apply and Cancel, as a whole-build tool has them, with {@code between} drawn between the two, placed with {@link #besideButton}. */
    static void applyCancel(boolean canApply, String applyWhyNot, boolean canCancel, Runnable apply, Runnable cancel, @Nullable Runnable between) {
        String cancelLabel = tr("axiom.chromastudio.cancel");
        if (button(tr("axiom.chromastudio.apply"), canApply, canApply ? tr("axiom.chromastudio.apply.tip") : applyWhyNot)) {
            apply.run();
            // The panel would keep the keyboard after the click, and Axiom's undo key would go to it rather than to
            // Axiom, when undo is the first thing a builder reaches for after Apply.
            ImGui.setWindowFocus(null);
        }
        if (between != null) between.run();
        besideButton(cancelLabel);
        if (button(cancelLabel, canCancel, tr(canCancel ? "axiom.chromastudio.cancel.tip" : "axiom.chromastudio.cancel.none"))) cancel.run();
    }

    static void sameLine() { ImGui.sameLine(); }

    /**
     * Puts the next button on the line of the last control when it fits there, or on a line of its own, so a longer
     * translation wraps rather than running off the panel's edge.
     */
    static void besideButton(String label) { beside(ImGui.calcTextSizeX(label, true) + ImGui.getStyle().getFramePaddingX() * 2); }

    /** As {@link #besideButton}, for a radio button or checkbox: its box, the gap after it, and its label. */
    static void besideChoice(String label) {
        beside(ImGui.getFrameHeight() + ImGui.getStyle().getItemInnerSpacingX() + ImGui.calcTextSizeX(label, true));
    }

    private static void beside(float width) {
        float right = ImGui.getItemRectMaxX() + ImGui.getStyle().getItemSpacingX() + width;
        if (right <= ImGui.getWindowPosX() + ImGui.getWindowContentRegionMaxX()) ImGui.sameLine();
    }
}
