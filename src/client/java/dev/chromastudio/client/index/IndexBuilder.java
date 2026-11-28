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
import net.minecraft.client.renderer.block.model.BlockModelPart;
import net.minecraft.client.renderer.block.model.BlockStateModel;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
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
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;
import org.joml.Vector3f;
import org.joml.Vector3fc;

final class IndexBuilder {
    static final int MAX_STATES = 4096;
    private static final Direction[] DIRECTIONS = Direction.values();

    private record Tex(Identifier sprite, int tintIndex) {}

    private record Look(List<Map<Tex, Double>> faces, String key, boolean drawn) {
        Look(List<Map<Tex, Double>> faces, boolean drawn) {
            this(faces, faces.stream().map(f -> f.keySet().toString()).collect(Collectors.joining()), drawn);
        }
    }

    private final BlockModelShaper models;
    private final BlockColors colours;
    private final ResourceManager resources;
    private final IndexOptions options;
    final Map<String, Textures.Frame> frames;
    int imagesRead;
    long readNanos, statsNanos;
    private final Map<String, Sampled> sampled = new HashMap<>();
    private final Map<BlockStateModel, Look> byModel = new IdentityHashMap<>();

    IndexBuilder(BlockModelShaper models, BlockColors colours, ResourceManager resources,
                 Map<String, Textures.Frame> frames, IndexOptions options) {
        this.models = models;
        this.colours = colours;
        this.resources = resources;
        this.frames = frames;
        this.options = options;
    }

    List<Entry> entries(Block block, boolean technical, Map<BlockState, Entry> byState) {
        BlockState byDefault = block.defaultBlockState();
        List<BlockState> all = block.getStateDefinition().getPossibleStates();
        boolean liquid = block instanceof LiquidBlock;
        List<BlockState> states = liquid || all.size() - 1 > MAX_STATES ? List.of(byDefault) : new ArrayList<>(all);
        if (states.size() > 1) states.sort(Comparator.comparingInt(s -> differences(s, byDefault)));

        String id = BuiltInRegistries.BLOCK.getKey(block).toString();
        Map<String, Entry> kept = new LinkedHashMap<>();
        for (BlockState state : states) {
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

    static String properties(BlockState state, BlockState byDefault) {
        List<String> out = new ArrayList<>();
        state.getValues().forEach((property, value) -> {
            if (!value.equals(byDefault.getValue(property))) out.add(property.getName() + "=" + valueName(property, value));
        });
        return String.join(",", out);
    }

    @SuppressWarnings("unchecked")
    static <T extends Comparable<T>> String valueName(Property<T> property, Comparable<?> value) { return property.getName((T) value); }

    private static boolean solid(BlockState state) {
        try {
            return !state.getCollisionShape(EmptyBlockGetter.INSTANCE, BlockPos.ZERO).isEmpty();
        } catch (RuntimeException ignored) {
            return true;
        }
    }

    private static Look modelLook(BlockStateModel model, BlockState state) {
        List<Map<Tex, Double>> faces = emptyFaces();
        List<BlockModelPart> parts = new ArrayList<>();
        ChromaStudioClient.platform.modelParts(model, state, RandomSource.create(42L), parts);
        for (BlockModelPart part : parts) {
            for (Direction side : DIRECTIONS) for (BakedQuad q : part.getQuads(side)) add(faces, q);
            for (BakedQuad q : part.getQuads(null)) add(faces, q);
        }
        boolean drawn = faces.stream().anyMatch(f -> !f.isEmpty());
        TextureAtlasSprite particle = ChromaStudioClient.platform.particleIcon(model, state);
        Identifier name = particle.contents().name();
        int tintIndex = -1;
        for (Map<Tex, Double> face : faces) for (Tex t : face.keySet()) if (t.sprite.equals(name)) tintIndex = Math.max(tintIndex, t.tintIndex);
        for (Map<Tex, Double> face : faces) if (face.isEmpty()) face.put(new Tex(name, tintIndex), 1.0);
        return new Look(faces, drawn);
    }

    private static void add(List<Map<Tex, Double>> faces, BakedQuad q) {
        Tex tex = new Tex(q.sprite().contents().name(), q.isTinted() ? q.tintIndex() : -1);
        faces.get(q.direction().ordinal()).merge(tex, area(q), Double::sum);
    }

    private static double area(BakedQuad q) {
        Vector3fc a = q.position(0), b = q.position(1), c = q.position(2), d = q.position(3);
        return new Vector3f(c).sub(a).cross(new Vector3f(d).sub(b)).length() / 2;
    }

    private static boolean vanillaFluid(BlockState state) {
        Fluid fluid = state.getFluidState().getType();
        return fluid == Fluids.WATER || fluid == Fluids.FLOWING_WATER || fluid == Fluids.LAVA || fluid == Fluids.FLOWING_LAVA;
    }

    private static Look fluidLook(BlockState state) {
        boolean water = state.getFluidState().is(Fluids.WATER) || state.getFluidState().is(Fluids.FLOWING_WATER);
        String name = water ? "water" : "lava";
        int tintIndex = water ? 0 : -1;
        List<Map<Tex, Double>> faces = emptyFaces();
        for (Direction side : DIRECTIONS) {
            Identifier sprite = Identifier.withDefaultNamespace("block/" + name + (side.getAxis().isVertical() ? "_still" : "_flow"));
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
                if (plain != null) layers.add(new FaceLayers.Layer(tex.sprite.toString(), plain, tex.tintIndex, tintSource(tex.tintIndex), area));
            });
            if (layers.isEmpty()) layers.add(new FaceLayers.Layer("map colour", mapColour(state), -1, null, 1));
            return new FaceLayers(layers);
        });
        return out;
    }

    private TintSource tintSource(int tintIndex) {
        return tintIndex < 0 ? null : TintSource.of(colours, tintIndex);
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
        return sampled.computeIfAbsent("map colour " + rgb, key -> {
            int[] colour = new int[Textures.SIDE * Textures.SIDE];
            float[] alpha = new float[colour.length];
            Arrays.fill(colour, rgb);
            Arrays.fill(alpha, 1);
            return new Sampled(new Textures.Frame(colour, alpha, false));
        });
    }
}
