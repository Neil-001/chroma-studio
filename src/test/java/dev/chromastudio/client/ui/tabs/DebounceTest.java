package dev.chromastudio.client.ui.tabs;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class DebounceTest {
    @Test
    void manyRequestsInAFrameStartOneRun() {
        Debounce d = new Debounce();
        assertEquals(-1, d.poll(), "nothing asked for, nothing to do");
        d.request();
        d.request();
        d.request();
        assertTrue(d.pending());
        int run = d.poll();
        assertTrue(run >= 0);
        assertFalse(d.pending());
        assertEquals(-1, d.poll(), "the next frame has nothing to do until asked again");
    }

    @Test
    void anAnswerToAnEarlierRunIsOutOfDate() {
        Debounce d = new Debounce();
        d.request();
        int first = d.poll();
        assertTrue(d.current(first));
        d.request();
        assertTrue(d.current(first), "asking isn't starting: the first answer still stands until the next run begins");
        int second = d.poll();
        assertNotEquals(first, second);
        assertFalse(d.current(first));
        assertTrue(d.current(second));
    }
}
