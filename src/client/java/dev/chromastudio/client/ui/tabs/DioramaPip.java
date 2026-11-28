package dev.chromastudio.client.ui.tabs;

import com.mojang.blaze3d.platform.Lighting;
import com.mojang.blaze3d.platform.Window;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexSorting;
import com.mojang.math.Axis;
import dev.chromastudio.client.ChromaStudioClient;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.world.level.block.state.BlockState;
import org.joml.Matrix4f;
import org.lwjgl.opengl.GL11;

public final class DioramaPip {
    private DioramaPip() {}

    private static final int FULL_BRIGHT = 15728880;

    private static final float FOV = 50;
    private static final float FIT = 0.85f;

    public static void draw(GuiGraphics g, int x, int y, int w, int h, float scale, int width, int height, BlockState[] cells, float yaw, float pitch, boolean perspective) {
        g.flush();
        g.enableScissor(x, y, x + w, y + h);
        RenderSystem.clear(GL11.GL_DEPTH_BUFFER_BIT, Minecraft.ON_OSX);
        float cx = x + w / 2f, cy = y + h / 2f;
        if (perspective) {
            RenderSystem.backupProjectionMatrix();
            RenderSystem.setProjectionMatrix(lens(cx, cy, h), VertexSorting.DISTANCE_TO_ORIGIN);
        }
        PoseStack pose = g.pose();
        pose.pushPose();
        pose.translate(cx, cy, perspective ? 0 : -scale * (width + height));
        pose.scale(scale, scale, -scale);
        Lighting.setupForFlatItems();
        pose.mulPose(Axis.ZP.rotationDegrees(180));
        pose.mulPose(Axis.XP.rotationDegrees(-pitch));
        pose.mulPose(Axis.YP.rotationDegrees(yaw));
        pose.translate(-width / 2f, -height / 2f, -0.5f);
        MultiBufferSource.BufferSource buffers = g.bufferSource();
        for (int row = 0; row < height; row++)
            for (int col = 0; col < width; col++) {
                BlockState block = cells[row * width + col];
                if (block == null) continue;
                pose.pushPose();
                pose.translate(width - 1 - col, row, 0);
                ChromaStudioClient.platform.renderBlock(block, pose, buffers, FULL_BRIGHT, OverlayTexture.NO_OVERLAY);
                pose.popPose();
            }
        g.flush();
        pose.popPose();
        if (perspective) RenderSystem.restoreProjectionMatrix();
        g.disableScissor();
    }

    private static Matrix4f lens(float cx, float cy, int h) {
        Window window = Minecraft.getInstance().getWindow();
        float w = window.getGuiScaledWidth(), screen = window.getGuiScaledHeight();
        float distance = h / 2f / (float) Math.tan(Math.toRadians(FOV / 2)) / FIT;
        float near = distance / 20, k = near / distance / FIT;
        return new Matrix4f()
                .setFrustum(-cx * k, (w - cx) * k, (cy - screen) * k, cy * k, near, distance * 20)
                .translate(0, 0, -distance)
                .scale(1, -1, 1)
                .translate(-cx, -cy, 0)
                .mul(new Matrix4f(RenderSystem.getModelViewMatrix()).invert());
    }
}
