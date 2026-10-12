package dev.chromastudio.client.index;

import dev.chromastudio.core.color.Color;
import dev.chromastudio.core.color.Face;
import dev.chromastudio.core.color.Metric;
import dev.chromastudio.core.color.Scale;
import dev.chromastudio.core.color.TextureStats;
import java.util.List;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

/** One block state in the index: what it is, and what each of its faces looks like. */
public final class Entry {
    static final Face[] SIDES = {Face.DOWN, Face.UP, Face.NORTH, Face.SOUTH, Face.WEST, Face.EAST};
    // The order faces are tried in. Among faces that match equally the first wins, so a block that looks the same
    // all round is matched by its top, the face a builder most often sees.
    static final Face[] BY_PREFERENCE = {Face.UP, Face.NORTH, Face.SOUTH, Face.WEST, Face.EAST, Face.DOWN};

    private final BlockState state;
    private final String id, properties;
    private final Kind kind;
    // Set again when the creative tabs are read, which only a world makes possible.
    private volatile boolean technical;
    private final boolean creativeOnly, tinted, animated, lightSource, fullCube, solid, opaque, sameTextureAllRound;
    private final FaceLayers[] faces;

    Entry(BlockState state, String id, String properties, boolean technical, boolean solid, FaceLayers[] faces) {
        this.state = state;
        this.id = id;
        this.properties = properties;
        this.technical = technical;
        this.solid = solid;
        this.faces = faces;
        this.kind = Kind.of(state);
        this.creativeOnly = CreativeOnly.is(state.getBlock());
        this.lightSource = Kind.lightEmission(state) > 0;
        this.fullCube = Kind.fullCube(state);
        this.opaque = state.canOcclude();
        boolean anyTinted = false, anyAnimated = false, same = true;
        for (FaceLayers f : faces) {
            anyTinted |= f.tinted();
            anyAnimated |= f.animated();
            same &= f.key().equals(faces[0].key());
        }
        this.tinted = anyTinted;
        this.animated = anyAnimated;
        this.sameTextureAllRound = same;
    }

    public Block block() { return state.getBlock(); }

    public BlockState state() { return state; }

    /** The properties that differ from the block's default state, as {@code axis=x,lit=true}. Empty for the default. */
    public String properties() { return properties; }

    public boolean isDefaultState() { return state == state.getBlock().defaultBlockState(); }

    /** The block's id, such as {@code minecraft:stone}. */
    public String id() { return id; }

    public Component name() { return state.getBlock().getName(); }

    public Kind kind() { return kind; }

    /** Whether no creative tab but Operator Utilities lists the block. */
    public boolean technical() { return technical; }

    public boolean creativeOnly() { return creativeOnly; }

    /** Whether any face has a texture the game tints, by biome or otherwise. */
    public boolean tinted() { return tinted; }

    public boolean animated() { return animated; }

    public boolean lightSource() { return lightSource; }

    public boolean fullCube() { return fullCube; }

    /** Whether the player collides with it. */
    public boolean solid() { return solid; }

    public boolean opaque() { return opaque; }

    public boolean sameTextureAllRound() { return sameTextureAllRound; }

    /** A face's stats with the tint applied. {@link Face#ANY} isn't a face: ask {@link #bestFace} which one first. */
    public TextureStats face(Face f, TintContext tint) { return sampled(f, tint).stats; }

    /**
     * A face's stats as another state of this block shows it. States that share their textures share an entry, but a
     * tint may still tell them apart, as redstone dust's does by its power.
     */
    public TextureStats face(Face f, BlockState as, TintContext tint) { return layers(f).sample(as, tint).stats; }

    /** The face closest to a target. Among equals, the top comes first and the bottom last. */
    public Face bestFace(Target t, Scale s, Metric m, TintContext tint) {
        Face best = Face.UP;
        double least = Double.POSITIVE_INFINITY;
        for (Face f : BY_PREFERENCE) {
            double d = t.distribution(s).emd(face(f, tint).distribution(s), m);
            if (d < least) {
                least = d;
                best = f;
            }
        }
        return best;
    }

    /** The names of the textures on a face, such as {@code minecraft:block/stone}, in the model's order. */
    public List<String> textures(Face f) { return layers(f).layers.stream().map(FaceLayers.Layer::sprite).toList(); }

    /** The tint a face's first tinted texture gets, packed 0xRRGGBB, or {@link TintContext#NO_TINT}. */
    public int tint(Face f, TintContext tint) { return layers(f).firstTint(tint, state); }

    void technical(boolean value) { technical = value; }

    Sampled sampled(Face f, TintContext tint) { return layers(f).sample(state, tint); }

    /**
     * A face's average colour as another state of the block shows it. States that share their textures share an
     * entry, but a tint may still tell them apart, as redstone dust's does by its power.
     */
    Color mean(Face f, BlockState as, TintContext tint) { return layers(f).sample(as, tint).stats.mean(Scale.T1); }

    FaceLayers layers(Face f) {
        if (f == Face.ANY) throw new IllegalArgumentException("ANY is not one face");
        return faces[f.ordinal()];
    }

    FaceLayers firstTintedFace() {
        for (FaceLayers f : faces) if (f.tinted()) return f;
        return null;
    }

    /** What makes two states of a block look the same: the textures on all six faces. */
    String textureKey() {
        StringBuilder key = new StringBuilder();
        for (FaceLayers f : faces) key.append(f.key()).append('|');
        return key.toString();
    }

    @Override
    public String toString() { return properties.isEmpty() ? id : id + "[" + properties + "]"; }
}
