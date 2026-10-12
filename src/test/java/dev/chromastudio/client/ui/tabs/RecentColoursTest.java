package dev.chromastudio.client.ui.tabs;

import java.util.List;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class RecentColoursTest {
    @Test
    void theNewestComesFirstAndEachIsListedOnce() {
        RecentColours r = new RecentColours();
        r.push(0x111111);
        r.push(0x222222);
        r.push(0x333333);
        assertEquals(List.of(0x333333, 0x222222, 0x111111), r.list());
        r.push(0x111111);
        assertEquals(List.of(0x111111, 0x333333, 0x222222), r.list(), "one matched again moves to the front");
        r.push(0x111111);
        assertEquals(3, r.list().size());
    }

    @Test
    void onlyTheLastTwelveAreKept() {
        RecentColours r = new RecentColours();
        for (int i = 0; i < 20; i++) r.push(i);
        assertEquals(RecentColours.KEPT, r.list().size());
        assertEquals(19, r.list().getFirst());
        assertEquals(8, r.list().getLast());
    }
}
