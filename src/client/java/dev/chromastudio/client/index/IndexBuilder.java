package dev.chromastudio.client.index;

import dev.chromastudio.client.ChromaStudioClient;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import net.minecraft.client.color.block.BlockColors;
import net.minecraft.client.renderer.block.BlockModelShaper;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.EmptyBlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;
import org.joml.Vector3f;

/** Turns blocks into entries: walks a block's states, reads each face's textures off its model, and keeps the states that look different. */
final class IndexBuilder {
    // A block with more states than this beyond its default is indexed by the default alone.
    static final int MAX_STATES = 4096;
    private static final Direction[] DIRECTIONS = Direction.values();

    /** A texture on a face as the model gives it, before any image is read. */
    private record Tex(ResourceLocation sprite, int tintIndex) {}

    /**
     * What a model shows: each face's textures with the area their quads cover, in the model's order, and the key
     * that two models share when they show the same textures. Only the names are in the key: a stair's states
     * cover different areas with the same textures. {@code drawn} is false when the model has no quads at all.
     */
    private record Look(List<Map<Tex, Double>> faces, String key, boolean drawn) {
        Look(List<Map<Tex, Double>> faces, boolean drawn) {
            this(faces, faces.stream().map(f -> f.keySet().toString()).collect(Collectors.joining()), drawn);
        }
    }

    private final BlockModelShaper models;
    private final BlockColors colours;
    private final ResourceManager resources;
    private final IndexOptions options;
    /** The frames by sprite name: those from the cache at first, with every one read since added. */
    final Map<String, Textures.Frame> frames;
    /** How many images were read, as opposed to found in the cache. */
    int imagesRead;
    /** The time spent reading images, and working out the statistics of the frames. */
    long readNanos, statsNanos;
    private final Map<String, Sampled> sampled = new HashMap<>();
    // Many states share one baked model, as a note block's 1,150 do, and a model is only walked once.
    private final Map<BakedModel, Look> byModel = new IdentityHashMap<>();

    IndexBuilder(BlockModelShaper models, BlockColors colours, ResourceManager resources,
                 Map<String, Textures.Frame> frames, IndexOptions options) {
        this.models = models;
        this.colours = colours;
        this.resources = resources;
        this.frames = frames;
        this.options = options;
    }

    /**
     * A block's entries: its default state first, then the states that look different from every one before, those
     * nearest the default first, up to the cap. Every state walked is noted in {@code byState} with the entry that
     * looks like it.
     */
    List<Entry> entries(Block block, boolean technical, Map<BlockState, Entry> byState) {
        BlockState byDefault = block.defaultBlockState();
        List<BlockState> all = block.getStateDefinition().getPossibleStates();
        boolean liquid = block instanceof LiquidBlock;
        List<BlockState> states = liquid || all.size() - 1 > MAX_STATES ? List.of(byDefault) : new ArrayList<>(all);
        if (states.size() > 1) states.sort(Comparator.comparingInt(s -> differences(s, byDefault)));

        String id = BuiltInRegistries.BLOCK.getKey(block).toString();
        Map<String, Entry> kept = new LinkedHashMap<>();
        for (BlockState state : states) {
            // Water in the block doesn't change its textures, so those states only crowd out the ones that do.
            if (state != byDefault && state.getOptionalValue(BlockStateProperties.WATERLOGGED).orElse(false)) continue;
            Look look = liquid && vanillaFluid(state) ? fluidLook(state) : byModel.computeIfAbsent(models.getBlockModel(state), model -> modelLook(model, state));
            Entry entry = kept.get(look.key);
            if (entry == null && kept.size() < Math.max(1, options.statesCap())) {
                entry = new Entry(state, id, properties(state, byDefault), technical, solid(state), look.drawn, layers(state, look.faces));
                kept.put(look.key, entry);
            }
            byState.put(state, entry != null ? entry : kept.values().iterator().next());
        }
        return List.copyOf(kept.values());
    }

    private static int differences(BlockState state, BlockState byDefault) {
        int n = 0;
        for (Property<?> p : state.getProperties()) if (!state.getValue(p).equals(byDefault.getValue(p))) n++;
        return n;
    }

    /** The properties that differ from the default state, in the game's {@code name=value} form. */
    static String properties(BlockState state, BlockState byDefault) {
        List<String> out = new ArrayList<>();
        state.getValues().forEach((property, value) -> {
            if (!value.equals(byDefault.getValue(property))) out.add(property.getName() + "=" + valueName(property, value));
        });
        return String.join(",", out);
    }

    /** A value's name in the game's form, such as {@code north} or {@code 3}. The map holds the property and value with their types apart. */
    @SuppressWarnings("unchecked")
    static <T extends Comparable<T>> String valueName(Property<T> property, Comparable<?> value) { return property.getName((T) value); }

    private static boolean solid(BlockState state) {
        try {
            return !state.getCollisionShape(EmptyBlockGetter.INSTANCE, BlockPos.ZERO).isEmpty();
        } catch (RuntimeException ignored) {
            // A modded shape that needs a real world.
            return true;
        }
    }

