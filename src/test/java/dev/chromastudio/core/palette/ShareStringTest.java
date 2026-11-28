package dev.chromastudio.core.palette;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

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

    private static void assertRefused(String s, UnreadablePalette.Reason reason) {
        assertEquals(reason, assertThrows(UnreadablePalette.class, () -> ShareString.decode(s)).reason(), s);
    }

    @Test
    void malformedStringsSayWhatIsWrong() {
        assertRefused(null, UnreadablePalette.Reason.NO_SHARE);
        assertRefused("   ", UnreadablePalette.Reason.NO_SHARE);
        assertRefused("stone,andesite", UnreadablePalette.Reason.NOT_SHARE);
        assertRefused("chroma2:stone", UnreadablePalette.Reason.OTHER_VERSION);
        assertRefused("chroma1:stone,,andesite", UnreadablePalette.Reason.EMPTY_ENTRY);
        assertRefused("chroma1:stone,", UnreadablePalette.Reason.EMPTY_ENTRY);
        assertRefused("chroma1:stone*0", UnreadablePalette.Reason.BAD_WEIGHT);
        assertRefused("chroma1:stone*lots", UnreadablePalette.Reason.BAD_WEIGHT);
        assertRefused("chroma1:stone*", UnreadablePalette.Reason.BAD_WEIGHT);
        assertRefused("chroma1:oak_log[axis=y", UnreadablePalette.Reason.OPEN_BRACKET);
        assertRefused("chroma1:oak_log[axis]", UnreadablePalette.Reason.BAD_PROPERTIES);
        assertRefused("chroma1:oak_log[]", UnreadablePalette.Reason.BAD_PROPERTIES);
        assertRefused("chroma1:Stone Bricks", UnreadablePalette.Reason.NOT_BLOCK);
        assertRefused("chroma1:a:b:c", UnreadablePalette.Reason.NOT_BLOCK);
    }
}
