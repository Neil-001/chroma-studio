package dev.chromastudio.client.ui.tabs;

import dev.chromastudio.client.action.PaletteBlocks;
import dev.chromastudio.client.ui.Bounds;
import dev.chromastudio.client.ui.ChromaScreen;
import dev.chromastudio.client.ui.Handoff;
import dev.chromastudio.client.ui.Theme;
import dev.chromastudio.client.ui.Widgets;
import dev.chromastudio.core.color.Badge;
import dev.chromastudio.core.color.Color;
import dev.chromastudio.core.color.Oklch;
import dev.chromastudio.core.color.Scale;
import dev.chromastudio.core.color.TextureStats;
import dev.chromastudio.core.palette.Analyzer;
import dev.chromastudio.core.palette.Palette;
import dev.chromastudio.core.palette.PaletteEntry;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;

final class AnalysePanel {
    private static final int LINE_H = 10, BAR_H = 8, ICON_BAR_H = 4, SWATCH = 16, ARROW_W = 7, PAD = 3;

    private final List<PaletteEntry> entries;
    private record Block(PaletteEntry entry, Color colour, int analysed) {}
    private final List<Block> blocks = new ArrayList<>();
    private final List<Analyzer.Finding> findings;
    private final int unknown;
    private Widgets.ScrollList list;
    private Bounds area;
    private int fixW, headingW, stripH;
    private Analyzer.Finding marked;
    private boolean icons;
    private int rowH;
    private final List<Button> fixes = new ArrayList<>();

    AnalysePanel(Palette palette) {
        entries = List.copyOf(palette.entries);
        List<Oklch> lch = new ArrayList<>();
        List<Badge> badges = new ArrayList<>();
        Scale scale = PaletteBlocks.scale();
        int missing = 0;
        for (PaletteEntry e : entries) {
            TextureStats stats = PaletteBlocks.stats(e);
            if (stats == null) {
                missing++;
                continue;
            }
            blocks.add(new Block(e, stats.mean(scale), lch.size()));
            lch.add(stats.mean(scale).oklch());
            badges.add(stats.badge());
        }
        unknown = missing;
        findings = Analyzer.analyse(lch, badges);
        blocks.sort((a, b) -> Double.compare(a.colour.oklch().l(), b.colour.oklch().l()));
    }

    void layout(Widgets widgets, Bounds area, Runnable close) {
        this.area = area;
        Component explore = Component.translatable("palettes.chromastudio.analyse.explore"), back = Component.translatable("palettes.chromastudio.panel.close");
        int backW = Widgets.buttonWidth(back), exploreW = Widgets.buttonWidth(explore);
        widgets.button(area.right() - backW, area.y(), back, close);
        widgets.button(area.right() - backW - PanelTab.GAP - exploreW, area.y(), explore, () -> {
            Handoff.exploreHighlight = entries;
            ChromaScreen screen = ChromaScreen.current();
            if (screen != null) screen.select("explore");
        });
        headingW = area.w() - backW - exploreW - 2 * PanelTab.GAP;
        Component fix = Component.translatable("palettes.chromastudio.analyse.fix");
        fixW = Widgets.buttonWidth(fix);
        fixes.clear();
        for (Analyzer.Finding f : findings) {
            fixes.add(f.suggestedTarget() == null ? null : Button.builder(fix, _ -> {
                Handoff.matchTarget = Color.ofOklch(f.suggestedTarget());
                Handoff.matchFilter = f.suggestedFilter();
                ChromaScreen screen = ChromaScreen.current();
                if (screen != null) screen.select("match");
            }).bounds(0, 0, fixW, Widgets.H).build());
        }
        icons = !blocks.isEmpty() && area.w() / blocks.size() >= ToolButton.ICON + 1;
        stripH = icons ? ToolButton.ICON + 1 + ICON_BAR_H : BAR_H;
        int top = area.y() + PanelTab.ROW + stripH + PanelTab.GAP;
        int lines = 1, w = area.w() - Widgets.BAR_W - 2;
        for (Analyzer.Finding f : findings) lines = Math.max(lines, wrapped(f, w).size() + suggestion(f, w).size());
        rowH = PAD + Widgets.H + 2 + lines * LINE_H + PAD + 2;
        list = new Widgets.ScrollList(new Bounds(area.x(), top, area.w(), area.bottom() - top), rowH, findings::size);
    }

