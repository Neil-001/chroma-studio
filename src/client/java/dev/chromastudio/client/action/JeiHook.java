package dev.chromastudio.client.action;

import dev.chromastudio.client.ChromaStudioClient;
import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.runtime.IJeiRuntime;
import net.minecraft.resources.Identifier;

/**
 * The mod's JEI plugin, there only to be handed JEI's runtime so {@link RecipeViewers} can open recipes. Only JEI
 * loads this class: on Fabric through the {@code jei_mod_plugin} entrypoint, on NeoForge by the annotation.
 */
@JeiPlugin
public final class JeiHook implements IModPlugin {
    @Override
    public Identifier getPluginUid() { return Identifier.fromNamespaceAndPath(ChromaStudioClient.MOD_ID, "recipes"); }

    @Override
    public void onRuntimeAvailable(IJeiRuntime runtime) { RecipeViewers.jeiRuntime = runtime; }

    @Override
    public void onRuntimeUnavailable() { RecipeViewers.jeiRuntime = null; }
}
