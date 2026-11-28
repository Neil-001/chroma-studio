package dev.chromastudio.client.index;

import com.mojang.blaze3d.platform.NativeImage;
import dev.chromastudio.client.Abgr;
import dev.chromastudio.core.color.TextureStats;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import net.minecraft.client.resources.metadata.animation.AnimationMetadataSection;
import net.minecraft.client.resources.metadata.animation.FrameSize;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;

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

    private static final int SUB = 8;

    record Frame(int[] rgb, float[] alpha, boolean animated) {
        static Frame of(double[][] cells, boolean animated) {
            int[] rgb = new int[cells.length];
            float[] alpha = new float[cells.length];
            for (int i = 0; i < cells.length; i++) {
                double a = cells[i][3];
                if (a <= 0) continue;
                rgb[i] = encode(cells[i][0] / a) << 16 | encode(cells[i][1] / a) << 8 | encode(cells[i][2] / a);
                alpha[i] = (float) Math.min(1, a);
            }
            return new Frame(rgb, alpha, animated);
        }

        TextureStats stats() {
            boolean plain = true;
            for (float a : alpha) plain &= a == 0 || a == 1;
            int side = plain ? SIDE : SIDE * SUB, per = plain ? 1 : SUB;
            int[] argb = new int[side * side];
            for (int i = 0; i < alpha.length; i++) {
                if (alpha[i] <= 0) continue;
                double wanted = alpha[i] * per * per;
                int whole = (int) Math.floor(wanted + 1e-6);
                int rest = (int) Math.round((wanted - whole) * 255);
                if (whole == 0) rest = Math.max(rest, TextureStats.ALPHA_CUTOFF);
                int x0 = i % SIDE * per, y0 = i / SIDE * per;
                for (int n = 0; n < whole; n++) argb[(y0 + n / per) * side + x0 + n % per] = 0xFF000000 | rgb[i];
                if (rest >= TextureStats.ALPHA_CUTOFF && whole < per * per) argb[(y0 + whole / per) * side + x0 + whole % per] = rest << 24 | rgb[i];
            }
            return TextureStats.of(argb, side, side);
        }
    }

    static Frame read(ResourceManager resources, ResourceLocation sprite, boolean averageFrames) {
        ResourceLocation file = ResourceLocation.fromNamespaceAndPath(sprite.getNamespace(), "textures/" + sprite.getPath() + ".png");
        Optional<Resource> found = resources.getResource(file);
        if (found.isEmpty()) return null;
        try (InputStream in = found.get().open(); NativeImage image = NativeImage.read(in)) {
            int w = image.getWidth(), h = image.getHeight();
            Optional<AnimationMetadataSection> animation = found.get().metadata().getSection(AnimationMetadataSection.SERIALIZER);
            if (animation.isEmpty()) return Frame.of(reduce(image, List.of(new int[] {0, 0, 1}), w, h), false);

            FrameSize size = animation.get().calculateFrameSize(w, h);
            int fw = size.width(), fh = size.height();
            int columns = w / fw, count = columns * (h / fh);
            if (count < 1) return null;
            List<int[]> frames = new ArrayList<>();
            int frameTime = animation.get().getDefaultFrameTime();
            animation.get().forEachFrame((index, time) -> {
                if (index < count) frames.add(new int[] {index % columns * fw, index / columns * fh, time});
            });
            if (frames.isEmpty()) for (int i = 0; i < count; i++) frames.add(new int[] {i % columns * fw, i / columns * fh, frameTime});
            boolean animated = frames.size() > 1;
            return Frame.of(reduce(image, averageFrames ? frames : frames.subList(0, 1), fw, fh), animated);
        } catch (Exception ignored) {
            return null;
        }
    }

    private static double[][] reduce(NativeImage image, List<int[]> frames, int fw, int fh) {
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
                            int p = Abgr.get(image, frame[0] + x, frame[1] + y);
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
        return cells;
    }

    static double linear(int channel) { return LINEAR[channel]; }

    private static int encode(double linear) {
        double v = Math.clamp(linear, 0.0, 1.0);
        return (int) Math.round((v <= 0.0031308 ? v * 12.92 : 1.055 * Math.pow(v, 1 / 2.4) - 0.055) * 255);
    }
}
