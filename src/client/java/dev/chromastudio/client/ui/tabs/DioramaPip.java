package dev.chromastudio.client.ui.tabs;

import com.mojang.blaze3d.platform.Lighting;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.gui.render.pip.PictureInPictureRenderer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.block.BlockModelRenderState;
import net.minecraft.client.renderer.block.BlockModelResolver;
import net.minecraft.client.renderer.block.model.BlockDisplayContext;
import net.minecraft.client.renderer.state.gui.pip.PictureInPictureRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Draws a wall of blocks inside the GUI with the game's own block models, lit as items are. Each loader registers
 * it as a picture-in-picture renderer, and {@link #submit} asks for one wall to be drawn this frame.
 */
public final class DioramaPip extends PictureInPictureRenderer<DioramaPip.State> {
    private static final int FULL_BRIGHT = 15728880;

    /**
     * One wall to draw. {@code cells} holds {@code width * height} states, row by row from the bottom, with null for
     * air. {@code scale} is how many GUI pixels a block is across, and the angles are in degrees.
     */
    public record State(int x0, int y0, int x1, int y1, float scale, ScreenRectangle scissorArea, ScreenRectangle bounds,
                        int width, int height, BlockState[] cells, float yaw, float pitch) implements PictureInPictureRenderState {}

    /** Asks for a wall in the rectangle, clipped to it. */
    public static void submit(int x, int y, int w, int h, float scale, int width, int height, BlockState[] cells, float yaw, float pitch) {
        ScreenRectangle area = new ScreenRectangle(x, y, w, h);
        Minecraft.getInstance().gameRenderer.gameRenderState().guiRenderState
                .addPicturesInPictureState(new State(x, y, x + w, y + h, scale, area, area, width, height, cells, yaw, pitch));
    }

    private final BlockModelRenderState model = new BlockModelRenderState();
    private final BlockDisplayContext context = BlockDisplayContext.create();

    @Override
    public Class<State> getRenderStateClass() { return State.class; }

    @Override
    protected void renderToTexture(State state, PoseStack pose, SubmitNodeCollector out) {
        Minecraft mc = Minecraft.getInstance();
        mc.gameRenderer.lighting().setupFor(Lighting.Entry.ITEMS_3D);
        // The game keeps its own resolver private on Fabric. One is only a handle on the model manager.
        BlockModelResolver resolver = new BlockModelResolver(mc.getModelManager());
        // The GUI's y runs down the screen, so the wall is turned the right way up before it is tilted.
        pose.rotateDegrees(Axis.ZP, 180);
        pose.rotateDegrees(Axis.XP, state.pitch);
        pose.rotateDegrees(Axis.YP, state.yaw);
        pose.translate(-state.width / 2f, -state.height / 2f, -0.5f);
        for (int y = 0; y < state.height; y++)
            for (int x = 0; x < state.width; x++) {
                BlockState block = state.cells[y * state.width + x];
                if (block == null) continue;
                resolver.update(model, block, context);
                pose.pushPose();
                pose.translate(x, y, 0);
                model.submit(pose, out, FULL_BRIGHT, OverlayTexture.NO_OVERLAY, 0);
                pose.popPose();
            }
    }

    /** The wall turns about the middle of the picture. */
    @Override
    protected float getTranslateY(int height, int guiScale) { return height / 2f; }

    @Override
    protected String getTextureLabel() { return "chroma studio diorama"; }
}
