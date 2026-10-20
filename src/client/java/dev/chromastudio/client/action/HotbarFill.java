package dev.chromastudio.client.action;

import com.mojang.serialization.DynamicOps;
import dev.chromastudio.client.ChromaStudioClient;
import dev.chromastudio.client.ModSettings;
import dev.chromastudio.client.OverflowMode;
import dev.chromastudio.client.ui.Hud;
import dev.chromastudio.core.palette.PaletteEntry;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ItemContainerContents;

/**
 * Puts a palette's blocks in the player's hotbar: the first nine, and the rest in free inventory slots, in shulker
 * boxes or on the ground, by {@link OverflowMode}. It remembers what was in every slot it touched, so the last fill
 * can be undone.
 */
public final class HotbarFill {
    private HotbarFill() {}

    private static final int HOTBAR = 9, INVENTORY = 36;
    /** The off hand, as the inventory menu numbers its slots. */
    private static final int MENU_OFFHAND = 45;

    /** A slot of the inventory, 0 to 35 with the hotbar first or 40 for the off hand, and what it held before the fill. */
    private record Was(int slot, ItemStack stack) {}

    private static List<Was> last = List.of();
    /** Whether the last fill dropped anything, which Undo can't pick up again. */
    private static boolean lastDropped;
    /** The connection the last fill was made on. Its slots are another inventory's once the player is in another world. */
    private static Object lastOn;

    /** The replies still due to the commands an operator's fill sent. */
    private static final CommandReplies REPLIES = new CommandReplies();

    /**
     * Whether a system message should be kept out of chat, as the game's reply to one of the fill's own commands.
     * Each loader asks this for every system message. The fill says what it did in the window instead.
     */
    public static boolean hidesFeedback(Component message, boolean overlay) {
        return !overlay && message.getSiblings().isEmpty() && message.getContents() instanceof TranslatableContents t && REPLIES.hides(t.getKey());
    }

    /**
     * 1.21.1's server refuses a creative player's drops that come too fast: its count rises by 20 a drop, falls by
     * one a tick and refuses past 200, so it never passes 219 and takes a drop every 20 ticks without fail. The
     * client keeps no such count here, and can't see the creative inventory's own drops to keep one, so the fill
     * sends its stacks one every 20 ticks rather than guess: the first at once, when it sent none in the last 20.
     */
    private static final int DROP_INTERVAL = 20;
    private static final List<ItemStack> DROPS = new ArrayList<>();
    /** Ticks until the next stack may go. */
    private static int dropWait;
    /** The connection the queued stacks are for. In another world they are forgotten. */
    private static Object dropsOn;

    /** Call at the end of every client tick. */
    public static void tick() {
        REPLIES.tick();
        if (dropWait > 0) dropWait--;
        sendDueDrop();
    }

