package dev.chromastudio.core.hotbar;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.chromastudio.core.hotbar.HotbarWrites.Slot;
import dev.chromastudio.core.hotbar.HotbarWrites.Was;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;

class HotbarWritesTest {
    private static final class Fake implements HotbarWrites.Hotbars<String> {
        final Map<Integer, String> slots = new HashMap<>();
        Set<Integer> failSets = Set.of();
        Set<Integer> ignoreSets = Set.of();
        RuntimeException reused;
        int failGetAt = -1, ignoreHotbar = -1, sets;

        Fake with(int hotbar, int slot, String item) {
            slots.put(hotbar * 9 + slot, item);
            return this;
        }

        String at(int hotbar, int slot) { return slots.getOrDefault(hotbar * 9 + slot, ""); }

        @Override
        public String get(int hotbar, int slot) {
            if (hotbar * 9 + slot == failGetAt) throw new IllegalStateException("get failed");
            return at(hotbar, slot);
        }

        @Override
        public void set(int hotbar, int slot, String item) {
            int call = sets++;
            if (failSets.contains(call)) throw reused != null ? reused : new IllegalStateException("set failed");
            if (hotbar != ignoreHotbar && !ignoreSets.contains(call)) slots.put(hotbar * 9 + slot, item);
        }

        @Override
        public boolean same(String a, String b) { return Objects.equals(a, b); }
    }

    private static final List<Slot> SLOTS = List.of(new Slot(0, 0), new Slot(0, 1), new Slot(1, 0), new Slot(1, 1));
    private static final List<String> ITEMS = List.of("stone", "dirt", "sand", "clay");

    @Test
    void writesEverySlotAndUndoPutsBackWhatWasThere() {
        Fake f = new Fake().with(0, 0, "torch").with(1, 1, "diamond");
        HotbarWrites.Result<String> r = HotbarWrites.write(f, SLOTS, ITEMS);
        assertTrue(r.worked());
        assertEquals(List.of("stone", "dirt", "sand", "clay"), List.of(f.at(0, 0), f.at(0, 1), f.at(1, 0), f.at(1, 1)));
        assertEquals(List.of(new Was<>(0, 0, "torch"), new Was<>(0, 1, ""), new Was<>(1, 0, ""), new Was<>(1, 1, "diamond")), r.undo());
        assertTrue(HotbarWrites.restore(f, r.undo()).done());
        assertEquals(Map.of(0, "torch", 10, "diamond"), withoutEmpty(f.slots));
    }

    @Test
    void aFailedReadChangesNothing() {
        Fake f = new Fake().with(1, 1, "diamond");
        f.failGetAt = 10;
        HotbarWrites.Result<String> r = HotbarWrites.write(f, SLOTS, ITEMS);
        assertFalse(r.worked());
        assertEquals(List.of(), r.undo());
        assertEquals(0, f.sets);
        assertEquals(Map.of(10, "diamond"), f.slots);
    }

    @Test
    void aSlotThatRefusesToBeSetHasWhatWasWrittenBeforeItPutBack() {
        Fake f = new Fake().with(0, 0, "torch").with(1, 1, "diamond");
        f.failSets = Set.of(1);
        HotbarWrites.Result<String> r = HotbarWrites.write(f, SLOTS, ITEMS);
        assertFalse(r.worked());
        assertEquals(List.of(), r.undo(), "everything was put back, so nothing is left for Undo");
        assertEquals(4, f.sets, "nothing past the slot that refused was set, and both it and the first were put back");
        assertEquals(Map.of(0, "torch", 10, "diamond"), withoutEmpty(f.slots));
    }

    @Test
    void aSetThatThrowsHalfwayPutsBackWhatWasWritten() {
        Fake f = new Fake().with(0, 0, "torch").with(1, 1, "diamond");
        f.failSets = Set.of(2);
        HotbarWrites.Result<String> r = HotbarWrites.write(f, SLOTS, ITEMS);
        assertFalse(r.worked());
        assertEquals(List.of(), r.undo(), "nothing is left for Undo");
        assertEquals(Map.of(0, "torch", 10, "diamond"), withoutEmpty(f.slots));
    }

    @Test
    void aSetThatQuietlyDoesNothingIsCaughtByReadingBack() {
        Fake f = new Fake().with(0, 0, "torch").with(1, 1, "diamond");
        f.ignoreHotbar = 1;
        HotbarWrites.Result<String> r = HotbarWrites.write(f, SLOTS, ITEMS);
        assertFalse(r.worked());
        assertEquals(List.of(), r.undo());
        assertEquals(Map.of(0, "torch", 10, "diamond"), withoutEmpty(f.slots));
    }

