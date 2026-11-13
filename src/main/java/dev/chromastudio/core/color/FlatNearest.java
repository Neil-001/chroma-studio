package dev.chromastudio.core.color;

import java.util.List;

/**
 * Finds which of a fixed list of textures comes closest to one flat colour, many times over, as fast as it can. It
 * answers as {@link Distribution#emd} in OKLab would: against a single colour that is the weighted mean distance to
 * the texture's colours, so the texture's colours are kept as plain numbers and nothing is made per question.
 *
 * <p>It never changes after it is made, so any number of threads may ask it at once.
 */
public final class FlatNearest {
    private final int size;
    /** Each texture's colours, back to back: its OKLab L, a and b and its share, four numbers a colour. */
    private final double[] colours;
    /** Where each texture's colours start in {@link #colours}, and one past the last texture's end. */
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

    /** The index of the texture closest to a colour, the first among equals, or -1 when there are none. */
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

    /** The distance from a colour to one texture, as {@link Distribution#emd} in OKLab gives it. */
    public double distance(Color c, int texture) { return distance(c.oklab(), texture, Double.POSITIVE_INFINITY); }

    /** The distance to one texture, given up as soon as it reaches {@code limit}, since it can then only lose. */
    private double distance(Oklab lab, int texture, double limit) {
        double l = lab.l(), a = lab.a(), b = lab.b(), sum = 0;
        for (int at = starts[texture], end = starts[texture + 1]; at < end && sum < limit; at += 4) {
            double dl = colours[at] - l, da = colours[at + 1] - a, db = colours[at + 2] - b;
            sum += colours[at + 3] * Math.sqrt(dl * dl + da * da + db * db);
        }
        return sum;
    }
}
