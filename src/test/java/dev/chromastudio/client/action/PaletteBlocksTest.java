package dev.chromastudio.client.action;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

import dev.chromastudio.core.palette.PaletteEntry;
import net.minecraft.SharedConstants;
import net.minecraft.core.Direction;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/** A state and its palette entry, with the game's blocks loaded. */
class PaletteBlocksTest {
    @BeforeAll
    static void loadBlocks() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    void aDefaultStateHasNoProperties() {
        assertEquals(new PaletteEntry("minecraft:stone", ""), PaletteBlocks.of(Blocks.STONE.defaultBlockState()));
        assertEquals(new PaletteEntry("minecraft:oak_log", ""), PaletteBlocks.of(Blocks.OAK_LOG.defaultBlockState()));
    }

    @Test
    void aStateKeepsEveryPropertyThatIsNotTheDefaultAndComesBackTheSame() {
        BlockState log = Blocks.OAK_LOG.defaultBlockState().setValue(BlockStateProperties.AXIS, Direction.Axis.X);
        assertEquals(new PaletteEntry("minecraft:oak_log", "axis=x"), PaletteBlocks.of(log));
        // A capture counts states as they stand, so two that only differ by water are two entries.
        BlockState stairs = Blocks.OAK_STAIRS.defaultBlockState().setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.EAST)
                .setValue(BlockStateProperties.WATERLOGGED, true);
        PaletteEntry entry = PaletteBlocks.of(stairs);
        assertEquals("minecraft:oak_stairs", entry.blockId());
        assertEquals("facing=east,waterlogged=true", entry.properties());
        assertSame(stairs, PaletteBlocks.state(entry));
        assertSame(log, PaletteBlocks.state(PaletteBlocks.of(log)));
    }

    @Test
    void anUnknownBlockHasNoState() {
        assertNull(PaletteBlocks.state(new PaletteEntry("somemod:gone", "")));
        assertNull(PaletteBlocks.state(new PaletteEntry("minecraft:oak_log", "axis=sideways")));
    }
}
