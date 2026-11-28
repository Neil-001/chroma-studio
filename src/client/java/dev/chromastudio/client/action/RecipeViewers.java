package dev.chromastudio.client.action;

import dev.chromastudio.client.ChromaStudioClient;
import java.lang.reflect.Method;
import me.shedaniel.rei.api.client.view.ViewSearchBuilder;
import me.shedaniel.rei.api.common.util.EntryStacks;
import mezz.jei.api.constants.VanillaTypes;
import mezz.jei.api.recipe.RecipeIngredientRole;
import mezz.jei.api.runtime.IJeiRuntime;
import net.minecraft.world.item.ItemStack;

public final class RecipeViewers {
    private RecipeViewers() {}

    static volatile Object jeiRuntime;

    public static boolean available() { return loaded("jei") || loaded("roughlyenoughitems") || loaded("emi"); }

    private static boolean loaded(String id) { return ChromaStudioClient.platform.isModLoaded(id); }

    public static boolean show(ItemStack stack) {
        if (stack.isEmpty()) return false;
        try {
            if (loaded("jei") && jeiRuntime != null) return Jei.show(jeiRuntime, stack);
            if (loaded("roughlyenoughitems")) return Rei.show(stack);
            if (loaded("emi")) return Emi.show(stack);
        } catch (Throwable t) {
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
