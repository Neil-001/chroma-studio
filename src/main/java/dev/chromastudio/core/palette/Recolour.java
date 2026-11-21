package dev.chromastudio.core.palette;

import dev.chromastudio.core.color.Color;
import dev.chromastudio.core.color.Metric;
import dev.chromastudio.core.color.Oklab;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.IntPredicate;

public final class Recolour {
    public enum Mode {
        SHADING,
        CLOSEST
    }

    public record Look(Color colour, boolean seeThrough, int weight, String family, String block) {
        public Look(Color colour, boolean seeThrough, int weight, String family) { this(colour, seeThrough, weight, family, null); }

        public Look(Color colour, boolean seeThrough, int weight) { this(colour, seeThrough, weight, null, null); }

        public Look(Color colour, boolean seeThrough) { this(colour, seeThrough, 1); }
    }

    private static final double SHARE = 0.02;

    private final Mode mode;
    private final Oklab[] palette;
    private final boolean[] paletteClear;
    private final int[] weights;
    private final String[] blocks;
    private final int[] byLightness;
    private final Shades buildSolid, buildClear;
    private final Map<List<Object>, int[]> tables = new ConcurrentHashMap<>();

    public Recolour(List<Look> palette, List<Look> build, Mode mode) {
        this.mode = mode;
        int n = palette.size();
        this.palette = new Oklab[n];
        this.paletteClear = new boolean[n];
        this.weights = new int[n];
        this.blocks = new String[n];
        for (int i = 0; i < n; i++) {
            this.palette[i] = palette.get(i).colour().oklab();
            this.paletteClear[i] = palette.get(i).seeThrough();
            this.weights[i] = Math.max(1, palette.get(i).weight());
            this.blocks[i] = palette.get(i).block();
        }
        List<Integer> order = new ArrayList<>();
        for (int i = 0; i < n; i++) order.add(i);
        order.sort(Comparator.comparingDouble(i -> this.palette[i].l()));
        this.byLightness = order.stream().mapToInt(Integer::intValue).toArray();
        this.buildSolid = new Shades(build.stream().filter(l -> !l.seeThrough()).toList());
        this.buildClear = new Shades(build.stream().filter(Look::seeThrough).toList());
    }

    public int pick(Look block, IntPredicate allowed) {
        boolean clear = block.seeThrough() || !any(allowed, false);
        if (!any(allowed, clear)) return -1;
        Oklab c = block.colour().oklab();
        if (mode == Mode.CLOSEST) {
            int best = -1;
            double bestDistance = Double.POSITIVE_INFINITY;
            for (int i = 0; i < palette.length; i++) {
                if (paletteClear[i] != clear || !allowed.test(i)) continue;
                double d = c.distance(palette[i]);
                if (d < bestDistance) {
                    best = i;
                    bestDistance = d;
                }
            }
            return best;
        }
        int own = own(block.block(), clear, allowed);
        if (own >= 0) return own;
        int[] eligible = Arrays.stream(byLightness).filter(i -> paletteClear[i] == clear && allowed.test(i)).toArray();
        Shades build = block.seeThrough() ? buildClear : buildSolid;
        int[] table = tables.computeIfAbsent(List.of(block.seeThrough(), Arrays.toString(eligible)), _ -> table(eligible, build));
        return table[build.of(c, block.family())];
    }

    private int own(String block, boolean clear, IntPredicate allowed) {
        if (block == null) return -1;
        for (int i = 0; i < palette.length; i++) if (block.equals(blocks[i]) && paletteClear[i] == clear && allowed.test(i)) return i;
        return -1;
    }

