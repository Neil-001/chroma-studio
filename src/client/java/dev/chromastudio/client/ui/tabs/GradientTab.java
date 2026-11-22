package dev.chromastudio.client.ui.tabs;

import static dev.chromastudio.client.ui.Theme.BAD;
import static dev.chromastudio.client.ui.Theme.BORDER;
import static dev.chromastudio.client.ui.Theme.DIM;
import static dev.chromastudio.client.ui.Theme.FAIR;
import static dev.chromastudio.client.ui.Theme.GOOD;
import static dev.chromastudio.client.ui.Theme.PANEL;
import static dev.chromastudio.client.ui.Theme.TEXT;
import static dev.chromastudio.client.ui.Theme.WELL;

import com.mojang.blaze3d.platform.InputConstants;
import dev.chromastudio.client.ChromaStudioClient;
import dev.chromastudio.client.ModSettings;
import dev.chromastudio.client.PaletteSink;
import dev.chromastudio.client.action.Clipboard;
import dev.chromastudio.client.action.HotbarFill;
import dev.chromastudio.client.action.PaletteBlocks;
import dev.chromastudio.client.axiom.AxiomBridge;
import dev.chromastudio.client.index.BlockIndex;
import dev.chromastudio.client.index.Entry;
import dev.chromastudio.client.index.Kind;
import dev.chromastudio.client.ui.Bounds;
import dev.chromastudio.client.ui.Flow;
import dev.chromastudio.client.ui.Handoff;
import dev.chromastudio.client.ui.Theme;
import dev.chromastudio.client.ui.Widgets;
import dev.chromastudio.core.color.Color;
import dev.chromastudio.core.color.Distribution;
import dev.chromastudio.core.color.Metric;
import dev.chromastudio.core.color.Oklch;
import dev.chromastudio.core.color.Scale;
import dev.chromastudio.core.color.TextureStats;
import dev.chromastudio.core.export.Layout;
import dev.chromastudio.core.palette.Easing;
import dev.chromastudio.core.palette.Gradient;
import dev.chromastudio.core.palette.HueArc;
import dev.chromastudio.core.palette.Palette;
import dev.chromastudio.core.palette.PaletteEntry;
import dev.chromastudio.core.palette.Ramp;
import dev.chromastudio.core.palette.StoredGradient;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.util.ARGB;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;

public final class GradientTab extends PanelTab {
    private static final int MAX_STOPS = 8, CHIP = 18, CELL_MIN = 18, CELL_MAX = 40, IDEAL_H = 10, BLOCK_H = 7, CELL_H = IDEAL_H + 16 + BLOCK_H + 4;
    private static final double GOOD_UP_TO = 0.02, FAIR_UP_TO = 0.06;
    private static final String ALL_KINDS = "all";
    private static final int SWAP_OPTIONS = 20, SWAP_LINES = 6, SWAP_LINES_MIN = 2, SWAP_ROW = 18, SWAP_W = 200;
    private static final int BACK = 9;
    private static final String[] UNDO = {"..#....", ".##....", "######.", ".##...#", "..#...#", "......#", "..####."};

    private static final List<Object> STOPS = new ArrayList<>(List.of(Color.fromHex("#1F2A44"), Color.fromHex("#E9D8A6")));
    private static int selectedStop;
    private static int steps = ModSettings.gradientSteps;
    private static Easing easing = named(Easing.class, ModSettings.easing, Easing.SMOOTH);
    private static HueArc arc = named(HueArc.class, ModSettings.hueArc, HueArc.SHORTEST);
    private static boolean spread = ModSettings.spreadMatching;
    private static String hex = "";
    private static final Map<Integer, Entry> PINS = new HashMap<>();
    private static Object pinsFor;
    private static int pinsBuild;
    private static StoredGradient restored;
    private static Object restoredFor;
    private static int restoredBuild;
    private static String savedName;

    private static final ExecutorService WORKER = Executors.newSingleThreadExecutor(task -> {
        Thread t = new Thread(task, "Chroma Studio gradient");
        t.setDaemon(true);
        return t;
    });
    private static final AtomicInteger ASKED = new AtomicInteger();

    private record Step(Color ideal, Entry entry, Color mean, double deltaE, boolean pinned) {}

    private record Option(Entry entry, double deltaE) {}

    private List<Step> result = List.of();
    private static List<PaletteEntry> onShow = List.of();

    public static List<PaletteEntry> stepsOnShow() { return onShow; }
    private List<Gradient.Candidate> pool = List.of();
    private Map<String, Entry> poolById = Map.of();
    private Metric poolMetric = Metric.OKLAB;
    private int swapStep = -1;
    private List<Option> swapOptions = List.of();
    private Bounds swapBox;
    private Widgets.ScrollList swapRows;
    private double meanDeltaE;
    private Component problem;
    private boolean solving;
    private int solvedBuild;
    private String solvedFor = "";
    private Bounds stopsArea, stripArea;
    private int pressedStop = -1;
    private double pressedX;
    private boolean draggingStop;
    private int dropGap;
    private int badgeY;
    private static final String[] ACTIONS = {"hotbar", "copy", "export", "axiom", "saved", "save", "swap"};
    private Flow.Spot[] actionSpots;
    private boolean filtersOpen;
    private final Widgets filterWidgets = new Widgets();
    private Bounds filtersFor;
    private MatchFilters filtersBuilt;
    private List<Object> filtersSeen = List.of();

    public GradientTab() { super("gradient"); }

    private static Component tr(String key, Object... args) { return Component.translatable(key, args); }

    private static <E extends Enum<E>> E named(Class<E> type, String name, E fallback) {
        try {
            return Enum.valueOf(type, name);
        } catch (IllegalArgumentException _) {
            return fallback;
        }
    }

