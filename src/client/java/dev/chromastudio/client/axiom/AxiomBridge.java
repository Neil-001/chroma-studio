package dev.chromastudio.client.axiom;

import dev.chromastudio.client.ChromaStudioClient;
import dev.chromastudio.client.ModSettings;
import dev.chromastudio.client.PaletteStore;
import dev.chromastudio.client.ui.Hud;
import dev.chromastudio.core.palette.AxiomScript;
import dev.chromastudio.core.palette.Palette;
import dev.chromastudio.core.palette.PaletteEntry;
import dev.chromastudio.core.palette.Recolour;
import java.util.List;
import java.util.Optional;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.BlockHitResult;
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

    /**
     * Why Axiom's features couldn't be set up, or null. Once set, nothing in this package runs again this session:
     * a class or service another Axiom version lacks would fail the same way on every call.
     */
    private static @Nullable Component broken;
    /** Whether the player has been told that Axiom's features are off. */
    private static boolean told;

    /** Why an Axiom control can't be used, or nothing when Axiom is here and Chroma Studio's features in it work. */
    public static Optional<Component> whyNot() {
        if (!isPresent()) return Optional.of(Component.translatable("axiom.chromastudio.missing"));
        return Optional.ofNullable(broken);
    }

    /** Whether Axiom is here and Chroma Studio's features in it work. */
    private static boolean usable() { return whyNot().isEmpty(); }

    /** Throws for a call that would reach Axiom once its features are off, as the hotbar calls' callers expect. */
    private static void live() {
        whyNot().ifPresent(why -> {
            throw new IllegalStateException(why.getString());
        });
    }

    /**
     * Adds {@code /chroma axiom}, on every client, and the tools to Axiom's editor when Axiom is here. Call once while
     * the client starts, after Axiom's own entrypoint, so its tool list is there to add to. Anything that goes wrong
     * with the tools, a missing class or service from another Axiom version included, is logged, turns every Axiom
     * feature off for the session with a reason the controls show, and leaves the rest of the mod as it was.
     */
    public static void register() {
        AxiomCommand.install();
        if (!isPresent()) return;
        try {
            AxiomTools.register();
        } catch (Throwable t) {
            fail(t);
        }
    }

    /** Forgets that the features were turned off, as if the game had restarted. Only tests call this. */
    static void forget() {
        broken = null;
        told = false;
    }

    static void fail(Throwable t) {
        ChromaStudioClient.LOGGER.warn("Chroma Studio's tools don't work with this version of Axiom, so its Axiom features are off", t);
        broken = Component.translatable("axiom.chromastudio.incompatible");
        try {
            AxiomTools.shutDown();
        } catch (Throwable _) {
            // Axiom's classes may be what's missing, before any tool could be put away; then there's none to put away.
        }
    }

    // Axiom's editor, which Chroma Studio's window steps out of while it's open.

    /** Call before the window opens: leaves Axiom's editor when it's open, to go back once the window has closed. */
    public static void leaveEditorForWindow() {
        if (usable()) AxiomEditor.leaveForWindow();
    }

    /** Whether Axiom's editor is open. False without Axiom, or when this version of it can't say. */
    public static boolean editorOpen() { return usable() && AxiomEditor.active(); }

    /** Call at the end of every client tick, for the tools and the window's trips out of the editor. */
    public static void tick(Minecraft mc) {
        if (!isPresent()) return;
        if (broken != null) {
            // Said once, in the world, as the game starts before there's anywhere to say it.
            if (!told && mc.player != null) {
                told = true;
                Hud.readout(Component.translatable("axiom.chromastudio.incompatible.title"), broken, ModSettings.readoutSeconds);
            }
            return;
        }
        try {
            AxiomTools.tick();
            AxiomEditor.tick(mc);
        } catch (LinkageError e) {
            fail(e);
        }
    }

    /** The block under the mouse in Axiom's editor, or null when there's none or the editor isn't open. */
    public static @Nullable BlockHitResult blockUnderMouse() { return editorOpen() ? AxiomBlock.underMouse() : null; }

    // Axiom's active block, for the Match tab.

    /** What {@link #takeActiveBlock} did: whether the Match target changed, and a line saying how it went. */
    public record Taken(boolean taken, Component message) {}

    /** Why the Match tab can't take Axiom's active block now, or nothing when it can. */
    public static Optional<Component> activeBlockUnavailable() {
        Optional<Component> why = whyNot();
        return why.isPresent() ? why : AxiomBlock.unavailable();
    }

    /** What taking Axiom's active block would do, for the button's tooltip. Only when {@link #activeBlockUnavailable} is empty. */
    public static Component activeBlockTip() { return AxiomBlock.tip(); }

    /** Makes Axiom's active block the Match target. */
    public static Taken takeActiveBlock() {
        Optional<Component> why = activeBlockUnavailable();
        return why.isPresent() ? new Taken(false, why.get()) : AxiomBlock.take();
    }

    // Axiom's extra hotbars, for a hotbar fill. The calls that throw do so when Axiom's methods fail, which also turns
    // the hotbars off for the session; callers catch it.

    /** Why Axiom's hotbars can't be used anywhere: Axiom is missing or isn't a version Chroma Studio knows. For the setting. */
    public static Optional<Component> hotbarsUnsupported() {
        Optional<Component> why = whyNot();
        return why.isPresent() ? why : AxiomHotbars.unsupported();
    }

    /** Why a fill can't use Axiom's hotbars now, or nothing when it can. */
    public static Optional<Component> hotbarsUnavailable() {
        Optional<Component> why = whyNot();
        return why.isPresent() ? why : AxiomHotbars.unavailable();
    }

    /** Why an Undo can't put back what a fill wrote in Axiom's hotbars now. It may try after a failed call; a fill may not. */
    public static Optional<Component> hotbarsUnreachable() {
        Optional<Component> why = whyNot();
        return why.isPresent() ? why : AxiomHotbars.unreachable();
    }

    /** Turns Axiom's hotbars off for the session after they didn't take a write, though no call threw. */
    public static void hotbarsFailed(Throwable t) { AxiomHotbars.failed(t); }

    /** The hotbar the player holds now. */
    public static int activeHotbar() throws Throwable {
        // Only read, through the hotbars' own guard, and still needed with the tools off: a fill must know which
        // hotbar it went into, or its Undo could put the slots back into another one the player has switched to.
        return AxiomHotbars.active();
    }

    /** A copy of what one slot of a hotbar holds, the active one included. */
    public static ItemStack hotbarItem(int hotbar, int slot) throws Throwable {
        live();
        return AxiomHotbars.get(hotbar, slot);
    }

    /** Sets one slot of a hotbar other than the active one, which callers write as the player's own hotbar. */
    public static void setHotbarItem(int hotbar, int slot, ItemStack stack) throws Throwable {
        live();
        AxiomHotbars.set(hotbar, slot, stack);
    }

    /** The key that opens Axiom's menu of hotbars, as the player has it bound, or Left Alt, its default. */
    public static Component hotbarKey() { return AxiomHotbars.switchKey(); }

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
    public static Script recolourScript(Palette to, @Nullable Palette from, Recolour.Mode mode) throws Refused {
        return AxiomScripts.recolour(to, from, mode);
    }

    /** See {@link AxiomScripts#scatter}. */
    public static Script scatterScript(Palette p) throws Refused { return AxiomScripts.scatter(p); }

    /** See {@link AxiomScripts#gradient}. */
    public static Script gradientScript(String name, List<PaletteEntry> steps, AxiomScript.Axis axis) throws Refused {
        return AxiomScripts.gradient(name, steps, axis);
    }

    /**
     * A palette's name for a From choice: as the Palettes tab shows it, and marked as a preset when one of the
     * player's own palettes shows the same name, so the two can be told apart.
     */
    public static String fromName(Palette p) {
        String name = PaletteStore.get().shownName(p);
        if (!p.preset) return name;
        for (Palette own : PaletteStore.get().own()) {
            if (own.name.strip().equalsIgnoreCase(name.strip())) return Component.translatable("axiom.chromastudio.from.preset", name).getString();
        }
        return name;
    }

    /** Puts a script on the clipboard. */
    public static void copy(Script script) { AxiomScripts.copy(script); }
}