    private static List<FormattedCharSequence> wrapped(Analyzer.Finding f, int w) {
        return PanelTab.font().split(Component.translatable(f.key()), w - 8 - PAD);
    }

    private List<FormattedCharSequence> suggestion(Analyzer.Finding f, int w) {
        Block from = from(f);
        if (from == null) return List.of();
        boolean lighter = f.suggestedTarget().l() > from.colour.oklch().l();
        Component way = Component.translatable(lighter ? "palettes.chromastudio.analyse.lighter" : "palettes.chromastudio.analyse.darker", PaletteBlocks.name(from.entry));
        return PanelTab.font().split(way, w - 8 - PAD);
    }

    private Block from(Analyzer.Finding f) {
        if (f.suggestedTarget() == null || f.blocks().size() != 1) return null;
        for (Block b : blocks) if (b.analysed == f.blocks().getFirst()) return b;
        return null;
    }

    void draw(GuiGraphicsExtractor g, int mouseX, int mouseY) {
        Component heading = Component.translatable(unknown > 0 ? "palettes.chromastudio.analyse.heading_unknown" : "palettes.chromastudio.analyse.heading", findings.size(), unknown);
        String fitted = MatchTab.ellipsis(PanelTab.font(), heading.getString(), headingW);
        g.text(PanelTab.font(), fitted, area.x(), area.y() + 4, Theme.TEXT, false);
        if (!fitted.equals(heading.getString()) && mouseX >= area.x() && mouseX < area.x() + headingW && mouseY >= area.y() && mouseY < area.y() + Widgets.H) {
            g.setTooltipForNextFrame(PanelTab.font(), heading, mouseX, mouseY);
        }
        for (Button button : fixes) if (button != null) button.setPosition(-fixW, -Widgets.H);
        g.fill(list.area.x(), list.area.y(), list.area.right(), list.area.bottom(), Theme.WELL);
        marked = null;
        if (findings.isEmpty()) Widgets.centred(g, Component.translatable("palettes.chromastudio.analyse.none"), area.x() + area.w() / 2, list.area.y() + 12, Theme.GOOD);
        else list.render(g, mouseX, mouseY, (gg, i, x, y, w, h, hovered) -> {
            Analyzer.Finding f = findings.get(i);
            if (hovered) marked = f;
            gg.fill(x, y, x + w, y + h - 2, hovered ? Theme.HOVER : Theme.PANEL);
            gg.fill(x, y, x + 3, y + h - 2, f.severity() == Analyzer.Severity.WARNING ? Theme.FAIR : Theme.INFO);
            int below = y + h - 2 + 14, rowY = y + PAD, right = x + w - PAD;
            Block from = from(f);
            if (f.suggestedTarget() != null) {
                Button button = fixes.get(i);
                button.setPosition(right - fixW, rowY);
                button.extractRenderState(gg, mouseX, mouseY, 0);
                if (button.isHovered()) gg.setTooltipForNextFrame(PanelTab.font(), Component.translatable("palettes.chromastudio.analyse.fix.tip"), mouseX, below);
                Color target = Color.ofOklch(f.suggestedTarget());
                int sx = button.getX() - 4 - SWATCH;
                gg.fill(sx - 1, rowY - 1, sx + SWATCH + 1, rowY + SWATCH + 1, Theme.SOFT);
                gg.fill(sx, rowY, sx + SWATCH, rowY + SWATCH, 0xFF000000 | target.rgb());
                if (inside(mouseX, mouseY, sx, rowY, SWATCH)) {
                    gg.setTooltipForNextFrame(PanelTab.font(), Component.translatable("palettes.chromastudio.analyse.target", target.hex()), mouseX, below);
                }
                right = sx - 4;
                if (from != null) {
                    int ax = right - ARROW_W, cy = rowY + Widgets.H / 2;
                    gg.fill(ax, cy, ax + ARROW_W - 1, cy + 1, Theme.DIM);
                    gg.fill(ax + ARROW_W - 3, cy - 2, ax + ARROW_W - 2, cy + 3, Theme.DIM);
                    gg.fill(ax + ARROW_W - 2, cy - 1, ax + ARROW_W - 1, cy + 2, Theme.DIM);
                    int fx = ax - 3 - ToolButton.ICON;
                    Widgets.blockIcon(gg, PaletteBlocks.icon(from.entry), fx, rowY);
                    if (inside(mouseX, mouseY, fx, rowY, ToolButton.ICON)) gg.setTooltipForNextFrame(PanelTab.font(), PaletteBlocks.name(from.entry), mouseX, below);
                    right = fx - 4;
                }
            }
            String title = MatchTab.ellipsis(PanelTab.font(), Component.translatable(f.key() + ".title").getString(), right - x - 8);
            gg.text(PanelTab.font(), title, x + 8, rowY + 4, Theme.STRONG, false);
            List<FormattedCharSequence> lines = wrapped(f, w);
            for (int k = 0; k < lines.size(); k++) gg.text(PanelTab.font(), lines.get(k), x + 8, rowY + Widgets.H + 2 + k * LINE_H, Theme.SOFT, false);
            List<FormattedCharSequence> way = suggestion(f, w);
            for (int k = 0; k < way.size(); k++) gg.text(PanelTab.font(), way.get(k), x + 8, rowY + Widgets.H + 2 + (lines.size() + k) * LINE_H, Theme.HIGHLIGHT, false);
        });
        drawStrip(g, mouseX, mouseY);
    }

