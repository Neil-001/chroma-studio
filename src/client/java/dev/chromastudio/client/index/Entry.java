package dev.chromastudio.client.index;

import dev.chromastudio.core.color.Color;
import dev.chromastudio.core.color.Face;
import dev.chromastudio.core.color.Metric;
import dev.chromastudio.core.color.Scale;
import dev.chromastudio.core.color.TextureStats;
import java.util.List;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.block.BellBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.state.BlockState;

public final class Entry {
    static final Face[] SIDES = {Face.DOWN, Face.UP, Face.NORTH, Face.SOUTH, Face.WEST, Face.EAST};
    static final Face[] BY_PREFERENCE = {Face.UP, Face.NORTH, Face.SOUTH, Face.WEST, Face.EAST, Face.DOWN};

    private final BlockState state;
    private final String id, properties;
    private final Kind kind;
    private volatile boolean technical;
    private final boolean creativeOnly, tinted, animated, lightSource, fullCube, solid, opaque, sameTextureAllRound, modelDraws;
    private final FaceLayers[] faces;

    Entry(BlockState state, String id, String properties, boolean technical, boolean solid, FaceLayers[] faces) {
        this(state, id, properties, technical, solid, true, faces);
    }

    Entry(BlockState state, String id, String properties, boolean technical, boolean solid, boolean modelDraws, FaceLayers[] faces) {
        this.state = state;
        this.id = id;
        this.properties = properties;
        this.technical = technical;
        this.solid = solid;
        this.modelDraws = modelDraws;
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

    public String properties() { return properties; }

    public static String properties(BlockState state) { return IndexBuilder.properties(state, state.getBlock().defaultBlockState()); }

    public boolean isDefaultState() { return state == state.getBlock().defaultBlockState(); }

    public String id() { return id; }

    public Component name() { return state.getBlock().getName(); }

    public Kind kind() { return kind; }

    public boolean technical() { return technical; }

    public boolean creativeOnly() { return creativeOnly; }

    public boolean tinted() { return tinted; }

    public boolean animated() { return animated; }

    public boolean lightSource() { return lightSource; }

    public boolean fullCube() { return fullCube; }

    public boolean solid() { return solid; }

    public boolean opaque() { return opaque; }

    public boolean sameTextureAllRound() { return sameTextureAllRound; }

    public boolean blockEntityRendered() {
        return state.getBlock() instanceof EntityBlock && (!modelDraws || state.getBlock() instanceof BellBlock);
    }

    public boolean modelDraws() { return modelDraws; }

    public TextureStats face(Face f, TintContext tint) { return sampled(f, tint).stats; }

    public TextureStats face(Face f, BlockState as, TintContext tint) { return layers(f).sample(as, tint).stats; }

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

    public List<String> textures(Face f) { return layers(f).layers.stream().map(FaceLayers.Layer::sprite).toList(); }

    public int tint(Face f, TintContext tint) { return layers(f).firstTint(tint, state); }

    void technical(boolean value) { technical = value; }

    Sampled sampled(Face f, TintContext tint) { return layers(f).sample(state, tint); }

    Color mean(Face f, BlockState as, TintContext tint) { return layers(f).sample(as, tint).stats.mean(Scale.T1); }

    FaceLayers layers(Face f) {
        if (f == Face.ANY) throw new IllegalArgumentException("ANY is not one face");
        return faces[f.ordinal()];
    }

    FaceLayers firstTintedFace() {
        for (FaceLayers f : faces) if (f.tinted()) return f;
        return null;
    }

    @Override
    public String toString() { return properties.isEmpty() ? id : id + "[" + properties + "]"; }
}
