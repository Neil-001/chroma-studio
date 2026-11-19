package dev.chromastudio.client.axiom;

import dev.chromastudio.client.ChromaStudioClient;
import dev.chromastudio.core.palette.AxiomScript;
import dev.chromastudio.core.palette.Palette;
import dev.chromastudio.core.palette.PaletteEntry;
import java.util.List;
import java.util.Optional;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;

/**
 * The one way into Chroma Studio's Axiom features from the rest of the mod. Axiom is optional and Fabric-only, so this
 * class names no Axiom or ImGui class, in its signatures or its bodies, and enters the classes that do only once
 * {@link #isPresent} says Axiom is loaded. The UI asks {@link #whyNot} whether Axiom is here, and greys a control out
 * with its answer, rather than checking the mod id itself.
 *
 * <p>Every block the tools change goes through Axiom's own history, as one of its brushes would, so Axiom's undo
 * covers it and Axiom's permissions decide whether it's allowed.
 */
public final class AxiomBridge {
    private AxiomBridge() {}

    private static final String MOD_ID = "axiom";

    public static boolean isPresent() {
        return ChromaStudioClient.platform != null && ChromaStudioClient.platform.isModLoaded(MOD_ID);
    }

    /** Why an Axiom control can't be used, or nothing when Axiom is here. */
    public static Optional<Component> whyNot() {
        return isPresent() ? Optional.empty() : Optional.of(Component.translatable("axiom.chromastudio.missing"));
    }

    /**
     * Adds {@code /chroma axiom}, on every client, and the tools to Axiom's editor when Axiom is here. Call once while
     * the client starts, after Axiom's own entrypoint, so its tool list is there to add to. Anything that goes wrong
     * with the tools, a missing class or service from another Axiom version included, is logged and leaves the rest
     * of the mod as it was.
     */
    public static void register() {
        AxiomCommand.install();
        if (!isPresent()) return;
        try {
            AxiomTools.register();
        } catch (Throwable t) {
            ChromaStudioClient.LOGGER.warn("Couldn't add Chroma Studio's tools to Axiom", t);
        }
    }

    // Axiom's active block, for the Match tab.

    /** What {@link #takeActiveBlock} did: whether the Match target changed, and a line saying how it went. */
    public record Taken(boolean taken, Component message) {}

    /** Makes Axiom's active block the Match target. */
    public static Taken takeActiveBlock() {
        Optional<Component> why = whyNot();
        return why.isPresent() ? new Taken(false, why.get()) : AxiomBlock.take();
    }

    // Axiom's extra hotbars, for a hotbar fill. The calls after the first throw when Axiom's methods fail; callers catch it.

    /** Why a fill can't use Axiom's hotbars now, or nothing when it can. */
    public static Optional<Component> hotbarsUnavailable() {
        Optional<Component> why = whyNot();
        return why.isPresent() ? why : AxiomHotbars.unavailable();
    }

    /** The hotbar the player holds now. */
    public static int activeHotbar() throws Throwable { return AxiomHotbars.active(); }

    /** A copy of what one slot of a hotbar holds, the active one included. */
    public static ItemStack hotbarItem(int hotbar, int slot) throws Throwable { return AxiomHotbars.get(hotbar, slot); }

    /** Sets one slot of a hotbar, to nothing for an empty stack. The active hotbar's slots are the real hotbar's. */
    public static void setHotbarItem(int hotbar, int slot, ItemStack stack) throws Throwable { AxiomHotbars.set(hotbar, slot, stack); }

    // Scripts for Axiom's Script Brush. They are only text, so these work without Axiom; the controls that offer them don't.

    /** A script and what to tell the player about it. */
    public record Script(String text, Component summary) {}

    /** Why a script couldn't be made, to show the player. */
    public static final class Refused extends Exception {
        private static final long serialVersionUID = 1L;
        private final transient Component reason;

        Refused(Component reason) {
            super(reason.getString());
            this.reason = reason;
        }

        public Component reason() { return reason; }
    }

    /** See {@link AxiomScripts#recolour}. */
    public static Script recolourScript(Palette to, @Nullable Palette from) throws Refused { return AxiomScripts.recolour(to, from); }

    /** See {@link AxiomScripts#scatter}. */
    public static Script scatterScript(Palette p) throws Refused { return AxiomScripts.scatter(p); }

    /** See {@link AxiomScripts#gradient}. */
    public static Script gradientScript(String name, List<PaletteEntry> steps, AxiomScript.Axis axis) throws Refused {
        return AxiomScripts.gradient(name, steps, axis);
    }

    /** Puts a script on the clipboard. */
    public static void copy(Script script) { AxiomScripts.copy(script); }
}
