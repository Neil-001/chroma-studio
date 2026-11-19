package dev.chromastudio.client.action;

import com.mojang.serialization.DynamicOps;
import dev.chromastudio.client.ChromaStudioClient;
import dev.chromastudio.client.ModSettings;
import dev.chromastudio.client.OverflowMode;
import dev.chromastudio.client.axiom.AxiomBridge;
import dev.chromastudio.client.ui.Hud;
import dev.chromastudio.core.hotbar.HotbarWrites;
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
 * boxes or on the ground, by {@link OverflowMode}; or, when the setting asks for it and {@link AxiomBridge#hotbarsUnavailable} says it can,
 * in Axiom's next hotbars. It remembers what was in every slot it touched, so the last fill can be undone.
 *
 * <p>With Axiom, the hotbar a fill wrote may not be the one in hand by the time of Undo, since Axiom swaps the
 * player's hotbar for another of its own. So what a fill replaced in the hotbar is kept by Axiom's number for it, and
 * Undo writes it back there, through Axiom when that hotbar is no longer the one held.
 */
public final class HotbarFill {
    private HotbarFill() {}

    private static final int HOTBAR = 9, INVENTORY = 36;
    /** The off hand, as the inventory menu numbers its slots. */
    private static final int MENU_OFFHAND = 45;

    /** A slot of the inventory, 0 to 35 with the hotbar first or 40 for the off hand, and what it held before the fill. */
    private record Was(int slot, ItemStack stack) {}

    private static List<Was> last = List.of();
    /** Axiom's number for the hotbar the last fill's first nine went into, {@link #NO_AXIOM} or {@link #UNKNOWN}. */
    private static int lastHotbar = -1;
    /** For {@link #lastHotbar}: Axiom isn't loaded, so the hotbar in hand is the only one. */
    private static final int NO_AXIOM = -1;
    /** For {@link #lastHotbar}: Axiom is loaded but couldn't say which hotbar was held, so Undo can't know where it belongs. */
    private static final int UNKNOWN = -2;
    /** What the last fill replaced when it went into Axiom's hotbars, the first nine included, kept by Axiom's hotbar. */
    private static List<HotbarWrites.Was<ItemStack>> lastAxiom = List.of();
    /** Whether the last fill went into Axiom's hotbars, failed partway and couldn't put back all it wrote, which only Undo can. */
    private static boolean lastFailed;
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

    /** Call at the end of every client tick. */
    public static void tick() { REPLIES.tick(); }

    /** Forgets a fill made in a world the player has since left. */
    private static void forgetOtherWorlds() {
        if (lastOn != Minecraft.getInstance().getConnection()) {
            last = List.of();
            lastAxiom = List.of();
            lastFailed = false;
        }
    }

    /** Why the hotbar can't be filled now, or nothing when it can. */
    public static Optional<Component> unavailable() {
        if (Minecraft.getInstance().player == null) return Optional.of(Component.translatable("palettes.chromastudio.no_world"));
        return Give.canGive() ? Optional.empty() : Optional.of(Component.translatable("hotbar.chromastudio.no_permission"));
    }

    /**
     * Why Fill can't be used now, or nothing when it can: as {@link #unavailable}, and not while a failed fill waits
     * for Undo, since a new fill would take the place of what Undo has to put back.
     */
    public static Optional<Component> whyNoFill() {
        forgetOtherWorlds();
        return unavailable().or(() -> lastFailed ? Optional.of(Component.translatable("hotbar.chromastudio.axiom.undo_first")) : Optional.empty());
    }

    /** Why the last fill can't be undone now, or nothing when it can. */
    public static Optional<Component> whyNoUndo() {
        forgetOtherWorlds();
        if (last.isEmpty() && lastAxiom.isEmpty()) return Optional.of(Component.translatable("hotbar.chromastudio.nothing_to_undo"));
        Optional<Component> why = unavailable();
        if (why.isPresent()) return why;
        // Axiom couldn't say which hotbar the fill went into, so there is no knowing where to put it back.
        if (lastHotbar == UNKNOWN) return Optional.of(AxiomBridge.hotbarsUnsupported().orElse(Component.translatable("hotbar.chromastudio.axiom.changed")));
        return undoNeedsAxiom() ? AxiomBridge.hotbarsUnreachable() : Optional.empty();
    }

    /**
     * Whether Undo has to go through Axiom: the fill wrote Axiom's other hotbars, or the player has since switched
     * away from the hotbar it filled, which only Axiom can reach now.
     */
    private static boolean undoNeedsAxiom() {
        return !lastAxiom.isEmpty() || lastHotbar == UNKNOWN || lastHotbar >= 0 && heldHotbar() != lastHotbar;
    }

    /** Whether a fill puts the blocks past the ninth in Axiom's hotbars: the setting is on and Axiom can take them. */
    public static boolean usesAxiom() { return ModSettings.hotbarAxiom && AxiomBridge.hotbarsUnavailable().isEmpty(); }

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
        Optional<Component> why = whyNoFill();
        if (why.isPresent()) return why.get();
        LocalPlayer player = Minecraft.getInstance().player;
        Inventory inventory = player.getInventory();
        List<ItemStack> stacks = new ArrayList<>();
        // Each block's place in the palette, from 1, which is how the player finds it there.
        List<Integer> places = new ArrayList<>();
        for (int i = 0; i < entries.size(); i++) {
            ItemStack stack = PaletteBlocks.icon(entries.get(i));
            if (stack.isEmpty()) continue;
            stacks.add(stack);
            places.add(i + 1);
        }
        if (stacks.isEmpty()) return Component.translatable("hotbar.chromastudio.nothing");
        if (stacks.size() > HOTBAR && usesAxiom()) return fillAxiom(stacks, places);
        int hotbar = heldHotbar();

        // The inventory as it will be once the commands sent so far have run, which is what /give goes by.
        List<ItemStack> now = new ArrayList<>();
        for (int i = 0; i <= Inventory.SLOT_OFFHAND; i++) now.add(i < INVENTORY || i == Inventory.SLOT_OFFHAND ? inventory.getItem(i).copy() : ItemStack.EMPTY);
        List<Was> before = new ArrayList<>();
        int placed = Math.min(HOTBAR, stacks.size());
        for (int i = 0; i < placed; i++) {
            Optional<Component> problem = Give.stack(stacks.get(i), i);
            if (problem.isPresent()) {
                remember(before, false, hotbar);
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
        remember(before, dropped + droppedBoxes > 0, hotbar);

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

    /**
     * Puts the first nine blocks in the hotbar and the rest, nine to each, in the Axiom hotbars after the one held,
     * replacing what was there, all or nothing. Axiom saves those hotbars itself a few seconds later.
     */
    private static Component fillAxiom(List<ItemStack> stacks, List<Integer> places) {
        LocalPlayer player = Minecraft.getInstance().player;
        // Checked before anything is written, so a block the world has turned off never leaves a fill half done.
        for (ItemStack stack : stacks)
            if (!stack.isItemEnabled(player.connection.enabledFeatures())) return Component.translatable("give.chromastudio.disabled", stack.getHoverName());
        int active;
        try {
            active = AxiomBridge.activeHotbar();
        } catch (Throwable _) {
            return Component.translatable("hotbar.chromastudio.axiom.failed");
        }
        List<HotbarWrites.Slot> slots = new ArrayList<>();
        for (int i = 0; i < HOTBAR; i++) slots.add(new HotbarWrites.Slot(active, i));
        int rest = stacks.size() - HOTBAR;
        for (HotbarPlan.Spot at : HotbarPlan.axiom(active, rest)) slots.add(new HotbarWrites.Slot(at.hotbar(), at.slot()));
        HotbarWrites.Result<ItemStack> result = HotbarWrites.write(axiomHotbars(active), slots, stacks);
        if (!result.worked()) {
            ChromaStudioClient.LOGGER.warn("Axiom's hotbars didn't take the fill", result.failure());
            // A write that quietly didn't take is as much a sign of a changed Axiom as one that threw.
            AxiomBridge.hotbarsFailed(result.failure());
            // Putting back what it wrote failed too, so Undo is left to try again, and no new fill may take its place.
            if (!result.undo().isEmpty()) {
                rememberAxiom(result.undo());
                lastFailed = true;
            }
            return Component.translatable(result.undo().isEmpty() ? "hotbar.chromastudio.axiom.failed" : "hotbar.chromastudio.axiom.failed_undo");
        }
        rememberAxiom(result.undo());
        Hud.strip(stacks.subList(0, HOTBAR), Component.translatable("hotbar.chromastudio.mode.axiom", axiomHotbarNames(active, rest)));
        // Which blocks went to which hotbar, by their place in the palette, so the player knows where to look for one.
        // Past two hotbars a list would crowd out the rest of the message, so it names the run of hotbars instead.
        int hotbars = HotbarPlan.axiomHotbars(rest);
        MutableComponent parts = Component.empty();
        // Only when the blocks are one run of the palette, as a skipped entry such as water would shift the boundaries.
        if (hotbars > 2 && places.getLast() - places.get(HOTBAR) == rest - 1) {
            parts.append(Component.translatable("hotbar.chromastudio.rest.axiom.blocks", places.get(HOTBAR), places.getLast(), axiomHotbarNames(active, rest)));
        } else {
            for (int h = 0; h < hotbars; h++) {
                int from = HOTBAR + 9 * h, to = Math.min(HOTBAR + rest, from + 9) - 1;
                Component hotbar = Component.translatable("hotbar.chromastudio.axiom.hotbar", active + h + 2);
                if (h > 0) parts.append(", ");
                parts.append(from == to ? Component.translatable("hotbar.chromastudio.rest.axiom.block", places.get(from), hotbar)
                        : Component.translatable("hotbar.chromastudio.rest.axiom.blocks", places.get(from), places.get(to), hotbar));
            }
        }
        return Component.translatable("hotbar.chromastudio.filled", HOTBAR).append(" ")
                .append(Component.translatable("hotbar.chromastudio.rest.axiom", rest, parts, AxiomBridge.hotbarKey()));
    }

    /**
     * Names the Axiom hotbars that {@code rest} blocks go into after hotbar {@code active}, as the player sees them,
     * numbered from 1 where Axiom numbers from 0.
     */
    private static Component axiomHotbarNames(int active, int rest) {
        int count = HotbarPlan.axiomHotbars(rest);
        return count == 1 ? Component.translatable("hotbar.chromastudio.axiom.hotbar", active + 2)
                : Component.translatable("hotbar.chromastudio.axiom.hotbars", active + 2, active + 1 + count);
    }

    /** What a fill into Axiom's hotbars would do now: the Axiom hotbars it uses, and how many of their slots hold something. */
    public record AxiomPreview(Component hotbars, int replaced) {}

    /** How many of these entries a fill puts somewhere: those with an item, which water, for one, lacks. */
    public static int holdable(List<PaletteEntry> entries) {
        return (int) entries.stream().filter(e -> !PaletteBlocks.icon(e).isEmpty()).count();
    }

    /**
     * What a fill of this many blocks, counted by {@link #holdable}, would put in Axiom's hotbars and replace there,
     * for the dialog to say before the fill. Nothing when the fill wouldn't use them or they can't be read.
     */
    public static Optional<AxiomPreview> axiomPreview(int blocks) {
        int rest = blocks - HOTBAR;
        if (rest <= 0 || !usesAxiom()) return Optional.empty();
        try {
            int active = AxiomBridge.activeHotbar(), replaced = 0;
            for (HotbarPlan.Spot at : HotbarPlan.axiom(active, rest)) if (!AxiomBridge.hotbarItem(at.hotbar(), at.slot()).isEmpty()) replaced++;
            return Optional.of(new AxiomPreview(axiomHotbarNames(active, rest), replaced));
        } catch (Throwable _) {
            return Optional.empty();
        }
    }

    /** Axiom's number for the hotbar in hand, {@link #NO_AXIOM} without Axiom, or {@link #UNKNOWN} when it can't say. */
    private static int heldHotbar() {
        if (!AxiomBridge.isPresent()) return NO_AXIOM;
        try {
            return AxiomBridge.activeHotbar();
        } catch (Throwable _) {
            return UNKNOWN;
        }
    }

    /**
     * Axiom's hotbars as a fill writes them, with {@code active} the one in hand: that one is the player's own, so it
     * is written as {@link #put} writes any slot, and the others through Axiom.
     */
    private static HotbarWrites.Hotbars<ItemStack> axiomHotbars(int active) {
        return new HotbarWrites.Hotbars<>() {
            @Override
            public ItemStack get(int hotbar, int slot) throws Throwable { return AxiomBridge.hotbarItem(hotbar, slot); }

            @Override
            public void set(int hotbar, int slot, ItemStack item) throws Throwable {
                if (hotbar == active) put(slot, item);
                else AxiomBridge.setHotbarItem(hotbar, slot, item);
            }

            @Override
            public boolean same(ItemStack a, ItemStack b) { return ItemStack.matches(a, b); }
        };
    }

    private static void rememberAxiom(List<HotbarWrites.Was<ItemStack>> before) {
        lastFailed = false;
        last = List.of();
        lastHotbar = -1;
        lastAxiom = List.copyOf(before);
        lastDropped = false;
        lastOn = Minecraft.getInstance().getConnection();
    }

    private static void remember(List<Was> before, boolean dropped, int hotbar) {
        lastFailed = false;
        lastAxiom = List.of();
        last = List.copyOf(before);
        lastHotbar = hotbar;
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
            // The server ignores a creative player's drops that come too fast, and the client keeps the same count.
            if (!player.getDropSpamThrottler().isUnderThreshold()) return Dropped.NOT_AT_ALL;
            mc.gameMode.handleCreativeModeItemDrop(stack.copy());
            return Dropped.ON_THE_GROUND;
        }
        int slot = roomFor(stack, now, player.getInventory().getSelectedSlot());
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

    /**
     * Puts back what the last fill replaced, and says so, writing the hotbar slots back to the Axiom hotbar they were
     * in even if the player holds another now. What it dropped stays on the ground.
     */
    public static Component undo() {
        Optional<Component> why = whyNoUndo();
        if (why.isPresent()) return why.get();
        List<HotbarWrites.Was<ItemStack>> hotbars = new ArrayList<>(lastAxiom);
        List<Was> slots = new ArrayList<>();
        // Through Axiom only when it must be, so a fill undone in the hotbar it filled works as it always did.
        boolean axiom = undoNeedsAxiom();
        for (Was was : last) {
            if (axiom && was.slot < HOTBAR && lastHotbar >= 0) hotbars.add(new HotbarWrites.Was<>(lastHotbar, was.slot, was.stack));
            else slots.add(was);
        }
        if (!hotbars.isEmpty()) {
            // In creative mode, as whyNoUndo makes sure, every write here takes at once, so each is read back.
            Throwable failed;
            try {
                failed = HotbarWrites.restore(axiomHotbars(AxiomBridge.activeHotbar()), hotbars);
            } catch (Throwable t) {
                failed = t;
            }
            if (failed != null) {
                ChromaStudioClient.LOGGER.warn("Axiom's hotbars couldn't be put back", failed);
                AxiomBridge.hotbarsFailed(failed);
                // What wasn't put back is still to be, so no new fill may take its place.
                lastFailed = true;
                return Component.translatable("hotbar.chromastudio.axiom.failed_undo");
            }
        }
        for (Was was : slots) put(was.slot, was.stack);
        boolean wroteAxiom = !lastAxiom.isEmpty();
        last = List.of();
        lastAxiom = List.of();
        lastHotbar = -1;
        lastFailed = false;
        Hud.strip(List.of());
        return Component.translatable(wroteAxiom ? "hotbar.chromastudio.undone_axiom" : lastDropped ? "hotbar.chromastudio.undone_dropped" : "hotbar.chromastudio.undone");
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
