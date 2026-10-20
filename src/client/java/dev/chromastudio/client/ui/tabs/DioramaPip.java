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

/**
 * Draws a wall of blocks inside the GUI with the game's own block models, lit as items are. 1.21.1 has no
 * picture-in-picture renderers, so {@link #draw} draws the wall straight into the screen, at the GUI's own
 * resolution, as the game draws items.
 */
public final class DioramaPip {
    private DioramaPip() {}

    private static final int FULL_BRIGHT = 15728880;

    /** The field of view with perspective, top to bottom, in degrees. */
    private static final float FOV = 50;
    /**
     * How much smaller the wall's middle is drawn with perspective. Its near end comes out bigger than its middle,
     * so at full size a turned wall would run out of the picture where the flat projection keeps it in.
     */
    private static final float FIT = 0.85f;

    /**
     * Draws a wall in the rectangle, clipped to it. {@code cells} holds {@code width * height} states, row by row
     * from the bottom, with null for air. {@code scale} is how many GUI pixels a block is across without
     * perspective. {@code yaw} turns the wall's front to the viewer's right and {@code pitch} tips its top towards
     * the viewer, in degrees, so adding a drag's movement to them makes the wall follow the cursor. With
     * {@code perspective}, the wall's middle stays where it is and is drawn {@link #FIT} times the size.
     */
    public static void draw(GuiGraphics g, int x, int y, int w, int h, float scale, int width, int height, BlockState[] cells, float yaw, float pitch, boolean perspective) {
        g.flush();   // what the GUI has queued is drawn as it was meant to be, before the depth and projection change
        g.enableScissor(x, y, x + w, y + h);
        // The wall has its own depth: its blocks hide each other, and nothing the GUI has drawn here hides them.
        // The clear keeps to the clipped area.
        RenderSystem.clear(GL11.GL_DEPTH_BUFFER_BIT, Minecraft.ON_OSX);
        float cx = x + w / 2f, cy = y + h / 2f;
        if (perspective) {
            RenderSystem.backupProjectionMatrix();
            RenderSystem.setProjectionMatrix(lens(cx, cy, h), VertexSorting.DISTANCE_TO_ORIGIN);
        }
        PoseStack pose = g.pose();
        pose.pushPose();
        // Without perspective the wall sits behind the GUI's plane, so what is drawn over the picture afterwards,
        // such as its caption, shows. With it the lens puts the wall well behind that plane of its own accord.
        pose.translate(cx, cy, perspective ? 0 : -scale * (width + height));
        pose.scale(scale, scale, -scale);
        Lighting.setupFor3DItems();
        // The GUI's y runs down the screen, so the wall is turned the right way up before it is tilted.
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
                // Turned upright, the wall's x runs right to left, so the cells are laid the other way to match the flat view.
                pose.translate(width - 1 - col, row, 0);
                ChromaStudioClient.platform.renderBlock(block, pose, buffers, FULL_BRIGHT, OverlayTexture.NO_OVERLAY);
                pose.popPose();
            }
        // Drawn now, while the projection and depth are the wall's.
        g.flush();
        pose.popPose();
        if (perspective) RenderSystem.restoreProjectionMatrix();
        g.disableScissor();
    }

    /**
     * A projection with perspective for the picture, with the eye straight out from the wall's middle at the centre
     * of the picture, {@link #FIT} short of where a GUI pixel at that depth would still be a GUI pixel. The eye is
     * off the screen's centre, so the frustum is off-centre too: at the wall's depth it takes in the whole screen,
     * {@link #FIT} times bigger about that point, and near and far are a twentieth and twenty times the depth. The
     * GUI's own view matrix is undone first, so the blocks are placed as they are without perspective.
     */
    private static Matrix4f lens(float cx, float cy, int h) {
        Window window = Minecraft.getInstance().getWindow();
        float w = window.getGuiScaledWidth(), screen = window.getGuiScaledHeight();
        float distance = h / 2f / (float) Math.tan(Math.toRadians(FOV / 2)) / FIT;
        float near = distance / 20, k = near / distance / FIT;
        return new Matrix4f()
                .setFrustum(-cx * k, (w - cx) * k, (cy - screen) * k, cy * k, near, distance * 20)
                .translate(0, 0, -distance)
                .scale(1, -1, 1)   // the GUI's y runs down
                .translate(-cx, -cy, 0)
                .mul(new Matrix4f(RenderSystem.getModelViewMatrix()).invert());
    }
}
