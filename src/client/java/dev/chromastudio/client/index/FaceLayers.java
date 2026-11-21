package dev.chromastudio.client.index;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import net.minecraft.client.color.block.BlockTintSource;
import net.minecraft.world.level.block.state.BlockState;

final class FaceLayers {
    record Layer(String sprite, Sampled plain, int tintIndex, BlockTintSource source, double area) {}

    private static final double WHOLE = 0.99;
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

    String key() {
        return layers.stream().map(l -> l.tintIndex < 0 ? l.sprite : l.sprite + "#" + l.tintIndex).collect(Collectors.joining("+"));
    }

    boolean tinted() { return tinted; }

    boolean animated() { return untinted.animated; }

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

    private Sampled combine(List<Integer> tints) {
        double[][] cells = new double[Textures.SIDE * Textures.SIDE][4];
        double totalArea = layers.stream().mapToDouble(Layer::area).sum();
        boolean animated = false;
        for (int n = 0; n < layers.size(); n++) {
            Layer layer = layers.get(n);
            animated |= layer.plain.animated;
            int tint = tints == null ? TintContext.NO_TINT : tints.get(n);
            Textures.Frame frame = layer.plain.frame;
            for (int i = 0; i < cells.length; i++) {
                int p = frame.rgb()[i];
                double a = frame.alpha()[i];
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
        return new Sampled(Textures.Frame.of(cells, animated));
    }

    private static double channel(int value, int tintChannel, int tint) {
        return Textures.linear(tint == TintContext.NO_TINT ? value : (value * tintChannel + 127) / 255);
    }
}
