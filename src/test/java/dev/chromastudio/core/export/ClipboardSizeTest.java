package dev.chromastudio.core.export;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.chromastudio.core.palette.Palette;
import dev.chromastudio.core.palette.PaletteEntry;
import dev.chromastudio.core.palette.ShareString;
import dev.chromastudio.core.palette.WorldEditPattern;
import org.junit.jupiter.api.Test;

class ClipboardSizeTest {
    @Test
    void theLimitIsInBytesOfUtf8() {
        String ascii = "a".repeat(ClipboardSize.MAX_BYTES);
        assertTrue(ClipboardSize.fits(ascii));
        assertFalse(ClipboardSize.fits(ascii + "a"));
        // "é" is two bytes, so half as many fit.
        String accents = "é".repeat(ClipboardSize.MAX_BYTES / 2);
        assertEquals(ClipboardSize.MAX_BYTES, ClipboardSize.bytes(accents));
        assertTrue(ClipboardSize.fits(accents));
        assertFalse(ClipboardSize.fits(accents + "é"));
    }

    @Test
    void theLimitLeavesRoomUnderTheStack() {
        // LWJGL's stack is 64 KiB, and the text needs a terminating zero too.
        assertTrue(ClipboardSize.MAX_BYTES + 1 < 64 * 1024);
    }

    @Test
    void kilobytesRoundUp() {
        assertEquals(0, ClipboardSize.kilobytes(0));
        assertEquals(1, ClipboardSize.kilobytes(1));
        assertEquals(40, ClipboardSize.kilobytes(40_960));
        assertEquals(41, ClipboardSize.kilobytes(40_961));
    }

    /** A palette of many long block ids, as a capture of a large build could make. */
    private static Palette big(int entries) {
        Palette p = new Palette("big");
        for (int i = 0; i < entries; i++) p.entries.add(new PaletteEntry("somemod:block_with_a_long_name_" + i, "facing=north,half=top", 3));
        return p;
    }

    @Test
    void aLargePalettesShareStringAndPatternGoPastTheLimit() {
        assertTrue(ClipboardSize.fits(ShareString.encode(big(10))));
        assertFalse(ClipboardSize.fits(ShareString.encode(big(1000))));
        assertFalse(ClipboardSize.fits(WorldEditPattern.command("set", big(1000), "")));
    }
}