    private static Component option(Enum<?> value) {
        return tr("option.chromastudio." + value.getClass().getSimpleName().toLowerCase(Locale.ROOT) + "." + value.name().toLowerCase(Locale.ROOT));
    }

    private static Component kindName(String name) { return tr("kind.chromastudio." + name.toLowerCase(Locale.ROOT)); }

    private static Color colourOf(Object stop) {
        return stop instanceof Entry e ? PaletteBlocks.stats(e).mean(PaletteBlocks.scale()) : (Color) stop;
    }

    @Override
    protected void layout(Bounds b) {
        MatchState.follow();
        boolean seeded = Handoff.gradientSeed instanceof Entry;
        if (Handoff.gradientSeed instanceof Entry seed) addStop(seed);
        Handoff.gradientSeed = null;
        StoredGradient asked = Handoff.gradientLoad;
        Handoff.gradientLoad = null;
        boolean loaded = asked != null && !restore(asked);
        if (asked == null && result.isEmpty() && !solving && restored != null && pinKey().equals(restoredFor) && BlockIndex.get().version() == restoredBuild) show(restored, restoredBuild);
        selectedStop = Math.clamp(selectedStop, 0, STOPS.size() - 1);

        int x = b.x() + PAD, y = b.y() + PAD;
        stopsArea = new Bounds(x, y, MAX_STOPS * (CHIP + 1), Widgets.H);
        x += stopsArea.w() + GAP;
        IconButton remove = widgets.add(new IconButton(x, y, 14, Widgets.H, IconButton.Glyph.REMOVE, tr("gradient.chromastudio.stop.remove"), () -> {
            STOPS.remove(selectedStop);
            changed();
        }));
        if (STOPS.size() <= 2) Widgets.disable(remove, tr("gradient.chromastudio.stop.two_needed"));
        x += 14 + GAP;
        Component add = tr("gradient.chromastudio.stop.add"), hand = tr("gradient.chromastudio.stop.in_hand");
        int addW = Widgets.buttonWidth(add), handW = Widgets.buttonWidth(hand);
        EditBox field = widgets.search(x, y, Math.max(48, Math.min(70, b.right() - PAD - x - addW - handW - 2 * GAP)), tr("gradient.chromastudio.stop.hex"), s -> hex = s);
        field.setMaxLength(7);
        field.setValue(hex);
        field.setTooltip(Tooltip.create(tr("gradient.chromastudio.stop.hex.tip")));
        x += field.getWidth() + GAP;
        Component full = STOPS.size() >= MAX_STOPS ? tr("gradient.chromastudio.stop.full", MAX_STOPS) : null;
        Button addButton = widgets.button(x, y, add, () -> {
            try {
                addStop(Color.fromHex(hex.strip()));
                changed();
            } catch (IllegalArgumentException _) {
                say(tr("palettes.chromastudio.generate.bad_hex"), BAD);
            }
        });
        if (full != null) Widgets.disable(addButton, full);
        x += addW + GAP;
        Entry held = inHand();
        Button handButton = widgets.button(x, y, hand, () -> {
            addStop(inHand());
            changed();
        });
        if (full != null) Widgets.disable(handButton, full);
        else if (held == null) Widgets.disable(handButton, tr("palettes.chromastudio.generate.in_hand.none"));

        x = b.x() + PAD;
        y += ROW;
        Component stepsLabel = tr("gradient.chromastudio.steps");
        widgets.label(x, y + 4, stepsLabel, DIM);
        x += Widgets.tw(stepsLabel) + 4;
        widgets.spin(x, y, 38, steps, 1, ModSettings.min("gradientSteps"), ModSettings.max("gradientSteps"), true, v -> {
            if ((int) v == steps) return;
            steps = (int) v;
            solve();
        });
        x += 38 + GAP;
        int w = widest(List.of(Easing.values()).stream().map(GradientTab::option).toList());
        widgets.cycler(x, y, w, null, List.of(Easing.values()), easing, GradientTab::option, v -> { easing = v; solve(); }, tr("gradient.chromastudio.easing.tip"));
        x += w + GAP;
        w = widest(List.of(HueArc.values()).stream().map(GradientTab::option).toList());
        widgets.cycler(x, y, w, null, List.of(HueArc.values()), arc, GradientTab::option, v -> { arc = v; solve(); }, tr("gradient.chromastudio.arc.tip"));
        x += w + GAP;
        Component spreadLabel = tr("gradient.chromastudio.spread");
        w = Widgets.tw(spreadLabel) + Widgets.tw(": ") + Math.max(Widgets.tw(CommonComponents.OPTION_ON), Widgets.tw(CommonComponents.OPTION_OFF)) + 10;
        widgets.toggle(x, y, w, spreadLabel, spread, v -> { spread = v; solve(); }, tr("gradient.chromastudio.spread.tip"));
        x += w + GAP;
        MatchFilters filters = MatchState.filters();
        int others = filters.besidesKind();
        Component more = others == 0 ? tr("match.chromastudio.filter.more") : tr("gradient.chromastudio.filters.more", others);
        int moreW = Chip.width(tr("gradient.chromastudio.filters.more", 88));
        List<String> kinds = new ArrayList<>(List.of(ALL_KINDS));
        for (Kind k : Kind.values()) kinds.add(k.name());
        w = Math.min(widest(kinds.stream().map(GradientTab::kindName).toList()), b.right() - PAD - x - GAP - moreW);
        widgets.cycler(x, y, w, null, kinds, filters.kind == null ? ALL_KINDS : filters.kind.name(), GradientTab::kindName, v -> {
            filters.kind = v.equals(ALL_KINDS) ? null : Kind.valueOf(v);
            MatchState.changed();
            solve();
        }, tr("match.chromastudio.filter.kind.tip"));
        x += w + GAP;
        widgets.add(new Chip(x, y, more, () -> filtersOpen || filters.besidesKind() > 0, () -> {
            filtersOpen = !filtersOpen;
            swapStep = -1;
            rebuild();
        })).setTooltip(Tooltip.create(tr("gradient.chromastudio.filters.tip")));

        int[] actionWidths = new int[ACTIONS.length];
        for (int i = 0; i < ACTIONS.length; i++) actionWidths[i] = Widgets.buttonWidth(tr("gradient.chromastudio.action." + ACTIONS[i]));
        actionSpots = Flow.place(actionWidths, b.x() + PAD, b.right() - PAD, GAP);
        int actionsY = b.bottom() - PAD - Widgets.H - (Flow.rows(actionSpots) - 1) * ROW;
        Component none = result.isEmpty() ? (problem != null ? problem : tr("gradient.chromastudio.working")) : null;
        action(0, actionsY, () -> open(new PaletteDialogs.Hotbar(entries())), none != null ? none : HotbarFill.unavailable().orElse(null));
        action(1, actionsY, () -> {
            Clipboard.copy(entries().stream().map(PaletteEntry::idWithProperties).collect(Collectors.joining("\n")))
                    .ifPresentOrElse(why -> say(why, BAD), () -> say(tr("gradient.chromastudio.copied", result.size()), GOOD));
        }, none);
        action(2, actionsY, () -> open(new PaletteDialogs.Export(asPalette(), Layout.STRIP, System.nanoTime())), none);
        action(3, actionsY, () -> open(new PaletteDialogs.Axiom(savedName != null ? savedName : asPalette().name, entries())),
                AxiomBridge.whyNot().orElse(none));
        action(4, actionsY, () -> open(new SavedGradients(this::toSave, this::unsaveable, savedName != null ? savedName : asPalette().name, this::load)), null);
        action(5, actionsY, () -> {
            Palette p = asPalette();
            PaletteSink sink = PaletteSink.current();
            if (sink != null && sink.createPalette(p.name, p.entries)) say(tr("gradient.chromastudio.saved", p.name), GOOD);
            else say(tr("capture.chromastudio.failed"), BAD);
        }, none);
        action(6, actionsY, () -> {
            Collections.reverse(STOPS);
            selectedStop = STOPS.size() - 1 - selectedStop;
            changed();
        }, null);

        badgeY = actionsY - GAP - 10;
        int top = y + ROW + GAP;
        stripArea = new Bounds(b.x() + PAD, top, b.w() - 2 * PAD, badgeY - GAP - top);
        Bounds body = filtersBody();
        if (filtersOpen && (!body.equals(filtersFor) || filtersBuilt != filters || !filters.state().equals(filtersSeen))) buildFilters();
        if (!filtersOpen) filtersFor = null;
        if (swapStep >= 0) placeSwap();
        if (seeded || loaded || stale() || result.isEmpty() && problem == null && !solving) solve();
    }

