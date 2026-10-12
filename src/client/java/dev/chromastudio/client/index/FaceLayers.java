package dev.chromastudio.client.index;

import dev.chromastudio.core.color.Scale;
import dev.chromastudio.core.color.TextureStats;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import net.minecraft.client.color.block.BlockTintSource;
import net.minecraft.world.level.block.state.BlockState;

/** The textures on one face of one block state, and how they combine into what the face looks like. */
final class FaceLayers {
    /**
     * One texture on the face.
     *
     * @param sprite the texture's name, such as {@code minecraft:block/stone}
     * @param plain the texture without tint
     * @param tintIndex the model's tint index, or -1
     * @param source what gives the tint, or null when the texture isn't tinted
     * @param area how much of the face its quads cover, in blocks squared
     */
    record Layer(String sprite, Sampled plain, int tintIndex, BlockTintSource source, double area) {}

    // A quad this close to a whole face is an overlay on the ones before it.
    private static final double WHOLE = 0.99;
    // How many tints of one face are kept. Walking between biomes makes a new one every few blocks.
    private static final int KEPT = 16;

    final List<Layer> layers;
    private final boolean stacked;
    private final boolean tinted;
    private final Sampled untinted;
    private final Map<List<Integer>, Sampled> byTint = new ConcurrentHashMap<>();

    FaceLayers(List<Layer> layers) {
        this.layers = List.copyOf(layers);
        this.stacked = layers.stream().allMatch(l -> l.area >= WHOLE);
        this.tinted = layers.stream().anyMatch(l -> l.source != null);
        this.untinted = layers.size() == 1 ? layers.getFirst().plain : combine(null);
    }

    /** The names and tint indexes, which is what makes two faces the same texture. */
    String key() {
        return layers.stream().map(l -> l.tintIndex < 0 ? l.sprite : l.sprite + "#" + l.tintIndex).collect(Collectors.joining("+"));
    }

    boolean tinted() { return tinted; }

    boolean animated() { return untinted.animated; }

    /** The first tinted texture's colour in a context, or {@link TintContext#NO_TINT}. */
    int firstTint(TintContext tint, BlockState state) {
        for (Layer l : layers) if (l.source != null) return tint.colour(l.source, state);
        return TintContext.NO_TINT;
    }

    Sampled sample(BlockState state, TintContext tint) {
        if (!tinted) return untinted;
        List<Integer> colours = layers.stream().map(l -> l.source == null ? TintContext.NO_TINT : tint.colour(l.source, state)).toList();
        if (colours.stream().allMatch(c -> c == TintContext.NO_TINT)) return untinted;
        Sampled s = byTint.get(colours);
        if (s == null) {
            if (byTint.size() >= KEPT) byTint.clear();
            s = layers.size() == 1
                    ? new Sampled(untinted.stats.tinted(colours.getFirst()), untinted.animated)
                    : combine(colours);
            byTint.put(colours, s);
        }
        return s;
    }

    /**
     * Several textures as one. Whole-face textures are laid over each other in the model's order, as a grass
     * block's green fringe lies over its dirt. Otherwise they are averaged by the area each covers.
     */
    private Sampled combine(List<Integer> tints) {
        double[][] cells = new double[Textures.SIDE * Textures.SIDE][4];
        double totalArea = layers.stream().mapToDouble(Layer::area).sum();
        boolean animated = false;
        for (int n = 0; n < layers.size(); n++) {
            Layer layer = layers.get(n);
            animated |= layer.plain.animated;
            int tint = tints == null ? TintContext.NO_TINT : tints.get(n);
            int[] pixels = layer.plain.stats.pixels(Scale.T16);
            for (int i = 0; i < pixels.length; i++) {
                int p = pixels[i];
                double a = (p >>> 24) / 255.0;
                if (a == 0) continue;
                double r = channel(p >> 16 & 0xFF, tint >> 16 & 0xFF, tint), g = channel(p >> 8 & 0xFF, tint >> 8 & 0xFF, tint),
                        b = channel(p & 0xFF, tint & 0xFF, tint);
                double[] cell = cells[i];
                if (stacked) {
                    cell[0] = r * a + cell[0] * (1 - a);
                    cell[1] = g * a + cell[1] * (1 - a);
                    cell[2] = b * a + cell[2] * (1 - a);
                    cell[3] = a + cell[3] * (1 - a);
                } else {
                    double share = a * layer.area / totalArea;
                    cell[0] += r * share;
                    cell[1] += g * share;
                    cell[2] += b * share;
                    cell[3] += share;
                }
            }
        }
        int[] argb = IntStream.range(0, cells.length).map(i -> Textures.pack(cells[i])).toArray();
        return new Sampled(TextureStats.of(argb, Textures.SIDE, Textures.SIDE), animated);
    }

    /** A channel in linear light after the tint, which the game multiplies into the stored value. */
    private static double channel(int value, int tintChannel, int tint) {
        return Textures.linear(tint == TintContext.NO_TINT ? value : (value * tintChannel + 127) / 255);
    }
}
