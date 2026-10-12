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
import net.minecraft.client.color.block.BlockTintSource;
import net.minecraft.client.renderer.block.BlockStateModelSet;
import net.minecraft.client.renderer.block.FluidModel;
import net.minecraft.client.renderer.block.FluidStateModelSet;
import net.minecraft.client.renderer.block.dispatch.BlockStateModel;
import net.minecraft.client.renderer.block.dispatch.BlockStateModelPart;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.geometry.BakedQuad;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.EmptyBlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.Property;
import org.joml.Vector3f;
import org.joml.Vector3fc;

/** Turns blocks into entries: walks a block's states, reads each face's textures off its model, and keeps the states that look different. */
final class IndexBuilder {
    // A block with more states than this beyond its default is indexed by the default alone.
    static final int MAX_STATES = 4096;
    private static final Direction[] DIRECTIONS = Direction.values();

    /** A texture on a face as the model gives it, before any image is read. */
    private record Tex(Identifier sprite, int tintIndex) {}

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

    private final BlockStateModelSet models;
    private final FluidStateModelSet fluids;
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
    private final Map<BlockStateModel, Look> byModel = new IdentityHashMap<>();

    IndexBuilder(BlockStateModelSet models, FluidStateModelSet fluids, BlockColors colours, ResourceManager resources,
                 Map<String, Textures.Frame> frames, IndexOptions options) {
        this.models = models;
        this.fluids = fluids;
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
            Look look = liquid ? fluidLook(state) : byModel.computeIfAbsent(models.get(state), model -> modelLook(model, state));
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
        state.getValues().forEach(v -> {
            if (!v.value().equals(byDefault.getValue(v.property()))) out.add(v.property().getName() + "=" + v.valueName());
        });
        return String.join(",", out);
    }

    private static boolean solid(BlockState state) {
        try {
            return !state.getCollisionShape(EmptyBlockGetter.INSTANCE, BlockPos.ZERO).isEmpty();
        } catch (RuntimeException _) {
            // A modded shape that needs a real world.
            return true;
        }
    }

    private static Look modelLook(BlockStateModel model, BlockState state) {
        List<Map<Tex, Double>> faces = emptyFaces();
        List<BlockStateModelPart> parts = new ArrayList<>();
        ChromaStudioClient.platform.modelParts(model, state, RandomSource.create(42L), parts);
        for (BlockStateModelPart part : parts) {
            // The quads on the block's outside, then those set back from it, such as a slab's top.
            for (Direction side : DIRECTIONS) for (BakedQuad q : part.getQuads(side)) add(faces, q);
            for (BakedQuad q : part.getQuads(null)) add(faces, q);
        }
        boolean drawn = faces.stream().anyMatch(f -> !f.isEmpty());
        // What a face with nothing drawn on it is taken to look like: the texture the block breaks into.
        TextureAtlasSprite particle = ChromaStudioClient.platform.particleMaterial(model, state).sprite();
        Identifier name = particle.contents().name();
        int tintIndex = -1;
        for (Map<Tex, Double> face : faces) for (Tex t : face.keySet()) if (t.sprite.equals(name)) tintIndex = Math.max(tintIndex, t.tintIndex);
        for (Map<Tex, Double> face : faces) if (face.isEmpty()) face.put(new Tex(name, tintIndex), 1.0);
        return new Look(faces, drawn);
    }

    private static void add(List<Map<Tex, Double>> faces, BakedQuad q) {
        BakedQuad.MaterialInfo material = q.materialInfo();
        Tex tex = new Tex(material.sprite().contents().name(), material.isTinted() ? material.tintIndex() : -1);
        faces.get(q.direction().ordinal()).merge(tex, area(q), Double::sum);
    }

    /** A quad's area in blocks squared, as half the cross product of its diagonals. */
    private static double area(BakedQuad q) {
        Vector3fc a = q.position(0), b = q.position(1), c = q.position(2), d = q.position(3);
        return new Vector3f(c).sub(a).cross(new Vector3f(d).sub(b)).length() / 2;
    }

    /** A fluid has no block model: its top and bottom show the still texture, and its sides the flowing one. */
    private Look fluidLook(BlockState state) {
        FluidModel model = fluids.get(state.getFluidState());
        int tintIndex = tintSource(model) == null ? -1 : 0;
        List<Map<Tex, Double>> faces = emptyFaces();
        for (Direction side : DIRECTIONS) {
            TextureAtlasSprite sprite = (side.getAxis().isVertical() ? model.stillMaterial() : model.flowingMaterial()).sprite();
            faces.get(side.ordinal()).put(new Tex(sprite.contents().name(), tintIndex), 1.0);
        }
        return new Look(faces, true);
    }

    // NeoForge deprecates this for its own fluid tint source, which is still a block tint source.
    @SuppressWarnings("deprecation")
    private static BlockTintSource tintSource(FluidModel model) { return model.tintSource(); }

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

    private BlockTintSource tintSource(BlockState state, int tintIndex) {
        if (tintIndex < 0) return null;
        if (state.getBlock() instanceof LiquidBlock) return tintSource(fluids.get(state.getFluidState()));
        return colours.getTintSource(state, tintIndex);
    }

    private Sampled sampled(Identifier sprite) {
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
        return sampled.computeIfAbsent("map colour " + rgb, _ -> {
            int[] colour = new int[Textures.SIDE * Textures.SIDE];
            float[] alpha = new float[colour.length];
            Arrays.fill(colour, rgb);
            Arrays.fill(alpha, 1);
            return new Sampled(new Textures.Frame(colour, alpha, false));
        });
    }
}
