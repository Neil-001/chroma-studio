package dev.chromastudio.client.axiom;

import dev.chromastudio.client.ChromaStudioClient;
import java.util.Optional;
import net.minecraft.network.chat.Component;

/**
 * Handles into one Axiom feature that isn't in its API, such as its extra hotbars. Axiom's internals change between
 * releases without notice, so nothing is compiled against them: the caller's lookup finds each method by its exact
 * signature, with {@code MethodHandles.publicLookup().findStatic}, the first time the feature is asked about. If any is
 * missing, or a call through them later throws, the feature is off for the rest of the session with the caller's
 * reason, and the log says why once.
 */
final class AxiomInternals<T> {
    /** Finds the handles. Anything it throws, a missing class or a changed signature, turns the feature off. */
    interface Lookup<T> {
        T find() throws Throwable;
    }

    private final String what;
    private final Component reason;
    private final Lookup<T> lookup;
    private T handles;
    private boolean looked, broken;

    /**
     * @param what   the feature, for the log, as in "Axiom's hotbars"
     * @param reason what the player reads on a greyed-out control once the feature is off, saying what they can do
     */
    AxiomInternals(String what, Component reason, Lookup<T> lookup) {
        this.what = what;
        this.reason = reason;
        this.lookup = lookup;
    }

    /** Why the feature can't be used, or nothing when its handles were found and no call has failed since. */
    synchronized Optional<Component> reason() {
        if (!looked) {
            looked = true;
            try {
                handles = lookup.find();
            } catch (Throwable t) {
                broken = true;
                ChromaStudioClient.LOGGER.warn("Axiom is loaded, but {} aren't as this version of Chroma Studio knows them, so they aren't used", what, t);
            }
        }
        return broken ? Optional.of(reason) : Optional.empty();
    }

    /** The handles. Call only once {@link #reason} is empty. */
    synchronized T handles() {
        if (reason().isPresent()) throw new IllegalStateException(what + " aren't available");
        return handles;
    }

    /** Turns the feature off after a call through the handles threw, logging it the first time. */
    synchronized void failed(Throwable t) {
        if (broken) return;
        broken = true;
        ChromaStudioClient.LOGGER.warn("A call into {} failed, so they aren't used until the game restarts", what, t);
    }
}
