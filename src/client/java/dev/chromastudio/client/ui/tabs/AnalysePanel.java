package dev.chromastudio.client.ui.tabs;

import com.mojang.blaze3d.platform.InputConstants;
import dev.chromastudio.client.action.PaletteBlocks;
import dev.chromastudio.client.ui.Bounds;
import dev.chromastudio.client.ui.ChromaScreen;
import dev.chromastudio.client.ui.Handoff;
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
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;

/**
 * What the analysis finds in a palette, in place of its entries: each finding with its severity and, where one
 * colour would mend it, a Fix button that opens Match on that colour.
 */
final class AnalysePanel {
    private static final int ROW_H = 31, STRIP_H = 8;

    private final List<PaletteEntry> entries;
    private final List<Color> colours = new ArrayList<>();
    private final List<Analyzer.Finding> findings;
    private final int unknown;
    private Widgets.ScrollList list;
    private Bounds area;
    private int fixW;
    /** The Fix buttons as the last frame drew them, which is where a click finds them. */
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
            colours.add(stats.mean(scale));
            lch.add(stats.mean(scale).oklch());
            badges.add(stats.badge());
        }
        unknown = missing;
        findings = Analyzer.analyse(lch, badges);
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
        }).setTooltip(Tooltip.create(Component.translatable("palettes.chromastudio.analyse.explore.tip")));
        fixW = Widgets.buttonWidth(Component.translatable("palettes.chromastudio.analyse.fix"));
        int top = area.y() + PanelTab.ROW + STRIP_H + PanelTab.GAP;
        list = new Widgets.ScrollList(new Bounds(area.x(), top, area.w(), area.bottom() - top), ROW_H, findings::size);
    }

    private Bounds fix(int x, int y, int w) { return new Bounds(x + w - fixW - 2, y + (ROW_H - Widgets.H) / 2, fixW, Widgets.H); }

    void draw(GuiGraphicsExtractor g, int mouseX, int mouseY) {
        Component heading = Component.translatable(unknown > 0 ? "palettes.chromastudio.analyse.heading_unknown" : "palettes.chromastudio.analyse.heading", findings.size(), unknown);
        g.text(PanelTab.font(), heading, area.x(), area.y() + 4, PanelTab.DIM, false);
        // The colours the findings are about, darkest first, so a gap or a narrow range shows at a glance.
        List<Color> sorted = new ArrayList<>(colours);
        sorted.sort((a, b) -> Double.compare(a.oklch().l(), b.oklch().l()));
        int stripY = area.y() + PanelTab.ROW;
        for (int i = 0; i < sorted.size(); i++) {
            int x0 = area.x() + area.w() * i / sorted.size(), x1 = area.x() + area.w() * (i + 1) / sorted.size();
            g.fill(x0, stripY, x1, stripY + STRIP_H, 0xFF000000 | sorted.get(i).rgb());
        }
        fixes.clear();
        g.fill(list.area.x(), list.area.y(), list.area.right(), list.area.bottom(), PanelTab.WELL);
        if (findings.isEmpty()) {
            g.centeredText(PanelTab.font(), Component.translatable("palettes.chromastudio.analyse.none"), area.x() + area.w() / 2, list.area.y() + 12, PanelTab.GOOD);
            return;
        }
        list.render(g, mouseX, mouseY, (gg, i, x, y, w, h, hovered) -> {
            Analyzer.Finding f = findings.get(i);
            if (hovered) gg.fill(x, y, x + w, y + h - 1, PanelTab.HOVER);
            boolean warning = f.severity() == Analyzer.Severity.WARNING;
            gg.fill(x + 2, y + 3, x + 5, y + h - 4, warning ? PanelTab.FAIR : 0xFF6E8CB5);
            boolean fixable = f.suggestedTarget() != null;
            int textW = w - 10 - (fixable ? fixW + 6 + 14 : 0);
            List<FormattedCharSequence> lines = PanelTab.font().split(Component.translatable(f.key()), textW);
            int lineY = y + (ROW_H - 1 - Math.min(3, lines.size()) * 10) / 2 + 1;
            for (int k = 0; k < Math.min(3, lines.size()); k++) gg.text(PanelTab.font(), lines.get(k), x + 8, lineY + k * 10, PanelTab.TEXT, false);
            if (lines.size() > 3 && hovered) gg.setTooltipForNextFrame(PanelTab.font(), lines, mouseX, mouseY);
            if (!fixable) return;
            Bounds b = fix(x, y, w);
            fixes.add(new Fix(b, f));
            Widgets.swatch(gg, b.x() - 14, b.y() + 2, 12, 12, Color.ofOklch(f.suggestedTarget()).rgb());
            boolean over = b.contains(mouseX, mouseY);
            gg.fill(b.x(), b.y(), b.right(), b.bottom(), over ? 0xFF55606E : 0xFF3A424D);
            gg.centeredText(PanelTab.font(), Component.translatable("palettes.chromastudio.analyse.fix"), b.x() + b.w() / 2, b.y() + 4, PanelTab.TEXT);
            if (over) gg.setTooltipForNextFrame(PanelTab.font(), Component.translatable("palettes.chromastudio.analyse.fix.tip"), mouseX, mouseY);
        });
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
