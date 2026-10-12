package dev.chromastudio.client.action;

import com.mojang.serialization.DynamicOps;
import dev.chromastudio.client.ChromaStudioClient;
import dev.chromastudio.client.ModSettings;
import dev.chromastudio.client.ui.Hud;
import dev.chromastudio.core.palette.PaletteEntry;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ItemContainerContents;

/**
 * Puts a palette's blocks in the player's hotbar: the first nine, and the rest in a shulker box in the first free
 * inventory slot. It remembers what was in every slot it touched, so the last fill can be undone.
 */
public final class HotbarFill {
    private HotbarFill() {}

    private static final int HOTBAR = 9, INVENTORY = 36, SHULKER = 27;

    /** A slot of the inventory, 0 to 35 with the hotbar first, and what it held before the fill. */
    private record Was(int slot, ItemStack stack) {}

    private static List<Was> last = List.of();

    /** Why the hotbar can't be filled now, or nothing when it can. */
    public static Optional<Component> unavailable() {
        if (Minecraft.getInstance().player == null) return Optional.of(Component.translatable("palettes.chromastudio.no_world"));
        return Give.canGive() ? Optional.empty() : Optional.of(Component.translatable("hotbar.chromastudio.no_permission"));
    }

    public static boolean canUndo() { return !last.isEmpty() && Give.canGive(); }

    /**
     * Fills the hotbar and says what happened. Entries with no item, such as water, are passed over. The slots past
     * the last entry are left as they are.
     */
    public static Component fill(List<PaletteEntry> entries) {
        Optional<Component> why = unavailable();
        if (why.isPresent()) return why.get();
        LocalPlayer player = Minecraft.getInstance().player;
        Inventory inventory = player.getInventory();
        List<ItemStack> stacks = new ArrayList<>();
        for (PaletteEntry e : entries) {
            ItemStack stack = PaletteBlocks.icon(e);
            if (!stack.isEmpty()) stacks.add(stack);
        }
        if (stacks.isEmpty()) return Component.translatable("hotbar.chromastudio.nothing");

        List<Was> before = new ArrayList<>();
        int placed = Math.min(HOTBAR, stacks.size());
        for (int i = 0; i < placed; i++) {
            ItemStack old = inventory.getItem(i).copy();
            Optional<Component> problem = Give.stack(stacks.get(i), i);
            if (problem.isPresent()) return problem.get();
            before.add(new Was(i, old));
        }

        List<ItemStack> rest = stacks.subList(placed, stacks.size());
        int boxed = 0, leftOut = rest.size();
        if (!rest.isEmpty() && ModSettings.hotbarOverflow) {
            int free = -1;
            for (int i = HOTBAR; i < INVENTORY && free < 0; i++) if (inventory.getItem(i).isEmpty()) free = i;
            if (free >= 0) {
                boxed = Math.min(SHULKER, rest.size());
                leftOut = rest.size() - boxed;
                ItemStack box = new ItemStack(Items.SHULKER_BOX);
                box.set(DataComponents.CONTAINER, ItemContainerContents.fromItems(rest.subList(0, boxed)));
                put(free, box);
                before.add(new Was(free, ItemStack.EMPTY));
            }
        }
        last = List.copyOf(before);

        List<ItemStack> strip = new ArrayList<>(Collections.nCopies(HOTBAR, ItemStack.EMPTY));
        for (int i = 0; i < placed; i++) strip.set(i, stacks.get(i));
        Hud.strip(strip);

        if (boxed > 0 && leftOut == 0) return Component.translatable("hotbar.chromastudio.filled_box", placed, boxed);
        if (boxed > 0) return Component.translatable("hotbar.chromastudio.filled_box_left", placed, boxed, leftOut);
        if (leftOut > 0) return Component.translatable(ModSettings.hotbarOverflow ? "hotbar.chromastudio.filled_no_room" : "hotbar.chromastudio.filled_left", placed, leftOut);
        return Component.translatable("hotbar.chromastudio.filled", placed);
    }

    /** Puts back what the last fill replaced, and says so. */
    public static Component undo() {
        if (last.isEmpty()) return Component.translatable("hotbar.chromastudio.nothing_to_undo");
        Optional<Component> why = unavailable();
        if (why.isPresent()) return why.get();
        for (Was was : last) put(was.slot, was.stack);
        last = List.of();
        Hud.strip(List.of());
        return Component.translatable("hotbar.chromastudio.undone");
    }

    /** Sets one inventory slot, to nothing for an empty stack: by the creative packet, or as an operator by command. */
    private static void put(int slot, ItemStack stack) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player.hasInfiniteMaterials()) {
            player.getInventory().setItem(slot, stack.copy());
            mc.gameMode.handleCreativeModeItemAdd(stack.copy(), slot < HOTBAR ? INVENTORY + slot : slot);
            return;
        }
        String where = slot < HOTBAR ? "hotbar." + slot : "inventory." + (slot - HOTBAR);
        String what = stack.isEmpty() ? "minecraft:air" : commandItem(stack) + (stack.getCount() == 1 ? "" : " " + stack.getCount());
        ChromaStudioClient.platform.sendChatCommand("item replace entity @s " + where + " with " + what);
    }

    /**
     * A stack as commands take one: its id, then any components it has of its own in brackets, which is how the
     * shulker box carries its contents. {@link Give} only promises whole stacks into the hotbar or the next free
     * slot, so the box, which goes to a slot of its own that Undo can empty again, is written out here.
     */
    private static String commandItem(ItemStack stack) {
        String id = BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
        Minecraft mc = Minecraft.getInstance();
        if (stack.getComponentsPatch().isEmpty() || mc.level == null) return id;
        DynamicOps<Tag> ops = mc.level.registryAccess().createSerializationContext(NbtOps.INSTANCE);
        List<String> parts = new ArrayList<>();
        var patch = stack.getComponentsPatch().split();
        for (var added : patch.added()) {
            if (added.type().codec() == null) continue;   // only ever sent over the network, so no command can set it
            added.encodeValue(ops).result().ifPresent(value -> parts.add(BuiltInRegistries.DATA_COMPONENT_TYPE.getKey(added.type()) + "=" + value));
        }
        for (var removed : patch.removed()) parts.add("!" + BuiltInRegistries.DATA_COMPONENT_TYPE.getKey(removed));
        return parts.isEmpty() ? id : id + "[" + String.join(",", parts) + "]";
    }
}
