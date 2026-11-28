package dev.chromastudio.client.ui.tabs;

import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.platform.cursor.CursorTypes;
import dev.chromastudio.client.ChromaStudioClient;
import dev.chromastudio.client.ModSettings;
import dev.chromastudio.client.action.PaletteBlocks;
import dev.chromastudio.client.ui.Bounds;
import dev.chromastudio.client.ui.Flow;
import dev.chromastudio.client.ui.Theme;
import dev.chromastudio.client.ui.Widgets;
import dev.chromastudio.core.color.Scale;
import dev.chromastudio.core.color.TextureStats;
import dev.chromastudio.core.export.Arrangement;
import dev.chromastudio.core.export.Layout;
import dev.chromastudio.core.export.LayoutOptions;
import dev.chromastudio.core.palette.Palette;
import dev.chromastudio.core.palette.PaletteEntry;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.BiConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.gui.screens.inventory.tooltip.BelowOrAboveWidgetTooltipPositioner;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipPositioner;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ARGB;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Util;
import net.minecraft.world.level.block.state.BlockState;
import org.joml.Vector2i;

final class DioramaPanel implements AutoCloseable {
    private static final List<Layout> LAYOUTS = List.of(Layout.NOISE_CLUSTERS, Layout.HEIGHT_GRADIENT, Layout.DITHER);
    private static final int TEXELS = 16;
    private static final int NO_TEXTURE = 0xFF2A2E35;
    private static final int INSET = 4, CAPTION = 10, PADDING = 8, SEPARATION = 6;
    private static final int MARK = 0xFFFFFFFF, SHADOW = 0xFF000000;
    private static final long DOUBLE_CLICK_MS = 250;
    private static final double CLICK_SLOP = 3;
    private static final int SWATCH = 10, LEGEND_ROW = 12;
    private static final float YAW = -25, PITCH = 15, MARGIN = 1.08f;
    private static int nextId;

    private static boolean threeD;
    private static Layout layout = Layout.NOISE_CLUSTERS;

    private final List<PaletteEntry> kept = new ArrayList<>();
    private final List<BlockState> states = new ArrayList<>();
    private final List<TextureStats> looks = new ArrayList<>();
    private long seed = System.nanoTime();
    private Arrangement arrangement;
    private DynamicTexture texture;
    private Identifier textureId;
    private boolean painted;
    private int paintedOver;
    private float yaw = YAW, pitch = PITCH;
    private Bounds view, stage, legend;
    private Widgets widgets;
    private final List<AbstractWidget> mine = new ArrayList<>();
    private final int[] same;
    private int[] counts, firsts;
    private final List<Integer> order = new ArrayList<>();
    private boolean orbiting;
    private long stageClick;
    private double moved;

    DioramaPanel(Palette palette) {
        for (PaletteEntry e : palette.entries) {
            BlockState state = PaletteBlocks.state(e);
            if (state == null) continue;
            kept.add(e);
            states.add(state);
            looks.add(PaletteBlocks.stats(e));
        }
        same = new int[kept.size()];
        for (int i = 0; i < same.length; i++) {
            int j = 0;
            while (states.get(j) != states.get(i) || looks.get(j) != looks.get(i)) j++;
            same[i] = j;
        }
        arrange();
    }

    boolean empty() { return kept.isEmpty(); }

    Layout layout() { return layout; }

    long seed() { return seed; }

    private void arrange() {
        arrangement = kept.isEmpty() ? null
                : Arrangement.of(kept.stream().map(PaletteEntry::weight).toList(), LayoutOptions.of(layout, ModSettings.patchSize, false, seed));
        painted = false;
        counts = new int[kept.size()];
        firsts = new int[kept.size()];
        order.clear();
        if (arrangement == null) return;
        int w = arrangement.sizeX();
        for (int i = 0; i < w * arrangement.sizeY(); i++) {
            int entry = block(i % w, i / w);
            if (entry >= 0 && counts[entry]++ == 0) firsts[entry] = i;
        }
        for (int i = 0; i < counts.length; i++) if (counts[i] > 0) order.add(i);
        order.sort((a, b) -> Integer.compare(counts[b], counts[a]));
    }

    private int block(int x, int y) {
        int entry = arrangement.at(x, y, 0);
        return entry < 0 ? entry : same[entry];
    }

    private static Component name(Layout l) { return Component.translatable("option.chromastudio.layoutchoice." + l.name().toLowerCase(Locale.ROOT)); }

