package dev.chromastudio.client.axiom;

import com.moulberry.axiomclientapi.CustomTool;
import com.moulberry.axiomclientapi.service.RegionProvider;
import com.moulberry.axiomclientapi.service.ToolPatherProvider;
import com.moulberry.axiomclientapi.service.ToolRegistryService;
import com.moulberry.axiomclientapi.service.ToolService;
import dev.chromastudio.core.text.Plural;
import java.util.ServiceLoader;
import java.util.function.IntSupplier;
import java.util.function.Supplier;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.network.chat.Component;

final class AxiomTools {
    private AxiomTools() {}

    static RegionProvider regions;
    static ToolPatherProvider pathers;
    static ToolService tools;
    static Supplier<ClientLevel> level = () -> Minecraft.getInstance().level;
    static IntSupplier frames = AxiomEditor::frame;
    private static PaletteBrushTool brush;
    private static GradientTool gradient;
    private static ShadeBrushTool shade;
    private static SamplerTool sampler;
    private static long visit;
    private static boolean wasOpen;

    static void register() {
        regions = service(RegionProvider.class);
        pathers = service(ToolPatherProvider.class);
        tools = service(ToolService.class);
        ToolRegistryService registry = service(ToolRegistryService.class);
        brush = new PaletteBrushTool();
        gradient = new GradientTool();
        shade = new ShadeBrushTool();
        EyedropperTool eyedropper = new EyedropperTool();
        sampler = new SamplerTool();
        add(registry, brush, AxiomIcons.PALETTE_BRUSH);
        add(registry, gradient, AxiomIcons.GRADIENT);
        add(registry, shade, AxiomIcons.SHADE_BRUSH);
        add(registry, sampler, AxiomIcons.SAMPLER);
        add(registry, eyedropper, AxiomIcons.EYEDROPPER);
    }

    static void add(ToolRegistryService registry, CustomTool tool, char icon) {
        GuardedTool guarded = new GuardedTool(tool);
        registry.register(guarded);
        AxiomIcons.give(guarded, icon);
    }

    static void tick() {
        editorOn();
        brush.tick();
        gradient.tick();
        shade.tick();
    }

    static boolean editorOn() {
        boolean open = AxiomEditor.known() && AxiomEditor.enabled();
        if (wasOpen && !open) visit++;
        wasOpen = open;
        return open;
    }

    static int frame() { return frames.getAsInt(); }

    static boolean off() { return AxiomBridge.whyNot().isPresent(); }

    static void shutDown(Throwable cause) {
        for (CustomTool tool : new CustomTool[] {brush, gradient, shade, sampler}) {
            try {
                if (tool != null) tool.reset();
            } catch (Throwable e) {
                AxiomBridge.suppress(cause, e);
            }
        }
    }

    static long visit() {
        editorOn();
        return visit;
    }

    static <T> T service(Class<T> type) {
        return ServiceLoader.load(type, type.getClassLoader()).findFirst().orElseThrow(() -> new IllegalStateException("Axiom gives no " + type.getSimpleName()));
    }

    static String tr(String key, Object... args) { return Component.translatable(key, args).getString(); }

    static String count(String key, int n, Object... args) { return tr(Plural.key(key, n), args); }
}
