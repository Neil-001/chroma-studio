package dev.chromastudio.core.palette;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

class ShareStringTest {
    private static Palette palette(String name, PaletteEntry... entries) {
        Palette p = new Palette(name);
        p.entries.addAll(List.of(entries));
        return p;
    }

    private static void assertRoundTrip(String expected, Palette p) {
        assertEquals(expected, ShareString.encode(p));
        Palette back = ShareString.decode(expected);
        assertEquals(p.name, back.name);
        assertEquals(p.entries, back.entries);
    }

    @Test
    void theSpecExample() {
        assertRoundTrip("chroma1:stone,andesite*3,oak_log[axis=y]/Name", palette("Name",
                new PaletteEntry("minecraft:stone", ""),
                new PaletteEntry("minecraft:andesite", "", 3),
                new PaletteEntry("minecraft:oak_log", "axis=y")));
    }

    @Test
    void plainEntriesWithNoName() {
        assertRoundTrip("chroma1:stone,andesite", palette("",
                new PaletteEntry("minecraft:stone", ""), new PaletteEntry("minecraft:andesite", "")));
    }

    @Test
    void otherNamespacesAreKept() {
        assertRoundTrip("chroma1:create:andesite_casing*2,stone", palette("",
                new PaletteEntry("create:andesite_casing", "", 2), new PaletteEntry("minecraft:stone", "")));
    }

    @Test
    void severalPropertiesAndAWeightTogether() {
        assertRoundTrip("chroma1:oak_stairs[facing=north,half=top]*4,furnace[lit=true]/Kiln", palette("Kiln",
                new PaletteEntry("minecraft:oak_stairs", "facing=north,half=top", 4),
                new PaletteEntry("minecraft:furnace", "lit=true")));
    }

    @Test
    void aNameMayHoldAnything() {
        assertRoundTrip("chroma1:stone/Walls, floors/ceilings * [old]: v2", palette("Walls, floors/ceilings * [old]: v2",
                new PaletteEntry("minecraft:stone", "")));
        assertRoundTrip("chroma1:stone//", palette("/", new PaletteEntry("minecraft:stone", "")));
    }

    @Test
    void aSlashInAnIdIsEscapedSoItDoesNotStartTheName() {
        assertRoundTrip("chroma1:mod:deco%2Ftile*2/Tiles", palette("Tiles", new PaletteEntry("mod:deco/tile", "", 2)));
    }

    @Test
    void anEmptyPaletteIsJustThePrefix() {
        assertRoundTrip("chroma1:", palette(""));
        assertRoundTrip("chroma1:/Empty", palette("Empty"));
    }

    @Test
    void tagsAndNotesAreNotCarried() {
        Palette p = palette("N", new PaletteEntry("minecraft:stone", ""));
        p.tags.add("tag");
        p.notes = "notes";
        Palette back = ShareString.decode(ShareString.encode(p));
        assertEquals(List.of(), back.tags);
        assertEquals("", back.notes);
    }

    @Test
    void whitespaceAroundIsAccepted() {
        Palette p = ShareString.decode("  \n chroma1:stone, andesite*2/Name \t\n");
        assertEquals("Name", p.name);
        assertEquals(List.of(new PaletteEntry("minecraft:stone", ""), new PaletteEntry("minecraft:andesite", "", 2)), p.entries);
    }

    private static void assertRefused(String s, String messagePart) {
        String message = assertThrows(IllegalArgumentException.class, () -> ShareString.decode(s)).getMessage();
        assertTrue(message.contains(messagePart), s + " gave: " + message);
    }

    @Test
    void malformedStringsSayWhatIsWrong() {
        assertRefused(null, "no share string");
        assertRefused("   ", "no share string");
        assertRefused("stone,andesite", "starts with chroma1:");
        assertRefused("chroma2:stone", "newer version");
        assertRefused("chroma1:stone,,andesite", "empty entry");
        assertRefused("chroma1:stone,", "empty entry");
        assertRefused("chroma1:stone*0", "weight");
        assertRefused("chroma1:stone*lots", "weight");
        assertRefused("chroma1:stone*", "weight");
        assertRefused("chroma1:oak_log[axis=y", "closing bracket");
        assertRefused("chroma1:oak_log[axis]", "properties");
        assertRefused("chroma1:oak_log[]", "properties");
        assertRefused("chroma1:Stone Bricks", "isn't a block id");
        assertRefused("chroma1:a:b:c", "isn't a block id");
    }
}
