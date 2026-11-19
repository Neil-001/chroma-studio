package dev.chromastudio.core.palette;

import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

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
        long[] out = new long[Math.min(cap, 1024)];
        int size = 0;
        Set<Long> seen = new HashSet<>();
        ArrayDeque<Long> queue = new ArrayDeque<>();
        long first = pack(x, y, z);
        seen.add(first);
        queue.add(first);
        while (!queue.isEmpty()) {
            long p = queue.poll();
            if (size == cap) return new Found(out, true);
            if (size == out.length) out = Arrays.copyOf(out, Math.min(cap, out.length * 2));
            out[size++] = p;
            int px = x(p), py = y(p), pz = z(p);
            for (int ox = -1; ox <= 1; ox++) {
                for (int oy = -1; oy <= 1; oy++) {
                    for (int oz = -1; oz <= 1; oz++) {
                        int nx = px + ox, ny = py + oy, nz = pz + oz;
                        long n = pack(nx, ny, nz);
                        if (seen.add(n) && member.test(nx, ny, nz)) queue.add(n);
                    }
                }
            }
        }
        return new Found(Arrays.copyOf(out, size), false);
    }

    // A position in one long, as the game packs its own: 26 bits each for x and z and 12 for y.

    public static long pack(int x, int y, int z) {
        return ((long) x & 0x3FFFFFF) << 38 | ((long) z & 0x3FFFFFF) << 12 | (long) y & 0xFFF;
    }

    public static int x(long p) { return (int) (p >> 38); }

    public static int y(long p) { return (int) (p << 52 >> 52); }

    public static int z(long p) { return (int) (p << 26 >> 38); }
}
