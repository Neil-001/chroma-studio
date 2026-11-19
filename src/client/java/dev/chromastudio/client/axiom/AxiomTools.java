package dev.chromastudio.client.axiom;

import com.moulberry.axiomclientapi.service.RegionProvider;
import com.moulberry.axiomclientapi.service.ToolPatherProvider;
import com.moulberry.axiomclientapi.service.ToolRegistryService;
import com.moulberry.axiomclientapi.service.ToolService;
import java.util.ServiceLoader;
import net.minecraft.network.chat.Component;

/**
 * Axiom's services, which Axiom itself supplies through {@link ServiceLoader}, and the registration of the tools.
 * Only {@link AxiomBridge} loads this class, once Axiom is known to be loaded.
 */
final class AxiomTools {
    private AxiomTools() {}

    static RegionProvider regions;
    static ToolPatherProvider pathers;
    static ToolService tools;

    static void register() {
        regions = service(RegionProvider.class);
        pathers = service(ToolPatherProvider.class);
        tools = service(ToolService.class);
        ToolRegistryService registry = service(ToolRegistryService.class);
        registry.register(new PaletteBrushTool());
        registry.register(new EyedropperTool());
    }

    private static <T> T service(Class<T> type) {
        return ServiceLoader.load(type).findFirst().orElseThrow(() -> new IllegalStateException("Axiom gives no " + type.getSimpleName()));
    }

    /**
     * A string for an options panel. Axiom's panels are drawn by ImGui, which takes plain strings rather than the
     * game's components, so the text is looked up in the player's language here, each frame, as the game's own would be.
     */
    static String tr(String key, Object... args) { return Component.translatable(key, args).getString(); }
}
