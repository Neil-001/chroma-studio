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

/**
 * The player's tool masks in Axiom's editor, which Axiom's own brushes and its Script Brush keep to. Axiom's API
 * doesn't apply them to a tool's changes, so the tools that change blocks ask Axiom's {@code MaskManager} for the
 * destination mask as the Script Brush does: the mask set under Tool Masks, kept within the selection when there is
 * one, or every block when neither is set. That's how a player limits a brush to air, or to existing blocks, as with
 * any Axiom brush. It's internal to Axiom, so it goes through {@link AxiomInternals}.
 *
 * <p>When the masks aren't as this version knows them, missing or moved included, or a call into them has failed, they
 * can't be read, so the tools change nothing until the game restarts, and their panels say so before the player
 * starts. A stroke or Apply during which they failed sends nothing ({@link QueuedChanges#check}). A mask that can't be
 * read never lets through what it might be keeping out, so no version of Axiom counts as having none.
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

    /**
     * @param luaError Axiom's last Lua mask error: a mask's script that fails makes Axiom answer "no" for that block,
     *                 and say so only here, so an answer given just before the failure would otherwise look trusted
     */
    private record Handles(MethodHandle destMask, MethodHandle context, MethodHandle setPosition, MethodHandle test, MethodHandle luaError) {}

    private static final AxiomInternals<Handles> INTERNALS = new AxiomInternals<>("Axiom's tool masks",
            Component.translatable("axiom.chromastudio.mask.unknown"), () -> {
                MethodHandles.Lookup lookup = MethodHandles.publicLookup();
                Class<?> manager = Class.forName("com.moulberry.axiom.mask.MaskManager");
                Class<?> element = Class.forName("com.moulberry.axiom.mask.MaskElement");
                Class<?> context = Class.forName("com.moulberry.axiom.mask.MaskContext");
                return new Handles(lookup.findStatic(manager, "getDestMask", MethodType.methodType(element)),
                        lookup.findConstructor(context, MethodType.methodType(void.class, Level.class)),
                        lookup.findVirtual(context, "setPosition", MethodType.methodType(void.class, int.class, int.class, int.class)),
                        lookup.findVirtual(element, "test", MethodType.methodType(boolean.class, context)),
                        lookup.findStaticGetter(Class.forName("com.moulberry.axiom.editor.windows.global_mask.ToolMaskWindow"), "luaExecutionError", String.class));
            });

    /**
     * Why the masks can't be followed, or nothing when they can: they aren't as this version knows them, or a call
     * into them failed. Either way the tools change nothing until the game restarts.
     */
    static Optional<Component> unavailable() {
        if (INTERNALS.reason().isEmpty()) return Optional.empty();
        return Optional.of(Component.translatable(INTERNALS.found() ? "axiom.chromastudio.mask.failed" : "axiom.chromastudio.mask.unknown"));
    }

    /**
     * The masks as they're set now, for one stroke or preview, in {@code level}. When they can't be read, or a call
     * into them fails, this session or during the test's use, no block passes and the test says it failed.
     */
    static Test now(Level level) {
        if (INTERNALS.reason().isPresent()) return FAILED;
        Handles h = INTERNALS.handles();
        Object mask, context, errorBefore;
        try {
            mask = h.destMask.invoke();
            context = h.context.invoke(level);
            errorBefore = h.luaError.invoke();
        } catch (Throwable t) {
            INTERNALS.failed(t);
            return FAILED;
        }
        return new Test() {
            private boolean failed;
            /**
             * Each position's first answer. A mask's script can answer differently each time it's asked, at random say,
             * and one stroke or preview must give a position one answer, or a door could be let through by halves.
             */
            private final Map<Long, Boolean> asked = new HashMap<>();

            @Override
            public boolean allows(int x, int y, int z) {
                if (failed) return false;
                return asked.computeIfAbsent(BuildSearch.pack(x, y, z), _ -> ask(x, y, z));
            }

            private boolean ask(int x, int y, int z) {
                try {
                    h.setPosition.invoke(context, x, y, z);
                    boolean allows = (boolean) h.test.invoke(mask, context);
                    // A new Lua error, a string Axiom makes afresh each time, fails the whole stroke or preview, as a throw does.
                    Object error = h.luaError.invoke();
                    if (error != null && error != errorBefore) {
                        failed = true;
                        return false;
                    }
                    return allows;
                } catch (Throwable t) {
                    INTERNALS.failed(t);
                    failed = true;
                    return false;
                }
            }

            @Override
            public boolean failed() { return failed; }
        };
    }
}
