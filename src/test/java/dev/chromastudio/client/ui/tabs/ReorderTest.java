package dev.chromastudio.client.ui.tabs;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class ReorderTest {
    private static List<String> moved(int from, int gap) {
        List<String> items = new ArrayList<>(List.of("a", "b", "c", "d", "e"));
        int to = Reorder.move(items, from, gap);
        assertEquals("abcde".substring(from, from + 1), items.get(to), "the place returned holds the item");
        return items;
    }

    @Test
    void theGapIsTheNearestEdgeBetweenItems() {
        assertEquals(0, Reorder.gap(-30, 19, 5));
        assertEquals(0, Reorder.gap(9, 19, 5));
        assertEquals(1, Reorder.gap(10, 19, 5));
        assertEquals(1, Reorder.gap(28, 19, 5));
        assertEquals(2, Reorder.gap(29, 19, 5));
        assertEquals(5, Reorder.gap(94, 19, 5));
        assertEquals(5, Reorder.gap(900, 19, 5), "past the last item is still the last gap");
    }

    @Test
    void anItemMovesPastOneOrSeveralEitherWay() {
        assertEquals(List.of("b", "a", "c", "d", "e"), moved(0, 2));
        assertEquals(List.of("b", "c", "d", "a", "e"), moved(0, 4));
        assertEquals(List.of("b", "c", "d", "e", "a"), moved(0, 5));
        assertEquals(List.of("a", "b", "d", "c", "e"), moved(3, 2));
        assertEquals(List.of("d", "a", "b", "c", "e"), moved(3, 0));
        assertEquals(List.of("e", "a", "b", "c", "d"), moved(4, 0));
    }

    @Test
    void theGapsEitherSideOfAnItemLeaveItWhereItIs() {
        for (int from = 0; from < 5; from++) {
            assertEquals(List.of("a", "b", "c", "d", "e"), moved(from, from));
            assertEquals(List.of("a", "b", "c", "d", "e"), moved(from, from + 1));
            assertEquals(from, Reorder.target(from, from + 1));
        }
    }
}
