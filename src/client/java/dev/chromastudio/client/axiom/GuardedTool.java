package dev.chromastudio.client.axiom;

import com.mojang.blaze3d.vertex.PoseStack;
import com.moulberry.axiomclientapi.CustomTool;
import dev.chromastudio.client.ChromaStudioClient;
import java.util.function.BooleanSupplier;
import net.minecraft.client.Camera;
import org.joml.Matrix4f;

record GuardedTool(CustomTool tool) implements CustomTool {
    interface PutAway {
        void putAway();
    }

    void deselected() {
        if (tool instanceof PutAway away) run(away::putAway);
    }

    @Override
    public String name() {
        try {
            return tool.name();
        } catch (Throwable t) {
            failed(t);
            return "Chroma Studio";
        }
    }

    @Override
    public void reset() { run(tool::reset); }

    @Override
    public void render(Camera camera, float tickDelta, long nanos, PoseStack poseStack, Matrix4f projection) {
        if (!AxiomTools.off()) run(() -> tool.render(camera, tickDelta, nanos, poseStack, projection));
    }

    @Override
    public boolean callUseTool() { return ask(tool::callUseTool); }

    @Override
    public boolean callConfirm() { return ask(tool::callConfirm); }

    @Override
    public boolean callDelete() { return ask(tool::callDelete); }

    @Override
    public void displayImguiOptions() { run(tool::displayImguiOptions); }

    private void run(Runnable call) {
        try {
            call.run();
        } catch (Throwable t) {
            failed(t);
        }
    }

    private boolean ask(BooleanSupplier call) {
        if (AxiomTools.off()) return false;
        try {
            return call.getAsBoolean();
        } catch (Throwable t) {
            failed(t);
            return false;
        }
    }

    private void failed(Throwable t) {
        AxiomBridge.fail(t);
        try {
            tool.reset();
        } catch (Throwable e) {
            ChromaStudioClient.LOGGER.debug("Chroma Studio couldn't reset its Axiom tool after a failure", e);
        }
    }
}
