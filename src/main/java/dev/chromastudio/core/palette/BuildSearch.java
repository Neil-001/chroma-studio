package dev.chromastudio.core.palette;

import java.util.Arrays;

public final class BuildSearch {
    @FunctionalInterface
    public interface Member {
        boolean test(int x, int y, int z);
    }

    public record Found(long[] blocks, boolean capped) {}

    private BuildSearch() {}

    public static Found from(int x, int y, int z, Member member, int cap) { return from(new long[] {pack(x, y, z)}, member, cap); }

    public static Found from(long[] starts, Member member, int cap) {
        if (cap <= 0) return new Found(new long[0], false);
        long[] out = new long[Math.min(cap, 1024)];
        int size = 0;
        Seen seen = new Seen();
        for (long s : starts) {
            int sx = x(s), sy = y(s), sz = z(s);
            if (!seen.add(sx, sy, sz) || !member.test(sx, sy, sz)) continue;
            if (size == cap) return new Found(out, true);
            if (size == out.length) out = Arrays.copyOf(out, Math.min(cap, out.length * 2));
            out[size++] = s;
        }
        for (int next = 0; next < size; next++) {
            long p = out[next];
            int px = x(p), py = y(p), pz = z(p);
            for (int ox = -1; ox <= 1; ox++) {
                for (int oy = -1; oy <= 1; oy++) {
                    for (int oz = -1; oz <= 1; oz++) {
                        int nx = px + ox, ny = py + oy, nz = pz + oz;
                        if (!seen.add(nx, ny, nz) || !member.test(nx, ny, nz)) continue;
                        if (size == cap) return new Found(out, true);
                        if (size == out.length) out = Arrays.copyOf(out, Math.min(cap, out.length * 2));
                        out[size++] = pack(nx, ny, nz);
                    }
                }
            }
        }
        return new Found(Arrays.copyOf(out, size), false);
    }

    private static final class Seen {
        private long[] keys = new long[256];
        private long[][] sections = new long[256][];
        private int size;
        private long lastKey;
        private long[] last;

        boolean add(int x, int y, int z) {
            long key = pack(x >> 4, y >> 4, z >> 4);
            if (last == null || key != lastKey) {
                last = section(key);
                lastKey = key;
            }
            int i = (x & 15) << 8 | (y & 15) << 4 | z & 15;
            long bit = 1L << i;
            if ((last[i >> 6] & bit) != 0) return false;
            last[i >> 6] |= bit;
            return true;
        }

        private long[] section(long key) {
            if (size * 2 >= keys.length) grow();
            int mask = keys.length - 1;
            for (int i = slot(key, mask); ; i = (i + 1) & mask) {
                if (sections[i] == null) {
                    keys[i] = key;
                    size++;
                    return sections[i] = new long[64];
                }
                if (keys[i] == key) return sections[i];
            }
        }

        private void grow() {
            long[] oldKeys = keys;
            long[][] oldSections = sections;
            keys = new long[oldKeys.length * 2];
            sections = new long[oldKeys.length * 2][];
            int mask = keys.length - 1;
            for (int j = 0; j < oldKeys.length; j++) {
                if (oldSections[j] == null) continue;
                int i = slot(oldKeys[j], mask);
                while (sections[i] != null) i = (i + 1) & mask;
                keys[i] = oldKeys[j];
                sections[i] = oldSections[j];
            }
        }

        private static int slot(long key, int mask) { return (int) ((key * 0x9E3779B97F4A7C15L) >>> 40) & mask; }
    }

    public static long pack(int x, int y, int z) {
        return ((long) x & 0x3FFFFFF) << 38 | ((long) z & 0x3FFFFFF) << 12 | (long) y & 0xFFF;
    }

    public static int x(long p) { return (int) (p >> 38); }

    public static int y(long p) { return (int) (p << 52 >> 52); }

    public static int z(long p) { return (int) (p << 26 >> 38); }
}