    private Bounds filtersArea() { return new Bounds(stripArea.x(), stripArea.y(), stripArea.w(), badgeY + 10 - stripArea.y()); }

    private Bounds filtersBody() {
        Bounds a = filtersArea();
        return new Bounds(a.x() + 1, a.y() + Dialog.TITLE_H, a.w() - 2, a.h() - Dialog.TITLE_H - 1);
    }

    private Bounds filtersClose() {
        Bounds a = filtersArea();
        return new Bounds(a.right() - Dialog.TITLE_H - 1, a.y() + 1, Dialog.TITLE_H, Dialog.TITLE_H - 1);
    }

    private void buildFilters() {
        filtersFor = filtersBody();
        filtersBuilt = MatchState.filters();
        filtersSeen = filtersBuilt.state();
        filterWidgets.clear();
        FilterControls.build(filterWidgets, filtersFor, true, () -> {
            filtersFor = null;
            rebuild();
        });
    }

    private boolean stale() { return BlockIndex.get().version() != solvedBuild || !inputs().equals(solvedFor); }

    private static String inputs() {
        return String.join("|", ModSettings.defaultFace.name(), ModSettings.metric, String.valueOf(ModSettings.viewingDistance), ModSettings.biomeMode.name(),
                ModSettings.biome, MatchState.filters().state().toString(), String.valueOf(ModSettings.statesSeparately), PaletteBlocks.scale().name());
    }

    private static int widest(List<Component> labels) { return labels.stream().mapToInt(Widgets::tw).max().orElse(40) + 10; }

    private void action(int index, int y, Runnable run, Component unavailable) {
        String key = ACTIONS[index];
        Flow.Spot spot = actionSpots[index];
        Button button = widgets.button(spot.x(), y + spot.row() * ROW, tr("gradient.chromastudio.action." + key), run);
        if (unavailable != null) Widgets.disable(button, unavailable);
    }

