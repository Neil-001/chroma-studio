package dev.chromastudio.client.axiom;

import dev.chromastudio.client.ChromaStudioClient;
import java.util.Optional;
import net.minecraft.network.chat.Component;

final class AxiomInternals<T> {
    interface Lookup<T> {
        T find() throws Throwable;
    }

    private final String what;
    private final Component reason;
    private final Lookup<T> lookup;
    private T handles;
    private boolean looked, broken;

    AxiomInternals(String what, Component reason, Lookup<T> lookup) {
        this.what = what;
        this.reason = reason;
        this.lookup = lookup;
    }

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

    synchronized boolean found() {
        reason();
        return handles != null;
    }

    synchronized T handles() {
        if (!found()) throw new IllegalStateException(what + " weren't found");
        return handles;
    }

    synchronized void forget() {
        handles = null;
        looked = false;
        broken = false;
    }

    synchronized void failed(Throwable t) {
        if (broken) return;
        broken = true;
        ChromaStudioClient.LOGGER.warn("A call into {} failed, so they aren't used until the game restarts", what, t);
    }
}
