package dev.chromastudio.client.axiom;

import static dev.chromastudio.client.axiom.AxiomTools.tr;

import com.moulberry.axiomclientapi.CustomTool;
import dev.chromastudio.client.ChromaStudioClient;
import dev.chromastudio.client.action.Eyedropper;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;

final class EyedropperTool implements CustomTool {
    private Component last;
    private final AxiomPanel.Layout layout = new AxiomPanel.Layout();

    @Override
    public String name() { return tr("axiom.chromastudio.eyedropper"); }

    @Override
    public boolean callUseTool() {
        if (AxiomTools.off()) return false;
        BlockHitResult hit = AxiomTools.tools.raycastBlock();
        if (hit == null || hit.getType() != HitResult.Type.BLOCK) return false;
        if (Eyedropper.read(hit, false)) {
            var level = Minecraft.getInstance().level;
            if (level != null) last = level.getBlockState(hit.getBlockPos()).getBlock().getName();
        }
        return true;
    }

    @Override
    public void displayImguiOptions() { layout.draw(() -> AxiomPanel.note(tr("axiom.chromastudio.eyedropper.how")), this::bottom); }

    private void bottom() {
        if (last != null) AxiomPanel.wrapped(tr("axiom.chromastudio.eyedropper.last", last));
        if (AxiomPanel.button(tr("key.chromastudio.open"), true, tr("axiom.chromastudio.open.tip"))) ChromaStudioClient.openWindowSoon("match");
    }
}
