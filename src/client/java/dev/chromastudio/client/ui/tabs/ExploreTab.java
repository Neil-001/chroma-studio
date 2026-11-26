package dev.chromastudio.client.ui.tabs;

import static dev.chromastudio.client.ui.Theme.BACKING;
import static dev.chromastudio.client.ui.Theme.BORDER;
import static dev.chromastudio.client.ui.Theme.DIM;
import static dev.chromastudio.client.ui.Theme.TEXT;
import static dev.chromastudio.client.ui.Theme.WELL;

import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.blaze3d.platform.cursor.CursorTypes;
import dev.chromastudio.client.index.BlockIndex;
import dev.chromastudio.client.index.Entry;
import dev.chromastudio.client.ui.Bounds;
import dev.chromastudio.client.ui.ChromaScreen;
import dev.chromastudio.client.ui.Handoff;
import dev.chromastudio.client.ui.Tab;
import dev.chromastudio.client.ui.Theme;
import dev.chromastudio.client.ui.Widgets;
import dev.chromastudio.core.color.Color;
import dev.chromastudio.core.color.Face;
import dev.chromastudio.core.color.HueWindow;
import dev.chromastudio.core.color.Oklch;
import dev.chromastudio.core.palette.PaletteEntry;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.util.ARGB;
import org.joml.Vector2i;

public final class ExploreTab extends Tab {
    private static final int PAD = 3, GAP = 2, AXIS = 6, FOOTER = 10, INSET = 4;
    private static final double MAX_ZOOM = 16;
    private static final int STACK_NAMES = 3, STACK_NAME_W = 140;
    private static final int[] HUE_STEPS = {90, 60, 30, 15, 10, 5, 2, 1}, LIGHTNESS_STEPS = {25, 10, 5, 2, 1};
    private static final int EDGE = 8, TIP_OFFSET = 12, TIP_MARGIN = 4, ICON_BOX = 18, MINI_W = 64, MINI_H = 32;
    private static final int REACH = 5, CLICK_SLOP = 3;

    private record Dot(Entry entry, Face face, int rgb, double hue, double lightness, double chroma, int size, boolean ringed) {}

    private Bounds area = new Bounds(0, 0, 0, 0), plot = area;
    private final Widgets controls = new Widgets();
    private AbstractWidget clear, reset, resetChroma;
    private List<Dot> dots = List.of();
    private int ringCount;
    private int seenRevision = -1, seenVersion = -1;
    private List<PaletteEntry> highlight;

    private double zoom = 1, centreX = 0.5, centreY = 0.5;
    private boolean pressed, panned;
    private double pressX, pressY;

    public ExploreTab() { super("explore"); }

    private static MutableComponent tr(String key, Object... args) { return Component.translatable("explore.chromastudio." + key, args); }

    @Override
    public void init(Bounds b) {
        area = b;
        controls.clear();
        MatchFilters f = MatchState.filters();
        int x = b.x() + PAD, y = b.y() + PAD;
        Component chroma = tr("chroma");
        controls.label(x, y + 4, chroma, TEXT).setTooltip(Tooltip.create(tr("chroma.tip")));
        x += Widgets.tw(chroma) + 4;
        controls.spin(x, y, 50, f.chromaMin, 0.02, 0, TargetModel.CHROMA_MAX, false, v -> {
            f.chromaMin = v;
            MatchState.changed();
        }).setTooltip(Tooltip.create(tr("chroma.min.tip")));
        x += 50 + GAP;
        Component to = tr("chroma.to");
        controls.label(x, y + 4, to, DIM);
        x += Widgets.tw(to) + GAP;
        EditBox max = controls.spin(x, y, 50, f.chromaMax, 0.02, 0, TargetModel.CHROMA_MAX, false, TargetModel.CHROMA_MAX, v -> {
            f.chromaMax = v;
            MatchState.changed();
        });
        max.setTooltip(Tooltip.create(tr("chroma.max.tip")));
        max.setHint(tr("no_limit"));
        if (f.chromaMax >= TargetModel.CHROMA_MAX) max.setValue("");
        x += 50 + GAP;
        Component resetLabel = tr("reset_view");
        reset = controls.button(x, y, resetLabel, () -> {
            zoom = 1;
            centreX = centreY = 0.5;
        });
        x += Widgets.buttonWidth(resetLabel) + GAP;
        clear = controls.button(x, y, tr("clear_highlight"), () -> {
            Handoff.exploreHighlight = null;
            seenRevision = -1;
        });
        int top = y + Widgets.H + GAP;
        plot = new Bounds(b.x() + PAD + AXIS, top, b.w() - 2 * PAD - AXIS, Math.max(8, b.bottom() - PAD - FOOTER - AXIS - top));
        Component resetChromaLabel = tr("reset_chroma");
        int resetChromaW = Widgets.buttonWidth(resetChromaLabel);
        resetChroma = controls.button(plot.x() + (plot.w() - resetChromaW) / 2, plot.y() + plot.h() / 2 + 8, resetChromaLabel, () -> {
            f.chromaMin = 0;
            f.chromaMax = TargetModel.CHROMA_MAX;
            MatchState.changed();
            init(area);
        });
        resetChroma.visible = false;
        seenRevision = -1;
    }

