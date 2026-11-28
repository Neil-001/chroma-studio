package dev.chromastudio.client.ui.tabs;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderSystem;
import dev.chromastudio.client.Abgr;
import dev.chromastudio.client.ChromaStudioClient;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.ResourceLocation;

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

    void set(int[] argb) {
        NativeImage image = texture.getPixels();
        for (int i = 0; i < argb.length; i++) Abgr.set(image, i % width, i / width, argb[i]);
        texture.upload();
    }

    void draw(GuiGraphics g, int x, int y, int w, int h, float opacity) {
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        g.setColor(1, 1, 1, opacity);
        g.blit(id, x, y, w, h, 0, 0, width, height, width, height);
        g.setColor(1, 1, 1, 1);
        RenderSystem.disableBlend();
    }

    @Override
    public void close() { Minecraft.getInstance().getTextureManager().release(id); }
}