    private static Entry inHand() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return null;
        ItemStack held = mc.player.getMainHandItem();
        return held.getItem() instanceof BlockItem item ? PaletteBlocks.entry(item.getBlock().defaultBlockState()) : null;
    }

    private static void addStop(Object stop) {
        if (stop == null || STOPS.size() >= MAX_STOPS) return;
        selectedStop = Math.min(selectedStop + 1, STOPS.size());
        STOPS.add(selectedStop, stop);
    }

    private void changed() {
        solve();
        rebuild();
    }

    private List<PaletteEntry> entries() { return result.stream().map(s -> PaletteBlocks.of(s.entry)).toList(); }

    private Palette asPalette() {
        Palette p = new Palette(tr("gradient.chromastudio.palette_name", colourOf(STOPS.getFirst()).hex(), colourOf(STOPS.getLast()).hex()).getString());
        p.entries.addAll(entries());
        return p;
    }

    private Component unsaveable() {
        if (solving || result.isEmpty() && problem == null) return tr("gradient.chromastudio.working");
        return result.isEmpty() ? problem : null;
    }

    private StoredGradient toSave(String name) {
        List<StoredGradient.Stop> stops = new ArrayList<>();
        for (Object stop : STOPS) stops.add(new StoredGradient.Stop(colourOf(stop).rgb(), stop instanceof Entry e ? PaletteBlocks.of(e) : null));
        List<StoredGradient.Step> blocks = new ArrayList<>();
        for (Step s : result) blocks.add(new StoredGradient.Step(s.ideal.rgb(), PaletteBlocks.of(s.entry), s.pinned));
        Kind kind = MatchState.filters().kind;
        savedName = name;
        return new StoredGradient(name, stops, steps, easing.name(), arc.name(), spread, kind == null ? "" : kind.name(), blocks);
    }

    private void load(StoredGradient g) {
        boolean shown = restore(g);
        say(tr("gradient.chromastudio.store.loaded", g.name()), GOOD);
        if (!shown) solve();
        rebuild();
    }

    private boolean restore(StoredGradient g) {
        while (true) {
            int build = BlockIndex.get().version();
            apply(g, build);
            boolean shown = show(g, build);
            if (BlockIndex.get().version() == build) return shown;
        }
    }

    private boolean show(StoredGradient g, int build) {
        restored = null;
        if (!BlockIndex.get().ready() || g.blocks().size() != steps) return false;
        Scale scale = PaletteBlocks.scale();
        List<Step> shown = new ArrayList<>();
        double mean = 0;
        for (StoredGradient.Step step : g.blocks()) {
            Entry e = PaletteBlocks.entry(step.block());
            if (e == null) return false;
            TextureStats stats = PaletteBlocks.stats(e);
            Color ideal = Color.ofRgb(step.ideal());
            double deltaE = Distribution.of(ideal).emd(stats.distribution(scale), Metric.OKLAB);
            mean += deltaE / g.blocks().size();
            shown.add(new Step(ideal, e, stats.mean(scale), deltaE, step.byHand()));
        }
        Pool chosenFrom = pool(scale);
        ASKED.incrementAndGet();
        solving = false;
        problem = null;
        swapStep = -1;
        result = List.copyOf(shown);
        onShow = entries();
        meanDeltaE = mean;
        pool = chosenFrom.candidates;
        poolById = chosenFrom.byId;
        poolMetric = PaletteBlocks.metric();
        solvedFor = inputs();
        solvedBuild = build;
        restored = g;
        restoredFor = pinKey();
        restoredBuild = solvedBuild;
        return true;
    }

    private static void apply(StoredGradient g, int build) {
        STOPS.clear();
        for (StoredGradient.Stop stop : g.stops()) {
            Entry e = stop.isBlock() ? PaletteBlocks.entry(stop.block()) : null;
            if (STOPS.size() < MAX_STOPS) STOPS.add(e != null ? e : Color.ofRgb(stop.rgb()));
        }
        selectedStop = 0;
        steps = g.steps();
        easing = named(Easing.class, g.easing(), easing);
        arc = named(HueArc.class, g.arc(), arc);
        spread = g.spread();
        MatchFilters filters = MatchState.filters();
        Kind kind = g.kind().isEmpty() ? null : named(Kind.class, g.kind(), filters.kind);
        if (kind != filters.kind) {
            filters.kind = kind;
            MatchState.changed();
        }
        PINS.clear();
        for (int i = 0; i < g.blocks().size(); i++) {
            StoredGradient.Step step = g.blocks().get(i);
            Entry e = step.byHand() ? PaletteBlocks.entry(step.block()) : null;
            if (e != null) PINS.put(i, e);
        }
        pinsFor = pinKey();
        pinsBuild = build;
        savedName = g.name();
    }

    private void solve() {
        int number = ASKED.incrementAndGet();
        restored = null;
        solvedFor = inputs();
        solvedBuild = BlockIndex.get().version();
        swapStep = -1;
        Object key = pinKey();
        if (!key.equals(pinsFor) || solvedBuild != pinsBuild) {
            PINS.clear();
            pinsFor = key;
            pinsBuild = solvedBuild;
        }
        if (!BlockIndex.get().ready()) {
            fail(tr("palettes.chromastudio.index_building"));
            return;
        }
        Scale scale = PaletteBlocks.scale();
        Metric metric = PaletteBlocks.metric();
        List<Oklch> stops = STOPS.stream().map(s -> colourOf(s).oklch()).toList();
        List<Oklch> ideal = Ramp.of(stops, steps, easing, arc);
        Pool chosenFrom = pool(scale);
        List<Gradient.Candidate> candidates = chosenFrom.candidates;
        Map<String, Entry> byId = chosenFrom.byId;
        Map<String, TextureStats> looks = chosenFrom.looks;
        for (Entry e : PINS.values()) {
            if (byId.get(e.id()) == e) continue;
            TextureStats stats = PaletteBlocks.stats(e);
            Gradient.Candidate pinned = new Gradient.Candidate(e.id(), stats.distribution(scale), stats.spread());
            if (byId.put(e.id(), e) != null) candidates.removeIf(c -> c.key().equals(e.id()));
            looks.put(e.id(), stats);
            candidates.add(pinned);
        }
        if (candidates.size() < steps) {
            fail(tr("gradient.chromastudio.too_few", candidates.size(), steps));
            return;
        }
        boolean matchSpread = spread;
        Map<Integer, String> pins = new HashMap<>();
        PINS.forEach((step, entry) -> pins.put(step, entry.id()));
        solving = true;
        problem = null;
        WORKER.execute(() -> {
            List<Step> found = new ArrayList<>();
            double mean = 0;
            try {
                Gradient.Result solved = Gradient.solve(ideal, candidates, metric, matchSpread, pins);
                for (int i = 0; i < solved.steps().size(); i++) {
                    Gradient.Candidate c = solved.steps().get(i);
                    Color wanted = Color.ofOklch(solved.ideal().get(i));
                    double deltaE = Distribution.of(wanted).emd(c.distribution(), Metric.OKLAB);
                    mean += deltaE / solved.steps().size();
                    found.add(new Step(wanted, byId.get((String) c.key()), looks.get((String) c.key()).mean(scale), deltaE, c.key().equals(pins.get(i))));
                }
            } catch (RuntimeException e) {
                ChromaStudioClient.LOGGER.warn("Couldn't work the gradient out", e);
                Minecraft.getInstance().execute(() -> {
                    if (number == ASKED.get()) fail(tr("gradient.chromastudio.failed"));
                });
                return;
            }
            double meanOklab = mean;
            Minecraft.getInstance().execute(() -> {
                if (number != ASKED.get()) return;
                solving = false;
                swapStep = -1;
                result = List.copyOf(found);
                onShow = entries();
                meanDeltaE = meanOklab;
                pool = candidates;
                poolById = byId;
                poolMetric = metric;
                PINS.keySet().removeIf(step -> step >= found.size() || !found.get(step).pinned);
                rebuild();
            });
        });
    }

    private record Pool(List<Gradient.Candidate> candidates, Map<String, Entry> byId, Map<String, TextureStats> looks) {}

    private static Pool pool(Scale scale) {
        List<Gradient.Candidate> candidates = new ArrayList<>();
        Map<String, Entry> byId = new HashMap<>();
        Map<String, TextureStats> looks = new HashMap<>();
        for (Entry e : MatchState.filters().candidates(BlockIndex.get(), PaletteBlocks.face(), scale, PaletteBlocks.tint())) {
            byId.put(e.id(), e);
            TextureStats stats = PaletteBlocks.stats(e);
            looks.put(e.id(), stats);
            candidates.add(new Gradient.Candidate(e.id(), stats.distribution(scale), stats.spread()));
        }
        return new Pool(candidates, byId, looks);
    }

    private void fail(Component why) {
        solving = false;
        swapStep = -1;
        result = List.of();
        onShow = List.of();
        problem = why;
        rebuild();
    }

    private static Object pinKey() { return List.of(List.copyOf(STOPS), steps, easing, arc, spread, inputs()); }

    private void openSwap(int step) {
        if (solving) return;
        Set<String> used = new HashSet<>();
        for (Step s : result) used.add(s.entry.id());
        Step wanted = result.get(step);
        Distribution target = Distribution.of(wanted.ideal);
        List<Option> options = new ArrayList<>();
        for (Gradient.Option o : Gradient.closest(wanted.ideal.oklch(), pool, poolMetric, used, SWAP_OPTIONS)) {
            options.add(new Option(poolById.get((String) o.candidate().key()), target.emd(o.candidate().distribution(), Metric.OKLAB)));
        }
        swapOptions = options;
        swapStep = step;
        swapRows = null;
        placeSwap();
    }

    private int swapLines() { return swapBack() + Math.max(1, swapOptions.size()); }

    private int swapBack() { return swapStep < result.size() && result.get(swapStep).pinned ? 1 : 0; }

    private void placeSwap() {
        if (swapStep >= result.size()) {
            swapStep = -1;
            return;
        }
        Grid grid = grid(stripArea, result.size());
        int stripTop = grid.y, stripBottom = grid.y + grid.rows * CELL_H, w = Math.min(SWAP_W, stripArea.w());
        int below = (stripArea.bottom() - stripBottom - 2) / SWAP_ROW, above = (stripTop - 2 - stripArea.y()) / SWAP_ROW;
        boolean under = below >= SWAP_LINES_MIN || below >= above;
        int shown = Math.min(swapLines(), Math.clamp(under ? below : above, SWAP_LINES_MIN, SWAP_LINES)), h = shown * SWAP_ROW + 2;
        int y = Math.clamp(under ? stripBottom + 2 : stripTop - 2 - h, stripArea.y(), Math.max(stripArea.y(), stripArea.bottom() - h));
        int centre = grid.x + swapStep % grid.perRow * grid.cellW + grid.cellW / 2;
        swapBox = new Bounds(Math.clamp(centre - w / 2, stripArea.x(), stripArea.right() - w), y, w, h);
        Bounds inside = new Bounds(swapBox.x() + 1, swapBox.y() + 1, w - 2, h - 2);
        if (swapRows == null) swapRows = new Widgets.ScrollList(inside, SWAP_ROW, this::swapLines);
        else swapRows.area = inside;
    }

    private Bounds stepsArea() { return stripArea; }

    private void swapList(GuiGraphicsExtractor g, int mouseX, int mouseY) {
        Bounds box = swapBox;
        g.fill(box.x(), box.y(), box.right(), box.bottom(), PANEL);
        g.outline(box.x(), box.y(), box.w(), box.h(), Theme.HIGHLIGHT);
        int back = swapBack();
        Step step = result.get(swapStep);
        swapRows.render(g, mouseX, mouseY, (g2, i, x, y, w, h, over) -> {
            if (over) g2.fill(x, y, x + w, y + h, Theme.veil(0x20));
            if (i < back) {
                sign(g2, x + 7, y + 6, UNDO, Theme.ACCENT);
                g2.text(font(), fit(tr("gradient.chromastudio.swap.back").getString(), w - 26), x + 22, y + 5, Theme.ACCENT, false);
            } else if (swapOptions.isEmpty()) {
                g2.text(font(), fit(tr("gradient.chromastudio.swap.none").getString(), w - 8), x + 4, y + 5, DIM, false);
            } else {
                Option o = swapOptions.get(i - back);
                Widgets.blockIcon(g2, o.entry.block(), x + 2, y + 1);
                String de = String.format(Locale.ROOT, "%.3f", o.deltaE);
                g2.text(font(), de, x + w - 4 - font().width(de), y + 5, DIM, false);
                g2.text(font(), fit(o.entry.name().getString(), w - 30 - font().width(de)), x + 22, y + 5, TEXT, false);
                if (over) {
                    g2.setComponentTooltipForNextFrame(font(), List.of(o.entry.name(), tr("gradient.chromastudio.step.tip", swapStep + 1, step.ideal.hex(), de).copy().withColor(Theme.TIP_DIM)), mouseX, mouseY);
                }
            }
        });
    }

    private void swapClicked(MouseButtonEvent e) {
        if (swapRows.mouseClicked(e) || e.button() != InputConstants.MOUSE_BUTTON_LEFT) return;
        int back = swapBack(), i = swapRows.rowAt(e.x(), e.y()) - back;
        if (i == -1 && back == 1) putBack(swapStep);
        else if (i >= 0 && i < swapOptions.size()) {
            Entry chosen = swapOptions.get(i).entry;
            boolean taken = false;
            for (int other = 0; other < result.size(); other++) taken |= other != swapStep && result.get(other).entry.id().equals(chosen.id());
            for (Map.Entry<Integer, Entry> pin : PINS.entrySet()) taken |= pin.getKey() != swapStep && pin.getValue().id().equals(chosen.id());
            if (taken) {
                swapStep = -1;
                return;
            }
            say(tr("gradient.chromastudio.swap.done", result.get(swapStep).entry.name(), chosen.name()), GOOD);
            PINS.put(swapStep, chosen);
            solve();
        }
    }

    private void putBack(int step) {
        PINS.remove(step);
        say(tr("gradient.chromastudio.swap.undone", step + 1));
        solve();
    }

    private int stepAt(double mx, double my) {
        if (filtersOpen || result.isEmpty()) return -1;
        Grid grid = grid(stepsArea(), result.size());
        for (int i = 0; i < result.size(); i++) {
            int x = grid.x + i % grid.perRow * grid.cellW, y = grid.y + i / grid.perRow * CELL_H;
            if (mx >= x && mx < x + grid.cellW && my >= y && my < y + CELL_H - 4) return i;
        }
        return -1;
    }

    private static boolean overBack(int x, int y, int cellW, double mx, double my) {
        return mx >= x + cellW - BACK && mx < x + cellW && my >= y && my < y + BACK;
    }

    private static void sign(GuiGraphicsExtractor g, int x, int y, String[] sign, int colour) {
        for (int j = 0; j < sign.length; j++)
            for (int i = 0; i < sign[j].length(); i++)
                if (sign[j].charAt(i) == '#') g.fill(x + i, y + j, x + i + 1, y + j + 1, colour);
    }

    private record Grid(int perRow, int rows, int cellW, int x, int y) {}

    private Grid grid(Bounds area, int count) {
        int perRow = Math.max(1, Math.min(count, area.w() / CELL_MIN));
        int rows = (count + perRow - 1) / perRow;
        perRow = (count + rows - 1) / rows;
        int cellW = Math.min(CELL_MAX, area.w() / perRow);
        return new Grid(perRow, rows, cellW, area.x() + (area.w() - perRow * cellW) / 2, area.y() + Math.max(0, (area.h() - rows * CELL_H) / 2));
    }

    private void strip(GuiGraphicsExtractor g, Bounds area, float opacity, boolean controls, int mouseX, int mouseY) {
        Grid grid = grid(area, result.size());
        for (int i = 0; i < result.size(); i++) {
            Step s = result.get(i);
            int x = grid.x + i % grid.perRow * grid.cellW, y = grid.y + i / grid.perRow * CELL_H;
            g.fill(x, y, x + grid.cellW, y + IDEAL_H, ARGB.multiplyAlpha(0xFF000000 | s.ideal.rgb(), opacity));
            Widgets.blockIcon(g, s.entry.block(), x + (grid.cellW - 16) / 2, y + IDEAL_H);
            g.fill(x, y + IDEAL_H + 16, x + grid.cellW, y + IDEAL_H + 16 + BLOCK_H, ARGB.multiplyAlpha(0xFF000000 | s.mean.rgb(), opacity));
            boolean over = mouseX >= x && mouseX < x + grid.cellW && mouseY >= y && mouseY < y + CELL_H - 4;
            boolean onBack = controls && s.pinned && over && overBack(x, y, grid.cellW, mouseX, mouseY);
            if (s.pinned) {
                int ix = x + (grid.cellW - 16) / 2, iy = y + IDEAL_H;
                g.fill(ix, iy + 11, ix + 5, iy + 16, ARGB.multiplyAlpha(0xFF10140F, opacity));
                g.fill(ix, iy + 12, ix + 4, iy + 16, ARGB.multiplyAlpha(0xFF000000 | Theme.TIP_SWAPPED, opacity));
                if (controls) {
                    g.fill(x + grid.cellW - BACK, y, x + grid.cellW, y + BACK, onBack ? Theme.LIT_HOVER : Theme.CHIP);
                    sign(g, x + grid.cellW - BACK + 1, y + 1, UNDO, TEXT);
                }
            }
            if (controls && i == swapStep) g.outline(x, y, grid.cellW, CELL_H - 4, Theme.HIGHLIGHT);
            if (onBack) g.setTooltipForNextFrame(font(), tr("gradient.chromastudio.swap.back"), mouseX, mouseY);
            else if (over) {
                List<Component> tip = new ArrayList<>(List.of(s.entry.name(), tr("gradient.chromastudio.step.tip", i + 1, s.ideal.hex(), String.format(Locale.ROOT, "%.3f", s.deltaE))
                        .copy().withColor(Theme.TIP_DIM)));
                if (s.pinned) tip.add(tr("gradient.chromastudio.step.pinned").copy().withColor(Theme.TIP_SWAPPED));
                g.setComponentTooltipForNextFrame(font(), tip, mouseX, mouseY);
            }
        }
    }

    private int badgeColour() { return meanDeltaE <= GOOD_UP_TO ? GOOD : meanDeltaE <= FAIR_UP_TO ? FAIR : BAD; }

    @Override
    protected void draw(GuiGraphicsExtractor g, Bounds b, int mouseX, int mouseY, float delta) {
        for (int i = 0; i < STOPS.size(); i++) {
            int x = stopsArea.x() + i * (CHIP + 1), y = stopsArea.y();
            Object stop = STOPS.get(i);
            g.fill(x, y, x + CHIP, y + Widgets.H, i == selectedStop ? Theme.ACCENT : BORDER);
            g.fill(x + 1, y + 1, x + CHIP - 1, y + Widgets.H - 1, 0xFF000000 | colourOf(stop).rgb());
            if (stop instanceof Entry e) Widgets.blockIcon(g, e.block(), x + 1, y);
            if (!draggingStop && mouseX >= x && mouseX < x + CHIP && mouseY >= y && mouseY < y + Widgets.H) {
                Component what = stop instanceof Entry e ? e.name() : Component.literal(colourOf(stop).hex());
                g.setComponentTooltipForNextFrame(font(), STOPS.size() > 2 ? List.of(what, tr("gradient.chromastudio.stop.tip").copy().withColor(Theme.TIP_DIM))
                        : List.of(what, tr("gradient.chromastudio.stop.drag").copy().withColor(Theme.TIP_DIM), tr("gradient.chromastudio.stop.two_needed").copy().withColor(Theme.TIP_GREYED)), mouseX, mouseY);
            }
        }

        if (draggingStop && Reorder.target(pressedStop, dropGap) != pressedStop) {
            int x = stopsArea.x() + dropGap * (CHIP + 1) - 1;
            g.fill(x - 1, stopsArea.y() - 2, x + 1, stopsArea.bottom() + 2, TEXT);
        }
        if (filtersOpen) return;

        g.fill(stripArea.x(), stripArea.y(), stripArea.right(), stripArea.bottom(), WELL);
        Component note = note();
        int noteRoom = stripArea.w() / 2;
        if (result.isEmpty()) {
            Component text = problem != null ? problem : tr("gradient.chromastudio.working");
            g.textWithWordWrap(font(), text, stripArea.x() + 8, stripArea.y() + 8, stripArea.w() - 16, problem != null ? FAIR : DIM, false);
        } else {
            boolean overSwap = swapStep >= 0 && swapBox.contains(mouseX, mouseY);
            strip(g, stepsArea(), 1, true, overSwap ? -1 : mouseX, overSwap ? -1 : mouseY);
            if (swapStep >= 0) swapList(g, mouseX, mouseY);
            int colour = badgeColour();
            g.fill(stripArea.x(), badgeY + 1, stripArea.x() + 8, badgeY + 9, colour);
            String quality = tr(meanDeltaE <= GOOD_UP_TO ? "gradient.chromastudio.badge.good" : meanDeltaE <= FAIR_UP_TO ? "gradient.chromastudio.badge.fair" : "gradient.chromastudio.badge.poor").getString();
            Component badge = tr("gradient.chromastudio.badge", String.format(Locale.ROOT, "%.3f", meanDeltaE), quality);
            g.text(font(), badge, stripArea.x() + 11, badgeY + 1, colour, false);
            noteRoom = Math.min(noteRoom, stripArea.w() - 11 - font().width(badge) - 6);
        }
        if (note != null) {
            String line = fit(note.getString(), noteRoom);
            g.text(font(), line, stripArea.right() - font().width(line), badgeY + 1, noteColour(), false);
        } else if (solving) {
            String line = tr("gradient.chromastudio.working").getString();
            g.text(font(), line, stripArea.right() - font().width(line), badgeY + 1, DIM, false);
        }
    }

    @Override
    protected void drawOver(GuiGraphicsExtractor g, Bounds b, int mouseX, int mouseY, float delta) {
        if (!filtersOpen) return;
        Bounds a = filtersArea(), close = filtersClose();
        g.fill(a.x(), a.y(), a.right(), a.bottom(), PANEL);
        g.outline(a.x(), a.y(), a.w(), a.h(), BORDER);
        Bounds body = filtersBody();
        g.fill(body.x(), body.y() - 1, body.right(), body.y(), BORDER);
        g.text(font(), tr("match.chromastudio.panel.filters"), a.x() + 5, a.y() + 3, TEXT, false);
        int cx = close.x() + close.w() / 2, cy = close.y() + close.h() / 2, c = close.contains(mouseX, mouseY) ? Theme.STRONG : DIM;
        for (int i = -3; i <= 2; i++) {
            g.fill(cx + i, cy + i, cx + i + 1, cy + i + 1, c);
            g.fill(cx + i, cy - 1 - i, cx + i + 1, cy - i, c);
        }
        filterWidgets.render(g, mouseX, mouseY, delta);
    }

    @Override
    public void renderPinned(GuiGraphicsExtractor g, Bounds b, float opacity) {
        if (result.isEmpty()) {
            Widgets.centred(g, problem != null ? problem : tr("gradient.chromastudio.working"), b.x() + b.w() / 2, b.y() + b.h() / 2 - 4, ARGB.multiplyAlpha(DIM, opacity));
            return;
        }
        strip(g, new Bounds(b.x() + PAD, b.y() + PAD, b.w() - 2 * PAD, b.h() - 2 * PAD - 12), opacity, false, -1, -1);
        String quality = String.format(Locale.ROOT, "%.3f", meanDeltaE);
        g.text(font(), tr("gradient.chromastudio.badge.short", quality), b.x() + PAD, b.bottom() - PAD - 9, ARGB.multiplyAlpha(badgeColour(), opacity), false);
    }

    @Override
    protected boolean clicked(MouseButtonEvent e, boolean doubled) {
        boolean left = e.button() == InputConstants.MOUSE_BUTTON_LEFT;
        Bounds drawn = stepsArea();
        int step = stepAt(e.x(), e.y()), shut = -1;
        if (swapStep >= 0) {
            if (swapBox.contains(e.x(), e.y())) {
                swapClicked(e);
                return true;
            }
            shut = swapStep;
            swapStep = -1;
        }
        if (step >= 0) {
            Step s = result.get(step);
            Grid grid = grid(drawn, result.size());
            boolean back = overBack(grid.x + step % grid.perRow * grid.cellW, grid.y + step / grid.perRow * CELL_H, grid.cellW, e.x(), e.y());
            if (s.pinned && (e.button() == InputConstants.MOUSE_BUTTON_RIGHT || left && back)) putBack(step);
            else if (left && step != shut) openSwap(step);
            return true;
        }
        if (filtersOpen) {
            if (e.button() == InputConstants.MOUSE_BUTTON_LEFT && filtersClose().contains(e.x(), e.y())) {
                filtersOpen = false;
                rebuild();
                return true;
            }
            if (filterWidgets.mouseClicked(e, doubled) || filtersArea().contains(e.x(), e.y())) return true;
        }
        if (!stopsArea.contains(e.x(), e.y())) return false;
        int i = (int) ((e.x() - stopsArea.x()) / (CHIP + 1));
        if (i >= STOPS.size()) return false;
        selectedStop = i;
        if (e.button() == InputConstants.MOUSE_BUTTON_RIGHT && STOPS.size() > 2) {
            STOPS.remove(i);
            changed();
        } else rebuild();
        if (e.button() == InputConstants.MOUSE_BUTTON_LEFT) {
            pressedStop = i;
            pressedX = e.x();
        }
        return true;
    }

    @Override
    protected boolean dragged(MouseButtonEvent e, double dx, double dy) {
        if (pressedStop < 0) return swapStep >= 0 && swapRows.mouseDragged(e) || filtersOpen && filterWidgets.mouseDragged(e, dx, dy);
        if (Math.abs(e.x() - pressedX) >= 3) draggingStop = true;
        dropGap = Reorder.gap(e.x() - stopsArea.x(), CHIP + 1, STOPS.size());
        return true;
    }

    @Override
    protected boolean released(MouseButtonEvent e) {
        if (pressedStop < 0) return swapStep >= 0 && swapRows.mouseReleased() || filtersOpen && filterWidgets.mouseReleased(e);
        boolean moved = draggingStop && pressedStop < STOPS.size() && Reorder.target(pressedStop, dropGap) != pressedStop;
        if (moved) selectedStop = Reorder.move(STOPS, pressedStop, dropGap);
        dropStop();
        if (moved) changed();
        return true;
    }

    private void dropStop() {
        pressedStop = -1;
        draggingStop = false;
    }

    @Override
    public void init(Bounds b) {
        dropStop();
        super.init(b);
    }

    @Override
    public void closed() { dropStop(); }

    @Override
    protected boolean scrolled(double x, double y, double h, double v) {
        if (swapStep >= 0 && swapBox.contains(x, y)) return swapRows.mouseScrolled(x, y, v) || true;
        return filtersOpen && filtersArea().contains(x, y) && (filterWidgets.mouseScrolled(x, y, h, v) || true);
    }

    @Override
    protected boolean pressed(KeyEvent e) {
        if (!filtersOpen || !filterWidgets.typing()) return false;
        filterWidgets.keyPressed(e);
        return true;
    }

    @Override
    protected boolean typed(CharacterEvent e) { return filtersOpen && filterWidgets.typing() && filterWidgets.charTyped(e); }

    @Override
    protected boolean typingElsewhere() { return filtersOpen && filterWidgets.typing(); }

    @Override
    protected boolean dismissPanel() {
        if (swapStep >= 0) {
            swapStep = -1;
            return true;
        }
        if (!filtersOpen) return false;
        filtersOpen = false;
        rebuild();
        return true;
    }

    @Override
    public void tick() {
        super.tick();
        if (Handoff.gradientLoad != null && !dialogOpen()) {
            StoredGradient asked = Handoff.gradientLoad;
            Handoff.gradientLoad = null;
            load(asked);
        }
        MatchState.follow();
        filtersSeen = MatchState.filters().state();
        if (!dialogOpen() && stale()) solve();
    }
}
