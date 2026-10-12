package dev.chromastudio.client.ui.tabs;

import com.mojang.blaze3d.platform.InputConstants;
import dev.chromastudio.client.index.BlockIndex;
import dev.chromastudio.client.index.Entry;
import dev.chromastudio.client.ui.Bounds;
import dev.chromastudio.client.ui.ChromaScreen;
import dev.chromastudio.client.ui.Handoff;
import dev.chromastudio.client.ui.Tab;
import dev.chromastudio.client.ui.Widgets;
import dev.chromastudio.core.color.Color;
import dev.chromastudio.core.color.Face;
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
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.util.ARGB;

/**
 * The Explore tab: every block under the Match tab's filters as a dot, with OKLCH hue across and lightness up, and
 * more vivid blocks drawn larger. The wheel zooms, a drag pans, and a click makes the block the Match target.
 */
public final class ExploreTab extends Tab {
    private static final int PAD = 3, GAP = 2, AXIS = 6;
    private static final int TEXT = 0xFFE4E9EF, DIM = 0xFF9AA5B3, WELL = 0xFF0B0D10, BORDER = 0xFF3A424D, BACKING = 0x80101317;
    private static final double MAX_ZOOM = 32;
    /** How near the cursor a dot must be to be the one hovered, and how far a press may move and still be a click. */
    private static final int REACH = 5, CLICK_SLOP = 3;

    /** One block on the plot. {@code hue} is 0 to 1 across, {@code lightness} 0 to 1 up. */
    private record Dot(Entry entry, Face face, int rgb, double hue, double lightness, int size, boolean ringed) {}

    private Bounds area = new Bounds(0, 0, 0, 0), plot = area;
    private final Widgets controls = new Widgets();
    private AbstractWidget clear;
    private List<Dot> dots = List.of();
    /** What the dots were worked out from, so they are only worked out again when one of these changes. */
    private int seenRevision = -1, seenVersion = -1;
    private List<PaletteEntry> highlight;

