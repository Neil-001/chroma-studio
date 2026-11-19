package dev.chromastudio.client.action;

import dev.chromastudio.client.OverflowMode;
import java.util.ArrayList;
import java.util.List;

/**
 * Where a hotbar fill's blocks past the ninth go, worked out from how many there are and how many inventory slots
 * are free. The first {@code slotted} take a slot each. The rest are packed into shulker boxes of the sizes in
 * {@code boxes}, the first {@code boxesSlotted} of which get a slot; or, with no boxes, they're dropped one by one.
 * Whatever has no slot is dropped.
 *
 * <p>When Axiom's extra hotbars take them instead, {@link #axiom} says which hotbar and slot each one goes to.
 */
public record HotbarPlan(int slotted, List<Integer> boxes, int boxesSlotted) {
    /** How many stacks a shulker box holds. */
    public static final int BOX = 27;

    public static HotbarPlan of(OverflowMode mode, int rest, int free) {
        if (rest <= 0) return new HotbarPlan(0, List.of(), 0);
        if (mode == OverflowMode.NEVER || mode == OverflowMode.WHEN_FULL && rest <= free) return new HotbarPlan(Math.min(rest, free), List.of(), 0);
        // Every box takes a slot a block could have had, so the fewest boxes that leave nothing over are used.
        int count = 1;
        if (mode == OverflowMode.ALWAYS) count = (rest + BOX - 1) / BOX;
        else while (Math.max(0, free - count) + count * BOX < rest) count++;
        int slotted = mode == OverflowMode.ALWAYS ? 0 : Math.max(0, free - count);
        List<Integer> boxes = new ArrayList<>();
        for (int left = rest - slotted; left > 0; left -= BOX) boxes.add(Math.min(BOX, left));
        return new HotbarPlan(slotted, List.copyOf(boxes), Math.min(count, free));
    }

    /** A slot of one of Axiom's hotbars, which it numbers from 0, nine slots to each. */
    public record Spot(int hotbar, int slot) {}

    /**
     * Where the blocks past the ninth go in Axiom's hotbars: nine to a hotbar, in the hotbars after the active one,
     * so the one the player holds keeps the first nine. A last hotbar that isn't filled keeps its other slots.
     */
    public static List<Spot> axiom(int active, int rest) {
        List<Spot> spots = new ArrayList<>();
        for (int k = 0; k < rest; k++) spots.add(new Spot(active + 1 + k / 9, k % 9));
        return List.copyOf(spots);
    }

    /** How many of Axiom's hotbars {@link #axiom} uses. */
    public static int axiomHotbars(int rest) { return (Math.max(0, rest) + 8) / 9; }

    /** How many blocks are in the boxes. */
    public int boxed() { return boxes.stream().mapToInt(Integer::intValue).sum(); }
}
