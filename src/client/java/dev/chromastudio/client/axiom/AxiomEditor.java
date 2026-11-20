package dev.chromastudio.client.axiom;

import dev.chromastudio.client.ModSettings;
import dev.chromastudio.client.ui.ChromaScreen;
import dev.chromastudio.client.ui.Hud;
import dev.chromastudio.client.ui.SettingsScreen;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.Optional;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.state.BlockState;
import org.jspecify.annotations.Nullable;

/**
 * Axiom's editor, as Chroma Studio's window and tools need it. Only {@link AxiomBridge} calls this, once Axiom is known
 * to be loaded. Axiom's API can't say whether the editor is open, so its {@code EditorUI} is asked through
 * {@link AxiomInternals}; when that fails, the editor counts as closed and everything acts as it did before.
 *
 * <p>While the editor is open, Axiom tells the game its window is only the editor's 3D view, and moves the mouse and
 * takes the first click to match. No screen can be bigger than that view, which leaves the window too small to use.
 * So the window leaves the editor when it opens there, as Axiom's own key would, and goes back once the player is
 * back in the world, or once a pinned window is put away. Axiom keeps its selection, tool, views and camera through
 * that, as it does for its key.
 */
final class AxiomEditor {
    private AxiomEditor() {}

    private record Handles(MethodHandle active, MethodHandle enabled, MethodHandle enable, MethodHandle disable) {}

    private static final AxiomInternals<Handles> INTERNALS = new AxiomInternals<>("Axiom's editor controls",
            Component.translatable("axiom.chromastudio.editor.changed"), () -> {
                MethodHandles.Lookup lookup = MethodHandles.publicLookup();
                Class<?> ui = Class.forName("com.moulberry.axiom.editor.EditorUI");
                MethodType bool = MethodType.methodType(boolean.class), none = MethodType.methodType(void.class);
                return new Handles(lookup.findStatic(ui, "isActive", bool), lookup.findStatic(ui, "isEnabled", bool),
                        lookup.findStatic(ui, "enable", none), lookup.findStatic(ui, "disable", none));
            });

    /** Set when the window took the player out of the editor, until they're back in it or have gone elsewhere. */
    private static boolean left;
    /** The active block when the editor was last open this session, or null. Outside it Axiom's API answers stone. */
    private static @Nullable BlockState lastActiveBlock;
    /** Whether the player has been told this Axiom can't say whether its editor is open. */
    private static boolean warned;

    /** Whether Chroma Studio can tell if the editor is open. */
    static boolean known() { return INTERNALS.reason().isEmpty(); }

    /** Whether the editor is switched on, which changes as soon as it's opened or closed. False when that can't be told. */
    static boolean enabled() { return call(h -> (boolean) h.enabled.invoke()); }

    /** Whether the editor is open. False when that can't be told. */
    static boolean active() { return call(h -> (boolean) h.active.invoke()); }

    /** The active block as Axiom last gave it while its editor was open, or null when it hasn't been open. */
    static @Nullable BlockState lastActiveBlock() { return lastActiveBlock; }

    /** Leaves the editor before the window opens, when it's open. */
    static void leaveForWindow() {
        if (!known()) {
            // The window may open squeezed into the editor's view. Said once a session, as it may not be open at all.
            if (!warned) {
                warned = true;
                Hud.readout(Component.translatable("axiom.chromastudio.editor.unknown"), Component.translatable("axiom.chromastudio.editor.unknown.how"),
                        ModSettings.readoutSeconds);
            }
            return;
        }
        // Axiom says it's active a frame late, so a player who has just pressed Axiom's own key would be counted wrongly
        // either way. Whether it's switched on changes at once.
        if (!call(h -> (boolean) h.enabled.invoke())) return;
        // Until the editor counts as active, Axiom answers stone for its block, which would replace the one last seen.
        if (active()) remember();
        if (!call(h -> { h.disable.invoke(); return true; })) return;
        left = true;
    }

    /**
     * Call at the end of every client tick. Remembers the active block while the editor is open, and takes the player
     * back into it once the window it left for has closed to the world. It doesn't when the player has left the world,
     * opened the editor themselves, gone to another mod's screen or changed game mode, so it never opens the editor
     * for a player who wasn't in it.
     */
    static void tick(Minecraft mc) {
        if (!known()) return;
        if (active()) remember();
        if (!left) return;
        // Axiom says its editor is active from one frame to the next, so just after leaving it still does. Whether it's
        // switched on changes at once, and is what tells that the player went back by themselves.
        Screen screen = mc.gui.screen();
        if (mc.level == null || mc.player == null || mc.gameMode == null || mc.gameMode.getPlayerMode() != GameType.CREATIVE
                || call(h -> (boolean) h.enabled.invoke()) || screen != null && !(screen instanceof ChromaScreen || screen instanceof SettingsScreen)) {
            left = false;
            return;
        }
        // A pinned window stays over the world: in the editor it would be squeezed over the 3D view as the open one would.
        if (screen != null || ChromaScreen.isPinned()) return;
        left = false;
        // A call that threw has been logged; either way the player is told the editor stayed closed, and how to open it.
        if (!call(h -> { h.enable.invoke(); return true; }) || !call(h -> (boolean) h.enabled.invoke()))
            Hud.readout(Component.translatable("axiom.chromastudio.editor.not_back"), Component.translatable("axiom.chromastudio.editor.not_back.how"),
                    ModSettings.readoutSeconds);
    }

    private static void remember() {
        try {
            lastActiveBlock = AxiomTools.tools.getActiveBlock();
        } catch (Throwable _) {
            // AxiomBlock reports a failure when the player asks for the block.
        }
    }

    private interface Call {
        boolean on(Handles h) throws Throwable;
    }

    /** Runs a call on the handles, false when they're missing or the call throws. */
    private static boolean call(Call c) {
        Optional<Component> why = INTERNALS.reason();
        if (why.isPresent()) return false;
        try {
            return c.on(INTERNALS.handles());
        } catch (Throwable t) {
            INTERNALS.failed(t);
            return false;
        }
    }
}