    private static void sendDueDrop() {
        if (DROPS.isEmpty()) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.getConnection() != dropsOn) {
            DROPS.clear();
            return;
        }
        if (dropWait > 0) return;
        mc.gameMode.handleCreativeModeItemDrop(DROPS.removeFirst());
        dropWait = DROP_INTERVAL;
    }

    /** Forgets a fill made in a world the player has since left. */
    private static void forgetOtherWorlds() {
        if (!last.isEmpty() && lastOn != Minecraft.getInstance().getConnection()) last = List.of();
    }

    /** Why the hotbar can't be filled now, or nothing when it can. */
    public static Optional<Component> unavailable() {
        if (Minecraft.getInstance().player == null) return Optional.of(Component.translatable("palettes.chromastudio.no_world"));
        return Give.canGive() ? Optional.empty() : Optional.of(Component.translatable("hotbar.chromastudio.no_permission"));
    }

    public static boolean canUndo() {
        forgetOtherWorlds();
        return !last.isEmpty() && Give.canGive();
    }

    /** The line that names the mode a fill went by, for the dialog and the strip. */
    public static Component modeLine(OverflowMode mode) {
        return Component.translatable("hotbar.chromastudio.mode", Component.translatable("option.chromastudio.overflowmode." + mode.name().toLowerCase(Locale.ROOT)));
    }

    /**
     * Fills the hotbar and says what happened. Entries with no item, such as water, are passed over. The slots past
     * the last entry are left as they are. The blocks past the ninth go where {@link HotbarPlan} says, by the
     * settings' mode.
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

        // The inventory as it will be once the commands sent so far have run, which is what /give goes by.
        List<ItemStack> now = new ArrayList<>();
        for (int i = 0; i <= Inventory.SLOT_OFFHAND; i++) now.add(i < INVENTORY || i == Inventory.SLOT_OFFHAND ? inventory.getItem(i).copy() : ItemStack.EMPTY);
        List<Was> before = new ArrayList<>();
        int placed = Math.min(HOTBAR, stacks.size());
        for (int i = 0; i < placed; i++) {
            Optional<Component> problem = Give.stack(stacks.get(i), i);
            if (problem.isPresent()) {
                remember(before, false);
                return problem.get();
            }
            if (!player.hasInfiniteMaterials()) REPLIES.sent();
            before.add(new Was(i, now.get(i)));
            now.set(i, stacks.get(i).copy());
        }

        List<ItemStack> rest = stacks.subList(placed, stacks.size());
        OverflowMode mode = ModSettings.hotbarOverflowMode;
        List<Integer> free = new ArrayList<>();
        for (int i = HOTBAR; i < INVENTORY; i++) if (now.get(i).isEmpty()) free.add(i);
        HotbarPlan plan = HotbarPlan.of(mode, rest.size(), free.size());
        int slotted = plan.slotted(), boxes = 0, boxed = 0, dropped = 0, droppedBoxes = 0, leftOut = 0, at = 0, next = 0;
        for (; at < plan.slotted(); at++) take(free.get(next++), rest.get(at), now, before);
        for (int i = 0; i < plan.boxes().size(); i++) {
            int size = plan.boxes().get(i);
            ItemStack box = new ItemStack(Items.SHULKER_BOX);
            box.set(DataComponents.CONTAINER, ItemContainerContents.fromItems(rest.subList(at, at + size)));
            at += size;
            if (i < plan.boxesSlotted()) take(free.get(next++), box, now, before);
            else if (drop(box, now, before) == Dropped.NOT_AT_ALL) {
                leftOut += size;
                continue;
            } else droppedBoxes++;
            boxes++;
            boxed += size;
        }
        // With no boxes, what has no slot goes to the ground block by block.
        for (; at < rest.size(); at++) {
            switch (drop(rest.get(at), now, before)) {
                case ONTO_A_STACK -> slotted++;
                case ON_THE_GROUND -> dropped++;
                case NOT_AT_ALL -> leftOut++;
            }
        }
        remember(before, dropped + droppedBoxes > 0);

        List<ItemStack> strip = new ArrayList<>(Collections.nCopies(HOTBAR, ItemStack.EMPTY));
        for (int i = 0; i < placed; i++) strip.set(i, stacks.get(i));
        Hud.strip(strip, rest.isEmpty() ? null : modeLine(mode));

        MutableComponent said = Component.translatable("hotbar.chromastudio.filled", placed);
        if (slotted > 0) said.append(" ").append(Component.translatable("hotbar.chromastudio.rest.slots", slotted));
        if (boxes == 1) said.append(" ").append(Component.translatable("hotbar.chromastudio.rest.box", boxed));
        if (boxes > 1) said.append(" ").append(Component.translatable("hotbar.chromastudio.rest.boxes", boxed, boxes));
        if (dropped > 0) said.append(" ").append(Component.translatable("hotbar.chromastudio.dropped", dropped));
        if (droppedBoxes > 0) said.append(" ").append(Component.translatable("hotbar.chromastudio.dropped_boxes", droppedBoxes));
        if (leftOut > 0) said.append(" ").append(Component.translatable("hotbar.chromastudio.left_out", leftOut));
        return said;
    }

    private static void remember(List<Was> before, boolean dropped) {
        last = List.copyOf(before);
        lastDropped = dropped;
        lastOn = Minecraft.getInstance().getConnection();
    }

    /** Puts a stack in a free slot, which Undo empties again. */
    private static void take(int slot, ItemStack stack, List<ItemStack> now, List<Was> before) {
        put(slot, stack);
        before.add(new Was(slot, ItemStack.EMPTY));
        now.set(slot, stack.copy());
    }

    private enum Dropped { ON_THE_GROUND, ONTO_A_STACK, NOT_AT_ALL }

    /**
     * Drops a stack that has no slot at the player's feet: in creative with the packet the creative inventory drops
     * with, and as an operator with {@code /give}, which drops what the inventory can't hold. {@code /give} tops up
     * a stack of the same block first, so that slot is remembered for Undo.
     */
    private static Dropped drop(ItemStack stack, List<ItemStack> now, List<Was> before) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player.hasInfiniteMaterials()) {
            // Sent as fast as the server takes them, over the ticks to come. The fill counts it as dropped now.
            DROPS.add(stack.copy());
            dropsOn = mc.getConnection();
            sendDueDrop();
            return Dropped.ON_THE_GROUND;
        }
        int slot = roomFor(stack, now, player.getInventory().selected);
        if (slot >= 0) {
            if (before.stream().noneMatch(was -> was.slot == slot)) before.add(new Was(slot, now.get(slot).copy()));
            now.get(slot).grow(stack.getCount());
        }
        ChromaStudioClient.platform.sendChatCommand("give @s " + commandItem(stack));
        REPLIES.sent();
        return slot >= 0 ? Dropped.ONTO_A_STACK : Dropped.ON_THE_GROUND;
    }

    /** The slot whose stack {@code /give} would add this one to, looked for in the game's own order, or -1. */
    private static int roomFor(ItemStack stack, List<ItemStack> now, int selected) {
        List<Integer> order = new ArrayList<>(List.of(selected, Inventory.SLOT_OFFHAND));
        for (int i = 0; i < INVENTORY; i++) order.add(i);
        for (int slot : order) {
            ItemStack held = now.get(slot);
            if (!held.isEmpty() && held.isStackable() && ItemStack.isSameItemSameComponents(held, stack) && held.getCount() + stack.getCount() <= held.getMaxStackSize()) return slot;
        }
        return -1;
    }

    /** Puts back what the last fill replaced, and says so. What it dropped stays on the ground. */
    public static Component undo() {
        forgetOtherWorlds();
        if (last.isEmpty()) return Component.translatable("hotbar.chromastudio.nothing_to_undo");
        Optional<Component> why = unavailable();
        if (why.isPresent()) return why.get();
        for (Was was : last) put(was.slot, was.stack);
        last = List.of();
        Hud.strip(List.of());
        return Component.translatable(lastDropped ? "hotbar.chromastudio.undone_dropped" : "hotbar.chromastudio.undone");
    }

    /** Sets one slot, to nothing for an empty stack: by the creative packet, or as an operator by command. */
    private static void put(int slot, ItemStack stack) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        boolean offhand = slot == Inventory.SLOT_OFFHAND;
        if (player.hasInfiniteMaterials()) {
            player.getInventory().setItem(slot, stack.copy());
            mc.gameMode.handleCreativeModeItemAdd(stack.copy(), offhand ? MENU_OFFHAND : slot < HOTBAR ? INVENTORY + slot : slot);
            return;
        }
        String where = offhand ? "weapon.offhand" : slot < HOTBAR ? "hotbar." + slot : "inventory." + (slot - HOTBAR);
        String what = stack.isEmpty() ? "minecraft:air" : commandItem(stack) + (stack.getCount() == 1 ? "" : " " + stack.getCount());
        ChromaStudioClient.platform.sendChatCommand("item replace entity @s " + where + " with " + what);
        REPLIES.sent();
    }

    /**
     * A stack as commands take one: its id, then any components it has of its own in brackets, which is how the
     * shulker box carries its contents. {@link Give} only promises whole stacks into the hotbar or the next free
     * slot, so what goes to a slot of its own that Undo can empty again is written out here.
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
