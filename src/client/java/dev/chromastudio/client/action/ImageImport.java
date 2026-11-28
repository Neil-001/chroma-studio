package dev.chromastudio.client.action;

import dev.chromastudio.core.color.Color;
import dev.chromastudio.core.color.Kmeans;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.util.List;
import java.util.function.Consumer;
import javax.imageio.ImageIO;

public final class ImageImport {
    private ImageImport() {}

    private static final int ALPHA_CUTOFF = 128;
    private static final int SAMPLE_SIDE = 256;

    public record Sample(int width, int height, int[] argb, int thumbW, int thumbH, int[] thumb) {}

    public static void choose(Consumer<Path> picked, Runnable failed) { FileDialog.choose("Images", "png;jpg;jpeg;gif;bmp", picked, failed); }

    public static Sample read(Path file, int thumbW, int thumbH) throws IOException {
        if (!Files.isRegularFile(file)) throw new NoSuchFileException(file.toString());
        BufferedImage image = ImageIO.read(file.toFile());
        if (image == null) throw new IOException(file.getFileName() + " isn't an image this game can read");
        return sample(image, thumbW, thumbH);
    }

    static Sample sample(BufferedImage image, int maxW, int maxH) {
        int w = image.getWidth(), h = image.getHeight();
        int step = Math.max(1, Math.max(w, h) / SAMPLE_SIDE);
        int sw = (w + step - 1) / step, sh = (h + step - 1) / step;
        int[] argb = new int[sw * sh];
        for (int y = 0; y < sh; y++) for (int x = 0; x < sw; x++) argb[y * sw + x] = image.getRGB(x * step, y * step);
        double fit = Math.min(1, Math.min((double) maxW / w, (double) maxH / h));
        int tw = Math.max(1, (int) Math.round(w * fit)), th = Math.max(1, (int) Math.round(h * fit));
        int[] thumb = new int[tw * th];
        for (int y = 0; y < th; y++) for (int x = 0; x < tw; x++) thumb[y * tw + x] = image.getRGB((int) ((x + 0.5) * w / tw), (int) ((y + 0.5) * h / th));
        return new Sample(w, h, argb, tw, th, thumb);
    }

    public static List<Color> colours(Sample sample, int count) { return Kmeans.cluster(sample.argb(), count, ALPHA_CUTOFF); }
}
