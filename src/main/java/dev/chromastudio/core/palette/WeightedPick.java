package dev.chromastudio.core.palette;

import java.util.List;
import java.util.function.ToIntFunction;

public final class WeightedPick<T> {
    private final List<T> items;
    private final long[] upTo;

    public WeightedPick(List<T> items, ToIntFunction<T> weight) {
        if (items.isEmpty()) throw new IllegalArgumentException("nothing to pick from");
        this.items = List.copyOf(items);
        upTo = new long[this.items.size()];
        long total = 0;
        for (int i = 0; i < upTo.length; i++) {
            total += Math.max(1, weight.applyAsInt(this.items.get(i)));
            upTo[i] = total;
        }
    }

    public long total() { return upTo[upTo.length - 1]; }

    public T at(double unit) {
        long target = Math.min(total() - 1, (long) Math.floor(unit * total()));
        int lo = 0, hi = upTo.length - 1;
        while (lo < hi) {
            int mid = (lo + hi) >>> 1;
            if (upTo[mid] > target) hi = mid;
            else lo = mid + 1;
        }
        return items.get(lo);
    }

    public T at(long seed, int x, int y, int z) { return at(unit(seed, x, y, z)); }

    public static double unit(long seed, int x, int y, int z) {
        long h = seed;
        h = mix(h ^ x);
        h = mix(h ^ ((long) y << 21));
        h = mix(h ^ ((long) z << 42));
        return (h >>> 11) * 0x1.0p-53;
    }

    private static long mix(long z) {
        z += 0x9E3779B97F4A7C15L;
        z = (z ^ (z >>> 30)) * 0xBF58476D1CE4E5B9L;
        z = (z ^ (z >>> 27)) * 0x94D049BB133111EBL;
        return z ^ (z >>> 31);
    }
}