    private void sync() {
        BlockIndex index = BlockIndex.get();
        MatchState.follow();
        if (Handoff.exploreHighlight != highlight) {
            highlight = Handoff.exploreHighlight;
            seenRevision = -1;
        }
        boolean ringing = highlight != null && !highlight.isEmpty();
        if (clear != null && clear.active != ringing) {
            clear.active = ringing;
            clear.setTooltip(ringing ? null : Tooltip.create(tr("clear_highlight.none")));
        }
        boolean moved = zoom != 1 || centreX != 0.5 || centreY != 0.5;
        if (reset != null && reset.active != moved) {
            reset.active = moved;
            reset.setTooltip(moved ? null : Tooltip.create(tr("reset_view.none")));
        }
        if (resetChroma != null) {
            MatchFilters f = MatchState.filters();
            resetChroma.visible = index.ready() && dots.isEmpty() && chromaLimited(f);
        }
        if (seenRevision == MatchState.revision() && seenVersion == index.version()) return;
        seenRevision = MatchState.revision();
        seenVersion = index.version();
        if (!index.ready()) {
            dots = List.of();
            return;
        }
        ringCount = 0;
        Set<String> ringed = new HashSet<>();
        if (ringing) for (PaletteEntry e : highlight) ringed.add(e.blockId());
        var query = MatchState.query(Integer.MAX_VALUE);
        List<Dot> out = new ArrayList<>();
        for (BlockIndex.Match m : index.closest(MatchState.target(), query)) {
            Color c = m.entry().face(m.face(), query.tint).mean(query.scale);
            Oklch lch = c.oklch();
            int size = 2 + (int) Math.min(3, Math.round(lch.c() / 0.08));
            boolean ring = ringed.contains(m.entry().id());
            if (ring) ringCount++;
            out.add(new Dot(m.entry(), m.face(), c.rgb(), HueWindow.wrap(lch.h() / 360), lch.l(), lch.c(), size, ring));
        }
        dots = out;
    }

    private int spanX() { return Math.max(1, plot.w() - 1 - 2 * INSET); }

    private int spanY() { return Math.max(1, plot.h() - 1 - 2 * INSET); }

    private HueWindow window() { return HueWindow.around(centreX, 1 / zoom); }

    private int screenX(double hue) {
        HueWindow w = window();
        return plot.x() + INSET + (int) Math.round(w.offset(hue, (double) EDGE / spanX() * w.width()) / w.width() * spanX());
    }

    private int screenY(double lightness) { return plot.y() + INSET + (int) Math.round((0.5 - (lightness - centreY) * zoom) * spanY()); }

    private static long degrees(double hue) { return Math.floorMod(Math.round(hue * 360), 360); }

    private double hueAt(double x) { return centreX + ((x - plot.x() - INSET) / spanX() - 0.5) / zoom; }

    private double lightnessAt(double y) { return centreY + (0.5 - (y - plot.y() - INSET) / spanY()) / zoom; }

    private static boolean chromaLimited(MatchFilters f) { return f.chromaMin > 0 || f.chromaMax < TargetModel.CHROMA_MAX; }

    private Bounds mini() { return new Bounds(plot.right() - 2 - MINI_W, plot.y() + 2, MINI_W, MINI_H); }

    private int grow() { return (int) Math.min(5, Math.round(1.2 * Math.log(zoom) / Math.log(2))); }

    private void confine() {
        zoom = Math.clamp(zoom, 1, MAX_ZOOM);
        double half = 0.5 / zoom;
        centreX = HueWindow.wrap(centreX);
        centreY = Math.clamp(centreY, half, 1 - half);
    }

