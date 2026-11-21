package dev.chromastudio.client.axiom;

import dev.chromastudio.client.ModSettings;
import dev.chromastudio.client.ui.ChromaScreen;
import dev.chromastudio.client.ui.Hud;
import dev.chromastudio.client.ui.SettingsScreen;
import imgui.moulberry92.ImGui;
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

final class AxiomEditor {
    private AxiomEditor() {}

    private record Handles(MethodHandle active, MethodHandle enabled, MethodHandle enable, MethodHandle disable, MethodHandle push, MethodHandle pop,
                           MethodHandle contextActive) {}

    static final AxiomInternals<Handles> INTERNALS = new AxiomInternals<>("Axiom's editor controls",
            Component.translatable("axiom.chromastudio.editor.changed"), () -> {
                MethodHandles.Lookup lookup = MethodHandles.publicLookup();
                Class<?> ui = Class.forName("com.moulberry.axiom.editor.EditorUI");
                MethodType bool = MethodType.methodType(boolean.class), none = MethodType.methodType(void.class);
                return new Handles(lookup.findStatic(ui, "isActive", bool), lookup.findStatic(ui, "isEnabled", bool),
                        lookup.findStatic(ui, "enable", none), lookup.findStatic(ui, "disable", none),
                        lookup.findStatic(ui, "pushImGuiContext", MethodType.methodType(long.class)),
                        lookup.findStatic(ui, "popImGuiContext", MethodType.methodType(void.class, long.class)),
                        lookup.findStatic(ui, "isImGuiContextActive", bool));
            });

    private static boolean left;
    private static @Nullable BlockState lastActiveBlock;
    private static boolean warned;

    static boolean known() { return INTERNALS.reason().isEmpty(); }

    static boolean enabled() { return call(h -> (boolean) h.enabled.invoke()); }

    static boolean active() { return call(h -> (boolean) h.active.invoke()); }

    static int frame() {
        if (!known()) return -1;
        Handles h = INTERNALS.handles();
        long before;
        try {
            before = (long) h.push.invoke();
        } catch (Throwable t) {
            INTERNALS.failed(t);
            return -1;
        }
        try {
            return (boolean) h.contextActive.invoke() ? ImGui.getFrameCount() : -1;
        } catch (Throwable t) {
            INTERNALS.failed(t);
            return -1;
        } finally {
            try {
                h.pop.invoke(before);
            } catch (Throwable t) {
                INTERNALS.failed(t);
            }
        }
    }

    static @Nullable BlockState lastActiveBlock() { return lastActiveBlock; }

    static void leaveForWindow() {
        if (!known()) {
            if (!warned) tell();
            return;
        }
        boolean on = call(h -> (boolean) h.enabled.invoke());
        if (on && active()) remember();
        if (on && call(h -> { h.disable.invoke(); return true; })) {
            left = true;
            return;
        }
        if (!known()) tell();
    }

    private static void tell() {
        warned = true;
        Hud.readout(Component.translatable("axiom.chromastudio.editor.unknown"), Component.translatable("axiom.chromastudio.editor.unknown.how"),
                ModSettings.readoutSeconds);
    }

    static void tick(Minecraft mc) {
        if (!known()) return;
        if (active()) remember();
        if (!left) return;
        Screen screen = mc.gui.screen();
        if (mc.level == null || mc.player == null || mc.gameMode == null || mc.gameMode.getPlayerMode() != GameType.CREATIVE
                || call(h -> (boolean) h.enabled.invoke()) || screen != null && !(screen instanceof ChromaScreen || screen instanceof SettingsScreen)) {
            left = false;
            return;
        }
        if (screen != null || ChromaScreen.isPinned()) return;
        left = false;
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
