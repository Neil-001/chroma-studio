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

final class AxiomPanel {
    private AxiomPanel() {}

    record Swatch(Color colour, String tip) {
        static List<Swatch> of(List<PaletteEntry> blocks) {
            List<Swatch> out = new ArrayList<>();
            for (PaletteEntry e : blocks) {
                Color c = PaletteBlocks.colour(e);
                if (c != null) out.add(new Swatch(c, PaletteBlocks.name(e).getString()));
            }
            return out;
        }
    }

    private static final int LIST_ROWS = 5;
    private static final float TIP_WIDTH = 30f;

    static final class Layout {
        private float bottom = -1;

        void draw(Runnable options, Runnable bottom) {
            Optional<Component> off = AxiomBridge.whyNot();
            if (off.isPresent()) {
                wrapped(off.get());
                return;
            }
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

        private static void child(String id, float height, Runnable body) {
            try {
                if (ImGui.beginChild(id, 0, height, false)) body.run();
            } finally {
                ImGui.endChild();
            }
        }
    }

    static void text(String text) { ImGui.textUnformatted(text); }

    static void wrapped(String text) { wrapped(text, ImGui.getContentRegionAvailX(), 0f); }

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

    private static final Map<List<Object>, List<String>> BROKEN = new LinkedHashMap<>(16, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<List<Object>, List<String>> eldest) { return size() > 64; }
    };

    private static List<String> lines(String text, float width) {
        return BROKEN.computeIfAbsent(List.of(text, width, ImGui.getFontSize()), _ -> LineBreaks.split(text, width, ImGui::calcTextSizeX));
    }

    static void wrapped(Component text) { wrapped(text.getString()); }

    static void note(String text) {
        ImGui.pushStyleColor(ImGuiCol.Text, ImGui.getColorU32(ImGuiCol.TextDisabled));
        wrapped(text);
        ImGui.popStyleColor();
    }

    static void tip(String text) {
        if (!ImGui.beginItemTooltip()) return;
        float width = ImGui.getFontSize() * TIP_WIDTH;
        wrapped(text, width, width);
        ImGui.endTooltip();
    }

    static boolean button(String label, boolean enabled, String tip) {
        ImGui.beginDisabled(!enabled);
        boolean pressed = ImGui.button(label);
        ImGui.endDisabled();
        tip(tip);
        return pressed && enabled;
    }

    static boolean radio(String label, boolean on, boolean enabled, String tip) {
        ImGui.beginDisabled(!enabled);
        boolean pressed = ImGui.radioButton(label, on);
        ImGui.endDisabled();
        tip(tip);
        return pressed && enabled;
    }

    static boolean checkbox(String label, boolean on, boolean enabled, String tip) {
        ImGui.beginDisabled(!enabled);
        boolean pressed = ImGui.checkbox(label, on);
        ImGui.endDisabled();
        tip(tip);
        return pressed && enabled;
    }

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

    static boolean radius(int[] radius, boolean enabled, String tip) {
        return slider(tr("axiom.chromastudio.radius"), radius, 0, ModSettings.BRUSH_MAX_RADIUS, "%d", enabled, tip);
    }

    static int held(int[] value, int min, int max) { return value[0] = Math.clamp(value[0], min, max); }

    static int radius(int[] radius) { return held(radius, 0, ModSettings.BRUSH_MAX_RADIUS); }

    static int list(String heading, String id, List<String> labels, int selected) {
        text(heading);
        int clicked = -1;
        float rows = Math.min(Math.max(labels.size(), 1), LIST_ROWS) * ImGui.getTextLineHeightWithSpacing() + ImGui.getStyle().getFramePaddingY() * 2;
        if (ImGui.beginListBox(id, -1, rows)) {
            float width = ImGui.getContentRegionAvailX();
            for (int i = 0; i < labels.size(); i++) {
                String label = labels.get(i);
                if (ImGui.selectable(label + "##" + i, i == selected)) clicked = i;
                if (ImGui.calcTextSizeX(label) > width) tip(label);
            }
            ImGui.endListBox();
        }
        return clicked;
    }

    static void swatches(String id, List<Swatch> shown) {
        float size = ImGui.getFrameHeight(), gap = ImGui.getStyle().getItemSpacingX();
        int perRow = Math.max(1, (int) ((ImGui.getContentRegionAvailX() + gap) / (size + gap)));
        for (int i = 0; i < shown.size(); i++) {
            if (i % perRow != 0) ImGui.sameLine();
            swatch(id + i, shown.get(i));
        }
    }

    static void swatch(String id, Swatch s) {
        Color c = s.colour();
        ImGui.colorButton("##" + id, c.red() / 255f, c.green() / 255f, c.blue() / 255f, 1f, ImGuiColorEditFlags.NoTooltip);
        tip(s.tip());
    }

    static void masks() {
        if (!AxiomEditor.known()) wrapped(tr("axiom.chromastudio.editor.unknown.tools"));
        AxiomMask.unavailable().ifPresentOrElse(why -> wrapped(why), () -> {
            if (AxiomMask.windowClosed().orElse(false)) {
                wrapped(tr("axiom.chromastudio.masks.off"));
                return;
            }
            note(tr("axiom.chromastudio.masks"));
            tip(tr("axiom.chromastudio.masks.tip"));
        });
    }

    static void applyCancel(boolean canApply, String applyWhyNot, boolean canCancel, Runnable apply, Runnable cancel, @Nullable Runnable between) {
        String cancelLabel = tr("axiom.chromastudio.cancel");
        if (button(tr("axiom.chromastudio.apply"), canApply, canApply ? tr("axiom.chromastudio.apply.tip") : applyWhyNot)) {
            apply.run();
            ImGui.setWindowFocus(null);
        }
        if (between != null) between.run();
        besideButton(cancelLabel);
        if (button(cancelLabel, canCancel, tr(canCancel ? "axiom.chromastudio.cancel.tip" : "axiom.chromastudio.cancel.none"))) cancel.run();
    }

    static void sameLine() { ImGui.sameLine(); }

    static void besideButton(String label) { beside(ImGui.calcTextSizeX(label, true) + ImGui.getStyle().getFramePaddingX() * 2); }

    static void besideChoice(String label) {
        beside(ImGui.getFrameHeight() + ImGui.getStyle().getItemInnerSpacingX() + ImGui.calcTextSizeX(label, true));
    }

    private static void beside(float width) {
        float right = ImGui.getItemRectMaxX() + ImGui.getStyle().getItemSpacingX() + width;
        if (right <= ImGui.getWindowPosX() + ImGui.getWindowContentRegionMaxX()) ImGui.sameLine();
    }
}