    @Override
    public void render(GuiGraphicsExtractor g, Bounds b, int mouseX, int mouseY, float delta) {
        sync();
        Font font = Minecraft.getInstance().font;
        Dot over = draw(g, font, 1, mouseX, mouseY);
        controls.render(g, mouseX, mouseY, delta);
        Component count = tr("count", dots.size());
        g.text(font, count, b.right() - PAD - font.width(count), b.y() + PAD + 4, DIM, false);
        footer(g, font, b);
        if (pressed && panned) g.requestCursor(CursorTypes.RESIZE_ALL);
        else if (over != null) g.requestCursor(CursorTypes.POINTING_HAND);
        if (over != null && !panned) tooltip(g, font, over, mouseX, mouseY);
    }

    private void tooltip(GuiGraphicsExtractor g, Font font, Dot over, int mouseX, int mouseY) {
        Entry e = over.entry;
        List<Component> tip = new ArrayList<>();
        tip.add(e.properties().isEmpty() ? e.name() : Component.translatable("match.chromastudio.name_with_state", e.name(), e.properties()));
        tip.add(Component.literal(String.format("#%06X", over.rgb)).withColor(Theme.TIP_DIM));
        tip.add(tr("lch", Math.round(over.lightness * 100), String.format("%.3f", over.chroma), degrees(over.hue)).withColor(Theme.TIP_DIM));
        int ox = screenX(over.hue), oy = screenY(over.lightness);
        List<Dot> stacked = new ArrayList<>();
        for (Dot d : dots) if (d != over && Math.abs(screenX(d.hue) - ox) <= 1 && Math.abs(screenY(d.lightness) - oy) <= 1) stacked.add(d);
        for (Dot d : stacked.subList(0, Math.min(STACK_NAMES, stacked.size())))
            tip.add(tr("stacked.name", MatchTab.ellipsis(font, d.entry.name().getString(), STACK_NAME_W)).withColor(Theme.TIP_DIM));
        if (stacked.size() > STACK_NAMES) tip.add(tr("stacked", stacked.size() - STACK_NAMES).withColor(Theme.TIP_DIM));
        tip.add(tr("click_tip").withColor(Theme.TIP_DIM));
        int w = tip.stream().mapToInt(font::width).max().orElse(0), h = tip.size() * 10;
        var window = Minecraft.getInstance().getWindow();
        int screenW = window.getGuiScaledWidth(), screenH = window.getGuiScaledHeight();
        int x = mouseX + TIP_OFFSET + ICON_BOX + 2 * TIP_MARGIN, ix = mouseX + TIP_OFFSET;
        if (x + w + TIP_MARGIN > screenW || ix + ICON_BOX > area.right()) {
            x = Math.max(TIP_MARGIN, Math.min(screenW - TIP_MARGIN - w, Math.max(area.x() + ICON_BOX + 2 * TIP_MARGIN, mouseX - TIP_OFFSET - TIP_MARGIN - w)));
            ix = x - 2 * TIP_MARGIN - ICON_BOX;
            if (ix < area.x()) ix = x + w + 2 * TIP_MARGIN;
        }
        int y = Math.clamp(mouseY - TIP_OFFSET, TIP_MARGIN, Math.max(TIP_MARGIN, screenH - h - TIP_MARGIN));
        int iy = Math.clamp(y - 3, area.y(), Math.max(area.y(), area.bottom() - ICON_BOX));
        if (ix >= area.x() && ix + ICON_BOX <= area.right()) {
            g.fill(ix, iy, ix + ICON_BOX, iy + ICON_BOX, Theme.sink(0xE0));
            g.outline(ix, iy, ICON_BOX, ICON_BOX, BORDER);
            Widgets.blockIcon(g, e.block(), ix + 1, iy + 1);
        }
        int tx = x, ty = y;
        g.setTooltipForNextFrame(font, tip.stream().map(Component::getVisualOrderText).toList(), (_, _, _, _, _, _) -> new Vector2i(tx, ty), mouseX, mouseY, false);
    }

    private void footer(GuiGraphicsExtractor g, Font font, Bounds b) {
        List<String> parts = new ArrayList<>();
        int visible = 0, ringsVisible = 0;
        for (Dot d : dots) {
            if (!plot.contains(screenX(d.hue), screenY(d.lightness))) continue;
            visible++;
            if (d.ringed) ringsVisible++;
        }
        if (zoom > 1) parts.add(tr("visible", visible).getString());
        if (ringCount > 0) parts.add((zoom > 1 ? tr("highlighted.view", ringsVisible, ringCount) : tr("highlighted", ringCount)).getString());
        int width = b.w() - 2 * PAD, y = b.bottom() - PAD - 8;
        String status = MatchTab.ellipsis(font, String.join("  ·  ", parts), width), hint = tr("hint").getString();
        int statusW = font.width(status);
        g.text(font, status, b.right() - PAD - statusW, y, DIM, false);
        if (font.width(hint) + (statusW > 0 ? statusW + 8 : 0) <= width) g.text(font, hint, b.x() + PAD, y, DIM, false);
    }