    void layout(Widgets widgets, Bounds area, Runnable close, BiConsumer<Layout, Long> export) {
        this.widgets = widgets;
        int y = area.y();
        Component flat = Component.translatable("palettes.chromastudio.diorama.flat"), solid = Component.translatable("palettes.chromastudio.diorama.3d");
        Component reroll = Component.translatable("palettes.chromastudio.diorama.reroll"), exportLabel = Component.translatable("palettes.chromastudio.tool.export");
        Component back = Component.translatable("palettes.chromastudio.panel.close");
        int flatW = Chip.width(flat), viewW = flatW + Chip.width(solid);
        int layoutW = LAYOUTS.stream().mapToInt(l -> Widgets.tw(arrows(l))).max().orElse(60) + PADDING;
        int rerollW = Widgets.tw(reroll) + PADDING, exportW = Widgets.tw(exportLabel) + PADDING, backW = Widgets.tw(back) + PADDING;
        Flow.Spot[] spots = Flow.place(new int[] {viewW, layoutW, rerollW, exportW}, area.x(), area.right() - backW - SEPARATION, PanelTab.GAP);
        mine.clear();
        Component viewTip = Component.translatable("palettes.chromastudio.diorama.view.tip");
        int viewY = y + spots[0].row() * PanelTab.ROW;
        mine.add(widgets.add(new Chip(spots[0].x(), viewY, flat, () -> !threeD, () -> threeD = false)));
        mine.getLast().setTooltip(Tooltip.create(viewTip));
        mine.add(widgets.add(new Chip(spots[0].x() + flatW, viewY, solid, () -> threeD, () -> threeD = true)));
        mine.getLast().setTooltip(Tooltip.create(viewTip));
        mine.add(widgets.cycler(spots[1].x(), y + spots[1].row() * PanelTab.ROW, layoutW, null, LAYOUTS, layout, DioramaPanel::arrows, l -> { layout = l; arrange(); },
                Component.translatable("palettes.chromastudio.diorama.layout.tip", name(LAYOUTS.get(0)), name(LAYOUTS.get(1)), name(LAYOUTS.get(2)))));
        mine.add(widgets.button(spots[2].x(), y + spots[2].row() * PanelTab.ROW, rerollW, reroll, () -> { seed = System.nanoTime(); arrange(); }));
        mine.getLast().setTooltip(Tooltip.create(Component.translatable("palettes.chromastudio.diorama.reroll.tip")));
        mine.add(widgets.button(spots[3].x(), y + spots[3].row() * PanelTab.ROW, exportW, exportLabel, () -> export.accept(layout, seed)));
        mine.getLast().setTooltip(Tooltip.create(Component.translatable("palettes.chromastudio.diorama.export.tip")));
        mine.add(widgets.button(area.right() - backW, y, backW, back, close));
        y += (Flow.rows(spots) - 1) * PanelTab.ROW;
        view = new Bounds(area.x(), y + PanelTab.ROW, area.w(), area.bottom() - y - PanelTab.ROW);
        int legendW = SWATCH + 3 + Widgets.tw("100%");
        stage = new Bounds(view.x() + INSET, view.y() + INSET, view.w() - 3 * INSET - legendW, view.h() - 2 * INSET - CAPTION);
        legend = new Bounds(stage.right() + INSET, stage.y(), legendW, stage.h());
    }

    private static Component arrows(Layout l) { return Component.literal("‹ ").append(name(l)).append(" ›"); }