    private int[] table(int[] eligible, Shades build) {
        int n = eligible.length, shades = build.count();
        int main = build.main();
        int[] pin = new int[shades];
        Arrays.fill(pin, -1);
        for (int k = 0, last = -1; k < shades; k++) {
            for (String id : build.blocks(k)) {
                for (int j = last + 1; j < n; j++) {
                    if (!id.equals(blocks[eligible[j]])) continue;
                    pin[k] = j;
                    break;
                }
                if (pin[k] >= 0) break;
            }
            if (pin[k] >= 0) last = pin[k];
        }
        int[] lo = new int[shades], hi = new int[shades];
        Arrays.fill(lo, -1);
        Arrays.fill(hi, -1);
        for (int k = 0; k < shades; k++) {
            for (String id : build.blocks(k)) {
                for (int j = 0; j < n; j++) {
                    if (!id.equals(blocks[eligible[j]])) continue;
                    lo[k] = lo[k] < 0 ? j : Math.min(lo[k], j);
                    hi[k] = Math.max(hi[k], j);
                }
            }
        }
        if (main < 0 || pin[main] < 0) return kept(eligible, byPlace(eligible, build, main, lo, hi), lo, hi);
        Oklab m = palette[eligible[pin[main]]];
        Oklab c = build.colour(main);
        Oklab shift = new Oklab(m.l() - c.l(), m.a() - c.a(), m.b() - c.b());

        double[][] best = new double[shades][n];
        int[][] back = new int[shades][n];
        for (int k = 0; k < shades; k++) {
            Oklab colour = build.colour(k);
            Oklab target = new Oklab(colour.l() + shift.l(), colour.a() + shift.a(), colour.b() + shift.b());
            double share = k == 0 ? 0 : Math.min(build.amount(k), build.amount(k - 1)) * (colour.distance(build.colour(k - 1)) + SHARE);
            double amount = Math.max(build.amount(k), Double.MIN_NORMAL);
            int least = -1;
            for (int j = 0; j < n; j++) {
                if (k > 0 && j > 0 && best[k - 1][j - 1] < Double.POSITIVE_INFINITY && (least < 0 || best[k - 1][j - 1] < best[k - 1][least])) least = j - 1;
                best[k][j] = Double.POSITIVE_INFINITY;
                if (pin[k] >= 0 && j != pin[k]) continue;
                double fit = amount * target.distance(palette[eligible[j]]);
                if (k == 0) {
                    best[k][j] = fit;
                    continue;
                }
                if (least >= 0) {
                    best[k][j] = best[k - 1][least] + fit;
                    back[k][j] = least;
                }
                if (best[k - 1][j] + share + fit < best[k][j]) {
                    best[k][j] = best[k - 1][j] + share + fit;
                    back[k][j] = j;
                }
            }
        }
        int last = 0;
        for (int j = 1; j < n; j++) if (best[shades - 1][j] < best[shades - 1][last]) last = j;
        int[] at = new int[shades];
        for (int k = shades - 1, j = last; k >= 0; k--) {
            at[k] = j;
            j = back[k][j];
        }
        return kept(eligible, at, lo, hi);
    }

    private static int[] kept(int[] eligible, int[] at, int[] lo, int[] hi) {
        int shades = lo.length;
        for (int k = 0; k < shades; k++) {
            if (lo[k] < 0) continue;
            if (at[k] < lo[k] || at[k] > hi[k]) at[k] = lo[k];
            for (int i = 0; i < k; i++) at[i] = Math.min(at[i], lo[k]);
            for (int i = k + 1; i < shades; i++) at[i] = Math.max(at[i], hi[k]);
        }
        int[] out = new int[at.length];
        for (int k = 0; k < at.length; k++) out[k] = eligible[at[k]];
        return out;
    }

    private static int[] room(int main, int n, int[] lo, int[] hi) {
        int from = -1, to = n;
        for (int k = 0; k < lo.length; k++) {
            if (lo[k] < 0) continue;
            if (k < main) from = Math.max(from, hi[k]);
            if (k > main) to = Math.min(to, lo[k]);
        }
        return from + 1 < to ? new int[] {from, to} : new int[] {-1, n};
    }

