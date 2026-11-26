package dev.chromastudio.client.action;

import static org.junit.jupiter.api.Assertions.assertEquals;

import dev.chromastudio.core.color.Color;
import java.awt.image.BufferedImage;
import java.util.Set;
import org.junit.jupiter.api.Test;

class ImageImportTest {
    @Test
    void thumbnailKeepsTheShapeAndFitsTheBox() {
        BufferedImage image = new BufferedImage(600, 200, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < 200; y++) for (int x = 0; x < 600; x++) image.setRGB(x, y, x < 300 ? 0xFFFF0000 : 0xFF0000FF);
        ImageImport.Sample s = ImageImport.sample(image, 64, 48);
        assertEquals(600, s.width());
        assertEquals(200, s.height());
        assertEquals(64, s.thumbW());
        assertEquals(21, s.thumbH());
        assertEquals(0xFFFF0000, s.thumb()[0]);
        assertEquals(0xFF0000FF, s.thumb()[63]);
        assertEquals(Set.of(Color.ofRgb(0xFF0000), Color.ofRgb(0x0000FF)), Set.copyOf(ImageImport.colours(s, 2)));
    }

    @Test
    void smallImagesAreNotEnlarged() {
        ImageImport.Sample s = ImageImport.sample(new BufferedImage(10, 30, BufferedImage.TYPE_INT_ARGB), 64, 48);
        assertEquals(10, s.thumbW());
        assertEquals(30, s.thumbH());
    }
}
