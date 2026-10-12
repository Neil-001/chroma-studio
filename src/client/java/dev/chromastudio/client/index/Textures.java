package dev.chromastudio.client.index;

import com.mojang.blaze3d.platform.NativeImage;
import dev.chromastudio.core.color.TextureStats;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import net.minecraft.client.resources.metadata.animation.AnimationFrame;
import net.minecraft.client.resources.metadata.animation.AnimationMetadataSection;
import net.minecraft.client.resources.metadata.animation.FrameSize;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;

/** Reads a sprite's image from the resource packs and brings it down to one 16 by 16 frame. */
final class Textures {
    private Textures() {}

    static final int SIDE = 16;
    private static final double[] LINEAR = new double[256];

    static {
        for (int i = 0; i < 256; i++) {
            double v = i / 255.0;
            LINEAR[i] = v <= 0.04045 ? v / 12.92 : Math.pow((v + 0.055) / 1.055, 2.4);
        }
    }

    /**
     * A texture as the index keeps it: 16 by 16 ARGB in rows from the top. The stats are always worked out from
     * this, so a texture read from the cache gives the same numbers as one read from its image.
     */
    record Frame(int[] argb, boolean animated) {
        TextureStats stats() { return TextureStats.of(argb, SIDE, SIDE); }
    }

    /**
     * The frame for a sprite such as {@code minecraft:block/stone}, or null when its image is missing or can't be
     * read. An animated texture's frames are averaged by how long each shows, or only the first is taken.
     */
    static Frame read(ResourceManager resources, Identifier sprite, boolean averageFrames) {
        Identifier file = Identifier.fromNamespaceAndPath(sprite.getNamespace(), "textures/" + sprite.getPath() + ".png");
        Optional<Resource> found = resources.getResource(file);
        if (found.isEmpty()) return null;
        try (InputStream in = found.get().open(); NativeImage image = NativeImage.read(in)) {
            int w = image.getWidth(), h = image.getHeight();
            Optional<AnimationMetadataSection> animation = found.get().metadata().getSection(AnimationMetadataSection.TYPE);
            if (animation.isEmpty()) return new Frame(reduce(image, List.of(new int[] {0, 0, 1}), w, h), false);

            FrameSize size = animation.get().calculateFrameSize(w, h);
            int fw = size.width(), fh = size.height();
            int columns = w / fw, count = columns * (h / fh);
            if (count < 1) return null;
            // Each frame as its left edge, top edge and time on screen.
            List<int[]> frames = new ArrayList<>();
            int frameTime = animation.get().defaultFrameTime();
            if (animation.get().frames().isPresent()) {
                for (AnimationFrame f : animation.get().frames().get()) {
                    if (f.index() < count) frames.add(new int[] {f.index() % columns * fw, f.index() / columns * fh, f.timeOr(frameTime)});
                }
            } else {
                for (int i = 0; i < count; i++) frames.add(new int[] {i % columns * fw, i / columns * fh, frameTime});
            }
            if (frames.isEmpty()) return null;
            boolean animated = frames.size() > 1;
            return new Frame(reduce(image, averageFrames ? frames : frames.subList(0, 1), fw, fh), animated);
        } catch (Exception _) {
            return null;
        }
    }

    /**
     * Averages the frames into 16 by 16 cells, in linear light and weighted by alpha, as {@link TextureStats} does.
     * A frame of any size fits: a cell covers the pixels that fall in it, and at least one.
     */
    private static int[] reduce(NativeImage image, List<int[]> frames, int fw, int fh) {
        double[][] cells = new double[SIDE * SIDE][4];
        double totalTime = 0;
        for (int[] frame : frames) totalTime += frame[2];
        for (int[] frame : frames) {
            for (int cy = 0; cy < SIDE; cy++) {
                int y0 = cy * fh / SIDE, y1 = Math.max(y0 + 1, (cy + 1) * fh / SIDE);
                for (int cx = 0; cx < SIDE; cx++) {
                    int x0 = cx * fw / SIDE, x1 = Math.max(x0 + 1, (cx + 1) * fw / SIDE);
                    double share = frame[2] / totalTime / ((x1 - x0) * (y1 - y0));
                    double[] cell = cells[cy * SIDE + cx];
                    for (int y = y0; y < y1; y++) {
                        for (int x = x0; x < x1; x++) {
                            int p = image.getPixel(frame[0] + x, frame[1] + y);
                            int alpha = p >>> 24;
                            if (alpha < TextureStats.ALPHA_CUTOFF) continue;
                            double a = alpha / 255.0 * share;
                            cell[0] += LINEAR[p >> 16 & 0xFF] * a;
                            cell[1] += LINEAR[p >> 8 & 0xFF] * a;
                            cell[2] += LINEAR[p & 0xFF] * a;
                            cell[3] += a;
                        }
                    }
                }
            }
        }
        int[] out = new int[cells.length];
        for (int i = 0; i < out.length; i++) out[i] = pack(cells[i]);
        return out;
    }

    /** ARGB for a cell of alpha-multiplied linear light and alpha. A cell with anything in it stays above the cutoff. */
    static int pack(double[] cell) {
        double a = cell[3];
        if (a <= 0) return 0;
        int alpha = Math.clamp(Math.round(a * 255), TextureStats.ALPHA_CUTOFF, 255);
        return alpha << 24 | encode(cell[0] / a) << 16 | encode(cell[1] / a) << 8 | encode(cell[2] / a);
    }

    static double linear(int channel) { return LINEAR[channel]; }

    private static int encode(double linear) {
        double v = Math.clamp(linear, 0.0, 1.0);
        return (int) Math.round((v <= 0.0031308 ? v * 12.92 : 1.055 * Math.pow(v, 1 / 2.4) - 0.055) * 255);
    }
}
