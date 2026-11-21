package dev.chromastudio.client.action;

import dev.chromastudio.core.color.Color;
import dev.chromastudio.core.color.Kmeans;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.function.Consumer;
import javax.imageio.ImageIO;

public final class ImageImport {
    private ImageImport() {}

    private static final int ALPHA_CUTOFF = 128;
    private static final int SAMPLE_SIDE = 256;

    public static void choose(Consumer<Path> picked, Runnable failed) { FileDialog.choose("Images", "png;jpg;jpeg;gif;bmp", picked, failed); }

    public static List<Color> colours(Path file, int count) throws IOException {
        if (!Files.isRegularFile(file)) throw new IOException("There's no file at " + file);
        BufferedImage image = ImageIO.read(file.toFile());
        if (image == null) throw new IOException(file.getFileName() + " isn't an image this game can read");
        return colours(image, count);
    }

    static List<Color> colours(BufferedImage image, int count) {
        int w = image.getWidth(), h = image.getHeight();
        int step = Math.max(1, Math.max(w, h) / SAMPLE_SIDE);
        int sw = (w + step - 1) / step, sh = (h + step - 1) / step;
        int[] argb = new int[sw * sh];
        for (int y = 0; y < sh; y++) for (int x = 0; x < sw; x++) argb[y * sw + x] = image.getRGB(x * step, y * step);
        return Kmeans.cluster(argb, count, ALPHA_CUTOFF);
    }
}
