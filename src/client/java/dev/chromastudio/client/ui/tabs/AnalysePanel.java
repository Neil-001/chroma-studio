package dev.chromastudio.client.ui.tabs;

import com.mojang.blaze3d.platform.InputConstants;
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
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;

final class AnalysePanel {
    private static final int MIN_ROW_H = 31, LINE_H = 10, BAR_H = 8, ICON_BAR_H = 4, SWATCH = 16;

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
    private int rowH = MIN_ROW_H;
    private record Fix(Bounds button, Analyzer.Finding finding) {}
    private final List<Fix> fixes = new ArrayList<>();

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
        fixW = Widgets.buttonWidth(Component.translatable("palettes.chromastudio.analyse.fix"));
        icons = !blocks.isEmpty() && area.w() / blocks.size() >= ToolButton.ICON + 1;
        stripH = icons ? ToolButton.ICON + 1 + ICON_BAR_H : BAR_H;
        int top = area.y() + PanelTab.ROW + stripH + PanelTab.GAP;
        int lines = 1, w = area.w() - Widgets.BAR_W - 2;
        for (Analyzer.Finding f : findings) lines = Math.max(lines, wrapped(f, w).size());
        rowH = Math.max(MIN_ROW_H, LINE_H + 1 + lines * LINE_H + 10);
        list = new Widgets.ScrollList(new Bounds(area.x(), top, area.w(), area.bottom() - top), rowH, findings::size);
    }

    private Bounds fix(int x, int y, int w) { return new Bounds(x + w - fixW - 3, y + (rowH - 2 - Widgets.H) / 2, fixW, Widgets.H); }

    private int textW(Analyzer.Finding f, int w) { return w - 12 - (f.suggestedTarget() != null ? fixW + 3 + SWATCH + 10 : 0); }

    private List<FormattedCharSequence> wrapped(Analyzer.Finding f, int w) {
        return PanelTab.font().split(Component.translatable(f.key()), textW(f, w));
    }

    void draw(GuiGraphicsExtractor g, int mouseX, int mouseY) {
        Component heading = Component.translatable(unknown > 0 ? "palettes.chromastudio.analyse.heading_unknown" : "palettes.chromastudio.analyse.heading", findings.size(), unknown);
        String fitted = MatchTab.ellipsis(PanelTab.font(), heading.getString(), headingW);
        g.text(PanelTab.font(), fitted, area.x(), area.y() + 4, Theme.TEXT, false);
        if (!fitted.equals(heading.getString()) && mouseX >= area.x() && mouseX < area.x() + headingW && mouseY >= area.y() && mouseY < area.y() + Widgets.H) {
            g.setTooltipForNextFrame(PanelTab.font(), heading, mouseX, mouseY);
        }
        fixes.clear();
        g.fill(list.area.x(), list.area.y(), list.area.right(), list.area.bottom(), Theme.WELL);
        marked = null;
        if (findings.isEmpty()) Widgets.centred(g, Component.translatable("palettes.chromastudio.analyse.none"), area.x() + area.w() / 2, list.area.y() + 12, Theme.GOOD);
        else list.render(g, mouseX, mouseY, (gg, i, x, y, w, h, hovered) -> {
            Analyzer.Finding f = findings.get(i);
            if (hovered) marked = f;
            gg.fill(x, y, x + w, y + h - 2, hovered ? Theme.HOVER : Theme.PANEL);
            int below = y + h - 2 + 14;
            boolean warning = f.severity() == Analyzer.Severity.WARNING;
            gg.fill(x, y, x + 3, y + h - 2, warning ? Theme.FAIR : Theme.INFO);
            List<FormattedCharSequence> lines = wrapped(f, w);
            int textY = y + (h - 2 - (lines.size() + 1) * LINE_H - 1) / 2 + 1;
            String title = MatchTab.ellipsis(PanelTab.font(), Component.translatable(f.key() + ".title").getString(), textW(f, w));
            gg.text(PanelTab.font(), title, x + 8, textY, Theme.STRONG, false);
            for (int k = 0; k < lines.size(); k++) gg.text(PanelTab.font(), lines.get(k), x + 8, textY + LINE_H + 1 + k * LINE_H, Theme.SOFT, false);
            if (f.suggestedTarget() == null) return;
            Bounds b = fix(x, y, w);
            fixes.add(new Fix(b, f));
            Color target = Color.ofOklch(f.suggestedTarget());
            int sx = b.x() - SWATCH - 4, sy = b.y();
            gg.fill(sx - 1, sy - 1, sx + SWATCH + 1, sy + SWATCH + 1, Theme.BORDER);
            Widgets.swatch(gg, sx, sy, SWATCH, SWATCH, target.rgb());
            if (mouseX >= sx - 1 && mouseX < sx + SWATCH + 1 && mouseY >= sy - 1 && mouseY < sy + SWATCH + 1) {
                gg.setTooltipForNextFrame(PanelTab.font(), Component.translatable("palettes.chromastudio.analyse.target", target.hex()), mouseX, below);
            }
            boolean over = b.contains(mouseX, mouseY);
            gg.fill(b.x(), b.y(), b.right(), b.bottom(), over ? Theme.CONTROL_HOVER : Theme.CONTROL);
            Widgets.centred(gg, Component.translatable("palettes.chromastudio.analyse.fix"), b.x() + b.w() / 2, b.y() + 4, Theme.TEXT);
            if (over) gg.setTooltipForNextFrame(PanelTab.font(), Component.translatable("palettes.chromastudio.analyse.fix.tip"), mouseX, below);
        });
        drawStrip(g, mouseX, mouseY);
    }

    private void drawStrip(GuiGraphicsExtractor g, int mouseX, int mouseY) {
        int stripY = area.y() + PanelTab.ROW;
        for (int i = 0; i < blocks.size(); i++) {
            int x0 = area.x() + area.w() * i / blocks.size(), x1 = area.x() + area.w() * (i + 1) / blocks.size();
            int gap = x1 - x0 > 3 && i < blocks.size() - 1 ? 1 : 0;
            int barY = stripY + stripH - (icons ? ICON_BAR_H : BAR_H);
            if (icons) Widgets.blockIcon(g, PaletteBlocks.icon(blocks.get(i).entry), (x0 + x1 - ToolButton.ICON) / 2, stripY);
            g.fill(x0, barY, x1 - gap, stripY + stripH, 0xFF000000 | blocks.get(i).colour.rgb());
            if (marked != null && marked.blocks().contains(blocks.get(i).analysed)) {
                g.fill(x0, stripY + stripH, x1 - gap, stripY + stripH + 2, Theme.STRONG);
            }
            if (mouseX >= x0 && mouseX < x1 && mouseY >= stripY && mouseY < stripY + stripH) {
                g.fill(x0, stripY + stripH, x1 - gap, stripY + stripH + 1, Theme.STRONG);
                g.setTooltipForNextFrame(PanelTab.font(), PaletteBlocks.name(blocks.get(i).entry), mouseX, mouseY);
            }
        }
    }

    boolean clicked(MouseButtonEvent e) {
        if (list.mouseClicked(e)) return true;
        if (e.button() != InputConstants.MOUSE_BUTTON_LEFT || !list.area.contains(e.x(), e.y())) return false;
        for (Fix fix : fixes) {
            if (!fix.button.contains(e.x(), e.y())) continue;
            Handoff.matchTarget = Color.ofOklch(fix.finding.suggestedTarget());
            Handoff.matchFilter = fix.finding.suggestedFilter();
            ChromaScreen screen = ChromaScreen.current();
            if (screen != null) screen.select("match");
            return true;
        }
        return false;
    }

    boolean dragged(MouseButtonEvent e) { return list.mouseDragged(e); }

    boolean released() { return list.mouseReleased(); }

    boolean scrolled(double x, double y, double v) { return list.mouseScrolled(x, y, v); }
}
