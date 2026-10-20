package dev.chromastudio.client.ui.tabs;

import com.mojang.blaze3d.ProjectionType;
import com.mojang.blaze3d.platform.Lighting;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import dev.chromastudio.client.ChromaStudioClient;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.gui.render.pip.PictureInPictureRenderer;
import net.minecraft.client.gui.render.state.pip.PictureInPictureRenderState;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.PerspectiveProjectionMatrixBuffer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.feature.FeatureRenderDispatcher;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.world.level.block.state.BlockState;
import org.joml.Matrix4f;

/**
 * Draws a wall of blocks inside the GUI with the game's own block models, lit as items are. Each loader registers
 * it as a picture-in-picture renderer, and {@link #submit} asks for one wall to be drawn this frame.
 */
public final class DioramaPip extends PictureInPictureRenderer<DioramaPip.State> {
    private static final int FULL_BRIGHT = 15728880;

    /** The field of view with perspective, top to bottom, in degrees. */
    private static final float FOV = 50;
    /**
     * How much smaller the wall's middle is drawn with perspective. Its near end comes out bigger than its middle,
     * so at full size a turned wall would run out of the picture where the flat projection keeps it in.
     */
    private static final float FIT = 0.85f;

    /**
     * One wall to draw. {@code cells} holds {@code width * height} states, row by row from the bottom, with null for
     * air. {@code scale} is how many GUI pixels a block is across without perspective. {@code yaw} turns the wall's front to the viewer's
     * right and {@code pitch} tips its top towards the viewer, in degrees, so adding a drag's movement to them makes
     * the wall follow the cursor. With {@code perspective}, the wall's middle stays where it is and is drawn
     * {@link #FIT} times the size.
     */
    public record State(int x0, int y0, int x1, int y1, float scale, ScreenRectangle scissorArea, ScreenRectangle bounds,
                        int width, int height, BlockState[] cells, float yaw, float pitch, boolean perspective) implements PictureInPictureRenderState {}

    /** Asks for a wall in the rectangle, clipped to it. */
    public static void submit(GuiGraphics g, int x, int y, int w, int h, float scale, int width, int height, BlockState[] cells, float yaw, float pitch, boolean perspective) {
        ScreenRectangle area = new ScreenRectangle(x, y, w, h);
        ChromaStudioClient.platform.submitPicture(g, new State(x, y, x + w, y + h, scale, area, area, width, height, cells, yaw, pitch, perspective));
    }

    /** Holds the perspective projection for the game, and is made the first time one is asked for. */
    private PerspectiveProjectionMatrixBuffer perspective;

    public DioramaPip(MultiBufferSource.BufferSource buffers) { super(buffers); }

    @Override
    public Class<State> getRenderStateClass() { return State.class; }

    @Override
    protected void renderToTexture(State state, PoseStack pose) {
        Minecraft mc = Minecraft.getInstance();
        mc.gameRenderer.getLighting().setupFor(Lighting.Entry.ITEMS_3D);
        // The blocks go through the game's own feature dispatcher, as its entity pictures do: 1.21.11's picture hands
        // over only a pose, and the ways to draw a block straight into the buffers are deprecated.
        FeatureRenderDispatcher features = mc.gameRenderer.getFeatureRenderDispatcher();
        SubmitNodeCollector out = features.getSubmitNodeStorage();
        if (state.perspective) lookThroughALens(pose);
        // The GUI's y runs down the screen, so the wall is turned the right way up before it is tilted.
        pose.mulPose(Axis.ZP.rotationDegrees(180));
        pose.mulPose(Axis.XP.rotationDegrees(-state.pitch));
        pose.mulPose(Axis.YP.rotationDegrees(state.yaw));
        pose.translate(-state.width / 2f, -state.height / 2f, -0.5f);
        for (int y = 0; y < state.height; y++)
            for (int x = 0; x < state.width; x++) {
                BlockState block = state.cells[y * state.width + x];
                if (block == null) continue;
                pose.pushPose();
                // Turned upright, the wall's x runs right to left, so the cells are laid the other way to match the flat view.
                pose.translate(state.width - 1 - x, y, 0);
                out.submitBlock(pose, block, FULL_BRIGHT, OverlayTexture.NO_OVERLAY, 0);
                pose.popPose();
            }
        features.renderAllFeatures();
    }

    /**
     * Swaps the flat projection the game has just set for this picture for one with perspective. The picture is
     * drawn in its own pixels, with the wall's middle at the centre and depth 0, so the eye is put straight out from
     * there, {@link #FIT} short of where a pixel at that depth would still be a pixel. The blocks themselves are
     * placed as before.
     */
    private void lookThroughALens(PoseStack pose) {
        // The game has moved the origin to the picture's centre, which is all the stack holds yet.
        Matrix4f placed = pose.last().pose();
        float halfW = placed.m30(), halfH = placed.m31();
        float distance = halfH / (float) Math.tan(Math.toRadians(FOV / 2)) / FIT;
        // Near and far are swapped, as the game's own projections have them: its depth runs from far to near.
        // 1.21.11 draws with OpenGL alone, whose depth runs from -1 to 1.
        Matrix4f lens = new Matrix4f()
                .setPerspective((float) Math.toRadians(FOV), halfW / halfH, distance * 20, distance / 20, false)
                .translate(0, 0, -distance)
                .scale(1, -1, 1)   // the picture's y runs down
                .translate(-halfW, -halfH, 0);
        if (perspective == null) perspective = new PerspectiveProjectionMatrixBuffer("PIP - DioramaPip perspective");
        RenderSystem.setProjectionMatrix(perspective.getBuffer(lens), ProjectionType.PERSPECTIVE);
    }

    @Override
    public void close() {
        super.close();
        if (perspective != null) perspective.close();
        perspective = null;
    }

    /** The wall turns about the middle of the picture. */
    @Override
    protected float getTranslateY(int height, int guiScale) { return height / 2f; }

    @Override
    protected String getTextureLabel() { return "chroma studio diorama"; }
}