    // The view: how far in, and the plot coordinates, 0 to 1 each way, at its middle.
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
        controls.label(x, y + 4, chroma, TEXT);
        x += Widgets.tw(chroma) + 4;
        controls.spin(x, y, 50, f.chromaMin, 0.02, 0, TargetModel.CHROMA_MAX, false, v -> {
            f.chromaMin = v;
            MatchState.changed();
        }).setTooltip(Tooltip.create(tr("chroma.min.tip")));
        x += 50 + GAP;
        controls.spin(x, y, 50, f.chromaMax, 0.02, 0, TargetModel.CHROMA_MAX, false, v -> {
            f.chromaMax = v;
            MatchState.changed();
        }).setTooltip(Tooltip.create(tr("chroma.max.tip")));
        x += 50 + GAP;
        Component reset = tr("reset_view");
        controls.button(x, y, reset, () -> {
            zoom = 1;
            centreX = centreY = 0.5;
        }).setTooltip(Tooltip.create(tr("reset_view.tip")));
        x += Widgets.buttonWidth(reset) + GAP;
        clear = controls.button(x, y, tr("clear_highlight"), () -> {
            Handoff.exploreHighlight = null;
            seenRevision = -1;
        });
        int top = y + Widgets.H + GAP;
        plot = new Bounds(b.x() + PAD + AXIS, top, b.w() - 2 * PAD - AXIS, Math.max(8, b.bottom() - PAD - AXIS - top));
        seenRevision = -1;
    }

    /** Works the dots out again when the index, the filters or the highlighted palette have changed. */
    private void sync() {
        BlockIndex index = BlockIndex.get();
        if (Handoff.exploreHighlight != highlight) {
            highlight = Handoff.exploreHighlight;
            seenRevision = -1;
        }
        boolean ringing = highlight != null && !highlight.isEmpty();
        if (clear != null && clear.active != ringing) {
            clear.active = ringing;
            clear.setTooltip(Tooltip.create(ringing ? tr("clear_highlight.tip") : tr("clear_highlight.none")));
        }
        if (seenRevision == MatchState.revision() && seenVersion == index.version()) return;
        seenRevision = MatchState.revision();
        seenVersion = index.version();
        if (!index.ready()) {
            dots = List.of();
            return;
        }
        Set<String> ringed = new HashSet<>();
        if (ringing) for (PaletteEntry e : highlight) ringed.add(e.blockId());
        var query = MatchState.query(Integer.MAX_VALUE);
        // The search does the filtering and says which face of each block the face control means.
        List<Dot> out = new ArrayList<>();
        for (BlockIndex.Match m : index.closest(MatchState.target(), query)) {
            Color c = m.entry().face(m.face(), query.tint).mean(query.scale);
            Oklch lch = c.oklch();
            int size = 2 + (int) Math.min(3, Math.round(lch.c() / 0.08));
            out.add(new Dot(m.entry(), m.face(), c.rgb(), lch.h() / 360, lch.l(), size, ringed.contains(m.entry().id())));
        }
        dots = out;
    }

    private double scale() { return zoom; }

    private int screenX(double hue) { return plot.x() + (int) Math.round(((hue - centreX) * scale() + 0.5) * (plot.w() - 1)); }

    private int screenY(double lightness) { return plot.y() + (int) Math.round((0.5 - (lightness - centreY) * scale()) * (plot.h() - 1)); }

    /** Keeps the view inside the plot: at no zoom it shows everything, and further in it stops at the edges. */
    private void confine() {
        zoom = Math.clamp(zoom, 1, MAX_ZOOM);
        double half = 0.5 / zoom;
        centreX = Math.clamp(centreX, half, 1 - half);
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
        if (over != null && !panned) {
            Entry e = over.entry;
            // The icon sits up and to the left of the cursor, where the tooltip, which goes to the right, never covers it.
            int ix = Math.max(b.x() + 1, mouseX - 22), iy = Math.max(b.y() + 1, mouseY - 22);
            g.fill(ix - 1, iy - 1, ix + 17, iy + 17, 0xE0000000);
            Widgets.blockIcon(g, e.block(), ix, iy);
            List<Component> tip = new ArrayList<>();
            tip.add(e.properties().isEmpty() ? e.name() : Component.translatable("match.chromastudio.name_with_state", e.name(), e.properties()));
            tip.add(Component.literal(String.format("#%06X", over.rgb)).withColor(DIM & 0xFFFFFF));
            tip.add(tr("click").withColor(DIM & 0xFFFFFF));
            g.setComponentTooltipForNextFrame(font, tip, mouseX, mouseY);
        }
    }

    @Override
    public void renderPinned(GuiGraphicsExtractor g, Bounds b, float opacity) {
        sync();
        draw(g, Minecraft.getInstance().font, opacity, -1, -1);
    }

    /** Draws the plot, and returns the dot nearest the cursor when one is within reach. */
    private Dot draw(GuiGraphicsExtractor g, Font font, float opacity, int mouseX, int mouseY) {
        Bounds p = plot;
        // Fullscreen has no panel of its own, so the controls and the axes get a backing to be read against.
        g.fill(area.x(), area.y(), area.right(), area.bottom(), ARGB.multiplyAlpha(BACKING, opacity));
        g.fill(p.x() - 1, p.y() - 1, p.right() + 1, p.bottom() + 1, ARGB.multiplyAlpha(BORDER, opacity));
        g.fill(p.x(), p.y(), p.right(), p.bottom(), ARGB.multiplyAlpha(WELL, opacity));
        // The axes: the hues along the bottom and the lightness up the side, each as the part of it in view.
        double half = 0.5 / zoom;
        for (int x = 0; x < p.w(); x += 2) {
            double hue = centreX - half + 2 * half * x / Math.max(1, p.w() - 1);
            g.fill(p.x() + x, p.bottom() + 2, Math.min(p.right(), p.x() + x + 2), p.bottom() + AXIS,
                    ARGB.multiplyAlpha(0xFF000000 | Color.ofOklch(new Oklch(0.72, 0.13, hue * 360)).rgb(), opacity));
        }
        for (int y = 0; y < p.h(); y += 2) {
            double lightness = centreY + half - 2 * half * y / Math.max(1, p.h() - 1);
            int grey = (int) Math.round(Math.clamp(lightness, 0, 1) * 255);
            g.fill(p.x() - AXIS, p.y() + y, p.x() - 2, Math.min(p.bottom(), p.y() + y + 2), ARGB.multiplyAlpha(0xFF000000 | grey * 0x010101, opacity));
        }
        if (!BlockIndex.get().ready()) {
            g.centeredText(font, Component.translatable("match.chromastudio.building", Math.round(BlockIndex.get().progress() * 100)),
                    p.x() + p.w() / 2, p.y() + p.h() / 2 - 4, ARGB.multiplyAlpha(DIM, opacity));
            return null;
        }
        if (dots.isEmpty()) {
            g.centeredText(font, Component.translatable("match.chromastudio.no_results"), p.x() + p.w() / 2, p.y() + p.h() / 2 - 4, ARGB.multiplyAlpha(DIM, opacity));
            return null;
        }
        Dot nearest = null;
        int best = REACH * REACH + 1;
        g.enableScissor(p.x(), p.y(), p.right(), p.bottom());
        for (Dot d : dots) {
            int x = screenX(d.hue), y = screenY(d.lightness), s = d.size, x0 = x - s / 2, y0 = y - s / 2;
            if (x0 + s < p.x() || x0 > p.right() || y0 + s < p.y() || y0 > p.bottom()) continue;
            g.fill(x0, y0, x0 + s, y0 + s, ARGB.multiplyAlpha(0xFF000000 | d.rgb, opacity));
            if (d.ringed) g.outline(x0 - 2, y0 - 2, s + 4, s + 4, ARGB.multiplyAlpha(0xFFFFFFFF, opacity));
            int dx = x - mouseX, dy = y - mouseY, far = dx * dx + dy * dy;
            if (far < best && p.contains(mouseX, mouseY)) {
                best = far;
                nearest = d;
            }
        }
        if (nearest != null) {
            int x = screenX(nearest.hue), y = screenY(nearest.lightness), s = nearest.size;
            g.outline(x - s / 2 - 1, y - s / 2 - 1, s + 2, s + 2, 0xFFFFFFFF);
        }
        g.disableScissor();
        return nearest;
    }

    private Dot at(double mx, double my) {
        if (!plot.contains(mx, my)) return null;
        Dot nearest = null;
        double best = REACH * REACH + 1;
        for (Dot d : dots) {
            double dx = screenX(d.hue) - mx, dy = screenY(d.lightness) - my, far = dx * dx + dy * dy;
            if (far < best) {
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
        centreX -= dx / Math.max(1, plot.w() - 1) / zoom;
        centreY += dy / Math.max(1, plot.h() - 1) / zoom;
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
        // The point under the cursor stays under it.
        double fx = (x - plot.x()) / Math.max(1, plot.w() - 1) - 0.5, fy = 0.5 - (y - plot.y()) / Math.max(1, plot.h() - 1);
        double hue = centreX + fx / zoom, lightness = centreY + fy / zoom;
        zoom = Math.clamp(zoom * Math.pow(1.25, v), 1, MAX_ZOOM);
        centreX = hue - fx / zoom;
        centreY = lightness - fy / zoom;
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
