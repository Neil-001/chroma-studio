package dev.chromastudio.client.axiom;

import dev.chromastudio.core.palette.BuildSearch;
import dev.chromastudio.core.palette.QueuedChanges;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.Level;
import org.jspecify.annotations.Nullable;

/**
 * The player's tool masks in Axiom's editor, which Axiom's own brushes and its Script Brush keep to. Axiom's API
 * doesn't apply them to a tool's changes, so the tools that change blocks ask Axiom's {@code MaskManager} for the
 * destination mask as the Script Brush does: the mask set under Tool Masks, kept within the selection when there is
 * one, or every block when neither is set. That's how a player limits a brush to air, or to existing blocks, as with
 * any Axiom brush. It's internal to Axiom, so it goes through {@link AxiomInternals}.
 *
 * <p>When the masks aren't as this version knows them, missing or moved included, or a call into them has failed, they
 * can't be read, so the tools change nothing until the game restarts, and their panels say so before the player
 * starts; no version of Axiom counts as having none. A mask whose Lua
 * script has an error answers no from then on, and Axiom says so only in its own error message, which stays until a
 * fresh run of the script compiles or the player closes it. While it's there the masks count as failed, so a stroke or
 * Apply sends nothing ({@link QueuedChanges#check}), and the panels say why. A mask that can't be read never lets
 * through what it might be keeping out.
 *
 * <p>Axiom applies the masks only while its Tool Masks window is open, docked or minimised; closed, every block passes,
 * or the selection's when there is one. The tools follow Axiom in that, as its own brushes do, and the panels say which
 * it is ({@link #applied}). A window opened or closed between a stroke and its sending changes what the masks answer,
 * so the plan worked out again isn't the same, and nothing is sent.
 */
final class AxiomMask {
    private AxiomMask() {}

    /** Whether a block may change, and whether the answers can be trusted. Ask it on the client thread, as Axiom does. */
    interface Test extends QueuedChanges.Mask {}

    private record Fixed(boolean allows, boolean failed) implements Test {
        @Override
        public boolean allows(int x, int y, int z) { return allows; }
    }

    private static final Test FAILED = new Fixed(false, true);

    /** @param luaError Axiom's last Lua mask error, which is the only sign that a mask's script failed */
    private record Handles(MethodHandle destMask, MethodHandle context, MethodHandle setPosition, MethodHandle test, MethodHandle luaError) {}

    private static final String MANAGER = "com.moulberry.axiom.mask.MaskManager";

    static final AxiomInternals<Handles> INTERNALS = new AxiomInternals<>("Axiom's tool masks",
            Component.translatable("axiom.chromastudio.mask.unknown"), () -> {
                MethodHandles.Lookup lookup = MethodHandles.publicLookup();
                Class<?> manager = Class.forName(MANAGER);
                Class<?> element = Class.forName("com.moulberry.axiom.mask.MaskElement");
                Class<?> context = Class.forName("com.moulberry.axiom.mask.MaskContext");
                return new Handles(lookup.findStatic(manager, "getDestMask", MethodType.methodType(element)),
                        lookup.findConstructor(context, MethodType.methodType(void.class, Level.class)),
                        lookup.findVirtual(context, "setPosition", MethodType.methodType(void.class, int.class, int.class, int.class)),
                        lookup.findVirtual(element, "test", MethodType.methodType(boolean.class, context)),
                        lookup.findStaticGetter(Class.forName("com.moulberry.axiom.editor.windows.global_mask.ToolMaskWindow"), "luaExecutionError", String.class));
            });

    /** Axiom's Tool Masks window, and the mode that turns the masks off for a tool's changes. */
    private record Window(Object toolMasks, MethodHandle isOpen, MethodHandle mode) {}

    /** Only the panels' line needs these, so when they're missing the masks are still followed, and the line says less. */
    static final AxiomInternals<Window> WINDOW = new AxiomInternals<>("Axiom's Tool Masks window", Component.empty(), () -> {
        MethodHandles.Lookup lookup = MethodHandles.publicLookup();
        Class<?> type = Class.forName("com.moulberry.axiom.editor.EditorWindowType");
        return new Window(lookup.findStaticGetter(type, "TOOL_MASKS", type).invoke(),
                lookup.findVirtual(type, "isOpen", MethodType.methodType(boolean.class)),
                lookup.findStaticGetter(Class.forName(MANAGER), "mode", int[].class));
    });

    /**
     * Whether Axiom applies the tool masks to a tool's changes now: only while its Tool Masks window is open, docked or
     * minimised, and its mode doesn't turn them off, as {@code MaskManager.getDestMask} has it in this version. Nothing
     * when that can't be told.
     */
    static Optional<Boolean> applied() {
        if (WINDOW.reason().isPresent()) return Optional.empty();
        Window w = WINDOW.handles();
        try {
            return Optional.of(((int[]) w.mode.invoke())[0] != 1 && (boolean) w.isOpen.invoke(w.toolMasks));
        } catch (Throwable t) {
            WINDOW.failed(t);
            return Optional.empty();
        }
    }

    /**
     * Why the masks can't be followed, or nothing when they can: they aren't as this version knows them, or a call into
     * them failed, so the tools change nothing until the game restarts; or a mask's script has an error, so the tools
     * change nothing until it's fixed or closed.
     */
    static Optional<Component> unavailable() {
        if (INTERNALS.reason().isEmpty()) {
            String error = luaError();
            return error == null ? Optional.empty() : Optional.of(Component.translatable("axiom.chromastudio.mask.lua", error));
        }
        return Optional.of(Component.translatable(INTERNALS.found() ? "axiom.chromastudio.mask.failed" : "axiom.chromastudio.mask.unknown"));
    }

    /** Axiom's Lua mask error as it is now, or null when there's none or the masks can't be read. */
    private static @Nullable String luaError() {
        if (INTERNALS.reason().isPresent()) return null;
        try {
            return (String) INTERNALS.handles().luaError.invoke();
        } catch (Throwable t) {
            INTERNALS.failed(t);
            return null;
        }
    }

    /**
     * The masks as they're set now, for one stroke, preview or check, in {@code level}. Each test has a context of its
     * own, as each of Axiom's brushes does, so a mask's script runs afresh. When they can't be read, or a call into them
     * fails, no block passes and the test says it failed, as it does while Axiom's Lua mask error is set.
     */
    static Test now(Level level) {
        if (INTERNALS.reason().isPresent()) return FAILED;
        Handles h = INTERNALS.handles();
        Object mask, context;
        try {
            mask = h.destMask.invoke();
            context = h.context.invoke(level);
        } catch (Throwable t) {
            INTERNALS.failed(t);
            return FAILED;
        }
        return new Test() {
            private boolean threw;
            /**
             * Each position's first answer. A mask's script can answer differently each time it's asked, at random say,
             * and one stroke or preview must give a position one answer, or a door could be let through by halves.
             */
            private final Map<Long, Boolean> asked = new HashMap<>();

            @Override
            public boolean allows(int x, int y, int z) {
                if (threw) return false;
                return asked.computeIfAbsent(BuildSearch.pack(x, y, z), _ -> ask(x, y, z));
            }

            private boolean ask(int x, int y, int z) {
                try {
                    h.setPosition.invoke(context, x, y, z);
                    return (boolean) h.test.invoke(mask, context);
                } catch (Throwable t) {
                    INTERNALS.failed(t);
                    threw = true;
                    return false;
                }
            }

            /** Read when it's asked, once the blocks have been, as running the script is what sets its error. */
            @Override
            public boolean failed() { return threw || INTERNALS.reason().isPresent() || luaError() != null; }
        };
    }
}