    private int[] byPlace(int[] eligible, Shades build, int main, int[] lo, int[] hi) {
        int n = eligible.length, shades = build.count();
        int[] at = new int[shades];
        if (shades == 0) return new int[] {(n - 1) / 2};
        if (shades <= n) {
            int[] picked = matched(eligible, build, main, lo, hi);
            for (int k = 0; k < shades; k++) for (int j = 0; j < n; j++) if (eligible[j] == picked[k]) at[k] = j;
        } else if (main < 0) {
            shareOut(eligible, 0, shades, 0, n, at);
        } else {
            double place = (main + 0.5) / shades, total = 0;
            for (int i : eligible) total += weights[i];
            int[] room = room(main, n, lo, hi);
            int heaviest = room[0] + 1;
            double upTo = 0, nearest = Double.POSITIVE_INFINITY;
            for (int j = 0; j < n; j++) {
                double middle = (upTo + weights[eligible[j]] / 2.0) / total, off = Math.abs(middle - place);
                upTo += weights[eligible[j]];
                if (j <= room[0] || j >= room[1]) continue;
                if (weights[eligible[j]] > weights[eligible[heaviest]] || weights[eligible[j]] == weights[eligible[heaviest]] && off < nearest) {
                    heaviest = j;
                    nearest = off;
                }
            }
            around(eligible, main, heaviest, at);
        }
        return at;
    }

    private void around(int[] eligible, int main, int heaviest, int[] at) {
        int n = eligible.length, shades = at.length;
        at[main] = heaviest;
        if (heaviest > 0) shareOut(eligible, 0, main, 0, heaviest, at);
        else Arrays.fill(at, 0, main, heaviest);
        if (heaviest < n - 1) shareOut(eligible, main + 1, shades, heaviest + 1, n, at);
        else Arrays.fill(at, main + 1, shades, heaviest);
    }

    private void shareOut(int[] eligible, int fromShade, int toShade, int fromBlock, int toBlock, int[] at) {
        int shades = toShade - fromShade, n = toBlock - fromBlock;
        if (shades <= 0) return;
        double total = 0;
        if (shades < n) {
            for (int j = fromBlock; j < toBlock; j++) total += weights[eligible[j]];
            for (int k = 0; k < shades; k++) {
                double place = (k + 0.5) / shades * total, upTo = 0;
                int j = fromBlock;
                while (j < toBlock - 1 && upTo + weights[eligible[j]] <= place) upTo += weights[eligible[j++]];
                at[fromShade + k] = j;
            }
            return;
        }
        double strength = Math.min(1, (shades - n) / (double) n);
        double[] share = new double[n];
        for (int j = 0; j < n; j++) total += share[j] = Math.pow(weights[eligible[fromBlock + j]], strength);
        int[] count = new int[n];
        int left = shades - n;
        Integer[] byRemainder = new Integer[n];
        double[] remainder = new double[n];
        for (int j = 0; j < n; j++) {
            double exact = (shades - n) * share[j] / total;
            count[j] = 1 + (int) exact;
            remainder[j] = exact - (int) exact;
            left -= (int) exact;
            byRemainder[j] = j;
        }
        Arrays.sort(byRemainder, Comparator.comparingDouble((Integer j) -> -remainder[j]).thenComparingInt(j -> j));
        for (int r = 0; r < left; r++) count[byRemainder[r]]++;
        for (int j = 0, k = fromShade; j < n; j++) for (int c = 0; c < count[j]; c++) at[k++] = fromBlock + j;
    }

