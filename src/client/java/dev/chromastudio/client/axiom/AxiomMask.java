package dev.chromastudio.client.axiom;

import dev.chromastudio.core.palette.QueuedChanges;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
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
 * <p>Only an Axiom with no {@code MaskManager} at all counts as having no masks: the tools then change every block they
 * reach, and their panels say so before the player starts. When it's there but not as this version knows it, or a call
 * into it has failed, the masks can't be read, so the tools change nothing until the game restarts, and a stroke or
 * Apply during which they failed sends nothing ({@link QueuedChanges#check}). A mask that can't be read never lets
 * through what it might be keeping out.
 */
final class AxiomMask {
    private AxiomMask() {}

    /** Whether a block may change, and whether the answers can be trusted. Ask it on the client thread, as Axiom does. */
    interface Test extends QueuedChanges.Mask {}

    private record Fixed(boolean allows, boolean failed) implements Test {
        @Override
        public boolean allows(int x, int y, int z) { return allows; }
    }

    private static final Test EVERY_BLOCK = new Fixed(true, false), FAILED = new Fixed(false, true);

    private record Handles(MethodHandle destMask, MethodHandle context, MethodHandle setPosition, MethodHandle test) {}

    private static final String MANAGER = "com.moulberry.axiom.mask.MaskManager";

    private static final AxiomInternals<Handles> INTERNALS = new AxiomInternals<>("Axiom's tool masks",
            Component.translatable("axiom.chromastudio.mask.changed"), () -> {
                MethodHandles.Lookup lookup = MethodHandles.publicLookup();
                Class<?> manager = Class.forName(MANAGER);
                Class<?> element = Class.forName("com.moulberry.axiom.mask.MaskElement");
                Class<?> context = Class.forName("com.moulberry.axiom.mask.MaskContext");
                return new Handles(lookup.findStatic(manager, "getDestMask", MethodType.methodType(element)),
                        lookup.findConstructor(context, MethodType.methodType(void.class, Level.class)),
                        lookup.findVirtual(context, "setPosition", MethodType.methodType(void.class, int.class, int.class, int.class)),
                        lookup.findVirtual(element, "test", MethodType.methodType(boolean.class, context)));
            });

    /** Whether this Axiom has no tool masks at all, as an older one might not, rather than masks this version can't read. */
    private static boolean none() {
        if (INTERNALS.found()) return false;
        try {
            Class.forName(MANAGER, false, AxiomMask.class.getClassLoader());
            return false;
        } catch (ClassNotFoundException _) {
            return true;
        } catch (LinkageError _) {
            return false;
        }
    }

    /**
     * Why the masks aren't followed, or nothing when they are: this Axiom has none, so the tools change every block
     * they reach, or they can't be read, so the tools change nothing until the game restarts.
     */
    static Optional<Component> unavailable() {
        if (INTERNALS.reason().isEmpty()) return Optional.empty();
        return Optional.of(Component.translatable(INTERNALS.found() ? "axiom.chromastudio.mask.failed"
                : none() ? "axiom.chromastudio.mask.changed" : "axiom.chromastudio.mask.unknown"));
    }

    /**
     * The masks as they're set now, for one stroke or preview, in {@code level}. Every block passes when this Axiom has
     * no masks, which the panels say. When they can't be read, or a call into them fails, this session or during the
     * test's use, no block passes and the test says it failed.
     */
    static Test now(Level level) {
        if (INTERNALS.reason().isPresent()) return none() ? EVERY_BLOCK : FAILED;
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
            private boolean failed;

            @Override
            public boolean allows(int x, int y, int z) {
                if (failed) return false;
                try {
                    h.setPosition.invoke(context, x, y, z);
                    return (boolean) h.test.invoke(mask, context);
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
