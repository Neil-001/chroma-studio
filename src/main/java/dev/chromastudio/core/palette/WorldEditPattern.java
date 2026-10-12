package dev.chromastudio.core.palette;

import java.util.ArrayList;
import java.util.List;

/** A palette as a WorldEdit pattern, and the commands that take one. */
public final class WorldEditPattern {
    /** The commands {@link #command} can build, without their slashes. */
    public static final List<String> COMMANDS = List.of("set", "replace", "walls", "outline", "overlay");

    private WorldEditPattern() {}

    /**
     * {@code stone,andesite} when every weight is 1, and otherwise {@code 70%stone,30%andesite}, with whole
     * percentages that sum to 100. No entry is given 0%, which WorldEdit would never place. Past a hundred
     * entries that can't be done, and the weights themselves stand in, which WorldEdit reads the same way.
     */
    public static String of(Palette p) {
        List<PaletteEntry> entries = p.entries;
        boolean even = entries.stream().allMatch(e -> e.weight() == 1);
        int[] shares = even ? null : entries.size() > 100 ? weights(entries) : percentages(entries);
        List<String> parts = new ArrayList<>(entries.size());
        for (int i = 0; i < entries.size(); i++) {
            String block = entries.get(i).shortIdWithProperties();
            parts.add(shares == null ? block : shares[i] + "%" + block);
        }
        return String.join(",", parts);
    }

    /**
     * {@code //set}, {@code //walls}, {@code //outline} or {@code //overlay} with the palette's pattern, or
     * {@code //replace} with the blocks to replace first. {@code which} is one of {@link #COMMANDS};
     * {@code replaceFrom} is a WorldEdit mask and only {@code replace} reads it.
     */
    public static String command(String which, Palette p, String replaceFrom) {
        if (which == null || !COMMANDS.contains(which)) throw new IllegalArgumentException("Unknown WorldEdit command: " + which);
        if (p.entries.isEmpty()) throw new IllegalArgumentException("The palette is empty");
        if (!which.equals("replace")) return "//" + which + " " + of(p);
        if (replaceFrom == null || replaceFrom.isBlank()) throw new IllegalArgumentException("Replace needs the blocks to replace");
        return "//replace " + replaceFrom.strip() + " " + of(p);
    }

    private static int[] weights(List<PaletteEntry> entries) {
        return entries.stream().mapToInt(PaletteEntry::weight).toArray();
    }

    /** Largest remainder rounding: everyone gets their share rounded down, and the points left over go to the largest fractions. */
    private static int[] percentages(List<PaletteEntry> entries) {
        int n = entries.size();
        long total = 0;
        for (PaletteEntry e : entries) total += e.weight();
        int[] out = new int[n];
        long[] remainder = new long[n];
        int left = 100;
        for (int i = 0; i < n; i++) {
            long scaled = entries.get(i).weight() * 100L;
            out[i] = (int) (scaled / total);
            remainder[i] = scaled % total;
            left -= out[i];
        }
        for (; left > 0; left--) {
            int best = 0;
            for (int i = 1; i < n; i++) if (remainder[i] > remainder[best]) best = i;
            out[best]++;
            remainder[best] = -1;
        }
        // An entry too rare for a whole point takes one from the largest, so it still turns up.
        for (int i = 0; i < n; i++) {
            if (out[i] > 0) continue;
            int largest = 0;
            for (int j = 1; j < n; j++) if (out[j] > out[largest]) largest = j;
            out[largest]--;
            out[i] = 1;
        }
        return out;
    }
}