    private int[] matched(int[] eligible, Shades build, int main, int[] keptFrom, int[] keptTo) {
        int n = eligible.length, shades = build.count();
        double[] at = new double[shades];
        double before = 0;
        for (int k = 0; k < shades; k++) {
            at[k] = (before + build.amount(k) / 2) / build.total();
            before += build.amount(k);
        }
        double weight = 0;
        for (int i : eligible) weight += weights[i];
        double[] from = new double[n], to = new double[n];
        double upTo = 0;
        for (int j = 0; j < n; j++) {
            from[j] = upTo / weight;
            upTo += weights[eligible[j]];
            to[j] = upTo / weight;
        }
        int heaviest = -1, shares = 0;
        if (main < 0 && shades >= 3) for (int k = 1; k < shades - 1; k++) if (main < 0 || build.amount(k) > build.amount(main)) main = k;
        boolean end = main == 0 || main == shades - 1;
        if (main >= 0 && n >= (end ? 2 : 3)) {
            int[] room = room(main, n, keptFrom, keptTo);
            int roomy = heaviest(eligible, at[main], from, to, main, shades, 0, room);
            int crowded = n > shades ? heaviest(eligible, at[main], from, to, main, shades, 1, room) : -1;
            if (crowded >= 0 && crowded != roomy && (roomy < 0 || weights[eligible[crowded]] > weights[eligible[roomy]])) {
                heaviest = crowded;
                shares = 1;
            } else {
                heaviest = roomy;
            }
            if (heaviest < 0) {
                heaviest = heaviest(eligible, at[main], from, to, main, shades, shades, room);
                shares = heaviest < 0 ? 0 : Math.max(0, main - heaviest) + Math.max(0, shades - 1 - main - (n - 1 - heaviest));
            }
            if (heaviest < 0) main = -1;
            if (shares > 1) {
                int[] places = new int[shades], out = new int[shades];
                around(eligible, main, heaviest, places);
                for (int k = 0; k < shades; k++) out[k] = eligible[places[k]];
                return out;
            }
        }
        double[][][] best = new double[shares + 1][shades][n];
        int[][][] back = new int[shares + 1][shades][n];
        int[] dropAt = new int[n];
        double[] dropTo = new double[n];
        for (int k = 0; k < shades; k++) {
            double share = build.amount(k) / build.total();
            for (int s = 0; s <= shares; s++) {
                int drops = 0;
                for (int j = 0; j < n; j++) {
                    if (k > 0 && j > 0) {
                        double cost = best[s][k - 1][j - 1];
                        if (cost != Double.POSITIVE_INFINITY && (drops == 0 || cost < dropTo[drops - 1])) {
                            dropAt[drops] = j - 1;
                            dropTo[drops++] = cost;
                        }
                    }
                    best[s][k][j] = Double.POSITIVE_INFINITY;
                    boolean pinned = shades > 1 && k != main && (k == 0 && j != 0 || k == shades - 1 && j != n - 1) || k == main && j != heaviest;
                    if (pinned) continue;
                    double fit = share * away(at[k], from[j], to[j]);
                    if (k == 0) {
                        if (s == 0) best[s][k][j] = fit;
                        continue;
                    }
                    if (drops > 0) {
                        double least = dropTo[drops - 1] + fit;
                        int lo = 0, hi = drops - 1;
                        while (lo < hi) {
                            int mid = (lo + hi) >>> 1;
                            if (dropTo[mid] + fit <= least) hi = mid;
                            else lo = mid + 1;
                        }
                        best[s][k][j] = least;
                        back[s][k][j] = dropAt[lo];
                    }
                    if (s > 0 && best[s - 1][k - 1][j] != Double.POSITIVE_INFINITY && best[s - 1][k - 1][j] + fit < best[s][k][j]) {
                        best[s][k][j] = best[s - 1][k - 1][j] + fit;
                        back[s][k][j] = j;
                    }
                }
            }
        }
        int last = 0;
        for (int j = 1; j < n; j++) if (best[shares][shades - 1][j] < best[shares][shades - 1][last]) last = j;
        int[] out = new int[shades];
        for (int k = shades - 1, j = last, s = shares; k >= 0; k--) {
            out[k] = eligible[j];
            int i = back[s][k][j];
            if (k > 0 && i == j) s--;
            j = i;
        }
        return out;
    }

    private int heaviest(int[] eligible, double at, double[] from, double[] to, int main, int shades, int crowded, int[] room) {
        int n = eligible.length, best = -1;
        boolean end = main == 0 || main == shades - 1;
        for (int j = Math.max(end ? 0 : 1, room[0] + 1); j < Math.min(end ? n : n - 1, room[1]); j++) {
            if (Math.max(0, main - j) + Math.max(0, shades - 1 - main - (n - 1 - j)) > crowded) continue;
            if (best < 0 || weights[eligible[j]] > weights[eligible[best]]
                    || weights[eligible[j]] == weights[eligible[best]] && away(at, from[j], to[j]) < away(at, from[best], to[best])) {
                best = j;
            }
        }
        return best;
    }

    private static double away(double at, double from, double to) { return Math.max(0, Math.max(from - at, at - to)); }

    public int pick(Look block) { return pick(block, _ -> true); }

