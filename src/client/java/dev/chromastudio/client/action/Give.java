package dev.chromastudio.client.action;

import com.mojang.serialization.DynamicOps;
import dev.chromastudio.client.ChromaStudioClient;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.permissions.Permissions;
import net.minecraft.world.item.ItemStack;

/**
 * Gives the player an item: with the creative inventory's own packet in creative mode, and with a command, as if
 * typed, for an operator in survival. Nobody else can be given anything, and callers grey their buttons out.
 */
public final class Give {
    private Give() {}

    /** The slots of the player's inventory before the hotbar, as the inventory menu numbers them. */
    private static final int MENU_HOTBAR = 36, HOTBAR = 9;

    public static boolean canGive() {
        LocalPlayer player = Minecraft.getInstance().player;
        return player != null && (player.hasInfiniteMaterials() || operator(player));
    }

    private static boolean operator(LocalPlayer player) { return player.permissions().hasPermission(Permissions.COMMANDS_GAMEMASTER); }

    /**
     * Puts a stack in a hotbar slot, 0 to 8, or with -1 in the next free slot of the inventory. Returns the reason it
     * couldn't, or nothing when it did.
     */
    public static Optional<Component> stack(ItemStack stack, int hotbarSlot) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null || mc.gameMode == null) return Optional.of(Component.translatable("give.chromastudio.no_world"));
        if (stack.isEmpty()) return Optional.of(Component.translatable("give.chromastudio.no_item"));
        if (player.hasInfiniteMaterials()) {
            int slot = hotbarSlot >= 0 ? hotbarSlot : player.getInventory().getFreeSlot();
            if (slot < 0) return Optional.of(Component.translatable("give.chromastudio.full"));
            player.getInventory().setItem(slot, stack.copy());
            mc.gameMode.handleCreativeModeItemAdd(stack.copy(), slot < HOTBAR ? MENU_HOTBAR + slot : slot);
            return Optional.empty();
        }
        if (!operator(player)) return Optional.of(Component.translatable("give.chromastudio.no_permission"));
        String item = commandItem(stack);
        String count = stack.getCount() == 1 ? "" : " " + stack.getCount();
        ChromaStudioClient.platform.sendChatCommand(hotbarSlot >= 0 ? "item replace entity @s hotbar." + hotbarSlot + " with " + item + count
                : "give @s " + item + count);
        return Optional.empty();
    }

    /** A stack as commands take one: its id, then any components it has of its own in brackets. */
    public static String commandItem(ItemStack stack) {
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
