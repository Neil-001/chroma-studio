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
import net.minecraft.network.chat.contents.TranslatableContents;
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

    record Status(String text, String tip) {
        static Status of(String text) { return new Status(text, ""); }

        static Status of(Component text) { return of(text.getString()); }

        Status withTip(String more) {
            if (more.isEmpty()) return this;
            return new Status(text, tip.isEmpty() ? more : tip + "\n" + more);
        }
    }

    private static final int LEAST_ROWS = 3;
    private static final float TIP_WIDTH = 30f;
    private static final String REFUSED = "axiom.chromastudio.refused.";
    private static final String ELLIPSIS = "...";

    static final class Layout {
        private float bottom = -1;
        private float optionsEnd = -1, grownEnd = -1;
        private final List<Runnable> later = new ArrayList<>();

        void later(Runnable action) { later.add(action); }

        void draw(Runnable options, Runnable bottom) {
            try {
                parts(options, bottom);
            } finally {
                List<Runnable> run = List.copyOf(later);
                later.clear();
                run.forEach(Runnable::run);
            }
        }

        private void parts(Runnable options, Runnable bottom) {
            Optional<Component> off = AxiomBridge.whyNot();
            if (off.isPresent()) {
                wrapped(off.get());
                return;
            }
            Runnable measured = () -> {
                options.run();
                optionsEnd = ImGui.getCursorPosY();
            };
            float room = ImGui.getContentRegionAvailY();
            float above = this.bottom < 0 ? 0 : room - this.bottom - ImGui.getStyle().getItemSpacingY();
            if (this.bottom >= 0 && above < ImGui.getFrameHeightWithSpacing() * 2) {
                child("##options", 0, () -> {
                    measured.run();
                    float top = ImGui.getCursorPosY();
                    ImGui.separator();
                    bottom.run();
                    this.bottom = ImGui.getCursorPosY() - top;
                });
                return;
            }
            if (above >= 1) child("##options", above, measured);
            child("##bottom", 0, () -> {
                ImGui.separator();
                bottom.run();
                this.bottom = ImGui.getCursorPosY();
            });
        }

        float grow(float wanted, float least) {
            float below = optionsEnd < 0 || grownEnd < 0 ? 0 : Math.max(0, optionsEnd - grownEnd);
            float room = ImGui.getContentRegionAvailY() - below - ImGui.getStyle().getItemSpacingY();
            return Math.max(Math.min(wanted, room), Math.min(wanted, least));
        }

        void grown() { grownEnd = ImGui.getCursorPosY(); }

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
        List<String> out = new ArrayList<>();
        for (String paragraph : text.split("\n", -1)) {
            out.addAll(BROKEN.computeIfAbsent(List.of(paragraph, width, ImGui.getFontSize()), _ -> LineBreaks.split(paragraph, width, ImGui::calcTextSizeX)));
        }
        return out;
    }

    static void wrapped(Component text) { wrapped(text.getString()); }

    static void note(String text) {
        ImGui.pushStyleColor(ImGuiCol.Text, ImGui.getColorU32(ImGuiCol.TextDisabled));
        wrapped(text);
        ImGui.popStyleColor();
    }

    static void tip(String text) {
        if (text.isEmpty() || !ImGui.beginItemTooltip()) return;
        float width = ImGui.getFontSize() * TIP_WIDTH;
        if (text.indexOf('\n') >= 0) {
            ImGui.beginGroup();
            for (String line : lines(text, width)) ImGui.textUnformatted(line);
            ImGui.endGroup();
        } else {
            wrapped(text, width, width);
        }
        ImGui.endTooltip();
    }

    static void heading(String label) { ImGui.separatorText(label); }

    static boolean disclosure(String label, String id) { return ImGui.collapsingHeader(label + "###" + id, 0); }

    static void status(@Nullable Status s) {
        if (s == null) return;
        wrapped(s.text());
        tip(s.tip());
    }

    static Status refusal(Component why, String again) {
        if (!(why.getContents() instanceof TranslatableContents t) || !t.getKey().startsWith(REFUSED)) return Status.of(why);
        Status s = Status.of(why.getString() + " " + tr(again));
        return t.getKey().equals(REFUSED + "masks_differ") && AxiomMask.scripted() ? s.withTip(tr(REFUSED + "masks_differ.tip")) : s;
    }

    static Optional<Status> masks() {
        if (!AxiomEditor.known()) return Optional.of(Status.of(tr("axiom.chromastudio.editor.unknown.tools")));
        Optional<Component> failed = AxiomMask.unavailable();
        if (failed.isPresent()) return Optional.of(Status.of(failed.get()).withTip(AxiomMask.detail().orElse("")));
        Optional<Boolean> closed = AxiomMask.windowClosed();
        note(tr(closed.isEmpty() ? "axiom.chromastudio.masks" : closed.get() ? "axiom.chromastudio.masks.off" : "axiom.chromastudio.masks.on"));
        tip(tr(closed.orElse(false) ? "axiom.chromastudio.masks.off.tip" : "axiom.chromastudio.masks.tip"));
        return Optional.empty();
    }

    static String fit(String text, float width) {
        if (ImGui.calcTextSizeX(text) <= width) return text;
        int[] points = text.codePoints().toArray();
        String best = ELLIPSIS;
        int low = 1, high = points.length - 1;
        while (low <= high) {
            int keep = (low + high) / 2, head = keep / 3;
            String tried = new String(points, 0, head) + ELLIPSIS + new String(points, points.length - (keep - head), keep - head);
            if (ImGui.calcTextSizeX(tried) <= width) {
                best = tried;
                low = keep + 1;
            } else {
                high = keep - 1;
            }
        }
        return best;
    }

    static float comboWidth() { return ImGui.calcItemWidth() - ImGui.getFrameHeight() - ImGui.getStyle().getFramePaddingX() * 2; }

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

    static int list(Layout layout, String id, List<String> labels, int selected) {
        int clicked = -1;
        float row = ImGui.getTextLineHeightWithSpacing(), pad = ImGui.getStyle().getFramePaddingY() * 2;
        int shown = Math.max(labels.size(), 1);
        float height = layout.grow(shown * row + pad, LEAST_ROWS * row + pad);
        if (ImGui.beginListBox(id, -1, height)) {
            float width = ImGui.getContentRegionAvailX();
            for (int i = 0; i < labels.size(); i++) {
                String label = labels.get(i), fitted = fit(label, width);
                if (ImGui.selectable(fitted + "##" + i, i == selected)) clicked = i;
                if (!fitted.equals(label)) tip(label);
            }
            ImGui.endListBox();
        }
        layout.grown();
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

    static void applyCancel(boolean canApply, String applyWhyNot, boolean canCancel, Runnable apply, Runnable cancel, @Nullable Runnable between) {
        String cancelLabel = tr("axiom.chromastudio.cancel");
        if (button(tr("axiom.chromastudio.apply"), canApply, canApply ? tr("axiom.chromastudio.apply.tip") : applyWhyNot)) {
            apply.run();
            ImGui.setWindowFocus(null);
        }
        if (between != null) between.run();
        besideButton(cancelLabel);
        if (button(cancelLabel, canCancel, canCancel ? "" : tr("axiom.chromastudio.cancel.none"))) cancel.run();
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
