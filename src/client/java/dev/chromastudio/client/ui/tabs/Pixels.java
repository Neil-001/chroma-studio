package dev.chromastudio.client.ui.tabs;

import com.mojang.blaze3d.platform.NativeImage;
import dev.chromastudio.client.Abgr;
import dev.chromastudio.client.ChromaStudioClient;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.ResourceLocation;

/** A small image the mod paints itself and draws stretched: a picker's gradient, or a texture thumbnail. Close it when done. */
final class Pixels implements AutoCloseable {
    private static int next;

    final int width, height;
    private final ResourceLocation id;
    private final DynamicTexture texture;

    Pixels(int width, int height) {
        this.width = width;
        this.height = height;
        id = ResourceLocation.fromNamespaceAndPath(ChromaStudioClient.MOD_ID, "painted/" + next++);
        texture = new DynamicTexture(width, height, false);
        Minecraft.getInstance().getTextureManager().register(id, texture);
    }

    /** Paints the whole image from ARGB pixels, in rows from the top. */
    void set(int[] argb) {
        NativeImage image = texture.getPixels();
        for (int i = 0; i < argb.length; i++) Abgr.set(image, i % width, i / width, argb[i]);
        texture.upload();
    }

    /** Draws it filling a rectangle, with every pixel's alpha scaled by {@code opacity}. */
    void draw(GuiGraphics g, int x, int y, int w, int h, float opacity) {
        g.setColor(1, 1, 1, opacity);
        g.blit(id, x, y, w, h, 0, 0, width, height, width, height);
        g.setColor(1, 1, 1, 1);
    }

    @Override
    public void close() { Minecraft.getInstance().getTextureManager().release(id); }
}
