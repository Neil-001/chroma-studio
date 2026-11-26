package dev.chromastudio.client.action;

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
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ItemContainerContents;

public final class HotbarFill {
    private HotbarFill() {}

    public static final int HOTBAR = 9;
    private static final int INVENTORY = 36;
    private static final int MENU_OFFHAND = 45;

    private record Was(int slot, ItemStack stack) {}

    private static List<Was> last = List.of();
    private static int lastHotbar = -1;
    private static final int NO_AXIOM = -1;
    private static final int UNKNOWN = -2;
    private static List<HotbarWrites.Was<ItemStack>> lastAxiom = List.of();
    private static boolean lastFailed;
    private static boolean lastDropped;
    private static Object lastOn;

    private static final CommandReplies REPLIES = new CommandReplies();

    public static boolean hidesFeedback(Component message, boolean overlay) {
        return !overlay && message.getSiblings().isEmpty() && message.getContents() instanceof TranslatableContents t && REPLIES.hides(t.getKey());
    }

    public static void tick() { REPLIES.tick(); }

    private static void forgetOtherWorlds() {
        if (lastOn != Minecraft.getInstance().getConnection()) {
            last = List.of();
            lastAxiom = List.of();
            lastFailed = false;
        }
    }

    public static Optional<Component> unavailable() {
        if (Minecraft.getInstance().player == null) return Optional.of(Component.translatable("palettes.chromastudio.no_world"));
        return Give.canGive() ? Optional.empty() : Optional.of(Component.translatable("hotbar.chromastudio.no_permission"));
    }

    public static Optional<Component> whyNoFill() {
        forgetOtherWorlds();
        return unavailable().or(() -> lastFailed ? Optional.of(Component.translatable("hotbar.chromastudio.axiom.undo_first")) : Optional.empty());
    }

    public static Optional<Component> whyNoUndo() {
        forgetOtherWorlds();
        if (last.isEmpty() && lastAxiom.isEmpty()) return Optional.of(Component.translatable("hotbar.chromastudio.nothing_to_undo"));
        Optional<Component> why = unavailable();
        if (why.isPresent()) return why;
        if (lastHotbar == UNKNOWN) return Optional.of(AxiomBridge.hotbarsUnsupported().orElse(Component.translatable("hotbar.chromastudio.axiom.changed")));
        return undoNeedsAxiom() ? AxiomBridge.hotbarsUnreachable() : Optional.empty();
    }

    private static boolean undoNeedsAxiom() {
        return !lastAxiom.isEmpty() || lastHotbar == UNKNOWN || lastHotbar >= 0 && heldHotbar() != lastHotbar;
    }

    public static boolean usesAxiom() { return ModSettings.hotbarAxiom && AxiomBridge.hotbarsUnavailable().isEmpty(); }

    public static Component modeLine(OverflowMode mode) {
        return Component.translatable("hotbar.chromastudio.mode", Component.translatable("option.chromastudio.overflowmode." + mode.name().toLowerCase(Locale.ROOT)));
    }

    public static Component fill(List<PaletteEntry> entries, boolean intoAxiom) {
        Optional<Component> why = whyNoFill();
        if (why.isPresent()) return why.get();
        boolean axiom = usesAxiom();
        LocalPlayer player = Minecraft.getInstance().player;
        Inventory inventory = player.getInventory();
        List<ItemStack> stacks = new ArrayList<>();
        for (PaletteEntry e : entries) {
            ItemStack stack = PaletteBlocks.icon(e);
            if (!stack.isEmpty()) stacks.add(stack);
        }
        if (stacks.isEmpty()) return Component.translatable("hotbar.chromastudio.nothing");
        if (stacks.size() > HOTBAR && axiom != intoAxiom) return Component.translatable("hotbar.chromastudio.axiom.moved");
        if (stacks.size() > HOTBAR && axiom) return fillAxiom(stacks);
        int hotbar = heldHotbar();

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

    private static Component fillAxiom(List<ItemStack> stacks) {
        LocalPlayer player = Minecraft.getInstance().player;
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
            AxiomBridge.hotbarsFailed(result.failure());
            if (result.undo().isEmpty()) return Component.translatable("hotbar.chromastudio.axiom.failed");
            rememberAxiom(result.undo());
            lastFailed = true;
            return Component.translatable("hotbar.chromastudio.axiom.failed_left", slotNames(result.undo()));
        }
        rememberAxiom(result.undo());
        Hud.strip(stacks.subList(0, HOTBAR), Component.translatable("hotbar.chromastudio.mode.axiom", axiomHotbarNames(active, rest)));
        return Component.translatable("hotbar.chromastudio.filled.axiom", axiomHotbarNames(active, rest), AxiomBridge.hotbarKey());
    }