    void draw(GuiGraphicsExtractor g, int mouseX, int mouseY) {
        if (Minecraft.getInstance().getLastInputType().isMouse() && mine.contains(widgets.getFocused())) widgets.setFocused(null);
        g.fill(view.x(), view.y(), view.right(), view.bottom(), Theme.WELL);
        if (arrangement == null) return;
        int w = arrangement.sizeX(), h = arrangement.sizeY();
        caption(g, w, h);
        if (!painted || paintedOver != Theme.WELL) paint();
        int cell = Math.max(1, Math.min(stage.w() / w, stage.h() / h));
        int dw = w * cell, dh = h * cell, dx = stage.x() + (stage.w() - dw) / 2, dy = stage.y() + (stage.h() - dh) / 2;
        int pointed = legend(g, mouseX, mouseY, dy);
        if (threeD) {
            BlockState[] cells = new BlockState[w * h];
            for (int y = 0; y < h; y++)
                for (int x = 0; x < w; x++) {
                    int entry = arrangement.at(x, y, 0);
                    if (entry >= 0) cells[y * w + x] = states.get(entry);
                }
            float scale = Math.min(stage.w() / spanX(w), stage.h() / spanY(w, h));
            if (stage.contains(mouseX, mouseY)) g.requestCursor(CursorTypes.RESIZE_ALL);
            DioramaPip.submit(stage.x(), stage.y(), stage.w(), stage.h(), scale, w, h, cells, yaw, pitch, ModSettings.dioramaPerspective);
            return;
        }
        g.fill(dx - 1, dy - 1, dx + dw + 1, dy + dh + 1, Theme.BORDER);
        g.blit(RenderPipelines.GUI_TEXTURED, textureId, dx, dy, 0f, 0f, dw, dh, w * TEXELS, h * TEXELS, w * TEXELS, h * TEXELS);
        ScreenRectangle anchor = null;
        if (pointed < 0 && mouseX >= dx && mouseX < dx + dw && mouseY >= dy && mouseY < dy + dh) {
            int cx = (mouseX - dx) / cell, cy = (mouseY - dy) / cell;
            pointed = block(cx, h - 1 - cy);
            anchor = new ScreenRectangle(dx + cx * cell, dy + cy * cell, cell, cell);
        }
        if (pointed < 0) return;
        for (int y = 0; y < h; y++)
            for (int x = 0; x < w; x++) {
                int px = dx + x * cell, py = dy + y * cell;
                if (block(x, h - 1 - y) == pointed) edges(g, x, y, pointed, px, py, cell);
                else g.fill(px, py, px + cell, py + cell, Theme.SCRIM);
            }
        if (anchor != null) tip(g, pointed, new BelowOrAboveWidgetTooltipPositioner(anchor), mouseX, mouseY);
    }

    private int legend(GuiGraphicsExtractor g, int mouseX, int mouseY, int top) {
        int rows = (legend.bottom() - top) / LEGEND_ROW, shown = order.size() > rows ? rows - 1 : order.size(), pointed = -1;
        int w = arrangement.sizeX(), h = arrangement.sizeY();
        for (int i = 0; i < shown; i++) {
            int entry = order.get(i), y = top + i * LEGEND_ROW, cell = firsts[entry];
            boolean over = mouseX >= legend.x() && mouseX < legend.right() && mouseY >= y && mouseY < y + LEGEND_ROW;
            if (over) g.fill(legend.x() - 2, y - 1, legend.right() + 1, y + SWATCH + 1, Theme.HOVER);
            g.blit(RenderPipelines.GUI_TEXTURED, textureId, legend.x(), y, (cell % w) * TEXELS, (h - 1 - cell / w) * TEXELS, SWATCH, SWATCH, TEXELS, TEXELS, w * TEXELS, h * TEXELS);
            if (over) {
                g.outline(legend.x() - 1, y - 1, SWATCH + 2, SWATCH + 2, MARK);
                pointed = entry;
                int rowY = y;
                tip(g, entry, (_, screenH, _, _, tipW, tipH) -> new Vector2i(Math.max(4, legend.x() - tipW - 6), Math.clamp(rowY - 4, 4, screenH - tipH - 4)), mouseX, mouseY);
            }
            g.text(PanelTab.font(), percent(counts[entry], w * h) + "%", legend.x() + SWATCH + 3, y + 2, over ? Theme.TEXT : Theme.SOFT, false);
        }
        if (shown < order.size())
            g.text(PanelTab.font(), "+" + (order.size() - shown), legend.x(), top + shown * LEGEND_ROW + 2, Theme.DIM, false);
        return pointed;
    }

    private static int percent(int count, int total) { return Math.max(1, Math.round(100f * count / total)); }

    private void tip(GuiGraphicsExtractor g, int entry, ClientTooltipPositioner place, int mouseX, int mouseY) {
        int total = arrangement.sizeX() * arrangement.sizeY();
        List<FormattedCharSequence> lines = List.of(PaletteBlocks.name(kept.get(entry)).getVisualOrderText(),
                Component.translatable("palettes.chromastudio.diorama.share", percent(counts[entry], total), counts[entry]).withColor(Theme.TIP_DIM).getVisualOrderText());
        g.setTooltipForNextFrame(PanelTab.font(), lines, place, mouseX, mouseY, false);
    }

    private void edges(GuiGraphicsExtractor g, int x, int y, int entry, int px, int py, int cell) {
        if (!same(x, y - 1, entry)) edge(g, px, py, cell, 1, 0, 1);
        if (!same(x, y + 1, entry)) edge(g, px, py + cell - 1, cell, 1, 0, -1);
        if (!same(x - 1, y, entry)) edge(g, px, py, 1, cell, 1, 0);
        if (!same(x + 1, y, entry)) edge(g, px + cell - 1, py, 1, cell, -1, 0);
    }

