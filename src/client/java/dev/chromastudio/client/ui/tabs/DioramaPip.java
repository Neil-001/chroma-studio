package dev.chromastudio.client.ui.tabs;

import com.mojang.blaze3d.ProjectionType;
import com.mojang.blaze3d.platform.Lighting;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.gui.render.pip.PictureInPictureRenderer;
import net.minecraft.client.renderer.ProjectionMatrixBuffer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.block.BlockModelRenderState;
import net.minecraft.client.renderer.block.BlockModelResolver;
import net.minecraft.client.renderer.block.model.BlockDisplayContext;
import net.minecraft.client.renderer.state.gui.pip.PictureInPictureRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.world.level.block.state.BlockState;
import org.joml.Matrix4f;

public final class DioramaPip extends PictureInPictureRenderer<DioramaPip.State> {
    private static final int FULL_BRIGHT = 15728880;

    private static final float FOV = 50;
    private static final float FIT = 0.85f;

    public record State(int x0, int y0, int x1, int y1, float scale, ScreenRectangle scissorArea, ScreenRectangle bounds,
                        int width, int height, BlockState[] cells, float yaw, float pitch, boolean perspective) implements PictureInPictureRenderState {}

    public static void submit(int x, int y, int w, int h, float scale, int width, int height, BlockState[] cells, float yaw, float pitch, boolean perspective) {
        ScreenRectangle area = new ScreenRectangle(x, y, w, h);
        Minecraft.getInstance().gameRenderer.gameRenderState().guiRenderState
                .addPicturesInPictureState(new State(x, y, x + w, y + h, scale, area, area, width, height, cells, yaw, pitch, perspective));
    }

    private final BlockModelRenderState model = new BlockModelRenderState();
    private final BlockDisplayContext context = BlockDisplayContext.create();
    private ProjectionMatrixBuffer perspective;

    @Override
    public Class<State> getRenderStateClass() { return State.class; }

    @Override
    protected void renderToTexture(State state, PoseStack pose, SubmitNodeCollector out) {
        Minecraft mc = Minecraft.getInstance();
        mc.gameRenderer.lighting().setupFor(Lighting.Entry.ITEMS_3D);
        BlockModelResolver resolver = new BlockModelResolver(mc.getModelManager());
        if (state.perspective) lookThroughALens(pose);
        pose.rotateDegrees(Axis.ZP, 180);
        pose.rotateDegrees(Axis.XP, -state.pitch);
        pose.rotateDegrees(Axis.YP, state.yaw);
        pose.translate(-state.width / 2f, -state.height / 2f, -0.5f);
        for (int y = 0; y < state.height; y++)
            for (int x = 0; x < state.width; x++) {
                BlockState block = state.cells[y * state.width + x];
                if (block == null) continue;
                resolver.update(model, block, context);
                pose.pushPose();
                pose.translate(state.width - 1 - x, y, 0);
                model.submit(pose, out, FULL_BRIGHT, OverlayTexture.NO_OVERLAY, 0);
                pose.popPose();
            }
    }

    private void lookThroughALens(PoseStack pose) {
        Matrix4f placed = pose.last().pose();
        float halfW = placed.m30(), halfH = placed.m31();
        float distance = halfH / (float) Math.tan(Math.toRadians(FOV / 2)) / FIT;
        Matrix4f lens = new Matrix4f()
                .setPerspective((float) Math.toRadians(FOV), halfW / halfH, distance * 20, distance / 20, RenderSystem.getDevice().getDeviceInfo().isZZeroToOne())
                .translate(0, 0, -distance)
                .scale(1, -1, 1)
                .translate(-halfW, -halfH, 0);
        if (perspective == null) perspective = new ProjectionMatrixBuffer("PIP - DioramaPip perspective");
        RenderSystem.setProjectionMatrix(perspective.getBuffer(lens), ProjectionType.PERSPECTIVE);
    }

    @Override
    public void close() {
        super.close();
        if (perspective != null) perspective.close();
        perspective = null;
    }

    @Override
    protected float getTranslateY(int height, int guiScale) { return height / 2f; }

    @Override
    protected String getTextureLabel() { return "chroma studio diorama"; }
}
