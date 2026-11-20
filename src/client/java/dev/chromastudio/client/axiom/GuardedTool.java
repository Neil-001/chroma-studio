package dev.chromastudio.client.axiom;

import com.moulberry.axiomclientapi.CustomTool;
import com.moulberry.axiomclientapi.IAxiomWorldRenderContext;
import java.util.function.BooleanSupplier;

/**
 * What Axiom holds for each of Chroma Studio's tools: the tool, with every call Axiom makes into it caught. A tool calls
 * into Axiom from each of them, and another version of Axiom can lack a method or throw where this one doesn't, which
 * would otherwise reach the game loop and crash the game. Anything thrown, a missing method included, turns Chroma
 * Studio's Axiom features off for the session with a reason the panels and controls show, and puts the tool away, so
 * no preview is left in the world. Once the features are off, the tool does nothing.
 */
record GuardedTool(CustomTool tool) implements CustomTool {
    /** A tool with a stroke or preview to put away the moment Axiom picks another tool. */
    interface PutAway {
        /**
         * Drops the stroke on now, as left partway, and lets go of the preview's hold on the world's look, which
         * Axiom gives one tool at a time: the next tool may take it at once. What the tool was used on is kept, so
         * a preview comes back with the tool.
         */
        void putAway();
    }

    /** Called when Axiom picks another tool, which it always says, unlike {@link #reset}. */
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
    public void render(IAxiomWorldRenderContext rc) {
        if (!AxiomTools.off()) run(() -> tool.render(rc));
    }

    @Override
    public boolean callUseTool() { return ask(tool::callUseTool); }

    @Override
    public boolean callConfirm() { return ask(tool::callConfirm); }

    @Override
    public boolean callDelete() { return ask(tool::callDelete); }

    /** Drawn even once the features are off, as the panel then says why. */
    @Override
    public void displayImguiOptions() { run(tool::displayImguiOptions); }

    private void run(Runnable call) {
        try {
            call.run();
        } catch (Throwable t) {
            failed(t);
        }
    }

    /** Whether the tool took the call, which it never does once the features are off. */
    private boolean ask(BooleanSupplier call) {
        if (AxiomTools.off()) return false;
        try {
            return call.getAsBoolean();
        } catch (Throwable t) {
            failed(t);
            return false;
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