    @Test
    void whenPuttingBackFailsTooUndoKeepsOnlyTheSlotsStillChanged() {
        Fake f = new Fake().with(0, 0, "torch");
        f.failSets = IntStream.range(1, 30).boxed().collect(Collectors.toSet());
        HotbarWrites.Result<String> r = HotbarWrites.write(f, SLOTS, ITEMS);
        assertFalse(r.worked());
        assertTrue(r.failure().getSuppressed().length >= 1, "the failed put-back is kept with the cause");
        assertEquals(List.of(new Was<>(0, 0, "torch")), r.undo());
        assertEquals("stone", f.at(0, 0), "the first write is still there for Undo");
        HotbarWrites.Restored<String> still = HotbarWrites.restore(f, r.undo());
        assertFalse(still.done());
        assertEquals(r.undo(), still.left());
        f.failSets = Set.of();
        assertTrue(HotbarWrites.restore(f, still.left()).done());
        assertEquals("torch", f.at(0, 0));
    }

    @Test
    void aSlotThatCantBeReadBackCountsAsStillChanged() {
        Fake f = new Fake().with(0, 0, "torch");
        f.failGetAt = 0;
        HotbarWrites.Restored<String> r = HotbarWrites.restore(f, List.of(new Was<>(0, 0, "torch"), new Was<>(0, 1, "")));
        assertEquals(List.of(new Was<>(0, 0, "torch")), r.left());
    }

    @Test
    void aPutBackThatQuietlyDoesNothingIsCaughtAndLeftForUndo() {
        Fake f = new Fake().with(0, 0, "torch");
        f.ignoreSets = Set.of(1, 7);
        HotbarWrites.Result<String> r = HotbarWrites.write(f, SLOTS, ITEMS);
        assertFalse(r.worked());
        assertEquals("stone", f.at(0, 0));
        assertTrue(r.undo().contains(new Was<>(0, 0, "torch")), "Undo is left what to put back");
    }

    @Test
    void theSameExceptionThrownTwiceDoesNotEscape() {
        Fake f = new Fake().with(0, 0, "torch");
        f.reused = new IllegalStateException("Axiom's one exception");
        f.failSets = IntStream.range(1, 30).boxed().collect(Collectors.toSet());
        HotbarWrites.Result<String> r = HotbarWrites.write(f, SLOTS, ITEMS);
        assertFalse(r.worked());
        assertEquals(f.reused, r.failure());
    }

    @Test
    void restoreGoesLastFirstSoASlotNamedTwiceEndsAsItBegan() {
        Fake f = new Fake().with(0, 0, "torch");
        HotbarWrites.Result<String> r = HotbarWrites.write(f, List.of(new Slot(0, 0), new Slot(0, 0)), List.of("stone", "stone"));
        assertTrue(r.worked());
        assertEquals(List.of(new Was<>(0, 0, "torch"), new Was<>(0, 0, "torch")), r.undo());
        f.slots.put(0, "stone");
        assertTrue(HotbarWrites.restore(f, List.of(new Was<>(0, 0, "torch"), new Was<>(0, 0, "stone"))).done());
        assertEquals("torch", f.at(0, 0));
    }

    @Test
    void restoreTriesEverySlotAndSaysWhatFailed() {
        Fake f = new Fake();
        f.failSets = Set.of(0);
        HotbarWrites.Restored<String> r = HotbarWrites.restore(f, List.of(new Was<>(0, 0, "torch"), new Was<>(1, 0, "dirt")));
        assertEquals("set failed", r.failure().getMessage());
        assertEquals(List.of(new Was<>(1, 0, "dirt")), r.left());
        assertEquals("torch", f.at(0, 0), "the slot after the failed one was still put back");
    }

    private static Map<Integer, String> withoutEmpty(Map<Integer, String> slots) {
        Map<Integer, String> m = new HashMap<>(slots);
        m.values().removeIf(String::isEmpty);
        return m;
    }

    @Test
    void aFillWritesOnlyTheNewItemsAndNamesTheSlotsAQuietRefusalLeavesChanged() {
        String[] held = {"old0", "old1"};
        List<String> writes = new java.util.ArrayList<>();
        boolean[] broken = {false};
        HotbarWrites.Hotbars<String> hotbars = new HotbarWrites.Hotbars<>() {
            @Override
            public String get(int hotbar, int slot) { return held[slot]; }

            @Override
            public void set(int hotbar, int slot, String item) {
                writes.add(slot + "=" + item);
                if (slot == 1) return;
                if (broken[0]) throw new IllegalStateException("writes lost");
                held[slot] = item;
                if (item.equals("new0")) broken[0] = true;
            }

            @Override
            public boolean same(String a, String b) { return a.equals(b); }
        };
        HotbarWrites.Result<String> r = HotbarWrites.write(hotbars, List.of(new Slot(0, 0), new Slot(0, 1)), List.of("new0", "new1"));
        assertEquals(List.of("0=new0", "1=new1", "1=old1", "0=old0"), writes);
        assertFalse(r.worked());
        assertEquals(List.of(new Was<>(0, 0, "old0")), r.undo(), "the slot left changed is named for Undo");
        broken[0] = false;
        assertTrue(HotbarWrites.restore(hotbars, r.undo()).done());
        assertEquals(List.of("old0", "old1"), List.of(held));
    }
}