    private static Component axiomHotbarNames(int active, int rest) {
        int count = HotbarPlan.axiomHotbars(rest);
        return count == 1 ? Component.translatable("hotbar.chromastudio.axiom.hotbar", active + 2)
                : Component.translatable("hotbar.chromastudio.axiom.hotbars", active + 2, active + 1 + count);
    }

    public record AxiomPreview(Component hotbars, int first, int last, int replaced) {}

    public static int freeSlots() {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null) return 0;
        int free = 0;
        for (int i = HOTBAR; i < INVENTORY; i++) if (player.getInventory().getItem(i).isEmpty()) free++;
        return free;
    }

    public static List<ItemStack> holdable(List<PaletteEntry> entries) {
        return entries.stream().map(PaletteBlocks::icon).filter(s -> !s.isEmpty()).toList();
    }

    public static Optional<AxiomPreview> axiomPreview(List<ItemStack> stacks) {
        int rest = stacks.size() - HOTBAR;
        if (rest <= 0 || !usesAxiom()) return Optional.empty();
        try {
            int active = AxiomBridge.activeHotbar(), replaced = 0, k = HOTBAR;
            for (HotbarPlan.Spot at : HotbarPlan.axiom(active, rest)) {
                ItemStack held = AxiomBridge.hotbarItem(at.hotbar(), at.slot());
                if (!held.isEmpty() && !ItemStack.isSameItemSameComponents(held, stacks.get(k))) replaced++;
                k++;
            }
            return Optional.of(new AxiomPreview(axiomHotbarNames(active, rest), active + 2, active + 1 + HotbarPlan.axiomHotbars(rest), replaced));
        } catch (Throwable _) {
            return Optional.empty();
        }
    }

    private static int heldHotbar() {
        if (!AxiomBridge.isPresent()) return NO_AXIOM;
        try {
            return AxiomBridge.activeHotbar();
        } catch (Throwable _) {
            return UNKNOWN;
        }
    }

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

    private static final int NAMED = 3;

    private static Component slotNames(List<HotbarWrites.Was<ItemStack>> left) {
        MutableComponent out = Component.empty();
        for (int i = 0; i < Math.min(NAMED, left.size()); i++) {
            HotbarWrites.Was<ItemStack> w = left.get(i);
            if (i > 0) out.append("; ");
            out.append(w.item().isEmpty() ? Component.translatable("hotbar.chromastudio.axiom.slot_empty", w.hotbar() + 1, w.slot() + 1)
                    : Component.translatable("hotbar.chromastudio.axiom.slot", w.hotbar() + 1, w.slot() + 1, w.item().getHoverName()));
        }
        if (left.size() > NAMED) out.append("; ").append(Component.translatable("hotbar.chromastudio.axiom.more", left.size() - NAMED));
        for (HotbarWrites.Was<ItemStack> w : left)
            ChromaStudioClient.LOGGER.warn("Axiom hotbar {} slot {} is still changed; it held {}", w.hotbar() + 1, w.slot() + 1, w.item());
        return out;
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

    private static void take(int slot, ItemStack stack, List<ItemStack> now, List<Was> before) {
        put(slot, stack);
        before.add(new Was(slot, ItemStack.EMPTY));
        now.set(slot, stack.copy());
    }

    private enum Dropped { ON_THE_GROUND, ONTO_A_STACK, NOT_AT_ALL }

    private static Dropped drop(ItemStack stack, List<ItemStack> now, List<Was> before) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player.hasInfiniteMaterials()) {
            if (!player.getDropSpamThrottler().isUnderThreshold()) return Dropped.NOT_AT_ALL;
            mc.gameMode.handleCreativeModeItemDrop(stack.copy());
            return Dropped.ON_THE_GROUND;
        }
        int slot = roomFor(stack, now, player.getInventory().getSelectedSlot());
        if (slot >= 0) {
            if (before.stream().noneMatch(was -> was.slot == slot)) before.add(new Was(slot, now.get(slot).copy()));
            now.get(slot).grow(stack.getCount());
        }
        ChromaStudioClient.platform.sendChatCommand("give @s " + Give.commandItem(stack));
        REPLIES.sent();
        return slot >= 0 ? Dropped.ONTO_A_STACK : Dropped.ON_THE_GROUND;
    }

    private static int roomFor(ItemStack stack, List<ItemStack> now, int selected) {
        List<Integer> order = new ArrayList<>(List.of(selected, Inventory.SLOT_OFFHAND));
        for (int i = 0; i < INVENTORY; i++) order.add(i);
        for (int slot : order) {
            ItemStack held = now.get(slot);
            if (!held.isEmpty() && held.isStackable() && ItemStack.isSameItemSameComponents(held, stack) && held.getCount() + stack.getCount() <= held.getMaxStackSize()) return slot;
        }
        return -1;
    }

    public static Component undo() {
        Optional<Component> why = whyNoUndo();
        if (why.isPresent()) return why.get();
        List<HotbarWrites.Was<ItemStack>> hotbars = new ArrayList<>(lastAxiom);
        List<Was> slots = new ArrayList<>();
        boolean axiom = undoNeedsAxiom();
        for (Was was : last) {
            if (axiom && was.slot < HOTBAR && lastHotbar >= 0) hotbars.add(new HotbarWrites.Was<>(lastHotbar, was.slot, was.stack));
            else slots.add(was);
        }
        if (!hotbars.isEmpty()) {
            HotbarWrites.Restored<ItemStack> back;
            try {
                back = HotbarWrites.restore(axiomHotbars(AxiomBridge.activeHotbar()), hotbars);
            } catch (Throwable t) {
                back = new HotbarWrites.Restored<>(hotbars, t);
            }
            if (back.failure() != null) {
                ChromaStudioClient.LOGGER.warn("Axiom's hotbars couldn't be put back", back.failure());
                AxiomBridge.hotbarsFailed(back.failure());
            }
            if (!back.done()) {
                lastAxiom = back.left();
                last = slots;
                lastFailed = true;
                return Component.translatable("hotbar.chromastudio.axiom.undo_left", slotNames(back.left()));
            }
        }
        boolean inventory = false;
        for (Was was : slots) {
            put(was.slot, was.stack);
            inventory |= was.slot >= HOTBAR;
        }
        boolean wroteAxiom = !lastAxiom.isEmpty();
        last = List.of();
        lastAxiom = List.of();
        lastHotbar = -1;
        lastFailed = false;
        Hud.strip(List.of());
        return Component.translatable(wroteAxiom ? "hotbar.chromastudio.undone_axiom" : lastDropped ? "hotbar.chromastudio.undone_dropped"
                : inventory ? "hotbar.chromastudio.undone_inventory" : "hotbar.chromastudio.undone");
    }

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
        String what = stack.isEmpty() ? "minecraft:air" : Give.commandItem(stack) + (stack.getCount() == 1 ? "" : " " + stack.getCount());
        ChromaStudioClient.platform.sendChatCommand("item replace entity @s " + where + " with " + what);
        REPLIES.sent();
    }
}
