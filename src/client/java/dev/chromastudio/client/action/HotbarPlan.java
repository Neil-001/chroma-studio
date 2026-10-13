package dev.chromastudio.client.action;

import dev.chromastudio.client.OverflowMode;
import java.util.ArrayList;
import java.util.List;

/**
 * Where a hotbar fill's blocks past the ninth go, worked out from how many there are and how many inventory slots
 * are free. The first {@code slotted} take a slot each. The rest are packed into shulker boxes of the sizes in
 * {@code boxes}, the first {@code boxesSlotted} of which get a slot; or, with no boxes, they're dropped one by one.
 * Whatever has no slot is dropped.
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

    /** How many blocks are in the boxes. */
    public int boxed() { return boxes.stream().mapToInt(Integer::intValue).sum(); }
}
