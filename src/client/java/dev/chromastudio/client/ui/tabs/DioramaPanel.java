package dev.chromastudio.client.ui.tabs;

import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.blaze3d.platform.NativeImage;
import dev.chromastudio.client.ChromaStudioClient;
import dev.chromastudio.client.ModSettings;
import dev.chromastudio.client.action.PaletteBlocks;
import dev.chromastudio.client.ui.Bounds;
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
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ARGB;
import net.minecraft.world.level.block.state.BlockState;

/**
 * A wall built from the palette, in place of its entries: flat, as tiled face textures, or in 3D with the game's
 * block models, turned by dragging. It is laid out by {@link Arrangement} with the export's own sizes, so exporting
 * from here writes the wall that shows.
 */
final class DioramaPanel implements AutoCloseable {
    private static final List<Layout> LAYOUTS = List.of(Layout.NOISE_CLUSTERS, Layout.HEIGHT_GRADIENT, Layout.DITHER);
    private static final int TEXELS = 16;
    /** A block the index has no texture for, such as one from a mod that's gone. */
    private static final int NO_TEXTURE = 0xFF2A2E35;
    private static int nextId;

    /** The choices outlive the panel, so opening it again shows what was last looked at. */
    private static boolean threeD;
    private static Layout layout = Layout.NOISE_CLUSTERS;

    private final List<PaletteEntry> kept = new ArrayList<>();
    private final List<BlockState> states = new ArrayList<>();
    /** Each kept entry's face, or null for a block the index has no texture for. */
    private final List<TextureStats> looks = new ArrayList<>();
    private long seed = System.nanoTime();
    private Arrangement arrangement;
    private DynamicTexture texture;
    private Identifier textureId;
    private boolean painted;
    private float yaw = -25, pitch = 15;
    private Bounds area, view;
    private boolean orbiting;

    /** Only the entries this game has blocks for are arranged, as the export does. */
    DioramaPanel(Palette palette) {
        for (PaletteEntry e : palette.entries) {
            BlockState state = PaletteBlocks.state(e);
            if (state == null) continue;
            kept.add(e);
            states.add(state);
            looks.add(PaletteBlocks.stats(e));
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
    }

    private static Component name(Layout l) { return Component.translatable("option.chromastudio.layoutchoice." + l.name().toLowerCase(Locale.ROOT)); }

    /** {@code export} opens the export dialog on a layout and seed. */
    void layout(Widgets widgets, Bounds area, Runnable close, BiConsumer<Layout, Long> export) {
        this.area = area;
        int x = area.x(), y = area.y();
        Component flat = Component.translatable("palettes.chromastudio.diorama.flat"), solid = Component.translatable("palettes.chromastudio.diorama.3d");
        int viewW = Math.max(Widgets.tw(flat), Widgets.tw(solid)) + 10;
        widgets.cycler(x, y, viewW, null, List.of(false, true), threeD, v -> v ? solid : flat, v -> threeD = v,
                Component.translatable("palettes.chromastudio.diorama.view.tip"));
        x += viewW + PanelTab.GAP;
        int layoutW = LAYOUTS.stream().mapToInt(l -> Widgets.tw(name(l))).max().orElse(60) + 10;
        widgets.cycler(x, y, layoutW, null, LAYOUTS, layout, DioramaPanel::name, l -> { layout = l; arrange(); });
        x += layoutW + PanelTab.GAP;
        Component reroll = Component.translatable("palettes.chromastudio.diorama.reroll");
        widgets.button(x, y, reroll, () -> { seed = System.nanoTime(); arrange(); })
                .setTooltip(Tooltip.create(Component.translatable("palettes.chromastudio.diorama.reroll.tip")));
        x += Widgets.buttonWidth(reroll) + PanelTab.GAP;
        widgets.button(x, y, Component.translatable("palettes.chromastudio.tool.export"), () -> export.accept(layout, seed))
                .setTooltip(Tooltip.create(Component.translatable("palettes.chromastudio.diorama.export.tip")));
        widgets.add(new IconButton(area.right() - Widgets.H, y, Widgets.H, Widgets.H, IconButton.Glyph.CLOSE, Component.translatable("palettes.chromastudio.panel.close"), close));
        view = new Bounds(area.x(), y + PanelTab.ROW, area.w(), area.bottom() - y - PanelTab.ROW);
    }

    void draw(GuiGraphicsExtractor g, int mouseX, int mouseY) {
        g.fill(view.x(), view.y(), view.right(), view.bottom(), PanelTab.WELL);
        if (arrangement == null) return;
        int w = arrangement.sizeX(), h = arrangement.sizeY();
        if (threeD) {
            BlockState[] cells = new BlockState[w * h];
            for (int y = 0; y < h; y++)
                for (int x = 0; x < w; x++) {
                    int entry = arrangement.at(x, y, 0);
                    if (entry >= 0) cells[y * w + x] = states.get(entry);
                }
            // Room for the corners as the wall turns.
            float scale = Math.min(view.w() / (w * 1.3f), view.h() / (h * 1.4f));
            DioramaPip.submit(view.x(), view.y(), view.w(), view.h(), scale, w, h, cells, yaw, pitch);
            g.text(PanelTab.font(), Component.translatable("palettes.chromastudio.diorama.orbit"), view.x() + 3, view.bottom() - 10, PanelTab.DIM, false);
            return;
        }
        if (!painted) paint();
        int cell = Math.max(1, Math.min(view.w() / w, view.h() / h));
        int dw = w * cell, dh = h * cell, dx = view.x() + (view.w() - dw) / 2, dy = view.y() + (view.h() - dh) / 2;
        g.blit(RenderPipelines.GUI_TEXTURED, textureId, dx, dy, 0f, 0f, dw, dh, w * TEXELS, h * TEXELS, w * TEXELS, h * TEXELS);
        if (mouseX >= dx && mouseX < dx + dw && mouseY >= dy && mouseY < dy + dh) {
            int entry = arrangement.at((mouseX - dx) / cell, h - 1 - (mouseY - dy) / cell, 0);
            if (entry >= 0) g.setTooltipForNextFrame(PanelTab.font(), PaletteBlocks.name(kept.get(entry)), mouseX, mouseY);
        }
    }

    /** Paints the wall into the texture, a block's face to each cell, see-through texels over the panel's dark. */
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
                        int under = entry >= 0 ? NO_TEXTURE : PanelTab.WELL;
                        image.setPixel(ox + tx, oy + ty, face == null ? under : over(face[ty * TEXELS + tx], PanelTab.WELL));
                    }
            }
        texture.upload();
        painted = true;
    }

    private static int over(int argb, int background) {
        int a = ARGB.alpha(argb);
        return a == 255 ? argb : ARGB.srgbLerp(a / 255f, background, ARGB.opaque(argb));
    }

    boolean clicked(MouseButtonEvent e) {
        orbiting = threeD && e.button() == InputConstants.MOUSE_BUTTON_LEFT && view.contains(e.x(), e.y());
        return orbiting;
    }

    boolean dragged(double dx, double dy) {
        if (!orbiting) return false;
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
