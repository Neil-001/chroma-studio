package dev.chromastudio.client.axiom;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.Optional;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.Level;

/**
 * The player's tool masks in Axiom's editor, which Axiom's own brushes and its Script Brush keep to. Axiom's API
 * doesn't apply them to a tool's changes, so the palette brush asks Axiom's {@code MaskManager} for the destination
 * mask as the Script Brush does: the mask set under Tool Masks, kept within the selection when there is one, or every
 * block when neither is set. That's how a player limits Scatter to air, or to existing blocks, as with any Axiom brush.
 * It's internal to Axiom, so it goes through {@link AxiomInternals}; when it fails, the brush changes every block it
 * reaches and its panel says the masks aren't followed.
 */
final class AxiomMask {
    private AxiomMask() {}

    /** Whether a block may change. */
    @FunctionalInterface
    interface Test {
        boolean allows(int x, int y, int z);
    }

    private static final Test EVERY_BLOCK = (_, _, _) -> true;

    private record Handles(MethodHandle destMask, MethodHandle context, MethodHandle setPosition, MethodHandle test) {}

    private static final AxiomInternals<Handles> INTERNALS = new AxiomInternals<>("Axiom's tool masks",
            Component.translatable("axiom.chromastudio.mask.changed"), () -> {
                MethodHandles.Lookup lookup = MethodHandles.publicLookup();
                Class<?> manager = Class.forName("com.moulberry.axiom.mask.MaskManager");
                Class<?> element = Class.forName("com.moulberry.axiom.mask.MaskElement");
                Class<?> context = Class.forName("com.moulberry.axiom.mask.MaskContext");
                return new Handles(lookup.findStatic(manager, "getDestMask", MethodType.methodType(element)),
                        lookup.findConstructor(context, MethodType.methodType(void.class, Level.class)),
                        lookup.findVirtual(context, "setPosition", MethodType.methodType(void.class, int.class, int.class, int.class)),
                        lookup.findVirtual(element, "test", MethodType.methodType(boolean.class, context)));
            });

    /** Why the masks aren't followed, or nothing when they are. */
    static Optional<Component> unavailable() { return INTERNALS.reason(); }

    /**
     * The masks as they're set now, for one stroke or preview, in {@code level}. Every block passes when they can't
     * be read. Ask it on the client thread, as Axiom does.
     */
    static Test now(Level level) {
        if (INTERNALS.reason().isPresent()) return EVERY_BLOCK;
        Handles h = INTERNALS.handles();
        try {
            Object mask = h.destMask.invoke();
            Object context = h.context.invoke(level);
            return (x, y, z) -> {
                try {
                    h.setPosition.invoke(context, x, y, z);
                    return (boolean) h.test.invoke(mask, context);
                } catch (Throwable t) {
                    INTERNALS.failed(t);
                    return true;
                }
            };
        } catch (Throwable t) {
            INTERNALS.failed(t);
            return EVERY_BLOCK;
        }
    }
}
