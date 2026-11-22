package dev.chromastudio.core.text;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class PluralTest {
    @Test
    void onlyOneTakesTheOneKey() {
        assertEquals("a.changed.one", Plural.key("a.changed", 1));
        for (int n : new int[] {0, 2, 5, 21, -1}) assertEquals("a.changed", Plural.key("a.changed", n));
    }
}