    @Override
    public void renderPinned(GuiGraphicsExtractor g, Bounds b, float opacity) {
        sync();
        Font font = Minecraft.getInstance().font;
        draw(g, font, opacity, -1, -1);
        MatchFilters f = MatchState.filters();
        List<String> parts = new ArrayList<>(List.of(tr("count", dots.size()).getString()));
        if (chromaLimited(f)) parts.add(tr("pinned.chroma", Widgets.number(f.chromaMin, false), f.chromaMax >= TargetModel.CHROMA_MAX ? tr("no_limit").getString() : Widgets.number(f.chromaMax, false)).getString());
        if (ringCount > 0) parts.add(tr("highlighted", ringCount).getString());
        g.text(font, String.join("  ·  ", parts), b.x() + PAD, b.bottom() - PAD - 8, ARGB.multiplyAlpha(DIM, opacity), false);
    }

    private Dot draw(GuiGraphicsExtractor g, Font font, float opacity, int mouseX, int mouseY) {
        Bounds p = plot;
        g.fill(area.x(), area.y(), area.right(), area.bottom(), ARGB.multiplyAlpha(BACKING, opacity));
        g.fill(p.x() - 1, p.y() - 1, p.right() + 1, p.bottom() + 1, ARGB.multiplyAlpha(BORDER, opacity));
        g.fill(p.x(), p.y(), p.right(), p.bottom(), ARGB.multiplyAlpha(WELL, opacity));
        for (int x = 0; x < p.w(); x += 2) {
            double hue = hueAt(p.x() + x);
            g.fill(p.x() + x, p.bottom() + 2, Math.min(p.right(), p.x() + x + 2), p.bottom() + AXIS,
                    ARGB.multiplyAlpha(0xFF000000 | Color.ofOklch(new Oklch(0.72, 0.13, HueWindow.wrap(hue) * 360)).rgb(), opacity));
        }
        for (int y = 0; y < p.h(); y += 2) {
            double lightness = lightnessAt(p.y() + y);
            int grey = (int) Math.round(Math.clamp(lightness, 0, 1) * 255);
            g.fill(p.x() - AXIS, p.y() + y, p.x() - 2, Math.min(p.bottom(), p.y() + y + 2), ARGB.multiplyAlpha(0xFF000000 | grey * 0x010101, opacity));
        }
        if (!BlockIndex.get().ready()) {
            Widgets.centred(g, Component.translatable("match.chromastudio.building", Math.round(BlockIndex.get().progress() * 100)),
                    p.x() + p.w() / 2, p.y() + p.h() / 2 - 4, ARGB.multiplyAlpha(DIM, opacity));
            return null;
        }
        if (dots.isEmpty()) {
            Component empty = chromaLimited(MatchState.filters()) ? tr("no_results_chroma") : Component.translatable("match.chromastudio.no_results");
            Widgets.centred(g, empty, p.x() + p.w() / 2, p.y() + p.h() / 2 - 4, ARGB.multiplyAlpha(DIM, opacity));
            return null;
        }
        ticks(g, font, opacity);
        Dot nearest = at(mouseX, mouseY);
        boolean ringing = ringCount > 0;
        g.enableScissor(p.x(), p.y(), p.right(), p.bottom());
        for (int pass = 0; pass < 2; pass++) {
            for (Dot d : dots) {
                if (d.ringed != (pass == 1)) continue;
                int x = screenX(d.hue), y = screenY(d.lightness), s = d.size + grow(), x0 = x - s / 2, y0 = y - s / 2;
                if (!shown(d, x0, y0, s)) continue;
                float fade = ringing && !d.ringed ? opacity * 0.5f : opacity;
                if (Theme.contrast(0xFF000000 | d.rgb, WELL) < 1.4) g.fill(x0 + 1, y0 + 1, x0 + s + 1, y0 + s + 1, ARGB.multiplyAlpha(Theme.veil(0x70), fade));
                g.fill(x0, y0, x0 + s, y0 + s, ARGB.multiplyAlpha(0xFF000000 | d.rgb, fade));
                if (d.ringed) {
                    g.outline(x0 - 2, y0 - 2, s + 4, s + 4, ARGB.multiplyAlpha(Theme.STRONG, opacity));
                    g.outline(x0 - 3, y0 - 3, s + 6, s + 6, ARGB.multiplyAlpha(Theme.sink(0xC0), opacity));
                }
            }
        }
        if (nearest != null) {
            int s = nearest.size + grow(), x0 = screenX(nearest.hue) - s / 2, y0 = screenY(nearest.lightness) - s / 2;
            g.outline(x0 - 1, y0 - 1, s + 2, s + 2, Theme.sink(0xFF));
            g.outline(x0 - 2, y0 - 2, s + 4, s + 4, Theme.ACCENT);
            g.outline(x0 - 3, y0 - 3, s + 6, s + 6, Theme.sink(0xFF));
        }
        g.disableScissor();
        if (miniShown()) minimap(g, opacity);
        double half = 0.5 / zoom;
        label(g, font, tr("range.lightness", Math.round((centreY - half) * 100), Math.round((centreY + half) * 100)), p.x() + 2, p.y() + 2, opacity);
        Component hue = hueLabel();
        label(g, font, hue, p.right() - 2 - font.width(hue), p.bottom() - 11, opacity);
        return nearest;
    }