    private static Look modelLook(BakedModel model, BlockState state) {
        List<Map<Tex, Double>> faces = emptyFaces();
        // The quads on the block's outside, then those set back from it, such as a slab's top. A model with several
        // variants picks one by the random, so each call gets the same seed and picks the same.
        for (Direction side : DIRECTIONS) for (BakedQuad q : ChromaStudioClient.platform.quads(model, state, side, RandomSource.create(42L))) add(faces, q);
        for (BakedQuad q : ChromaStudioClient.platform.quads(model, state, null, RandomSource.create(42L))) add(faces, q);
        boolean drawn = faces.stream().anyMatch(f -> !f.isEmpty());
        // What a face with nothing drawn on it is taken to look like: the texture the block breaks into.
        TextureAtlasSprite particle = ChromaStudioClient.platform.particleIcon(model, state);
        ResourceLocation name = particle.contents().name();
        int tintIndex = -1;
        for (Map<Tex, Double> face : faces) for (Tex t : face.keySet()) if (t.sprite.equals(name)) tintIndex = Math.max(tintIndex, t.tintIndex);
        for (Map<Tex, Double> face : faces) if (face.isEmpty()) face.put(new Tex(name, tintIndex), 1.0);
        return new Look(faces, drawn);
    }

    private static void add(List<Map<Tex, Double>> faces, BakedQuad q) {
        Tex tex = new Tex(q.getSprite().contents().name(), q.isTinted() ? q.getTintIndex() : -1);
        faces.get(q.getDirection().ordinal()).merge(tex, area(q), Double::sum);
    }

    /** A quad's area in blocks squared, as half the cross product of its diagonals. */
    private static double area(BakedQuad q) {
        Vector3f a = position(q, 0), b = position(q, 1), c = position(q, 2), d = position(q, 3);
        return c.sub(a).cross(d.sub(b)).length() / 2;
    }

    // 1.21.1 packs a quad's vertices as ints, eight to a vertex, the position's three floats first.
    private static final int VERTEX_INTS = 8;

    private static Vector3f position(BakedQuad q, int vertex) {
        int[] v = q.getVertices();
        int at = vertex * VERTEX_INTS;
        return new Vector3f(Float.intBitsToFloat(v[at]), Float.intBitsToFloat(v[at + 1]), Float.intBitsToFloat(v[at + 2]));
    }

    /** Water or lava. 1.21.1 has no fluid models to ask, so only these two are known by their textures; a modded fluid is read like a block. */
    private static boolean vanillaFluid(BlockState state) {
        Fluid fluid = state.getFluidState().getType();
        return fluid == Fluids.WATER || fluid == Fluids.FLOWING_WATER || fluid == Fluids.LAVA || fluid == Fluids.FLOWING_LAVA;
    }

    /**
     * A fluid has no block model: its top and bottom show the still texture, and its sides the flowing one. Water is
     * tinted by the biome, through the block colours, as the game draws it.
     */
    private static Look fluidLook(BlockState state) {
        boolean water = state.getFluidState().is(Fluids.WATER) || state.getFluidState().is(Fluids.FLOWING_WATER);
        String name = water ? "water" : "lava";
        int tintIndex = water ? 0 : -1;
        List<Map<Tex, Double>> faces = emptyFaces();
        for (Direction side : DIRECTIONS) {
            ResourceLocation sprite = ResourceLocation.withDefaultNamespace("block/" + name + (side.getAxis().isVertical() ? "_still" : "_flow"));
            faces.get(side.ordinal()).put(new Tex(sprite, tintIndex), 1.0);
        }
        return new Look(faces, true);
    }

    private static List<Map<Tex, Double>> emptyFaces() {
        List<Map<Tex, Double>> faces = new ArrayList<>();
        for (int i = 0; i < DIRECTIONS.length; i++) faces.add(new LinkedHashMap<>());
        return faces;
    }

    private FaceLayers[] layers(BlockState state, List<Map<Tex, Double>> faces) {
        FaceLayers[] out = new FaceLayers[faces.size()];
        Arrays.setAll(out, i -> {
            List<FaceLayers.Layer> layers = new ArrayList<>();
            faces.get(i).forEach((tex, area) -> {
                Sampled plain = sampled(tex.sprite);
                if (plain != null) layers.add(new FaceLayers.Layer(tex.sprite.toString(), plain, tex.tintIndex, tintSource(state, tex.tintIndex), area));
            });
            // No image to read, as for a texture a mod draws itself: the colour the block has on a map.
            if (layers.isEmpty()) layers.add(new FaceLayers.Layer("map colour", mapColour(state), -1, null, 1));
            return new FaceLayers(layers);
        });
        return out;
    }

    private TintSource tintSource(BlockState state, int tintIndex) {
        return tintIndex < 0 ? null : TintSource.of(colours, tintIndex);
    }

    private Sampled sampled(ResourceLocation sprite) {
        String name = sprite.toString();
        if (sampled.containsKey(name)) return sampled.get(name);
        Textures.Frame frame = frames.get(name);
        if (frame == null) {
            long started = System.nanoTime();
            frame = Textures.read(resources, sprite, options.animatedAverage());
            readNanos += System.nanoTime() - started;
            imagesRead++;
            if (frame != null) frames.put(name, frame);
        }
        long started = System.nanoTime();
        Sampled s = frame == null ? null : new Sampled(frame);
        statsNanos += System.nanoTime() - started;
        sampled.put(name, s);
        return s;
    }

    private Sampled mapColour(BlockState state) {
        int rgb = state.getMapColor(EmptyBlockGetter.INSTANCE, BlockPos.ZERO).col;
        return sampled.computeIfAbsent("map colour " + rgb, key -> {
            int[] colour = new int[Textures.SIDE * Textures.SIDE];
            float[] alpha = new float[colour.length];
            Arrays.fill(colour, rgb);
            Arrays.fill(alpha, 1);
            return new Sampled(new Textures.Frame(colour, alpha, false));
        });
    }
}
