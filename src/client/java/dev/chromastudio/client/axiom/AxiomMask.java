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
 * It's internal to Axiom, so it goes through {@link AxiomInternals}. When this version of Axiom lacks it, the brush
 * changes every block it reaches; when a call into it fails, the brush changes nothing; either way its panel says so.
 */
final class AxiomMask {
    private AxiomMask() {}

    /** Whether a block may change. */
    @FunctionalInterface
    interface Test {
        boolean allows(int x, int y, int z);
    }

    private static final Test EVERY_BLOCK = (_, _, _) -> true, NO_BLOCK = (_, _, _) -> false;

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

    /**
     * Why the masks aren't followed, or nothing when they are: this version of Axiom doesn't share them, so the tool
     * changes every block it reaches, or a call into them failed, so it changes nothing until the game restarts.
     */
    static Optional<Component> unavailable() {
        if (INTERNALS.reason().isEmpty()) return Optional.empty();
        return Optional.of(Component.translatable(INTERNALS.found() ? "axiom.chromastudio.mask.failed" : "axiom.chromastudio.mask.changed"));
    }

    /**
     * The masks as they're set now, for one stroke or preview, in {@code level}. Every block passes when this version
     * of Axiom doesn't share them, which the panel says. Once a call into them has failed, no block passes, for the
     * rest of the session, so a mask that stopped working never lets through what it was keeping out.
     * Ask it on the client thread, as Axiom does.
     */
    static Test now(Level level) {
        if (INTERNALS.reason().isPresent()) return INTERNALS.found() ? NO_BLOCK : EVERY_BLOCK;
        Handles h = INTERNALS.handles();
        try {
            Object mask = h.destMask.invoke();
            Object context = h.context.invoke(level);
            boolean[] broken = {false};
            return (x, y, z) -> {
                if (broken[0]) return false;
                try {
                    h.setPosition.invoke(context, x, y, z);
                    return (boolean) h.test.invoke(mask, context);
                } catch (Throwable t) {
                    INTERNALS.failed(t);
                    broken[0] = true;
                    return false;
                }
            };
        } catch (Throwable t) {
            INTERNALS.failed(t);
            return NO_BLOCK;
        }
    }
}
