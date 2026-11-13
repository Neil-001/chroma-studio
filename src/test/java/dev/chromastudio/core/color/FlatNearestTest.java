package dev.chromastudio.core.color;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import org.junit.jupiter.api.Test;

class FlatNearestTest {
    private static Distribution random(Random r) {
        int n = 1 + r.nextInt(Distribution.MAX_ENTRIES);
        List<Color> colours = new ArrayList<>();
        List<Double> weights = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            colours.add(Color.ofRgb(r.nextInt(0x1000000)));
            weights.add(0.05 + r.nextDouble());
        }
        return Distribution.of(colours, weights);
    }

    @Test
    void itAnswersAsTheEarthMoversDistanceDoes() {
        Random r = new Random(7);
        List<Distribution> textures = new ArrayList<>();
        for (int i = 0; i < 200; i++) textures.add(random(r));
        FlatNearest nearest = new FlatNearest(textures);
        assertEquals(200, nearest.size());
        for (int q = 0; q < 500; q++) {
            Color c = Color.ofRgb(r.nextInt(0x1000000));
            Distribution flat = Distribution.of(c);
            int best = 0;
            for (int i = 0; i < textures.size(); i++) {
                double d = flat.emd(textures.get(i), Metric.OKLAB);
                assertEquals(d, nearest.distance(c, i), 1e-9);
                if (d < flat.emd(textures.get(best), Metric.OKLAB)) best = i;
            }
            assertEquals(best, nearest.nearest(c), c.hex());
        }
    }

    @Test
    void theFirstOfEqualsWinsAndNoTexturesGiveNone() {
        Distribution grey = Distribution.of(Color.ofRgb(0x808080));
        FlatNearest twice = new FlatNearest(List.of(Distribution.of(Color.ofRgb(0xFF0000)), grey, grey));
        assertEquals(1, twice.nearest(Color.ofRgb(0x7F7F7F)));
        assertEquals(0, twice.distance(Color.ofRgb(0x808080), 2), 1e-12);
        assertEquals(-1, new FlatNearest(List.of()).nearest(Color.ofRgb(0x123456)));
    }
}
