package dev.chromastudio.client.action;

import dev.chromastudio.client.ChromaStudioClient;
import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.runtime.IJeiRuntime;
import net.minecraft.resources.ResourceLocation;

@JeiPlugin
public final class JeiHook implements IModPlugin {
    @Override
    public ResourceLocation getPluginUid() { return ResourceLocation.fromNamespaceAndPath(ChromaStudioClient.MOD_ID, "recipes"); }

    @Override
    public void onRuntimeAvailable(IJeiRuntime runtime) { RecipeViewers.jeiRuntime = runtime; }

    @Override
    public void onRuntimeUnavailable() { RecipeViewers.jeiRuntime = null; }
}
