package dev.chromastudio.client.axiom;

import dev.chromastudio.client.ChromaStudioClient;

/**
 * Adds Chroma Studio's tools to Axiom's editor, when Axiom is there. Axiom is optional and Fabric-only, so this is
 * the one way in: nothing outside this package names an Axiom or ImGui class, and the game only loads
 * {@link AxiomTools}, which does, once the loader says Axiom is present. Anything that goes wrong, a missing class
 * or service from another Axiom version included, is logged and leaves the rest of the mod as it was.
 *
 * <p>Every block the tools change goes through Axiom's own history, as one of its brushes would, so Axiom's undo
 * covers it and Axiom's permissions decide whether it's allowed.
 */
public final class AxiomBridge {
    private AxiomBridge() {}

    private static final String MOD_ID = "axiom";

    public static boolean isAvailable() {
        return ChromaStudioClient.platform != null && ChromaStudioClient.platform.isModLoaded(MOD_ID);
    }

    /** Registers the tools with Axiom. Call once, while the client starts. */
    public static void register() {
        if (!isAvailable()) return;
        try {
            AxiomTools.register();
        } catch (Throwable t) {
            ChromaStudioClient.LOGGER.warn("Couldn't add Chroma Studio's tools to Axiom", t);
        }
    }
}
