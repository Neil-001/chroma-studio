package dev.chromastudio.api;

import dev.chromastudio.core.color.Color;
import dev.chromastudio.core.color.FlatNearest;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

/**
 * Finds the closest of a fixed set of block states to a colour, on one face. Make one on the client thread with
 * {@link ChromaStudioApi#matcher}; it is a snapshot of the block index as it was then, and never changes after.
 *
 * <p><b>Threads.</b> Every method may be called from any thread, by any number of threads at once.
 *
 * <p><b>Speed.</b> A colour is matched by going through every block in the set once, a few dozen arithmetic steps
 * each, and answers are remembered, so the same colour asked again costs a lookup. Ten thousand colours against a
 * few hundred blocks take some tens of milliseconds.
 *
 * <p><b>Matching.</b> As {@link ChromaStudioApi#closest(int, int)} and the Match tab with nothing changed match a
 * colour: the earth mover's distance in OKLab between the colour and the face's texture seen at eight texels to a
 * block, without the game's shading. A face's tint was read when the matcher was made.
 *
 * @since 1.2.0
 */
public final class BlockMatcher {
    /** How many answers are remembered. Past this a colour is worked out each time, so memory stays bounded. */
    private static final int REMEMBERED = 1 << 16;

    private final List<BlockState> blocks;
    private final FlatNearest nearest;
    private final int indexVersion;
    private final Map<Integer, Integer> answers = new ConcurrentHashMap<>();

    BlockMatcher(List<BlockState> blocks, FlatNearest nearest, int indexVersion) {
        this.blocks = List.copyOf(blocks);
        this.nearest = nearest;
        this.indexVersion = indexVersion;
    }

    /**
     * The block closest to a colour, or null when the matcher holds none. Among blocks that match equally well, the
     * one that came first in the set the matcher was made from wins, so the answer never varies.
     *
     * @param rgb the colour, packed {@code 0xRRGGBB}. Anything in the top eight bits, such as alpha, is ignored.
     */
    public @Nullable BlockState closest(int rgb) {
        if (blocks.isEmpty()) return null;
        int colour = rgb & 0xFFFFFF;
        Integer known = answers.get(colour);
        if (known == null) {
            known = nearest.nearest(Color.ofRgb(colour));
            if (answers.size() < REMEMBERED) answers.put(colour, known);
        }
        return blocks.get(known);
    }

    /**
     * How far a colour is from a block's face, as the matcher measures it: 0 for a flat texture of exactly that
     * colour, and about 0.02 for a difference the eye just notices. Use it to tell a close match from the best of a
     * poor lot.
     *
     * @return the distance, or {@link Double#NaN} for a block the matcher doesn't hold
     */
    public double distance(int rgb, BlockState block) {
        int i = blocks.indexOf(block);
        return i < 0 ? Double.NaN : nearest.distance(Color.ofRgb(rgb & 0xFFFFFF), i);
    }

    /**
     * The blocks this matcher chooses among, in the order they were given, each once. A state the index doesn't know,
     * such as one of a block with no model, or every one before the index's first build, is left out.
     */
    public List<BlockState> blocks() { return blocks; }

    /** Whether the matcher holds no blocks, so {@link #closest} always answers null. */
    public boolean isEmpty() { return blocks.isEmpty(); }

    /**
     * The {@link ChromaStudioApi#indexVersion()} this matcher was made from. When that has moved on, as it does when
     * resource packs change, a new matcher gives answers for the textures the player sees now.
     */
    public int indexVersion() { return indexVersion; }
}