    private static void edge(GuiGraphicsExtractor g, int x, int y, int w, int h, int inX, int inY) {
        g.fill(x, y, x + w, y + h, MARK);
        g.fill(x + inX, y + inY, x + inX + w, y + inY + h, SHADOW);
    }

    private boolean same(int x, int y, int entry) {
        int w = arrangement.sizeX(), h = arrangement.sizeY();
        return x >= 0 && y >= 0 && x < w && y < h && block(x, h - 1 - y) == entry;
    }

    private static float spanX(int w) {
        double yaw = Math.toRadians(YAW);
        return (float) (w * Math.cos(yaw) + Math.sin(-yaw)) * MARGIN;
    }

    private static float spanY(int w, int h) {
        double yaw = Math.toRadians(YAW), pitch = Math.toRadians(PITCH);
        return (float) (h * Math.cos(pitch) + (w * Math.sin(-yaw) + Math.cos(yaw)) * Math.sin(pitch)) * MARGIN;
    }

    private void caption(GuiGraphicsExtractor g, int w, int h) {
        int y = view.bottom() - CAPTION - 1, hintX = view.right() - INSET;
        if (threeD) {
            int room = view.w() - 2 * INSET;
            Component full = Component.translatable("palettes.chromastudio.diorama.orbit");
            String hint = PanelTab.fit((Widgets.tw(full) <= room ? full : Component.translatable("palettes.chromastudio.diorama.orbit.short")).getString(), room);
            hintX -= Widgets.tw(hint);
            g.text(PanelTab.font(), hint, hintX, y, Theme.SOFT, false);
        }
        Component size = Component.translatable("palettes.chromastudio.diorama.size", w, h);
        if (view.x() + INSET + Widgets.tw(size) + 8 <= hintX) g.text(PanelTab.font(), size, view.x() + INSET, y, Theme.DIM, false);
    }

    private void paint() {
        int w = arrangement.sizeX(), h = arrangement.sizeY();
        if (texture == null || texture.getPixels().getWidth() != w * TEXELS || texture.getPixels().getHeight() != h * TEXELS) {
            close();
            textureId = Identifier.fromNamespaceAndPath(ChromaStudioClient.MOD_ID, "diorama_" + nextId++);
            texture = new DynamicTexture(textureId::toString, new NativeImage(w * TEXELS, h * TEXELS, false));
            Minecraft.getInstance().getTextureManager().register(textureId, texture);
        }
        NativeImage image = texture.getPixels();
        int[][] faces = new int[looks.size()][];
        for (int y = 0; y < h; y++)
            for (int x = 0; x < w; x++) {
                int entry = arrangement.at(x, y, 0);
                int[] face = null;
                if (entry >= 0 && looks.get(entry) != null) {
                    if (faces[entry] == null) faces[entry] = looks.get(entry).pixels(Scale.T16);
                    face = faces[entry];
                }
                int ox = x * TEXELS, oy = (h - 1 - y) * TEXELS;
                for (int ty = 0; ty < TEXELS; ty++)
                    for (int tx = 0; tx < TEXELS; tx++) {
                        int under = entry >= 0 ? NO_TEXTURE : Theme.WELL;
                        image.setPixel(ox + tx, oy + ty, face == null ? under : over(face[ty * TEXELS + tx], Theme.WELL));
                    }
            }
        texture.upload();
        painted = true;
        paintedOver = Theme.WELL;
    }

    private static int over(int argb, int background) {
        int a = ARGB.alpha(argb);
        return a == 255 ? argb : ARGB.srgbLerp(a / 255f, background, ARGB.opaque(argb));
    }

    boolean clicked(MouseButtonEvent e) {
        orbiting = threeD && e.button() == InputConstants.MOUSE_BUTTON_LEFT && stage.contains(e.x(), e.y());
        long now = Util.getMillis();
        moved = 0;
        if (orbiting && now - stageClick < DOUBLE_CLICK_MS) {
            yaw = YAW;
            pitch = PITCH;
        }
        stageClick = orbiting ? now : 0;
        return orbiting;
    }

    boolean dragged(double dx, double dy) {
        if (!orbiting) return false;
        moved += Math.abs(dx) + Math.abs(dy);
        if (moved > CLICK_SLOP) stageClick = 0;
        yaw += (float) dx;
        pitch = Math.clamp(pitch + (float) dy, -80, 80);
        return true;
    }

    boolean released() {
        boolean was = orbiting;
        orbiting = false;
        return was;
    }

    @Override
    public void close() {
        if (texture == null) return;
        Minecraft.getInstance().getTextureManager().release(textureId);
        texture = null;
        painted = false;
    }
}
