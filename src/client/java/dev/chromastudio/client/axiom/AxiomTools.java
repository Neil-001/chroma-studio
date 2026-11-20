package dev.chromastudio.client.axiom;

import com.moulberry.axiomclientapi.CustomTool;
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
    /** The tools that need to notice what Axiom doesn't tell them, for {@link #tick}. */
    private static PaletteBrushTool brush;
    private static GradientTool gradient;
    private static ShadeBrushTool shade;
    /** Counts the times the player has left Axiom's editor, so changes queued on one visit aren't made on the next. */
    private static long visit;
    private static boolean wasOpen;

    static void register() {
        regions = service(RegionProvider.class);
        pathers = service(ToolPatherProvider.class);
        tools = service(ToolService.class);
        ToolRegistryService registry = service(ToolRegistryService.class);
        // Every tool is made before any is added, so one that can't be made with this Axiom leaves none behind.
        brush = new PaletteBrushTool();
        gradient = new GradientTool();
        shade = new ShadeBrushTool();
        EyedropperTool eyedropper = new EyedropperTool();
        SamplerTool sampler = new SamplerTool();
        add(registry, brush, AxiomIcons.PALETTE_BRUSH);
        add(registry, eyedropper, AxiomIcons.EYEDROPPER);
        add(registry, gradient, AxiomIcons.GRADIENT);
        add(registry, shade, AxiomIcons.SHADE_BRUSH);
        add(registry, sampler, AxiomIcons.SAMPLER);
    }

    private static void add(ToolRegistryService registry, CustomTool tool, char icon) {
        registry.register(tool);
        AxiomIcons.give(tool, icon);
    }

    /** Called every client tick, for the tools to notice what Axiom doesn't tell them. */
    static void tick() {
        // When this Axiom can't say whether its editor is open, the tools' own check on being drawn stands alone.
        boolean open = !AxiomEditor.known() || AxiomEditor.active();
        if (wasOpen && !open) visit++;
        wasOpen = open;
        brush.tick();
        gradient.tick();
        shade.tick();
    }

    /** Which visit to Axiom's editor this is. */
    static long visit() { return visit; }

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
