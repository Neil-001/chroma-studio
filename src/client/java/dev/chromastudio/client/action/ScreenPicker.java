package dev.chromastudio.client.action;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import dev.chromastudio.client.ChromaStudioClient;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.function.IntConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import org.lwjgl.opengl.GL11;

/**
 * The screen picker: once armed, the next click anywhere takes the colour of the frame under the cursor, averaged
 * over 3 by 3 pixels. While it is armed the window isn't drawn, so what is under the cursor is the game and nothing
 * of the mod's, and a magnifier beside the cursor shows the nine pixels.
 *
 * <p>The game lays a frame's GUI out before it draws the world, so when {@link #render} runs the frame that can be
 * read is the last one shown, GUI and all. That is why the window steps aside rather than being read around.
 */
public final class ScreenPicker {
    private ScreenPicker() {}

    private static final int SIDE = 3, CELL = 8;
    /** Frames to wait after arming before a click counts, so the window is out of the frame that is read. */
    private static final int SETTLE = 2;

    private static IntConsumer onPick;
    private static int frames;
    /** Where the player clicked, in GUI pixels, until the frame there has been read. */
    private static double clickX = -1, clickY = -1;
    /** The nine pixels last read under the cursor, packed 0xRRGGBB in rows from the top, or null before the first read. */
    private static int[] seen;
    /** Counts the reads asked for, so an old one that comes back late doesn't finish a newer pick. */
    private static int session;

    /** Arms the picker. {@code onPick} gets the colour, packed 0xRRGGBB, on the client thread. */
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

    /** Takes the click that picks. The colour arrives once the frame has been read. */
    public static void click(double x, double y) {
        if (frames < SETTLE) return;
        clickX = x;
        clickY = y;
    }

    /** Draws the magnifier and the hint, and reads the frame under the cursor or the click. The window calls it in place of drawing itself. */
    public static void render(GuiGraphics g, int mouseX, int mouseY) {
        Minecraft mc = Minecraft.getInstance();
        Font font = mc.font;
        frames++;
        boolean picking = clickX >= 0;
        if (frames >= SETTLE) {
            int run = session;
            read(picking ? clickX : mouseX, picking ? clickY : mouseY, pixels -> {
                if (run != session) return;
                seen = pixels;
                if (picking) finish(mean(pixels));
            });
        }

        Component hint = Component.translatable("picker.chromastudio.hint");
        int w = font.width(hint) + 8;
        g.fill((g.guiWidth() - w) / 2, 4, (g.guiWidth() + w) / 2, 8 + font.lineHeight, 0xC0000000);
        g.drawCenteredString(font, hint, g.guiWidth() / 2, 6, 0xFFFFFFFF);

        if (seen == null) return;
        // Beside the cursor, never under it, and inside the screen.
        int size = SIDE * CELL + 2, x = mouseX + 14, y = mouseY + 14;
        int textW = font.width("#000000") + 4;
        if (x + Math.max(size, textW) > g.guiWidth()) x = mouseX - 14 - Math.max(size, textW);
        if (y + size + font.lineHeight + 3 > g.guiHeight()) y = mouseY - 14 - size - font.lineHeight - 3;
        g.fill(x, y, x + size, y + size, 0xFF000000);
        for (int i = 0; i < seen.length; i++) {
            int cx = x + 1 + i % SIDE * CELL, cy = y + 1 + i / SIDE * CELL;
            g.fill(cx, cy, cx + CELL, cy + CELL, 0xFF000000 | seen[i]);
        }
        g.renderOutline(x + CELL, y + CELL, CELL + 2, CELL + 2, 0xFFFFFFFF);
        g.fill(x, y + size, x + textW, y + size + font.lineHeight + 2, 0xC0000000);
        g.drawString(font, String.format("#%06X", mean(seen)), x + 2, y + size + 2, 0xFFFFFFFF, false);
    }

    private static void finish(int rgb) {
        IntConsumer taker = onPick;
        cancel();
        if (taker != null) taker.accept(rgb);
    }

    /** The average of some pixels, each packed 0xRRGGBB. */
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
        void accept(int[] rgb);
    }

    /**
     * Reads the 3 by 3 pixels around a point given in GUI pixels off the frame being drawn, whose world is already
     * in. Near an edge the block of nine stays inside the frame.
     */
    private static void read(double guiX, double guiY, Pixels then) {
        Minecraft mc = Minecraft.getInstance();
        RenderTarget target = mc.getMainRenderTarget();
        if (target.width < SIDE || target.height < SIDE) return;
        double scale = (double) target.width / mc.getWindow().getGuiScaledWidth();
        int px = Math.clamp((int) (guiX * scale) - 1, 0, target.width - SIDE);
        // The frame's rows run from the bottom up.
        int fromTop = Math.clamp((int) (guiY * scale) - 1, 0, target.height - SIDE);
        int py = target.height - SIDE - fromTop;
        try {
            RenderSystem.assertOnRenderThread();
            ByteBuffer data = ByteBuffer.allocateDirect(SIDE * SIDE * 4).order(ByteOrder.nativeOrder());
            GlStateManager._readPixels(px, py, SIDE, SIDE, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, data);
            int[] out = new int[SIDE * SIDE];
            for (int row = 0; row < SIDE; row++)
                for (int col = 0; col < SIDE; col++) {
                    int at = (col + row * SIDE) * 4;
                    out[col + (SIDE - 1 - row) * SIDE] = (data.get(at) & 0xFF) << 16 | (data.get(at + 1) & 0xFF) << 8 | data.get(at + 2) & 0xFF;
                }
            then.accept(out);
        } catch (RuntimeException e) {
            ChromaStudioClient.LOGGER.warn("Couldn't read the screen for the screen picker", e);
            cancel();
        }
    }
}
