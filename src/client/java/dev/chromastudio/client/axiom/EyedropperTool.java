package dev.chromastudio.client.axiom;

import static dev.chromastudio.client.axiom.AxiomTools.tr;

import com.moulberry.axiomclientapi.CustomTool;
import dev.chromastudio.client.ChromaStudioClient;
import dev.chromastudio.client.action.Eyedropper;
import dev.chromastudio.client.ui.tabs.MatchState;
import dev.chromastudio.core.color.Color;
import dev.chromastudio.core.color.Face;
import imgui.moulberry92.ImGui;
import net.minecraft.network.chat.Component;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import org.jspecify.annotations.Nullable;

final class EyedropperTool implements CustomTool {
    private record Picked(Color colour, Component name, Face face) {}

    private @Nullable Picked last;
    private final AxiomPanel.Layout layout = new AxiomPanel.Layout();

    @Override
    public String name() { return tr("axiom.chromastudio.eyedropper"); }

    @Override
    public boolean callUseTool() {
        if (AxiomTools.off()) return false;
        BlockHitResult hit = AxiomTools.tools.raycastBlock();
        if (hit == null || hit.getType() != HitResult.Type.BLOCK) return false;
        if (Eyedropper.read(hit, false) && MatchState.entry() != null) {
            last = new Picked(MatchState.colour(), MatchState.entry().name(), MatchState.entryFace());
        }
        return true;
    }

    @Override
    public void displayImguiOptions() { layout.draw(() -> {}, this::bottom); }

    private void bottom() {
        Picked p = last;
        if (p == null) {
            AxiomPanel.note(tr("axiom.chromastudio.eyedropper.how"));
        } else {
            String face = tr("axiom.chromastudio.eyedropper.face", Eyedropper.faceName(p.face()));
            AxiomPanel.swatch("picked", new AxiomPanel.Swatch(p.colour(), face));
            AxiomPanel.sameLine();
            ImGui.alignTextToFramePadding();
            String name = p.name().getString(), hex = "  " + p.colour().hex();
            AxiomPanel.text(AxiomPanel.fit(name, ImGui.getContentRegionAvailX() - ImGui.calcTextSizeX(hex)) + hex);
            AxiomPanel.tip(name + "\n" + face);
        }
        if (AxiomPanel.button(tr("key.chromastudio.open"), true, tr("axiom.chromastudio.open.tip"))) ChromaStudioClient.openWindowSoon("match");
    }
}
