package dev.chromastudio.client.axiom;

import dev.chromastudio.core.palette.BuildSearch;
import dev.chromastudio.core.palette.QueuedChanges;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.Collections;
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
 * Apply sends nothing ({@link QueuedChanges#check}), and the panels say why. A test that saw the error once stays
 * failed, so a stroke or preview whose masks failed partway is never sent, though the check's fresh context clears it. A mask that can't be read never lets
 * through what it might be keeping out.
 *
 * <p>Axiom applies the masks only while its Tool Masks window is open, docked or minimised; closed, every block passes,
 * or the selection's when there is one. The tools follow Axiom in that, as its own brushes do, and the panels say which
 * it is ({@link #windowClosed}). A window opened or closed between a stroke and its sending changes what the masks answer,
 * so the plan worked out again isn't the same, and nothing is sent.
 */
final class AxiomMask {
    private AxiomMask() {}

    /** Whether a block may change, and whether the answers can be trusted. Ask it on the client thread, as Axiom does. */
    interface Test extends QueuedChanges.Mask {
        /** The answer given for each position asked so far, by {@link BuildSearch#pack}. */
        Map<Long, Boolean> answers();
    }

    private record Fixed(boolean allows, boolean failed) implements Test {
        @Override
        public boolean allows(int x, int y, int z) { return allows; }

        @Override
        public Map<Long, Boolean> answers() { return Map.of(); }
    }

    /**
     * Whether two tests answered differently for a position both were asked about. Each test has a context of its own,
     * and Axiom gives each context a random seed for a Lua script's {@code getSimplexNoise} and
     * {@code getVoronoiEdgeNoise}, so a script that gives its noise no seed of its own answers differently each time.
     * Reusing a context instead would run a script edited since as it was, so the tools tell the player to seed it.
     */
    static boolean differ(Test a, Test b) {
        Map<Long, Boolean> theirs = b.answers();
        for (Map.Entry<Long, Boolean> e : a.answers().entrySet()) {
            Boolean other = theirs.get(e.getKey());
            if (other != null && !other.equals(e.getValue())) return true;
        }
        return false;
    }

    /**
     * Masks that answer as {@code before} did wherever it was asked, and as {@code now} elsewhere, to tell whether the
     * masks alone changed a plan. Only for choosing what the player is told: it decides nothing about sending.
     */
    static QueuedChanges.Mask answeringAs(Test before, Test now) {
        Map<Long, Boolean> answers = before.answers();
        return new QueuedChanges.Mask() {
            @Override
            public boolean allows(int x, int y, int z) {
                Boolean was = answers.get(BuildSearch.pack(x, y, z));
                return was != null ? was : now.allows(x, y, z);
            }

            @Override
            public boolean failed() { return false; }
        };
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

    /**
     * What Axiom's Scripting tab holds: whether it's the tab in use, the script it last compiled, and when it last
     * compiled one. A script that doesn't compile leaves no script at all, and Axiom then applies no mask, quietly,
     * where the player expects their script's.
     */
    private record Script(MethodHandle inUse, MethodHandle script, MethodHandle lastCompile) {}

    /** Only the check for a script that doesn't compile needs these, so when they're missing the masks are still followed. */
    static final AxiomInternals<Script> SCRIPT = new AxiomInternals<>("Axiom's mask scripts", Component.empty(), () -> {
        MethodHandles.Lookup lookup = MethodHandles.publicLookup();
        Class<?> manager = Class.forName(MANAGER);
        Class<?> window = Class.forName("com.moulberry.axiom.editor.windows.global_mask.ToolMaskWindow");
        // The last compile is private to the window, so it's read through the window's own class.
        MethodHandles.Lookup inWindow = MethodHandles.privateLookupIn(window, MethodHandles.lookup());
        return new Script(lookup.findStaticGetter(manager, "useLuaScript", boolean.class),
                lookup.findStaticGetter(manager, "luaScript", String.class),
                inWindow.findStaticGetter(window, "lastLuaCompile", long.class));
    });

    /**
     * Whether the Scripting tab is in use with a script that doesn't compile, while the masks apply. Axiom then applies
     * no mask at all, so the tools change nothing until it's fixed. Before the tab has compiled anything there's no
     * script either, which isn't an error.
     */
    private static boolean scriptInvalid() {
        if (SCRIPT.reason().isPresent() || windowClosed().orElse(false)) return false;
        Script s = SCRIPT.handles();
        try {
            return (boolean) s.inUse.invoke() && s.script.invoke() == null && (long) s.lastCompile.invoke() > 0;
        } catch (Throwable t) {
            SCRIPT.failed(t);
            return false;
        }
    }

    /** Axiom's Tool Masks window. */
    private record Window(Object toolMasks, MethodHandle isOpen) {}

    /** Only the panels' line needs these, so when they're missing the masks are still followed, and the line says less. */
    static final AxiomInternals<Window> WINDOW = new AxiomInternals<>("Axiom's Tool Masks window", Component.empty(), () -> {
        MethodHandles.Lookup lookup = MethodHandles.publicLookup();
        Class<?> type = Class.forName("com.moulberry.axiom.editor.EditorWindowType");
        return new Window(lookup.findStaticGetter(type, "TOOL_MASKS", type).invoke(),
                lookup.findVirtual(type, "isOpen", MethodType.methodType(boolean.class)));
    });

    /**
     * Whether Axiom's Tool Masks window is closed now, so no mask applies, as {@code MaskManager.getDestMask} has it in
     * this version: masks apply only while it's open, docked or minimised. Nothing when that can't be told.
     */
    static Optional<Boolean> windowClosed() {
        if (WINDOW.reason().isPresent()) return Optional.empty();
        Window w = WINDOW.handles();
        try {
            return Optional.of(!(boolean) w.isOpen.invoke(w.toolMasks));
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
            if (error != null) return Optional.of(Component.translatable("axiom.chromastudio.mask.lua", error));
            return scriptInvalid() ? Optional.of(Component.translatable("axiom.chromastudio.mask.lua_invalid")) : Optional.empty();
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
             * Whether one of this test's answers raised Axiom's Lua mask error. It's kept, as Axiom clears the error
             * the moment another context compiles the script, which the check before sending does.
             */
            private boolean errored;
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
                    Object before = h.luaError.invoke();
                    boolean allows = (boolean) h.test.invoke(mask, context);
                    // Axiom makes a new message each time a script fails, so one that's a different object from
                    // before was raised by this answer. An error left from before, by a script no longer running,
                    // isn't this test's, and counts only while it's set ({@link #failed}).
                    Object after = h.luaError.invoke();
                    if (after != null && after != before) errored = true;
                    return allows;
                } catch (Throwable t) {
                    INTERNALS.failed(t);
                    threw = true;
                    return false;
                }
            }

            /**
             * True for good once an answer raised Axiom's Lua mask error, and while an error is set now, as running the
             * script is what sets it, or while the script on the Scripting tab doesn't compile.
             */
            @Override
            public boolean failed() { return threw || errored || INTERNALS.reason().isPresent() || luaError() != null || scriptInvalid(); }

            @Override
            public Map<Long, Boolean> answers() { return Collections.unmodifiableMap(asked); }
        };
    }
}
