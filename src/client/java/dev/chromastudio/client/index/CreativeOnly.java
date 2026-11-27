package dev.chromastudio.client.index;

import java.util.Set;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.InfestedBlock;

final class CreativeOnly {
    private CreativeOnly() {}

    private static final Set<Block> BLOCKS = Set.of(
            Blocks.BEDROCK, Blocks.REINFORCED_DEEPSLATE,
            Blocks.END_PORTAL_FRAME, Blocks.END_PORTAL, Blocks.END_GATEWAY, Blocks.NETHER_PORTAL,
            Blocks.SPAWNER, Blocks.TRIAL_SPAWNER, Blocks.VAULT,
            Blocks.PETRIFIED_OAK_SLAB,
            Blocks.COMMAND_BLOCK, Blocks.CHAIN_COMMAND_BLOCK, Blocks.REPEATING_COMMAND_BLOCK,
            Blocks.JIGSAW, Blocks.STRUCTURE_BLOCK, Blocks.STRUCTURE_VOID, Blocks.TEST_BLOCK, Blocks.TEST_INSTANCE_BLOCK,
            Blocks.BARRIER, Blocks.LIGHT,
            Blocks.BUDDING_AMETHYST, Blocks.CHORUS_PLANT, Blocks.FROSTED_ICE, Blocks.BUBBLE_COLUMN,
            Blocks.MOVING_PISTON, Blocks.PISTON_HEAD,
            Blocks.FARMLAND, Blocks.DIRT_PATH,
            Blocks.SUSPICIOUS_SAND, Blocks.SUSPICIOUS_GRAVEL);

    static boolean is(Block block) {
        return block instanceof InfestedBlock || BLOCKS.contains(block);
    }
}
