package dev.chromastudio.client.axiom;

import com.moulberry.axiomclientapi.CustomTool;
import com.moulberry.axiomclientapi.service.RegionProvider;
import com.moulberry.axiomclientapi.service.ToolPatherProvider;
import com.moulberry.axiomclientapi.service.ToolRegistryService;
import com.moulberry.axiomclientapi.service.ToolService;
import java.util.ServiceLoader;
import java.util.function.IntSupplier;
import java.util.function.Supplier;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
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
    /** The world the player is in, or null. Tests stand in their own. */
    static Supplier<ClientLevel> level = () -> Minecraft.getInstance().level;
    /** How many frames Axiom's editor has drawn, or -1 when that can't be told ({@link AxiomEditor#frame}). Tests stand in their own. */
    static IntSupplier frames = AxiomEditor::frame;
    /** The tools that need to notice what Axiom doesn't tell them, for {@link #tick}. */
    private static PaletteBrushTool brush;
    private static GradientTool gradient;
    private static ShadeBrushTool shade;
    private static SamplerTool sampler;
    /**
     * Counts the times the player has left Axiom's editor, so changes queued on one visit aren't made on the next. It
     * goes up whenever the editor is seen switched off having been seen on, read on every tick, every frame a tool of
     * ours is drawn and every time changes are queued or sent.
     */
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
        sampler = new SamplerTool();
        // The tools that change blocks first, then those that only read them, each group in the order a build goes:
        // paint it, lay a gradient over it, shade it; then sample it, or take one block's colour.
        add(registry, brush, AxiomIcons.PALETTE_BRUSH);
        add(registry, gradient, AxiomIcons.GRADIENT);
        add(registry, shade, AxiomIcons.SHADE_BRUSH);
        add(registry, sampler, AxiomIcons.SAMPLER);
        add(registry, eyedropper, AxiomIcons.EYEDROPPER);
    }

    /** Hands a tool to Axiom, caught by {@link GuardedTool}, and gives it its icon. */
    static void add(ToolRegistryService registry, CustomTool tool, char icon) {
        GuardedTool guarded = new GuardedTool(tool);
        registry.register(guarded);
        AxiomIcons.give(guarded, icon);
    }

    /** Called every client tick, for the tools to notice what Axiom doesn't tell them. */
    static void tick() {
        // A stroke or preview notices the editor closing by the visit it was made in.
        editorOn();
        brush.tick();
        gradient.tick();
        shade.tick();
    }

    /**
     * Whether the editor is switched on now, which changes as soon as it's opened or closed, where Axiom says it's
     * active a frame late. False when this Axiom can't say. Counts a visit when it was on and now isn't.
     */
    static boolean editorOn() {
        boolean open = AxiomEditor.known() && AxiomEditor.enabled();
        if (wasOpen && !open) visit++;
        wasOpen = open;
        return open;
    }

    /** The frame Axiom is drawing, for {@link Drawn}, or -1 when that can't be told. */
    static int frame() { return frames.getAsInt(); }

    /** Whether Chroma Studio's Axiom features are off for the session, so a tool Axiom still holds does nothing. */
    static boolean off() { return AxiomBridge.whyNot().isPresent(); }

    /** Drops every preview and stroke, as Axiom's features are turned off, so none is left in the world. */
    static void shutDown() {
        // Each on its own, so one that can't be put away doesn't leave the others' previews in the world.
        for (CustomTool tool : new CustomTool[] {brush, gradient, shade, sampler}) {
            try {
                if (tool != null) tool.reset();
            } catch (Throwable _) {
                // Logged once already, as the failure that turned the features off.
            }
        }
    }

    /** Which visit to Axiom's editor this is, as of now. */
    static long visit() {
        editorOn();
        return visit;
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

    /**
     * As {@link #tr}, for a sentence about {@code n} of something: {@code key.one} when there's one, which English and
     * most of the languages say differently. Languages with more forms word {@code key} so that it fits any other number.
     */
    static String count(String key, int n, Object... args) { return tr(n == 1 ? key + ".one" : key, args); }
}
