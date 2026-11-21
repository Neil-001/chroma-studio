package dev.chromastudio.core.color;

import java.util.List;

public final class FlatNearest {
    private final int size;
    private final double[] colours;
    private final int[] starts;

    public FlatNearest(List<Distribution> textures) {
        size = textures.size();
        starts = new int[size + 1];
        int total = 0;
        for (int i = 0; i < size; i++) {
            starts[i] = total;
            total += textures.get(i).colours().size() * 4;
        }
        starts[size] = total;
        colours = new double[total];
        for (int i = 0; i < size; i++) {
            Distribution d = textures.get(i);
            List<Color> cs = d.colours();
            List<Double> ws = d.weights();
            for (int k = 0; k < cs.size(); k++) {
                Oklab lab = cs.get(k).oklab();
                int at = starts[i] + k * 4;
                colours[at] = lab.l();
                colours[at + 1] = lab.a();
                colours[at + 2] = lab.b();
                colours[at + 3] = ws.get(k);
            }
        }
    }

    public int size() { return size; }

    public int nearest(Color c) {
        Oklab lab = c.oklab();
        double least = Double.POSITIVE_INFINITY;
        int best = -1;
        for (int i = 0; i < size; i++) {
            double sum = distance(lab, i, least);
            if (sum < least) {
                least = sum;
                best = i;
            }
        }
        return best;
    }

    public double distance(Color c, int texture) { return distance(c.oklab(), texture, Double.POSITIVE_INFINITY); }

    private double distance(Oklab lab, int texture, double limit) {
        double l = lab.l(), a = lab.a(), b = lab.b(), sum = 0;
        for (int at = starts[texture], end = starts[texture + 1]; at < end && sum < limit; at += 4) {
            double dl = colours[at] - l, da = colours[at + 1] - a, db = colours[at + 2] - b;
            sum += colours[at + 3] * Math.sqrt(dl * dl + da * da + db * db);
        }
        return sum;
    }
}
