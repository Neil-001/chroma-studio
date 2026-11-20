package dev.chromastudio.client.axiom;

import com.moulberry.axiomclientapi.CustomTool;
import com.moulberry.axiomclientapi.IAxiomWorldRenderContext;

/**
 * What Axiom holds for each of Chroma Studio's tools: the tool, with every call Axiom makes into it caught. A tool calls
 * into Axiom from each of them, and another version of Axiom can lack a method or throw where this one doesn't, which
 * would otherwise reach the game loop and crash the game. Anything thrown, a missing method included, turns Chroma
 * Studio's Axiom features off for the session with a reason the panels and controls show, and puts the tool away, so
 * no preview is left in the world. Once the features are off, the tool does nothing.
 */
record GuardedTool(CustomTool tool) implements CustomTool {
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
    public void reset() {
        try {
            tool.reset();
        } catch (Throwable t) {
            failed(t);
        }
    }

    @Override
    public void render(IAxiomWorldRenderContext rc) {
        if (AxiomTools.off()) return;
        try {
            tool.render(rc);
        } catch (Throwable t) {
            failed(t);
        }
    }

    @Override
    public boolean callUseTool() {
        if (AxiomTools.off()) return false;
        try {
            return tool.callUseTool();
        } catch (Throwable t) {
            failed(t);
            return false;
        }
    }

    @Override
    public boolean callConfirm() {
        if (AxiomTools.off()) return false;
        try {
            return tool.callConfirm();
        } catch (Throwable t) {
            failed(t);
            return false;
        }
    }

    @Override
    public boolean callDelete() {
        if (AxiomTools.off()) return false;
        try {
            return tool.callDelete();
        } catch (Throwable t) {
            failed(t);
            return false;
        }
    }

    /** Drawn even once the features are off, as the panel then says why. */
    @Override
    public void displayImguiOptions() {
        try {
            tool.displayImguiOptions();
        } catch (Throwable t) {
            failed(t);
        }
    }

    /** Turns the features off, and puts this tool away along with the others. */
    private void failed(Throwable t) {
        AxiomBridge.fail(t);
        try {
            tool.reset();
        } catch (Throwable _) {
            // The failure is logged once already; there's nothing more to put away.
        }
    }
}
