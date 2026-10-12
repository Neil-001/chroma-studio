package dev.chromastudio.client.ui.tabs;

import com.mojang.blaze3d.platform.NativeImage;
import dev.chromastudio.client.ChromaStudioClient;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ARGB;

/** A small image the mod paints itself and draws stretched: a picker's gradient, or a texture thumbnail. Close it when done. */
final class Pixels implements AutoCloseable {
    private static int next;

    final int width, height;
    private final Identifier id;
    private final DynamicTexture texture;

    Pixels(int width, int height) {
        this.width = width;
        this.height = height;
        id = Identifier.fromNamespaceAndPath(ChromaStudioClient.MOD_ID, "painted/" + next++);
        texture = new DynamicTexture(id::toString, width, height, false);
        Minecraft.getInstance().getTextureManager().register(id, texture);
    }

    /** Paints the whole image from ARGB pixels, in rows from the top. */
    void set(int[] argb) {
        NativeImage image = texture.getPixels();
        for (int i = 0; i < argb.length; i++) image.setPixel(i % width, i / width, argb[i]);
        texture.upload();
    }

    /** Draws it filling a rectangle, with every pixel's alpha scaled by {@code opacity}. */
    void draw(GuiGraphicsExtractor g, int x, int y, int w, int h, float opacity) {
        g.blit(RenderPipelines.GUI_TEXTURED, id, x, y, 0, 0, w, h, width, height, width, height, ARGB.white(opacity));
    }

    @Override
    public void close() { Minecraft.getInstance().getTextureManager().release(id); }
}
