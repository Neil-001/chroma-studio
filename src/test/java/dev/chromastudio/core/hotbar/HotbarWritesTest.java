package dev.chromastudio.core.hotbar;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
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
    /** Hotbars in a map, "" for an empty slot, that can be told to fail in the ways another mod's might. */
    private static final class Fake implements HotbarWrites.Hotbars<String> {
        final Map<Integer, String> slots = new HashMap<>();
        /** The calls to set, counted from 0, that throw. */
        Set<Integer> failSets = Set.of();
        /** The calls to set, counted from 0, that return without doing anything. */
        Set<Integer> ignoreSets = Set.of();
        /** When set, every failing call throws this one exception, as a mod that reuses its exceptions would. */
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
        assertNull(HotbarWrites.restore(f, r.undo()));
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
        // As Axiom does for the held hotbar outside creative mode: the call returns, and the slot is unchanged.
        Fake f = new Fake().with(0, 0, "torch").with(1, 1, "diamond");
        f.ignoreHotbar = 1;
        HotbarWrites.Result<String> r = HotbarWrites.write(f, SLOTS, ITEMS);
        assertFalse(r.worked());
        assertEquals(List.of(), r.undo());
        assertEquals(Map.of(0, "torch", 10, "diamond"), withoutEmpty(f.slots));
    }

    @Test
    void whenPuttingBackFailsTooUndoKeepsWhatToTryAgain() {
        Fake f = new Fake().with(0, 0, "torch");
        // The first set takes, the second throws, and so does every set after it.
        f.failSets = IntStream.range(1, 20).boxed().collect(Collectors.toSet());
        HotbarWrites.Result<String> r = HotbarWrites.write(f, SLOTS, ITEMS);
        assertFalse(r.worked());
        assertEquals(1, r.failure().getSuppressed().length, "the failed put-back is kept with the cause");
        assertEquals(List.of(new Was<>(0, 0, "torch"), new Was<>(0, 1, "")), r.undo());
        assertEquals("stone", f.at(0, 0), "the first write is still there for Undo");
    }

    @Test
    void aPutBackThatQuietlyDoesNothingIsCaughtAndLeftForUndo() {
        // The second write and the last put-back return without doing anything, so the first slot keeps the new item.
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
        f.failSets = IntStream.range(1, 20).boxed().collect(Collectors.toSet());
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
        assertNull(HotbarWrites.restore(f, List.of(new Was<>(0, 0, "torch"), new Was<>(0, 0, "stone"))));
        assertEquals("torch", f.at(0, 0));
    }

    @Test
    void restoreTriesEverySlotAndSaysWhatFailed() {
        Fake f = new Fake();
        f.failSets = Set.of(0);
        Throwable t = HotbarWrites.restore(f, List.of(new Was<>(0, 0, "torch"), new Was<>(1, 0, "dirt")));
        assertEquals("set failed", t.getMessage());
        assertEquals("torch", f.at(0, 0), "the slot after the failed one was still put back");
    }

    private static Map<Integer, String> withoutEmpty(Map<Integer, String> slots) {
        Map<Integer, String> m = new HashMap<>(slots);
        m.values().removeIf(String::isEmpty);
        return m;
    }
}
