package dev.chromastudio.core.palette;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class BuildSample {
    public static final int MAX_WEIGHT = 999;

    public record Row(String id, int count) {}

    private final Map<Long, String> at = new HashMap<>();
    private final Map<String, Integer> counts = new HashMap<>();
    private final Set<String> removed = new LinkedHashSet<>();
    private List<Row> rows;

    public boolean add(int x, int y, int z, String id) {
        long p = BuildSearch.pack(x, y, z);
        String was = id == null ? at.remove(p) : at.put(p, id);
        if (id != null && id.equals(was)) return false;
        boolean seen = counted(was) || counted(id);
        if (was != null && counts.computeIfPresent(was, (ignored, n) -> n == 1 ? null : n - 1) == null) removed.remove(was);
        if (id != null) counts.merge(id, 1, Integer::sum);
        rows = null;
        return seen;
    }

    private boolean counted(String id) { return id != null && !removed.contains(id); }

    public void remove(String id) {
        if (counts.containsKey(id) && removed.add(id)) rows = null;
    }

    public void putBack(String id) {
        if (removed.remove(id)) rows = null;
    }

    public int count(String id) { return counts.getOrDefault(id, 0); }

    public boolean isEmpty() { return at.isEmpty(); }

    public void clear() {
        at.clear();
        counts.clear();
        removed.clear();
        rows = null;
    }

    public List<String> removed() {
        return removed.stream().filter(counts::containsKey).toList();
    }

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

    public int total() {
        int n = 0;
        for (Row r : rows()) n += r.count();
        return n;
    }

    public String kindAt(int x, int y, int z) { return at.get(BuildSearch.pack(x, y, z)); }

    public boolean leftOut(int x, int y, int z) {
        String id = at.get(BuildSearch.pack(x, y, z));
        return id != null && removed.contains(id);
    }

    public boolean counts(int x, int y, int z) {
        return counted(at.get(BuildSearch.pack(x, y, z)));
    }

    @FunctionalInterface
    public interface Visitor {
        void at(int x, int y, int z);
    }

    public void forEach(Visitor visitor) {
        at.forEach((p, id) -> {
            if (counted(id)) visitor.at(BuildSearch.x(p), BuildSearch.y(p), BuildSearch.z(p));
        });
    }

    public void forEachLeftOut(Visitor visitor) {
        if (removed.isEmpty()) return;
        at.forEach((p, id) -> {
            if (removed.contains(id)) visitor.at(BuildSearch.x(p), BuildSearch.y(p), BuildSearch.z(p));
        });
    }

    public void forEachOf(String id, Visitor visitor) {
        at.forEach((p, kind) -> {
            if (kind.equals(id)) visitor.at(BuildSearch.x(p), BuildSearch.y(p), BuildSearch.z(p));
        });
    }

    public List<PaletteEntry> entries() {
        List<Row> ranked = rows();
        if (ranked.isEmpty()) return List.of();
        double scale = Math.min(1, MAX_WEIGHT / (double) ranked.getFirst().count());
        List<PaletteEntry> out = new ArrayList<>();
        for (Row r : ranked) out.add(new PaletteEntry(r.id(), "", (int) Math.max(1, Math.round(r.count() * scale))));
        return out;
    }
}
