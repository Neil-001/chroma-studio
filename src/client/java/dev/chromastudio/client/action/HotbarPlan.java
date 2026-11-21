package dev.chromastudio.client.action;

import dev.chromastudio.client.OverflowMode;
import java.util.ArrayList;
import java.util.List;

public record HotbarPlan(int slotted, List<Integer> boxes, int boxesSlotted) {
    public static final int BOX = 27;

    public static HotbarPlan of(OverflowMode mode, int rest, int free) {
        if (rest <= 0) return new HotbarPlan(0, List.of(), 0);
        if (mode == OverflowMode.NEVER || mode == OverflowMode.WHEN_FULL && rest <= free) return new HotbarPlan(Math.min(rest, free), List.of(), 0);
        int count = 1;
        if (mode == OverflowMode.ALWAYS) count = (rest + BOX - 1) / BOX;
        else while (Math.max(0, free - count) + count * BOX < rest) count++;
        int slotted = mode == OverflowMode.ALWAYS ? 0 : Math.max(0, free - count);
        List<Integer> boxes = new ArrayList<>();
        for (int left = rest - slotted; left > 0; left -= BOX) boxes.add(Math.min(BOX, left));
        return new HotbarPlan(slotted, List.copyOf(boxes), Math.min(count, free));
    }

    public record Spot(int hotbar, int slot) {}

    public static List<Spot> axiom(int active, int rest) {
        List<Spot> spots = new ArrayList<>();
        for (int k = 0; k < rest; k++) spots.add(new Spot(active + 1 + k / 9, k % 9));
        return List.copyOf(spots);
    }

    public static int axiomHotbars(int rest) { return (Math.max(0, rest) + 8) / 9; }

    public int boxed() { return boxes.stream().mapToInt(Integer::intValue).sum(); }
}
