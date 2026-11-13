package dev.chromastudio.api;

import dev.chromastudio.client.GradientStore;
import dev.chromastudio.client.PaletteStore;
import dev.chromastudio.client.action.PaletteBlocks;
import dev.chromastudio.client.index.BlockIndex;
import dev.chromastudio.client.index.Entry;
import dev.chromastudio.client.index.Query;
import dev.chromastudio.client.index.Target;
import dev.chromastudio.client.index.TintContext;
import dev.chromastudio.client.ui.ChromaScreen;
import dev.chromastudio.client.ui.Handoff;
import dev.chromastudio.core.color.Color;
import dev.chromastudio.core.color.Distribution;
import dev.chromastudio.core.color.Face;
import dev.chromastudio.core.color.FlatNearest;
import dev.chromastudio.core.color.Scale;
import dev.chromastudio.core.palette.Palette;
import dev.chromastudio.core.palette.PaletteEntry;
import dev.chromastudio.core.palette.StoredGradient;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import net.minecraft.client.Minecraft;
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
 * <p>The mod is client-only, so these exist on a client alone. Call them on the client thread unless a method says
 * otherwise. Colours are packed {@code 0xRRGGBB}. Before the block index has been built for the first time, which
 * happens in the background when the game's resources load, every method answers as if no block were known:
 * {@link #indexReady()} says when it has been.
 *
 * <p><b>Versions.</b> What each method was added in is marked {@code @since}. To use one added after the oldest
 * Chroma Studio your mod accepts, check {@link #apiVersion()} first, and treat a {@link NoSuchMethodError} from that
 * call as version 1, the API of 1.0 and 1.1:
 *
 * <pre>{@code
 * static int chromaApi() {
 *     try {
 *         return ChromaStudioApi.apiVersion();
 *     } catch (NoSuchMethodError e) {
 *         return 1;
 *     }
 * }
 * }</pre>
 *
 * Only call this once the loader says the mod {@code chromastudio} is loaded, from a class that is only loaded then.
 */
public final class ChromaStudioApi {
    private ChromaStudioApi() {}

    /**
     * The API's version: 2 since Chroma Studio 1.2.0, and one more with each later minor version that adds to it.
     * Chroma Studio 1.0 and 1.1 have no such method, which makes them version 1. Any thread.
     *
     * @since 1.2.0
     */
    public static int apiVersion() { return 2; }

    /**
     * Whether the block index has finished its first build. Until then every colour method answers as if no block were
     * known, and a {@link #matcher} made then holds none. Any thread.
     *
     * @since 1.2.0
     */
    public static boolean indexReady() { return BlockIndex.get().ready(); }

    /**
     * A number that changes each time the block index finishes a build, as it does when resource packs change. Keep
     * it with what you worked out from the index, and work it out again when this has moved on. Any thread.
     *
     * @since 1.2.0
     */
    public static int indexVersion() { return BlockIndex.get().version(); }

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
     * A matcher for the closest of some blocks to a colour, on one face, which may then be used on any thread: see
     * {@link BlockMatcher}. Make it on the client thread. It is a snapshot: the textures and tints are read now, and a
     * later index build or move of the player changes nothing in it. Made before {@link #indexReady()}, it holds no
     * blocks.
     *
     * @param allowed the block states to choose among, each matched as that state shows itself, so a log's axis and a
     *                stair's facing count. A state given twice counts once, and one the index doesn't know is left out.
     * @param face    the face to match: {@link Direction#UP} for a block seen from above, a side for one seen from the side
     * @param biome   the biome that tints blocks such as grass, leaves and water. With null the tint is the one where
     *                the player stands, and outside a world there is none.
     * @since 1.2.0
     */
    public static BlockMatcher matcher(Collection<BlockState> allowed, Direction face, @Nullable ResourceKey<Biome> biome) {
        Face side = Face.values()[face.ordinal()];
        TintContext tint = biome == null ? TintContext.HERE : TintContext.biome(biome);
        List<BlockState> blocks = new ArrayList<>();
        List<Distribution> looks = new ArrayList<>();
        int version = BlockIndex.get().version();
        if (BlockIndex.get().ready()) {
            for (BlockState state : new LinkedHashSet<>(allowed)) {
                Entry e = BlockIndex.get().entryOf(state);
                if (e == null) continue;
                blocks.add(state);
                looks.add(e.face(side, state, tint).distribution(Scale.T8));
            }
        }
        return new BlockMatcher(blocks, new FlatNearest(looks), version);
    }

    /**
     * The player's palettes, then the bundled presets, as they are now. Each is a copy: changing one changes
     * nothing in the mod. An entry's weight says how often its block turns up beside the others: a block of weight 3
     * takes three times the share of one of weight 1. Use {@link #blockState} for an entry's block.
     */
    public static List<Palette> palettes() { return copies(PaletteStore.get()); }

    /**
     * A palette entry's block state, or null when this game has no such block or state, such as one from a mod since
     * removed. Any thread.
     *
     * @since 1.2.0
     */
    public static @Nullable BlockState blockState(PaletteEntry entry) { return PaletteBlocks.state(entry); }

    /**
     * The gradients the player saved in the Gradient tab, by name, as they are now. Each is a copy: see
     * {@link SavedGradient}. Any thread. The list is read from a file the first time and kept after, so this is cheap
     * to call each time it is wanted.
     *
     * @since 1.2.0
     */
    public static List<SavedGradient> savedGradients() {
        List<SavedGradient> out = new ArrayList<>();
        for (StoredGradient g : GradientStore.get().all()) out.add(copy(g));
        return List.copyOf(out);
    }

    static SavedGradient copy(StoredGradient g) {
        List<SavedGradient.Stop> stops = new ArrayList<>();
        for (StoredGradient.Stop s : g.stops()) {
            stops.add(new SavedGradient.Stop(s.rgb(), s.isBlock() ? PaletteBlocks.state(s.block()) : null, s.isBlock() ? s.block().idWithProperties() : null));
        }
        List<SavedGradient.Step> steps = new ArrayList<>();
        for (StoredGradient.Step s : g.blocks()) {
            steps.add(new SavedGradient.Step(s.ideal(), PaletteBlocks.state(s.block()), s.block().idWithProperties(), s.byHand()));
        }
        return new SavedGradient(g.name(), stops, g.steps(), steps);
    }

    /**
     * Opens Chroma Studio's window on the Gradient tab, with a saved gradient loaded into it when one is named. Closing
     * the window then returns to the game, not to the screen that was open. Outside a world it does nothing.
     *
     * @param savedName the name of a gradient from {@link #savedGradients()} to load, whatever its case, or null to
     *                  show the tab as the player left it. A name no saved gradient has also leaves the tab as it was.
     * @return whether the window opened
     * @since 1.2.0
     */
    public static boolean openGradientTab(@Nullable String savedName) {
        if (Minecraft.getInstance().player == null) return false;
        Handoff.gradientLoad = savedName == null ? null : GradientStore.get().get(savedName);
        ChromaScreen.open("gradient");
        return ChromaScreen.current() != null;
    }

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
