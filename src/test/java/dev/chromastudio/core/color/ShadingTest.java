package dev.chromastudio.core.color;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class ShadingTest {
    @Test
    void factorsAreTheGames() {
        assertEquals(1.0, Shading.factor(Face.UP));
        assertEquals(0.8, Shading.factor(Face.NORTH));
        assertEquals(0.8, Shading.factor(Face.SOUTH));
        assertEquals(0.6, Shading.factor(Face.WEST));
        assertEquals(0.6, Shading.factor(Face.EAST));
        assertEquals(0.5, Shading.factor(Face.DOWN));
        assertEquals(1.0, Shading.factor(Face.ANY));
    }

    @Test
    void applyDarkensTheStoredChannels() {
        Color c = Color.ofRgb(0xC86432); // 200, 100, 50
        assertEquals(0xC86432, Shading.apply(c, Face.UP).rgb());
        assertEquals(0xA05028, Shading.apply(c, Face.NORTH).rgb()); // 160, 80, 40
        assertEquals(0x783C1E, Shading.apply(c, Face.EAST).rgb());  // 120, 60, 30
        assertEquals(0x643219, Shading.apply(c, Face.DOWN).rgb());  // 100, 50, 25
    }

    @Test
    void removeDividesAndStopsAtWhite() {
        assertEquals(0xC86432, Shading.remove(Color.ofRgb(0x643219), Face.DOWN).rgb());
        assertEquals(0xFFFF64, Shading.remove(Color.ofRgb(0xC8FF32), Face.DOWN).rgb());
    }

    @Test
    void applyThenRemoveIsWithinOneStep() {
        for (Face f : Face.values()) {
            for (int rgb = 0; rgb <= 0xFFFFFF; rgb += 0x010307) {
                Color c = Color.ofRgb(rgb);
                Color back = Shading.remove(Shading.apply(c, f), f);
                assertTrue(Math.abs(back.red() - c.red()) <= 1 && Math.abs(back.green() - c.green()) <= 1
                        && Math.abs(back.blue() - c.blue()) <= 1, f + " " + c.hex() + " came back " + back.hex());
            }
        }
    }
}
