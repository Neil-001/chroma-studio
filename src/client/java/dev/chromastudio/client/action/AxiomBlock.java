package dev.chromastudio.client.action;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import dev.chromastudio.client.ChromaCommand;
import dev.chromastudio.client.ChromaStudioClient;
import dev.chromastudio.client.index.BlockIndex;
import dev.chromastudio.client.index.Entry;
import dev.chromastudio.client.ui.tabs.MatchState;
import dev.chromastudio.core.color.Face;
import java.lang.reflect.Method;
import java.util.Optional;
import java.util.ServiceLoader;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Makes Axiom's active block, the one its editor's tools paint with, the Match target. Axiom is optional and closed
 * source, so its public API ({@code com.moulberry.axiomclientapi}, which Axiom nests in its jar and supplies through
 * {@link ServiceLoader}) is reached by name rather than compiled against: the build then needs no extra repository,
 * and NeoForge, which Axiom doesn't support, compiles the same code. Every call is caught.
 * <p>
 * Axiom's API only gives the active block while its editor is open. Outside it, the API answers plain stone, which
 * can't be told apart from stone chosen on purpose (and stone is also Axiom's own choice after a restart), so a stone
 * answer comes with a note saying so.
 */
public final class AxiomBlock {
    private AxiomBlock() {}

    private static final String MOD_ID = "axiom";
    private static final String SERVICE = "com.moulberry.axiomclientapi.service.ToolService";

    /** Axiom's service and its method, looked up on first use. Null until then, or for good once the lookup failed. */
    private static Object service;
    private static Method getActiveBlock;
    private static boolean lookedUp;

    /** What {@link #take} did: whether the target changed, and a line saying how it went. */
    public record Result(boolean taken, Component message) {}

    public static boolean isAvailable() {
        return ChromaStudioClient.platform != null && ChromaStudioClient.platform.isModLoaded(MOD_ID);
    }

    /** Why the action can't be used, or nothing when it can. */
    public static Optional<Component> whyNot() {
        return isAvailable() ? Optional.empty() : Optional.of(Component.translatable("axiom.chromastudio.missing"));
    }

    /** Adds {@code /chroma axiom}. Call once at client start. */
    public static void install() {
        ChromaCommand.SUBCOMMANDS.add(new ChromaCommand.Subcommand() {
            @Override
            public <S> void attach(LiteralArgumentBuilder<S> root) {
                root.then(LiteralArgumentBuilder.<S>literal("axiom").executes(_ -> {
                    Result result = take();
                    ChromaCommand.say(result.message());
                    if (result.taken()) ChromaStudioClient.openWindowSoon("match");
                    return result.taken() ? 1 : 0;
                }));
            }
        });
    }

    /** Makes Axiom's active block the Match target, as a block taken from the hotbar is: no face and no biome. */
    public static Result take() {
        Optional<Component> why = whyNot();
        if (why.isPresent()) return new Result(false, why.get());
        BlockState state = activeBlock();
        if (state == null) return new Result(false, Component.translatable("axiom.chromastudio.failed"));
        BlockIndex index = BlockIndex.get();
        if (!index.ready()) return new Result(false, Component.translatable("eyedropper.chromastudio.not_ready"));
        Entry entry = index.entryOf(state);
        if (entry == null) return new Result(false, Component.translatable("eyedropper.chromastudio.not_indexed", state.getBlock().getName()));
        MatchState.setEntry(entry, state, Face.ANY, null);
        MatchState.remember();
        String key = state.is(Blocks.STONE) ? "axiom.chromastudio.taken.stone" : "axiom.chromastudio.taken";
        return new Result(true, Component.translatable(key, entry.name()));
    }

    /** Axiom's active block, or null when Axiom couldn't be asked. */
    private static BlockState activeBlock() {
        try {
            if (!lookedUp) {
                lookedUp = true;
                Class<?> api = Class.forName(SERVICE);
                // Axiom registers its implementation in its own jar, which the API class's loader sees.
                service = ServiceLoader.load(api, api.getClassLoader()).findFirst().orElse(null);
                getActiveBlock = api.getMethod("getActiveBlock");
            }
            if (service == null) return null;
            return getActiveBlock.invoke(service) instanceof BlockState state ? state : null;
        } catch (Throwable t) {
            ChromaStudioClient.LOGGER.warn("Axiom didn't give its active block", t);
            return null;
        }
    }
}
