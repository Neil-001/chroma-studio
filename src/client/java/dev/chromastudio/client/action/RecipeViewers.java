package dev.chromastudio.client.action;

import dev.chromastudio.client.ChromaStudioClient;
import java.lang.reflect.Method;
import me.shedaniel.rei.api.client.view.ViewSearchBuilder;
import me.shedaniel.rei.api.common.util.EntryStacks;
import mezz.jei.api.constants.VanillaTypes;
import mezz.jei.api.recipe.RecipeIngredientRole;
import mezz.jei.api.runtime.IJeiRuntime;
import net.minecraft.world.item.ItemStack;

/**
 * Opens an item's recipes in JEI, REI or EMI, whichever is installed. All three are optional: each one's classes
 * are named only in its own inner class here, which is entered only once the mod is known to be loaded, and any
 * failure there just means no recipe is shown.
 */
public final class RecipeViewers {
    private RecipeViewers() {}

    /** JEI's runtime, which {@link JeiHook} is handed while JEI is running. It is held as an Object so this class loads without JEI. */
    static volatile Object jeiRuntime;

    /** Whether a recipe viewer is installed. */
    public static boolean available() { return loaded("jei") || loaded("roughlyenoughitems") || loaded("emi"); }

    private static boolean loaded(String id) { return ChromaStudioClient.platform.isModLoaded(id); }

    /** Shows how to make an item. Returns false when no viewer is installed or the one that is couldn't show it. */
    public static boolean show(ItemStack stack) {
        if (stack.isEmpty()) return false;
        try {
            if (loaded("jei") && jeiRuntime != null) return Jei.show(jeiRuntime, stack);
            if (loaded("roughlyenoughitems")) return Rei.show(stack);
            if (loaded("emi")) return Emi.show(stack);
        } catch (Throwable t) {
            // A viewer of a version that has moved its classes, or one that isn't ready yet.
            ChromaStudioClient.LOGGER.warn("Couldn't open the recipe viewer", t);
        }
        return false;
    }

    private static final class Jei {
        static boolean show(Object runtime, ItemStack stack) {
            IJeiRuntime jei = (IJeiRuntime) runtime;
            jei.getRecipesGui().show(jei.getJeiHelpers().getFocusFactory().createFocus(RecipeIngredientRole.OUTPUT, VanillaTypes.ITEM_STACK, stack));
            return true;
        }
    }

    private static final class Rei {
        static boolean show(ItemStack stack) {
            return ViewSearchBuilder.builder().addRecipesFor(EntryStacks.of(stack)).open();
        }
    }

    /**
     * EMI has no release for this Minecraft yet, so there is nothing to compile against. Until there is, this calls
     * what its API has long offered, {@code EmiApi.displayRecipes(EmiStack.of(stack))}, by name. It hasn't been run
     * against a real EMI; when one is out, compile against it and call it directly as the others are.
     */
    private static final class Emi {
        static boolean show(ItemStack stack) throws ReflectiveOperationException {
            Class<?> ingredient = Class.forName("dev.emi.emi.api.stack.EmiIngredient");
            Object emiStack = Class.forName("dev.emi.emi.api.stack.EmiStack").getMethod("of", ItemStack.class).invoke(null, stack);
            Method display = Class.forName("dev.emi.emi.api.EmiApi").getMethod("displayRecipes", ingredient);
            display.invoke(null, emiStack);
            return true;
        }
    }
}
