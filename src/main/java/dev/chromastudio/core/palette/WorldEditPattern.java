package dev.chromastudio.core.palette;

import java.util.ArrayList;
import java.util.List;

public final class WorldEditPattern {
    public static final List<String> COMMANDS = List.of("set", "replace", "walls", "outline", "overlay");

    private WorldEditPattern() {}

    public enum Kind { COMMAND, FROM, SHARE, BLOCK, SEPARATOR }

    public record Piece(Kind kind, String text) {}

    public static String of(Palette p) {
        StringBuilder out = new StringBuilder();
        for (Piece piece : pattern(p)) out.append(piece.text());
        return out.toString();
    }

    public static String command(String which, Palette p, String replaceFrom) {
        StringBuilder out = new StringBuilder();
        for (Piece piece : pieces(which, p, replaceFrom)) out.append(piece.text());
        return out.toString();
    }

    public static List<Piece> pieces(String which, Palette p, String replaceFrom) {
        if (which == null || !COMMANDS.contains(which)) throw new IllegalArgumentException("Unknown WorldEdit command: " + which);
        if (p.entries.isEmpty()) throw new IllegalArgumentException("The palette is empty");
        List<Piece> out = new ArrayList<>();
        out.add(new Piece(Kind.COMMAND, "//" + which));
        out.add(new Piece(Kind.SEPARATOR, " "));
        if (which.equals("replace")) {
            if (replaceFrom == null || replaceFrom.isBlank()) throw new IllegalArgumentException("Replace needs the blocks to replace");
            String[] blocks = replaceFrom.strip().split(",", -1);
            for (int i = 0; i < blocks.length; i++) {
                if (i > 0) out.add(new Piece(Kind.SEPARATOR, ","));
                if (!blocks[i].isEmpty()) out.add(new Piece(Kind.FROM, blocks[i]));
            }
            out.add(new Piece(Kind.SEPARATOR, " "));
        }
        out.addAll(pattern(p));
        return out;
    }

    private static List<Piece> pattern(Palette p) {
        List<PaletteEntry> entries = p.entries;
        boolean even = entries.stream().allMatch(e -> e.weight() == 1);
        int[] shares = even ? null : entries.size() > 100 ? weights(entries) : percentages(entries);
        List<Piece> out = new ArrayList<>();
        for (int i = 0; i < entries.size(); i++) {
            if (i > 0) out.add(new Piece(Kind.SEPARATOR, ","));
            if (shares != null) out.add(new Piece(Kind.SHARE, shares[i] + "%"));
            out.add(new Piece(Kind.BLOCK, entries.get(i).shortIdWithProperties()));
        }
        return out;
    }

    private static int[] weights(List<PaletteEntry> entries) {
        return entries.stream().mapToInt(PaletteEntry::weight).toArray();
    }

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
