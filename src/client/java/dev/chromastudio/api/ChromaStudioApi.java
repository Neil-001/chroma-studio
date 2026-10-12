package dev.chromastudio.api;

import dev.chromastudio.client.PaletteStore;
import dev.chromastudio.client.index.BlockIndex;
import dev.chromastudio.client.index.Query;
import dev.chromastudio.client.index.Target;
import dev.chromastudio.client.index.TintContext;
import dev.chromastudio.core.color.Color;
import dev.chromastudio.core.color.Face;
import dev.chromastudio.core.palette.Palette;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.state.BlockState;
import org.jspecify.annotations.Nullable;

/**
 * What Chroma Studio knows about block colours, for other mods to read.
 *
 * <p>This package, {@code dev.chromastudio.api}, follows semantic versioning: its methods keep their signatures and
 * meaning until the mod's next major version. The rest of the mod doesn't, and may change in any release.
 *
 * <p>The mod is client-only, so these exist on a client alone. Call them on the client thread. Colours are packed
 * {@code 0xRRGGBB}. Before the block index has been built for the first time, which happens in the background when the
 * game's resources load, every method answers as if no block were known.
 */
public final class ChromaStudioApi {
    private ChromaStudioApi() {}

    /**
     * The average colour of one face of a block state, as its textures show it without the game's shading.
     *
     * @param biome the biome that tints the block, as with grass, leaves and water. With null the tint is the one
     *              where the player stands, and outside a world there is none.
     * @return the colour, or empty when the block isn't in the index
     */
    public static Optional<Integer> colourOf(BlockState state, Direction face, @Nullable ResourceKey<Biome> biome) {
        // The index's faces are in the order of the game's directions.
        Face side = Face.values()[face.ordinal()];
        return BlockIndex.get().colourOf(state, side, biome == null ? TintContext.HERE : TintContext.biome(biome)).map(Color::rgb);
    }

    /**
     * The blocks closest to a colour, nearest first, each as the state whose best face comes closest. They are
     * matched as the Match tab does with nothing changed: in OKLab, on any face, at eight texels to a block, tinted
     * as where the player stands. Technical blocks follow the player's setting, and blocks a block entity draws,
     * such as chests and banners, are left out.
     *
     * @param limit the most to return. Nothing is returned for zero or less.
     */
    public static List<BlockState> closest(int rgb, int limit) {
        if (limit <= 0) return List.of();
        Query query = new Query().limit(limit);
        // One state a block, whatever the player's setting lists in the Match tab.
        query.statesSeparately = false;
        List<BlockState> out = new ArrayList<>();
        for (BlockIndex.Match m : BlockIndex.get().closest(Target.of(Color.ofRgb(rgb & 0xFFFFFF)), query)) out.add(m.entry().state());
        return List.copyOf(out);
    }

    /**
     * The player's palettes, then the bundled presets, as they are now. Each is a copy: changing one changes
     * nothing in the mod.
     */
    public static List<Palette> palettes() { return copies(PaletteStore.get()); }

    static List<Palette> copies(PaletteStore store) {
        List<Palette> out = new ArrayList<>();
        for (Palette p : store.all()) {
            Palette copy = p.copy();
            copy.preset = p.preset;
            out.add(copy);
        }
        return List.copyOf(out);
    }
}