    private Component hueLabel() {
        double half = 0.5 / zoom;
        return zoom > 1 ? tr("range.hue", degrees(centreX - half), degrees(centreX + half)) : tr("axis.hue");
    }

    private static int step(int[] steps, double span) {
        for (int st : steps) if (span / st >= 3) return st;
        return steps[steps.length - 1];
    }

    private void ticks(GuiGraphicsExtractor g, Font font, float opacity) {
        Bounds p = plot;
        int grid = ARGB.multiplyAlpha(Theme.veil(0x18), opacity), text = ARGB.multiplyAlpha(DIM, opacity);
        double half = 0.5 / zoom;
        int hueStep = step(HUE_STEPS, 360 / zoom);
        for (int d = 0; d < 360; d += hueStep) {
            int x = screenX(d / 360.0);
            if (x < p.x() || x >= p.right()) continue;
            g.fill(x, p.y(), x + 1, p.bottom(), grid);
            g.fill(x, p.bottom() + 2, x + 1, p.bottom() + AXIS, ARGB.multiplyAlpha(BORDER, opacity));
            String label = tr("tick.hue", d).getString();
            int w = font.width(label), lx = Math.clamp(x - w / 2, p.x() + 2, p.right() - 2 - w);
            if (lx + w < p.right() - 8 - font.width(hueLabel())) g.text(font, label, lx, p.bottom() - 10, text, false);
        }
        int lightStep = step(LIGHTNESS_STEPS, 100 / zoom);
        for (int l = lightStep; l < 100; l += lightStep) {
            double frac = l / 100.0;
            if (frac < centreY - half || frac > centreY + half) continue;
            int y = screenY(frac);
            g.fill(p.x(), y, p.right(), y + 1, grid);
            g.fill(p.x() - AXIS, y, p.x() - 2, y + 1, ARGB.multiplyAlpha(BORDER, opacity));
            if (y > p.y() + 14 && y < p.bottom() - 16) g.text(font, tr("tick.lightness", l), p.x() + 2, y - 4, text, false);
        }
    }

    private void minimap(GuiGraphicsExtractor g, float opacity) {
        Bounds m = mini();
        g.fill(m.x() - 1, m.y() - 1, m.right() + 1, m.bottom() + 1, ARGB.multiplyAlpha(BORDER, opacity));
        g.fill(m.x(), m.y(), m.right(), m.bottom(), ARGB.multiplyAlpha(WELL, opacity));
        int w = m.w() - 2, h = m.h() - 2;
        for (Dot d : dots) {
            int x = m.x() + 1 + (int) Math.round(d.hue * (w - 1)), y = m.y() + 1 + (int) Math.round((1 - d.lightness) * (h - 1));
            if (d.ringed) g.fill(x - 1, y - 1, x + 1, y + 1, ARGB.multiplyAlpha(Theme.STRONG, opacity));
            else g.fill(x, y, x + 1, y + 1, ARGB.multiplyAlpha(0xFF000000 | d.rgb, opacity * (ringCount > 0 ? 0.5f : 1)));
        }
        HueWindow view = window();
        int frame = ARGB.multiplyAlpha(Theme.ACCENT, opacity);
        int vx = m.x() + (int) Math.round(view.start() * w), vw = Math.max(3, (int) Math.round(view.width() * w) + 2);
        int vy = m.y() + (int) Math.round((1 - centreY - 0.5 / zoom) * h), vh = Math.max(3, (int) Math.round(h / zoom) + 2);
        g.enableScissor(m.x(), m.y(), m.right(), m.bottom());
        g.outline(vx, vy, vw, vh, frame);
        if (vx + vw > m.right()) g.outline(vx - w, vy, vw, vh, frame);
        g.disableScissor();
    }

