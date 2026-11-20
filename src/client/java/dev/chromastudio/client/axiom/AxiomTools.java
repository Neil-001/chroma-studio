package dev.chromastudio.client.axiom;

import com.moulberry.axiomclientapi.service.RegionProvider;
import com.moulberry.axiomclientapi.service.ToolPatherProvider;
import com.moulberry.axiomclientapi.service.ToolRegistryService;
import com.moulberry.axiomclientapi.service.ToolService;
import java.util.ServiceLoader;
import net.minecraft.network.chat.Component;

/**
 * Axiom's services, which Axiom itself supplies through {@link ServiceLoader}, and the list of Chroma Studio's tools
 * in Axiom's editor. A tool is one class that implements Axiom's {@code CustomTool}, and one line in {@link #register}.
 * Only {@link AxiomBridge} and the classes it enters use this, once Axiom is known to be loaded.
 */
final class AxiomTools {
    private AxiomTools() {}

    static RegionProvider regions;
    static ToolPatherProvider pathers;
    static ToolService tools;
    /** The gradient tool, once it's registered, for {@link #tick}. */
    private static GradientTool gradient;

    static void register() {
        regions = service(RegionProvider.class);
        pathers = service(ToolPatherProvider.class);
        tools = service(ToolService.class);
        ToolRegistryService registry = service(ToolRegistryService.class);
        registry.register(new PaletteBrushTool());
        registry.register(new EyedropperTool());
        registry.register(gradient = new GradientTool());
        registry.register(new ShadeBrushTool());
    }

    /** Called every client tick, for the tools that need to notice what Axiom doesn't tell them. */
    static void tick() {
        if (gradient != null) gradient.tick();
    }

    /** One of Axiom's services. Throws when Axiom gives none, as another version of it might not. */
    static <T> T service(Class<T> type) {
        // Axiom registers its implementations in its own jar, which the API class's loader sees.
        return ServiceLoader.load(type, type.getClassLoader()).findFirst().orElseThrow(() -> new IllegalStateException("Axiom gives no " + type.getSimpleName()));
    }

    /**
     * A string for an options panel. Axiom's panels are drawn by ImGui, which takes plain strings rather than the
     * game's components, so the text is looked up in the player's language here, each frame, as the game's own would be.
     */
    static String tr(String key, Object... args) { return Component.translatable(key, args).getString(); }
}
