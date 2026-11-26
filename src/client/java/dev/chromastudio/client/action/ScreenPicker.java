package dev.chromastudio.client.action;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.renderpearl.api.buffers.GpuBuffer;
import com.mojang.renderpearl.api.buffers.GpuBufferSlice;
import com.mojang.renderpearl.api.textures.GpuTexture;
import dev.chromastudio.client.ChromaStudioClient;
import dev.chromastudio.client.ui.Hud;
import dev.chromastudio.client.ui.Widgets;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.function.IntConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;

public final class ScreenPicker {
    private ScreenPicker() {}

    static final int SIDE = 3, LOUPE = 11;
    private static final int CELL = 5, SWATCH = 10, RETICLE = 5, OFFSET = 16;
    private static final int SETTLE = 2;

    private static IntConsumer onPick;
    private static int frames;
    private static double clickX = -1, clickY = -1;
    private static int[] seen;
    private static int seenAt;
    private static int session;

    public static void arm(IntConsumer onPick) {
        ScreenPicker.onPick = onPick;
        frames = 0;
        clickX = clickY = -1;
        seen = null;
        session++;
    }

    public static boolean armed() { return onPick != null; }

    public static void cancel() {
        onPick = null;
        session++;
    }

    public static void click(double x, double y) {
        if (frames < SETTLE || clickX >= 0) return;
        clickX = x;
        clickY = y;
        frames = 0;
    }

    public static void render(GuiGraphicsExtractor g, int mouseX, int mouseY) {
        Minecraft mc = Minecraft.getInstance();
        Font font = mc.font;
        frames++;
        boolean picking = clickX >= 0;
        if (frames >= SETTLE) {
            int run = session;
            read(picking ? clickX : mouseX, picking ? clickY : mouseY, (pixels, at) -> {
                if (run != session) return;
                seen = pixels;
                seenAt = at;
                if (picking) finish(mean(centre(pixels, at)));
            });
        }

        if (picking) return;
        Hud.banner(g, Component.translatable("picker.chromastudio.title"), Component.translatable("picker.chromastudio.hint"));

        RenderTarget target = mc.gameRenderer.mainRenderTarget();
        double scale = (double) target.width / mc.getWindow().getGuiScaledWidth();
        for (int[] bar : target.width < LOUPE || target.height < LOUPE ? List.<int[]>of() : reticle(mouseX, mouseY, scale, target.width, target.height)) {
            g.fill(bar[0] - 1, bar[1] - 1, bar[2] + 1, bar[3] + 1, 0xFF000000);
            g.fill(bar[0], bar[1], bar[2], bar[3], 0xFFFFFFFF);
        }
        if (seen == null) return;
        int rgb = mean(centre(seen, seenAt));
        String hex = String.format("#%06X", rgb);
        int grid = LOUPE * CELL, w = Math.max(grid, SWATCH + 3 + font.width(hex)) + 4, h = grid + 4 + SWATCH + 3;
        int x = mouseX + OFFSET, y = mouseY + OFFSET;
        if (x + w > g.guiWidth()) x = mouseX - OFFSET - w;
        if (y + h > g.guiHeight()) y = mouseY - OFFSET - h;
        g.fill(x - 1, y - 1, x + w + 1, y + h + 1, 0xFFFFFFFF);
        g.fill(x, y, x + w, y + h, 0xFF000000);
        int gx = x + (w - grid) / 2, gy = y + 2;
        for (int i = 0; i < seen.length; i++) {
            int cx = gx + i % LOUPE * CELL, cy = gy + i / LOUPE * CELL;
            g.fill(cx, cy, cx + CELL, cy + CELL, 0xFF000000 | seen[i]);
        }
        int ox = gx + seenAt % LOUPE * CELL, oy = gy + seenAt / LOUPE * CELL, o = SIDE * CELL;
        g.outline(ox - 2, oy - 2, o + 4, o + 4, 0xFF000000);
        g.outline(ox - 1, oy - 1, o + 2, o + 2, 0xFFFFFFFF);
        int sy = gy + grid + 2;
        g.fill(x + 2, sy, x + 2 + SWATCH, sy + SWATCH, 0xFFFFFFFF);
        Widgets.swatch(g, x + 3, sy + 1, SWATCH - 2, SWATCH - 2, rgb);
        g.text(font, hex, x + 2 + SWATCH + 3, sy + 1, 0xFFFFFFFF, false);
    }

