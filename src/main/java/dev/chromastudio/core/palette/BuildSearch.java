package dev.chromastudio.core.palette;

import java.util.Arrays;

/**
 * Finds a build: the blocks joined to a first block through others like it, where blocks touching at a face, an edge
 * or a corner count as joined, so a staircase of blocks or a thin diagonal spire holds together. What counts as part
 * of the build is the caller's to say. The search stops at a cap, so a build that runs on into the landscape can't
 * hold up the game.
 */
public final class BuildSearch {
    /** Whether the block at a position is part of the build. */
    @FunctionalInterface
    public interface Member {
        boolean test(int x, int y, int z);
    }

    /**
     * What was found: each block as {@link #pack packed}, nearest the first block first, and whether the cap stopped
     * the search before it ran out of blocks.
     */
    public record Found(long[] blocks, boolean capped) {}

    private BuildSearch() {}

    /** The build joined to the first block, at most {@code cap} blocks of it. Nothing when the first block isn't a member. */
    public static Found from(int x, int y, int z, Member member, int cap) {
        if (cap <= 0 || !member.test(x, y, z)) return new Found(new long[0], false);
        // The blocks found, in the order they were found, are also the queue of those whose neighbours are still to
        // look at, so the search is breadth first and nearest the first block first.
        long[] out = new long[Math.min(cap, 1024)];
        out[0] = pack(x, y, z);
        int size = 1;
        Seen seen = new Seen();
        seen.add(x, y, z);
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

    /**
     * The positions already looked at, one bit each, in sections of 16 by 16 by 16 kept in an open-addressed table.
     * A search looks at each found block's 26 neighbours, which nearly always share its section, so this is several
     * times quicker than a set of boxed positions, which spent most of the search's time making and hashing them.
     */
    private static final class Seen {
        private long[] keys = new long[256];
        private long[][] sections = new long[256][];
        private int size;
        // The section of the last position, which the next one is most likely in.
        private long lastKey;
        private long[] last;

        /** Marks a position as looked at, and says whether it is new. */
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

    // A position in one long, as the game packs its own: 26 bits each for x and z and 12 for y.

    public static long pack(int x, int y, int z) {
        return ((long) x & 0x3FFFFFF) << 38 | ((long) z & 0x3FFFFFF) << 12 | (long) y & 0xFFF;
    }

    public static int x(long p) { return (int) (p >> 38); }

    public static int y(long p) { return (int) (p << 52 >> 52); }

    public static int z(long p) { return (int) (p << 26 >> 38); }
}
