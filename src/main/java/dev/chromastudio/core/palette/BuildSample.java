package dev.chromastudio.core.palette;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The blocks sampled from a build, counted by where they stand, so going over the same block twice counts it once, and
 * a block that has changed since it was last sampled moves to its new kind. What kind a block counts as, a block id,
 * is the caller's to say. A kind the player removes stays out of the counts, though it's still kept track of, until
 * it's put back or the sample is cleared, so sampling on doesn't bring it back unasked.
 */
public final class BuildSample {
    /** The heaviest weight a palette entry gets, which is also the most the Palettes tab's weight field takes. */
    public static final int MAX_WEIGHT = 999;

    /** One kind of block in the sample and how many of it there are. */
    public record Row(String id, int count) {}

    /** What stands at each sampled position, packed as {@link BuildSearch#pack} does. */
    private final Map<Long, String> at = new HashMap<>();
    private final Map<String, Integer> counts = new HashMap<>();
    private final Set<String> removed = new LinkedHashSet<>();
    /** The rows as last ranked, or null once the sample has changed since. */
    private List<Row> rows;

    /**
     * Samples one position, which holds a block of kind {@code id}, or nothing that counts when {@code id} is null.
     * True when the sample's counts changed.
     */
    public boolean add(int x, int y, int z, String id) {
        long p = BuildSearch.pack(x, y, z);
        String was = id == null ? at.remove(p) : at.put(p, id);
        if (id != null && id.equals(was)) return false;
        // Whether the player sees a change, judged before a left-out kind can be forgotten below.
        boolean seen = counted(was) || counted(id);
        // A kind left out that no longer stands anywhere sampled is forgotten, so it can't stay out unseen.
        if (was != null && counts.computeIfPresent(was, (_, n) -> n == 1 ? null : n - 1) == null) removed.remove(was);
        if (id != null) counts.merge(id, 1, Integer::sum);
        rows = null;
        return seen;
    }

    private boolean counted(String id) { return id != null && !removed.contains(id); }

    /** Takes a kind out of the counts until it's {@link #putBack put back} or the sample is {@link #clear cleared}. */
    public void remove(String id) {
        if (counts.containsKey(id) && removed.add(id)) rows = null;
    }

    /** Brings back a removed kind, with all of it that was sampled meanwhile. */
    public void putBack(String id) {
        if (removed.remove(id)) rows = null;
    }

    /** Whether nothing has been sampled, counting the kinds left out. */
    public boolean isEmpty() { return at.isEmpty(); }

    public void clear() {
        at.clear();
        counts.clear();
        removed.clear();
        rows = null;
    }

    /** The kinds removed that the sample holds, in the order they were removed. */
    public List<String> removed() {
        return removed.stream().filter(counts::containsKey).toList();
    }

    /** Each kind that counts, most common first, and of two as common the one with the earlier id. */
    public List<Row> rows() {
        if (rows == null) {
            List<Row> out = new ArrayList<>();
            counts.forEach((id, n) -> {
                if (!removed.contains(id)) out.add(new Row(id, n));
            });
            out.sort(Comparator.comparingInt(Row::count).reversed().thenComparing(Row::id));
            rows = List.copyOf(out);
        }
        return rows;
    }

    /** How many blocks count, all kinds together. */
    public int total() {
        int n = 0;
        for (Row r : rows()) n += r.count();
        return n;
    }

    /** Whether a position was sampled and its kind counts. */
    public boolean counts(int x, int y, int z) {
        return counted(at.get(BuildSearch.pack(x, y, z)));
    }

    /** Something done at a position. */
    @FunctionalInterface
    public interface Visitor {
        void at(int x, int y, int z);
    }

    /** Visits each position whose kind counts. */
    public void forEach(Visitor visitor) {
        at.forEach((p, id) -> {
            if (counted(id)) visitor.at(BuildSearch.x(p), BuildSearch.y(p), BuildSearch.z(p));
        });
    }

    /**
     * The sample as palette entries, most common first, each weighted by its count. When the commonest has more than
     * {@link #MAX_WEIGHT}, every weight is scaled down to fit, and a kind too rare to reach 1 still gets 1, so the few
     * blocks of an accent are never rounded away.
     */
    public List<PaletteEntry> entries() {
        List<Row> ranked = rows();
        if (ranked.isEmpty()) return List.of();
        double scale = Math.min(1, MAX_WEIGHT / (double) ranked.getFirst().count());
        List<PaletteEntry> out = new ArrayList<>();
        for (Row r : ranked) out.add(new PaletteEntry(r.id(), "", (int) Math.max(1, Math.round(r.count() * scale))));
        return out;
    }
}
