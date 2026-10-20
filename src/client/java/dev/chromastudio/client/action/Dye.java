package dev.chromastudio.client.action;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.DyedItemColor;

/** Hex to dye: gives a dyeable item, such as leather armour, in exactly the target's colour. */
public final class Dye {
    private Dye() {}

    /**
     * The tag older versions listed dyeable items in. This version has none: what can be dyed is decided by the
     * {@code crafting_dye} recipes, which a client isn't sent. A mod or data pack that still fills the tag is heard.
     */
    private static final TagKey<Item> DYEABLE = TagKey.create(Registries.ITEM, ResourceLocation.withDefaultNamespace("dyeable"));

    /** The targets of the game's own dye recipes. */
    private static final List<String> VANILLA = List.of("leather_helmet", "leather_chestplate", "leather_leggings", "leather_boots",
            "leather_horse_armor", "wolf_armor");

    /** Every item known to be dyeable, each already in this colour: the game's own, then any the tag adds. */
    public static List<ItemStack> items(int rgb) {
        Set<Item> items = new LinkedHashSet<>();
        for (String path : VANILLA) BuiltInRegistries.ITEM.getOptional(ResourceLocation.withDefaultNamespace(path)).ifPresent(items::add);
        for (Holder<Item> item : BuiltInRegistries.ITEM.getTagOrEmpty(DYEABLE)) items.add(item.value());
        List<ItemStack> out = new ArrayList<>();
        for (Item item : items) out.add(dyed(item, rgb));
        return out;
    }

    public static ItemStack dyed(Item item, int rgb) {
        ItemStack stack = new ItemStack(item);
        stack.set(DataComponents.DYED_COLOR, new DyedItemColor(rgb & 0xFFFFFF, true));
        return stack;
    }

    /** Gives one dyed item. Returns the reason when it can't, as {@link Give#stack} does. */
    public static Optional<Component> give(ItemStack dyed) { return Give.stack(dyed, -1); }
}