    private static boolean inside(int mouseX, int mouseY, int x, int y, int size) {
        return mouseX >= x - 1 && mouseX < x + size + 1 && mouseY >= y - 1 && mouseY < y + size + 1;
    }

    private void drawStrip(GuiGraphicsExtractor g, int mouseX, int mouseY) {
        int stripY = area.y() + PanelTab.ROW;
        for (int i = 0; i < blocks.size(); i++) {
            int x0 = area.x() + area.w() * i / blocks.size(), x1 = area.x() + area.w() * (i + 1) / blocks.size();
            int gap = x1 - x0 > 3 && i < blocks.size() - 1 ? 1 : 0;
            int barY = stripY + stripH - (icons ? ICON_BAR_H : BAR_H);
            if (icons) Widgets.blockIcon(g, PaletteBlocks.icon(blocks.get(i).entry), (x0 + x1 - ToolButton.ICON) / 2, stripY);
            g.fill(x0, barY, x1 - gap, stripY + stripH, 0xFF000000 | blocks.get(i).colour.rgb());
            if (marked != null && !marked.blocks().isEmpty()) {
                if (marked.blocks().contains(blocks.get(i).analysed)) g.fill(x0, stripY + stripH, x1 - gap, stripY + stripH + 2, Theme.STRONG);
                else g.fill(x0, stripY, x1, stripY + stripH, Theme.sink(0x80));
            }
            if (mouseX >= x0 && mouseX < x1 && mouseY >= stripY && mouseY < stripY + stripH) {
                g.fill(x0, stripY + stripH, x1 - gap, stripY + stripH + 1, Theme.STRONG);
                g.setTooltipForNextFrame(PanelTab.font(), PaletteBlocks.name(blocks.get(i).entry), mouseX, mouseY);
            }
        }
    }

    boolean clicked(MouseButtonEvent e) {
        if (list.mouseClicked(e)) return true;
        if (!list.area.contains(e.x(), e.y())) return false;
        for (Button button : fixes) if (button != null && button.mouseClicked(e, false)) return true;
        return false;
    }

    boolean dragged(MouseButtonEvent e) { return list.mouseDragged(e); }

    boolean released() { return list.mouseReleased(); }

    boolean scrolled(double x, double y, double v) { return list.mouseScrolled(x, y, v); }
}
