package dev.chromastudio.client.action;

import static org.junit.jupiter.api.Assertions.assertEquals;

import dev.chromastudio.client.OverflowMode;
import java.util.List;
import org.junit.jupiter.api.Test;

class HotbarPlanTest {
    private static HotbarPlan plan(int slotted, int boxesSlotted, Integer... boxes) { return new HotbarPlan(slotted, List.of(boxes), boxesSlotted); }

    @Test
    void nothingPastTheHotbarNeedsNothing() {
        for (OverflowMode mode : OverflowMode.values()) assertEquals(plan(0, 0), HotbarPlan.of(mode, 0, 27));
    }

    @Test
    void neverUsesFreeSlotsAndLeavesTheRestLoose() {
        assertEquals(plan(21, 0), HotbarPlan.of(OverflowMode.NEVER, 21, 27));
        assertEquals(plan(5, 0), HotbarPlan.of(OverflowMode.NEVER, 21, 5));
        assertEquals(plan(0, 0), HotbarPlan.of(OverflowMode.NEVER, 21, 0));
    }

    @Test
    void whenFullBoxesOnlyWhatHasNoSlot() {
        assertEquals(plan(21, 0), HotbarPlan.of(OverflowMode.WHEN_FULL, 21, 27));
        assertEquals(plan(21, 0), HotbarPlan.of(OverflowMode.WHEN_FULL, 21, 21));
        // The box takes the fifth free slot, so four blocks keep a slot and seventeen are boxed.
        assertEquals(plan(4, 1, 17), HotbarPlan.of(OverflowMode.WHEN_FULL, 21, 5));
        assertEquals(plan(0, 1, 21), HotbarPlan.of(OverflowMode.WHEN_FULL, 21, 1));
        assertEquals(plan(0, 0, 21), HotbarPlan.of(OverflowMode.WHEN_FULL, 21, 0));
        // One box and two slots hold 29, one short, so a second box takes another slot.
        assertEquals(plan(1, 2, 27, 2), HotbarPlan.of(OverflowMode.WHEN_FULL, 30, 3));
        assertEquals(plan(0, 0, 27, 27, 6), HotbarPlan.of(OverflowMode.WHEN_FULL, 60, 0));
    }

    @Test
    void alwaysBoxesEverythingInAsManyBoxesAsItTakes() {
        assertEquals(plan(0, 1, 21), HotbarPlan.of(OverflowMode.ALWAYS, 21, 27));
        assertEquals(plan(0, 1, 27), HotbarPlan.of(OverflowMode.ALWAYS, 27, 27));
        assertEquals(plan(0, 2, 27, 1), HotbarPlan.of(OverflowMode.ALWAYS, 28, 27));
        assertEquals(plan(0, 1, 27, 27, 6), HotbarPlan.of(OverflowMode.ALWAYS, 60, 1));
        assertEquals(plan(0, 0, 21), HotbarPlan.of(OverflowMode.ALWAYS, 21, 0));
    }

    @Test
    void everyBlockIsAccountedForOnce() {
        for (OverflowMode mode : OverflowMode.values())
            for (int rest = 0; rest <= 120; rest++)
                for (int free = 0; free <= 27; free++) {
                    HotbarPlan p = HotbarPlan.of(mode, rest, free);
                    int loose = rest - p.slotted() - p.boxed();
                    assertEquals(0, p.boxes().isEmpty() ? 0 : loose, "boxes leave nothing loose");
                    if (mode != OverflowMode.NEVER) assertEquals(0, loose);
                    assertEquals(true, p.slotted() + p.boxesSlotted() <= free);
                    assertEquals(true, p.boxes().stream().allMatch(n -> n >= 1 && n <= HotbarPlan.BOX));
                }
    }
}
