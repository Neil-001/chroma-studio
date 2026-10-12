package dev.chromastudio.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

import java.util.Set;
import java.util.function.Predicate;
import net.minecraft.network.chat.Component;
import org.junit.jupiter.api.Test;

class ChromaCommandTest {
    private static final Predicate<String> BLOCKS = Set.of("minecraft:oak_planks", "minecraft:stone", "create:andesite_casing", "odd:facade")::contains;

    @Test
    void aColourIsSixOrThreeHexDigits() {
        assertEquals(0xFF8800, ChromaCommand.parse("#ff8800", BLOCKS));
        assertEquals(0xFF8800, ChromaCommand.parse(" #FF8800 ", BLOCKS));
        assertEquals(0xFF8800, ChromaCommand.parse("ff8800", BLOCKS));
        assertEquals(0xFF8800, ChromaCommand.parse("#f80", BLOCKS));
        assertEquals(0, ChromaCommand.parse("#000000", BLOCKS));
    }

    @Test
    void aBlockIsItsIdWithMinecraftAssumed() {
        assertEquals("minecraft:oak_planks", ChromaCommand.parse("oak_planks", BLOCKS));
        assertEquals("minecraft:stone", ChromaCommand.parse("minecraft:stone", BLOCKS));
        assertEquals("create:andesite_casing", ChromaCommand.parse("Create:Andesite_Casing", BLOCKS));
    }

    @Test
    void aBlockWinsOverAColourUnlessThereIsAHash() {
        Predicate<String> withFacade = id -> id.equals("minecraft:facade");
        assertEquals("minecraft:facade", ChromaCommand.parse("facade", withFacade));
        assertEquals(0xFACADE, ChromaCommand.parse("#facade", withFacade));
        assertEquals(0xFACADE, ChromaCommand.parse("facade", BLOCKS));
    }

    @Test
    void anythingElseSaysWhy() {
        assertInstanceOf(Component.class, ChromaCommand.parse("#ff88", BLOCKS));
        assertInstanceOf(Component.class, ChromaCommand.parse("#oak_planks", BLOCKS));
        assertInstanceOf(Component.class, ChromaCommand.parse("no_such_block", BLOCKS));
        assertInstanceOf(Component.class, ChromaCommand.parse("two words", BLOCKS));
        assertInstanceOf(Component.class, ChromaCommand.parse("", BLOCKS));
    }
}