    private static void label(GuiGraphicsExtractor g, Font font, Component text, int x, int y, float opacity) {
        g.fill(x - 1, y - 1, x + font.width(text) + 1, y + 9, ARGB.multiplyAlpha(Theme.sink(0xA0), opacity));
        g.text(font, text, x, y, ARGB.multiplyAlpha(DIM, opacity), false);
    }

    private boolean shown(Dot d, int x0, int y0, int s) {
        int ring = d.ringed ? 3 : 0;
        return x0 + s + ring > plot.x() && x0 - ring < plot.right() && y0 + s + ring > plot.y() && y0 - ring < plot.bottom();
    }

    private boolean miniShown() { return zoom > 1 && !dots.isEmpty(); }

    private Dot at(double mouseX, double mouseY) {
        int mx = (int) Math.floor(mouseX), my = (int) Math.floor(mouseY);
        if (!plot.contains(mx, my) || miniShown() && mini().contains(mx, my)) return null;
        Dot nearest = null;
        int best = Integer.MAX_VALUE;
        for (Dot d : dots) {
            int x = screenX(d.hue), y = screenY(d.lightness), s = d.size + grow(), x0 = x - s / 2, y0 = y - s / 2;
            if (!shown(d, x0, y0, s)) continue;
            int reach = Math.max(REACH, s / 2 + (d.ringed ? 4 : 1)), dx = x - mx, dy = y - my, far = dx * dx + dy * dy;
            if (far <= reach * reach && far < best) {
                best = far;
                nearest = d;
            }
        }
        return nearest;
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent e, boolean doubled) {
        if (controls.mouseClicked(e, doubled)) return true;
        if (e.button() != InputConstants.MOUSE_BUTTON_LEFT || !plot.contains(e.x(), e.y())) return false;
        Bounds m = mini();
        if (miniShown() && m.contains(e.x(), e.y())) {
            centreX = HueWindow.wrap((e.x() - m.x() - 1) / (m.w() - 2));
            centreY = 1 - (e.y() - m.y() - 1) / (m.h() - 2);
            confine();
            return true;
        }
        pressed = true;
        panned = false;
        pressX = e.x();
        pressY = e.y();
        return true;
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent e, double dx, double dy) {
        if (!pressed) return controls.mouseDragged(e, dx, dy);
        if (!panned && Math.abs(e.x() - pressX) <= CLICK_SLOP && Math.abs(e.y() - pressY) <= CLICK_SLOP) return true;
        panned = true;
        centreX -= dx / spanX() / zoom;
        centreY += dy / spanY() / zoom;
        confine();
        return true;
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent e) {
        if (!pressed) return controls.mouseReleased(e);
        pressed = false;
        boolean wasPan = panned;
        panned = false;
        if (wasPan) return true;
        Dot d = at(e.x(), e.y());
        if (d == null) return true;
        MatchState.setEntry(d.entry, d.face, null);
        MatchState.remember();
        ChromaScreen screen = ChromaScreen.current();
        if (screen != null) screen.select("match");
        return true;
    }

    @Override
    public boolean mouseScrolled(double x, double y, double h, double v) {
        if (v == 0) return false;
        if (controls.mouseScrolled(x, y, h, v)) return true;
        if (!plot.contains(x, y)) return false;
        double hue = hueAt(x), lightness = lightnessAt(y);
        zoom = Math.clamp(zoom * Math.pow(1.25, v), 1, MAX_ZOOM);
        centreX += hue - hueAt(x);
        centreY += lightness - lightnessAt(y);
        confine();
        return true;
    }

    @Override
    public boolean keyPressed(KeyEvent e) {
        if (controls.typing() && e.isEscape()) return false;
        return controls.keyPressed(e) || controls.typing();
    }

    @Override
    public boolean charTyped(CharacterEvent e) { return controls.charTyped(e); }

    @Override
    public boolean typing() { return controls.typing(); }
}