    private boolean any(IntPredicate allowed, boolean clear) {
        for (int i = 0; i < palette.length; i++) if (paletteClear[i] == clear && allowed.test(i)) return true;
        return false;
    }

    static final class Shades {
        private static final double VARIANT = 3;

        private record Key(Oklab colour, String family) {}

        private final List<Oklab> starts = new ArrayList<>();
        private final List<String> families = new ArrayList<>();
        private final List<Double> amounts = new ArrayList<>();
        private final List<Oklab> colours = new ArrayList<>();
        private final List<Integer> heaviest = new ArrayList<>();
        private final List<Map<String, Integer>> blocks = new ArrayList<>();
        private final Map<Key, Integer> shadeOf = new HashMap<>();
        private double total;
        private final int main;

        Shades(List<Look> looks) {
            List<Look> sorted = new ArrayList<>(looks);
            sorted.sort(Comparator.comparingDouble(l -> l.colour().oklab().l()));
            int fewest = Integer.MAX_VALUE, most = 0;
            boolean weighed = looks.stream().anyMatch(l -> l.weight() > 0);
            for (Look l : sorted) {
                Oklab c = l.colour().oklab();
                Key key = new Key(c, l.family());
                Integer shade = shadeOf.get(key);
                if (shade == null && !starts.isEmpty()) {
                    int s = starts.size() - 1;
                    double d = c.distance(starts.get(s)), jnd = Metric.OKLAB.justNoticeable();
                    String f = families.get(s);
                    boolean same = f != null && f.equals(l.family()), unknown = f == null || l.family() == null;
                    if (d < jnd && (same || unknown) || same && d < VARIANT * jnd) shade = s;
                }
                if (shade == null) {
                    shade = starts.size();
                    starts.add(c);
                    families.add(l.family());
                    amounts.add(0.0);
                    colours.add(c);
                    heaviest.add(-1);
                    blocks.add(new LinkedHashMap<>());
                }
                shadeOf.putIfAbsent(key, shade);
                int w = weighed ? Math.max(0, l.weight()) : 1;
                if (w > 0) {
                    fewest = Math.min(fewest, w);
                    most = Math.max(most, w);
                }
                amounts.set(shade, amounts.get(shade) + w);
                if (w > heaviest.get(shade)) {
                    heaviest.set(shade, w);
                    colours.set(shade, c);
                }
                if (l.block() != null) blocks.get(shade).merge(l.block(), w, Integer::sum);
            }
            int shades = starts.size(), largest = -1, inner = -1;
            boolean tied = false;
            double all = 0;
            for (int s = 0; s < shades; s++) {
                double a = amounts.get(s);
                all += a;
                if (s > 0 && s < shades - 1 && (inner < 0 || a > amounts.get(inner))) inner = s;
                if (largest < 0 || a > amounts.get(largest)) {
                    largest = s;
                    tied = false;
                } else if (a == amounts.get(largest)) {
                    tied = true;
                }
            }
            int counted = most > fewest && !tied ? largest : -1;
            if (counted >= 0 && (counted == 0 || counted == shades - 1)) counted = amounts.get(counted) * 2 > all ? counted : inner;
            for (int s = 0; s < shades; s++) {
                amounts.set(s, Math.sqrt(amounts.get(s)));
                total += amounts.get(s);
            }
            main = shades == 1 ? 0 : counted;
        }

        int count() { return starts.size(); }

        double amount(int shade) { return amounts.get(shade); }

        double total() { return total; }

        Oklab colour(int shade) { return colours.get(shade); }

        List<String> blocks(int shade) {
            List<Map.Entry<String, Integer>> out = new ArrayList<>(blocks.get(shade).entrySet());
            out.sort(Map.Entry.<String, Integer>comparingByValue().reversed());
            return out.stream().map(Map.Entry::getKey).toList();
        }

        int main() { return main; }

        int of(Oklab c, String family) {
            Integer known = shadeOf.get(new Key(c, family));
            if (known == null && family != null) known = shadeOf.get(new Key(c, null));
            if (known != null) return known;
            int best = 0;
            for (int s = 1; s < starts.size(); s++) if (c.distance(starts.get(s)) < c.distance(starts.get(best))) best = s;
            return best;
        }
    }
}
