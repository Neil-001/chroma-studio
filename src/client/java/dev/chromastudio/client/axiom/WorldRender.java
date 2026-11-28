package dev.chromastudio.client.axiom;

import com.mojang.blaze3d.vertex.PoseStack;
import com.moulberry.axiomclientapi.CustomTool;
import com.moulberry.axiomclientapi.regions.BlockRegion;
import com.moulberry.axiomclientapi.regions.BooleanRegion;
import net.minecraft.client.Camera;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

record WorldRender(Camera camera, long nanos, PoseStack poseStack, Matrix4f projection) {
    interface Tool extends CustomTool {
        void render(WorldRender rc);

        @Override
        default void render(Camera camera, float tickDelta, long nanos, PoseStack poseStack, Matrix4f projection) {
            render(new WorldRender(camera, nanos, poseStack, projection));
        }
    }

    void draw(BooleanRegion region, Vec3 at, int effects) {
        region.render(camera, at, poseStack, projection, nanos, effects);
    }

    void draw(BlockRegion region, Vec3 at, float blockOpacity, float outlineOpacity) {
        region.render(camera, at, poseStack, projection, blockOpacity, outlineOpacity);
    }
}
