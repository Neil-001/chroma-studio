package dev.chromastudio.client.ui;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class FlowTest {
    private static Flow.Spot at(int x, int row) { return new Flow.Spot(x, row); }

    @Test
    void controlsThatFitStayOnOneRowAsBefore() {
        Flow.Spot[] spots = Flow.place(new int[] {40, 50, 30}, 3, 200, 2);
        assertArrayEquals(new Flow.Spot[] {at(3, 0), at(45, 0), at(97, 0)}, spots);
        assertEquals(1, Flow.rows(spots));
    }

    @Test
    void oneThatWouldPassTheEdgeStartsTheNextRow() {
        // 3 + 40 + 2 + 50 + 2 = 97, and 97 + 30 passes 120.
        Flow.Spot[] spots = Flow.place(new int[] {40, 50, 30, 20, 80}, 3, 120, 2);
        assertArrayEquals(new Flow.Spot[] {at(3, 0), at(45, 0), at(3, 1), at(35, 1), at(3, 2)}, spots);
        assertEquals(3, Flow.rows(spots));
    }

    @Test
    void aControlEndingExactlyAtTheEdgeFits() {
        assertArrayEquals(new Flow.Spot[] {at(0, 0), at(52, 0)}, Flow.place(new int[] {50, 48}, 0, 100, 2));
    }

    @Test
    void oneWiderThanTheRoomStillGetsARowOfItsOwn() {
        assertArrayEquals(new Flow.Spot[] {at(0, 0), at(0, 1), at(0, 2)}, Flow.place(new int[] {10, 150, 10}, 0, 100, 2));
        assertEquals(1, Flow.rows(Flow.place(new int[0], 0, 100, 2)));
    }
}