    static List<int[]> reticle(int x, int y, double scale, int width, int height) {
        int near = (int) Math.ceil(LOUPE / 2 / scale) + 2, far = near + RETICLE;
        int lx = loupeStart(pick(x, scale, width), width), ly = loupeStart(pick(y, scale, height), height);
        List<int[]> bars = new ArrayList<>();
        for (int[] bar : List.of(new int[] {x - far, y, x - near + 1, y + 2}, new int[] {x + near, y, x + far + 1, y + 2},
                new int[] {x, y - far, x + 2, y - near + 1}, new int[] {x, y + near, x + 2, y + far + 1})) {
            boolean clearX = (bar[2] + 1) * scale <= lx || (bar[0] - 1) * scale >= lx + LOUPE;
            boolean clearY = (bar[3] + 1) * scale <= ly || (bar[1] - 1) * scale >= ly + LOUPE;
            if (clearX || clearY) bars.add(bar);
        }
        return bars;
    }

    static int pick(double gui, double scale, int size) { return Math.clamp((int) (gui * scale) - 1, 0, size - SIDE); }

    static int loupeStart(int picked, int size) { return Math.clamp(picked - (LOUPE - SIDE) / 2, 0, size - LOUPE); }

    static int[] centre(int[] loupe, int at) {
        int[] out = new int[SIDE * SIDE];
        for (int i = 0; i < out.length; i++) out[i] = loupe[at + i % SIDE + i / SIDE * LOUPE];
        return out;
    }

    private static void finish(int rgb) {
        IntConsumer taker = onPick;
        cancel();
        if (taker != null) taker.accept(rgb);
    }

    static int mean(int[] pixels) {
        int r = 0, g = 0, b = 0;
        for (int p : pixels) {
            r += p >> 16 & 0xFF;
            g += p >> 8 & 0xFF;
            b += p & 0xFF;
        }
        int n = pixels.length;
        return (r + n / 2) / n << 16 | (g + n / 2) / n << 8 | (b + n / 2) / n;
    }

    private interface Pixels {
        void accept(int[] rgb, int pickedAt);
    }

    private static void read(double guiX, double guiY, Pixels then) {
        Minecraft mc = Minecraft.getInstance();
        RenderTarget target = mc.gameRenderer.mainRenderTarget();
        GpuTexture texture = target.getColorTexture();
        int bytes = texture == null ? 0 : texture.getFormat().blockSize();
        if (bytes != 4 || target.width < LOUPE || target.height < LOUPE) return;
        double scale = (double) target.width / mc.getWindow().getGuiScaledWidth();
        int pickX = pick(guiX, scale, target.width), pickTop = pick(guiY, scale, target.height);
        int px = loupeStart(pickX, target.width), fromTop = loupeStart(pickTop, target.height);
        int at = pickX - px + (pickTop - fromTop) * LOUPE;
        int py = target.height - LOUPE - fromTop;
        try {
            GpuBuffer buffer = RenderSystem.getDevice().createBuffer(() -> "Chroma Studio screen picker",
                    GpuBuffer.USAGE_COPY_DST | GpuBuffer.USAGE_MAP_READ, (long) LOUPE * LOUPE * bytes);
            RenderSystem.getDevice().createCommandEncoder().copyTextureToBuffer(texture, buffer, 0L, () -> {
                int[] out = new int[LOUPE * LOUPE];
                try (GpuBufferSlice.MappedView view = buffer.map(true, false)) {
                    ByteBuffer data = view.data();
                    for (int row = 0; row < LOUPE; row++)
                        for (int col = 0; col < LOUPE; col++) {
                            int abgr = data.getInt((col + row * LOUPE) * bytes);
                            int rgb = (abgr & 0xFF) << 16 | abgr & 0xFF00 | abgr >> 16 & 0xFF;
                            out[col + (LOUPE - 1 - row) * LOUPE] = rgb;
                        }
                }
                buffer.close();
                then.accept(out, at);
            }, 0, px, py, LOUPE, LOUPE);
        } catch (RuntimeException e) {
            ChromaStudioClient.LOGGER.warn("Couldn't read the screen for the screen picker", e);
            cancel();
        }
    }
}
