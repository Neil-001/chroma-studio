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
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.gui.screens.inventory.tooltip.BelowOrAboveWidgetTooltipPositioner;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.util.ARGB;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;

public final class GradientTab extends PanelTab {
    private static final int MAX_STOPS = 8, CHIP = 17, CELL_MIN = 18, CELL_MAX = 56, IDEAL_H = 10, BLOCK_H = 7, BAR_MAX = 34, CELL_GAP = 4, LEGEND = 11;
    static final double GOOD_UP_TO = 0.02, FAIR_UP_TO = 0.06;
    private static final String ALL_KINDS = "all";
    private static final int SWAP_OPTIONS = 20, SWAP_LINES_MIN = 2, SWAP_ROW = 18, SWAP_HEAD = 24, SWAP_W = 230, SWAP_GAP = 4;
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

    private record Option(Entry entry, Color mean, double deltaE) {}

    private List<Step> result = List.of();
    private static List<PaletteEntry> onShow = List.of();

    public static List<PaletteEntry> stepsOnShow() { return onShow; }
    private List<Gradient.Candidate> pool = List.of();
    private Map<String, Entry> poolById = Map.of();
    private Map<String, TextureStats> poolLooks = Map.of();
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
    private static final List<String> ACTIONS = List.of("hotbar", "copy", "export", "axiom", "saved", "save");
    private List<String> actions = ACTIONS;
    private Flow.Spot[] actionSpots;
    private boolean filtersOpen;
    private List<Detail> details = List.of();
    private Bounds detailsFor;
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
        return tr("option.chromastudio." + value.getDeclaringClass().getSimpleName().toLowerCase(Locale.ROOT) + "." + value.name().toLowerCase(Locale.ROOT));
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

        int left = b.x() + PAD, right = b.right() - PAD, x = left, y = b.y() + PAD;
        Component add = tr("gradient.chromastudio.stop.add"), hand = tr("colour.chromastudio.in_hand"), stepsLabel = tr("gradient.chromastudio.steps");
        Component easingLabel = tr("gradient.chromastudio.easing"), arcLabel = tr("gradient.chromastudio.arc"), spreadLabel = tr("gradient.chromastudio.spread");
        List<Component> easings = List.of(Easing.values()).stream().map(GradientTab::option).toList(), arcs = List.of(HueArc.values()).stream().map(GradientTab::option).toList();
        List<String> kinds = new ArrayList<>(List.of(ALL_KINDS));
        for (Kind k : Kind.values()) kinds.add(k.name());
        int addW = Widgets.buttonWidth(add), handW = Widgets.buttonWidth(hand), colon = Widgets.tw(": ");
        int spreadW = Widgets.tw(spreadLabel) + colon + Math.max(Widgets.tw(CommonComponents.OPTION_ON), Widgets.tw(CommonComponents.OPTION_OFF)) + 10;
        int others = MatchState.filters().besidesKind();
        Component more = others == 0 ? tr("match.chromastudio.filter.more") : tr("gradient.chromastudio.filters.more", others);
        int moreW = Chip.width(more), kindW = widest(kinds.stream().map(GradientTab::kindName).toList());
        int easingW = widest(easings) + Widgets.tw(easingLabel) + colon, arcW = widest(arcs) + Widgets.tw(arcLabel) + colon;
        int settingsW = Widgets.tw(stepsLabel) + 4 + 38 + GAP + easingW + GAP + arcW + GAP + spreadW;
        int stopsW = MAX_STOPS * (CHIP + 1) + 2 * (14 + GAP) + GAP + 48 + GAP + addW + GAP + handW;
        boolean named = settingsW + GAP + kindW + GAP + moreW <= right - left;
        boolean filtersUp = !named && settingsW <= right - left && stopsW + GAP + kindW + GAP + moreW <= right - left;
        named |= filtersUp;
        int stopsRight = filtersUp ? right - kindW - GAP - moreW - GAP : right;

        stopsArea = new Bounds(x, y, MAX_STOPS * (CHIP + 1), Widgets.H);
        int tools = x + STOPS.size() * (CHIP + 1) + 1;
        IconButton remove = widgets.add(new IconButton(tools, y, 14, Widgets.H, IconButton.Glyph.REMOVE, tr("gradient.chromastudio.stop.remove"), () -> {
            STOPS.remove(selectedStop);
            changed();
        }));
        if (STOPS.size() <= 2) Widgets.disable(remove, tr("gradient.chromastudio.stop.two_needed"));
        widgets.add(new IconButton(tools + 14 + GAP, y, 14, Widgets.H, IconButton.Glyph.SWAP, tr("gradient.chromastudio.action.swap"), () -> {
            Collections.reverse(STOPS);
            selectedStop = STOPS.size() - 1 - selectedStop;
            changed();
        }));
        x += stopsArea.w() + 2 * (14 + GAP) + GAP;
        EditBox field = widgets.search(x, y, Math.max(48, Math.min(70, stopsRight - x - addW - handW - 2 * GAP)), tr("gradient.chromastudio.stop.hex"), s -> hex = s);
        field.setMaxLength(7);
        field.setValue(hex);
        field.setTooltip(Tooltip.create(tr("gradient.chromastudio.stop.hex.tip")));
        x += field.getWidth() + GAP;
        Component full = STOPS.size() >= MAX_STOPS ? tr("gradient.chromastudio.stop.full", MAX_STOPS) : null;
        Button addButton = widgets.button(x, y, add, () -> {
            try {
                addStop(Color.fromHex(hex));
                hex = "";
                changed();
            } catch (IllegalArgumentException _) {
                say(tr("colour.chromastudio.hex.bad"), BAD);
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
        else if (held == null) Widgets.disable(handButton, tr("colour.chromastudio.in_hand.none"));
        else handButton.setTooltip(Tooltip.create(tr("gradient.chromastudio.stop.in_hand.tip")));
        if (filtersUp) filterButtons(right - moreW - GAP - kindW, y, kindW, kinds, more);

        x = left;
        y += ROW;
        widgets.label(x, y + 4, stepsLabel, DIM);
        x += Widgets.tw(stepsLabel) + 4;
        widgets.spin(x, y, 38, steps, 1, ModSettings.min("gradientSteps"), ModSettings.max("gradientSteps"), true, v -> {
            if ((int) v == steps) return;
            steps = (int) v;
            solve();
        });
        x += 38 + GAP;
        int w = named ? easingW : widest(easings);
        widgets.cycler(x, y, w, named ? easingLabel : null, List.of(Easing.values()), easing, GradientTab::option, v -> { easing = v; solve(); }, tr("gradient.chromastudio.easing.tip"));
        x += w + GAP;
        w = named ? arcW : widest(arcs);
        widgets.cycler(x, y, w, named ? arcLabel : null, List.of(HueArc.values()), arc, GradientTab::option, v -> { arc = v; solve(); }, tr("gradient.chromastudio.arc.tip"));
        x += w + GAP;
        widgets.toggle(x, y, spreadW, spreadLabel, spread, v -> { spread = v; solve(); }, tr("gradient.chromastudio.spread.tip"));
        x += spreadW + GAP;
        if (!filtersUp) filterButtons(x, y, Math.min(kindW, right - x - GAP - moreW), kinds, more);

        actions = AxiomBridge.isPresent() ? ACTIONS : ACTIONS.stream().filter(a -> !a.equals("axiom")).toList();
        int[] actionWidths = actions.stream().mapToInt(a -> Widgets.buttonWidth(tr("gradient.chromastudio.action." + a))).toArray();
        actionSpots = Flow.place(actionWidths, b.x() + PAD, b.right() - PAD, GAP);
        int actionsY = b.bottom() - PAD - Widgets.H - (Flow.rows(actionSpots) - 1) * ROW;
        Component none = result.isEmpty() ? (problem != null ? problem : tr("gradient.chromastudio.working")) : null;
        action("hotbar", actionsY, () -> open(new PaletteDialogs.Hotbar(entries())), none != null ? none : HotbarFill.unavailable().orElse(null));
        action("copy", actionsY, () -> {
            Clipboard.copy(entries().stream().map(PaletteEntry::idWithProperties).collect(Collectors.joining("\n")))
                    .ifPresentOrElse(why -> say(why, BAD), () -> say(tr("gradient.chromastudio.copied", result.size()), GOOD));
        }, none);
        action("export", actionsY, () -> open(new PaletteDialogs.Export(asPalette(), Layout.STRIP, System.nanoTime())), none);
        action("axiom", actionsY, () -> open(new PaletteDialogs.Axiom(savedName != null ? savedName : asPalette().name, entries())),
                AxiomBridge.whyNot().orElse(none));
        action("saved", actionsY, () -> open(new SavedGradients(this::toSave, this::unsaveable, () -> result.stream().map(s -> s.ideal.rgb()).toList(), savedName, asPalette().name, this::load, g -> savedName = g.name())), null);
        action("save", actionsY, () -> {
            Palette p = asPalette();
            PaletteSink sink = PaletteSink.current();
            if (sink != null && sink.createPalette(p.name, p.entries)) say(tr("gradient.chromastudio.saved", p.name), GOOD);
            else say(tr("capture.chromastudio.failed"), BAD);
        }, none);

        badgeY = actionsY - GAP - 10;
        int top = y + ROW + GAP;
        stripArea = new Bounds(b.x() + PAD, top, b.w() - 2 * PAD, badgeY - GAP - top);
        Bounds body = filtersBody();
        MatchFilters filters = MatchState.filters();
        if (filtersOpen && (!body.equals(filtersFor) || filtersBuilt != filters || !filters.state().equals(filtersSeen))) buildFilters();
        if (!filtersOpen) filtersFor = null;
        if (swapStep >= 0) placeSwap();
        if (seeded || loaded || stale() || result.isEmpty() && problem == null && !solving) solve();
    }

    private void filterButtons(int x, int y, int w, List<String> kinds, Component more) {
        MatchFilters filters = MatchState.filters();
        widgets.cycler(x, y, w, null, kinds, filters.kind == null ? ALL_KINDS : filters.kind.name(), GradientTab::kindName, v -> {
            filters.kind = v.equals(ALL_KINDS) ? null : Kind.valueOf(v);
            MatchState.changed();
            solve();
        }, tr("match.chromastudio.filter.kind.tip"));
        widgets.add(new Chip(x + w + GAP, y, more, () -> filtersOpen || filters.besidesKind() > 0, () -> {
            filtersOpen = !filtersOpen;
            swapStep = -1;
            rebuild();
        })).setTooltip(Tooltip.create(tr("gradient.chromastudio.filters.tip")));
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

    private void action(String key, int y, Runnable run, Component unavailable) {
        int index = actions.indexOf(key);
        if (index < 0) return;
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
        poolLooks = chosenFrom.looks;
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
                poolLooks = looks;
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
        Set<String> materials = new HashSet<>();
        for (Gradient.Option o : Gradient.closest(wanted.ideal.oklch(), pool, poolMetric, used, pool.size())) {
            Entry e = poolById.get((String) o.candidate().key());
            List<Entry> set = BlockIndex.get().materialSet(e);
            if (!materials.add(set.isEmpty() ? e.id() : set.getFirst().id())) continue;
            options.add(new Option(e, poolLooks.get(e.id()).mean(PaletteBlocks.scale()), target.emd(o.candidate().distribution(), Metric.OKLAB)));
            if (options.size() == SWAP_OPTIONS) break;
        }
        swapOptions = options;
        swapStep = step;
        swapRows = null;
        placeSwap();
    }

    private int swapLines() { return swapBack() + 1 + Math.max(1, swapOptions.size()); }

    private int swapBack() { return swapStep < result.size() && result.get(swapStep).pinned ? 1 : 0; }

    private void placeSwap() {
        if (swapStep >= result.size()) {
            swapStep = -1;
            return;
        }
        Grid grid = stepsGrid();
        int cellX = grid.cellX(swapStep), cellRight = cellX + grid.cellW;
        int roomRight = stripArea.right() - cellRight - SWAP_GAP, roomLeft = cellX - SWAP_GAP - stripArea.x();
        boolean toRight = roomRight >= roomLeft;
        int w = Math.min(SWAP_W, Math.max(roomRight, roomLeft));
        int shown = Math.min(swapLines(), Math.max(SWAP_LINES_MIN, (stripArea.h() - 2 - SWAP_HEAD) / SWAP_ROW)), h = SWAP_HEAD + shown * SWAP_ROW + 2;
        int y = Math.max(stripArea.y(), stripArea.y() + (stripArea.h() - h) / 2);
        swapBox = new Bounds(toRight ? cellRight + SWAP_GAP : cellX - SWAP_GAP - w, y, w, h);
        Bounds inside = new Bounds(swapBox.x() + 1, swapBox.y() + SWAP_HEAD + 1, w - 2, h - SWAP_HEAD - 2);
        if (swapRows == null) swapRows = new Widgets.ScrollList(inside, SWAP_ROW, this::swapLines);
        else swapRows.area = inside;
    }

    private Grid stepsGrid() { return grid(stripArea, result.size(), false); }

    private Bounds swapClose() { return new Bounds(swapBox.right() - 12, swapBox.y() + 1, 11, 11); }

    private void swapList(GuiGraphicsExtractor g, int mouseX, int mouseY) {
        Bounds box = swapBox;
        Grid grid = stepsGrid();
        int cellX = grid.cellX(swapStep), linkY = grid.cellY(swapStep) + grid.ideal + grid.icon / 2;
        if (box.x() > cellX) g.fill(cellX + grid.cellW, linkY - 1, box.x(), linkY + 1, Theme.HIGHLIGHT);
        else g.fill(box.right(), linkY - 1, cellX, linkY + 1, Theme.HIGHLIGHT);
        g.fill(box.x(), box.y(), box.right(), box.bottom(), PANEL);
        g.outline(box.x(), box.y(), box.w(), box.h(), Theme.HIGHLIGHT);
        int back = swapBack();
        Step step = result.get(swapStep);
        Widgets.swatch(g, box.x() + 3, box.y() + 2, 10, 9, step.ideal.rgb());
        g.text(font(), fit(tr("gradient.chromastudio.swap.head", swapStep + 1, step.ideal.hex()).getString(), box.w() - 30), box.x() + 16, box.y() + 3, TEXT, false);
        Bounds close = swapClose();
        int cx = close.x() + close.w() / 2, cy = close.y() + close.h() / 2, c = close.contains(mouseX, mouseY) ? Theme.STRONG : DIM;
        for (int i = -3; i <= 2; i++) {
            g.fill(cx + i, cy + i, cx + i + 1, cy + i + 1, c);
            g.fill(cx + i, cy - 1 - i, cx + i + 1, cy - i, c);
        }
        int listW = swapRows.area.w() - (swapLines() * SWAP_ROW > swapRows.area.h() ? Widgets.BAR_W + 2 : 0);
        Component deHead = tr("gradient.chromastudio.swap.delta");
        g.text(font(), fit(tr("gradient.chromastudio.swap.closest").getString(), listW - 30), box.x() + 23, box.y() + 14, DIM, false);
        g.text(font(), deHead, box.x() + 1 + listW - 4 - font().width(deHead), box.y() + 14, DIM, false);
        g.fill(box.x() + 1, box.y() + SWAP_HEAD, box.right() - 1, box.y() + SWAP_HEAD + 1, BORDER);
        swapRows.render(g, mouseX, mouseY, (g2, i, x, y, w, h, over) -> {
            if (over && i != back) g2.fill(x, y, x + w, y + h, Theme.veil(0x20));
            if (i == back) {
                g2.fill(x, y, x + w, y + h, Theme.veil(0x10));
                swapRow(g2, step.entry, step.ideal, step.mean, step.deltaE, tr("gradient.chromastudio.swap.current", step.entry.name()).getString(), DIM, x, y, w, over, mouseX, mouseY);
            } else if (i < back) {
                sign(g2, x + 7, y + 6, UNDO, Theme.HIGHLIGHT);
                g2.text(font(), fit(tr("gradient.chromastudio.swap.back").getString(), w - 26), x + 22, y + 5, Theme.HIGHLIGHT, false);
            } else if (swapOptions.isEmpty()) {
                g2.text(font(), fit(tr("gradient.chromastudio.swap.none").getString(), w - 8), x + 4, y + 5, DIM, false);
            } else {
                Option o = swapOptions.get(i - back - 1);
                swapRow(g2, o.entry, step.ideal, o.mean, o.deltaE, o.entry.name().getString(), TEXT, x, y, w, over, mouseX, mouseY);
            }
        });
    }

    private static void swapRow(GuiGraphicsExtractor g, Entry e, Color wanted, Color mean, double deltaE, String name, int colour, int x, int y, int w, boolean over, int mouseX, int mouseY) {
        icon(g, e, x + 2, y + 1, 16);
        g.fill(x + 20, y + 4, x + 38, y + 14, BORDER);
        g.fill(x + 21, y + 5, x + 29, y + 13, 0xFF000000 | wanted.rgb());
        g.fill(x + 29, y + 5, x + 37, y + 13, 0xFF000000 | mean.rgb());
        String de = String.format(Locale.ROOT, "%.3f", deltaE);
        int deX = x + w - 4 - font().width(de), room = deX - 6 - x - 42;
        g.text(font(), de, deX, y + 5, DIM, false);
        g.text(font(), fit(name, room), x + 42, y + 5, colour, false);
        if (over && font().width(name) > room) g.setTooltipForNextFrame(font(), Component.literal(name), mouseX, mouseY);
    }

    private void swapClicked(MouseButtonEvent e) {
        if (e.button() == InputConstants.MOUSE_BUTTON_LEFT && swapClose().contains(e.x(), e.y())) {
            swapStep = -1;
            return;
        }
        if (swapRows.mouseClicked(e) || e.button() != InputConstants.MOUSE_BUTTON_LEFT) return;
        int back = swapBack(), row = swapRows.rowAt(e.x(), e.y()), i = row - back - 1;
        if (row >= 0 && row < back) putBack(swapStep);
        else if (i >= 0 && i < swapOptions.size()) {
            Entry chosen = swapOptions.get(i).entry;
            boolean taken = false;
            for (int other = 0; other < result.size(); other++) taken |= other != swapStep && result.get(other).entry.id().equals(chosen.id());
            for (Map.Entry<Integer, Entry> pin : PINS.entrySet()) taken |= pin.getKey() != swapStep && pin.getValue().id().equals(chosen.id());
            if (taken) {
                swapStep = -1;
                return;
            }
            say(tr("gradient.chromastudio.swap.done", swapStep + 1, chosen.name()), GOOD);
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
        if (filtersOpen || solving || result.isEmpty()) return -1;
        Grid grid = stepsGrid();
        for (int i = 0; i < result.size(); i++) {
            int x = grid.cellX(i), y = grid.cellY(i);
            if (mx >= x && mx < x + grid.cellW && my >= y && my < y + grid.body()) return i;
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

    private static void icon(GuiGraphicsExtractor g, Entry e, int x, int y, int size) {
        ItemStack stack = new ItemStack(e.block());
        if (stack.isEmpty()) {
            int inset = size / 8;
            Thumbnails.draw(g, e, PaletteBlocks.face(), PaletteBlocks.scale(), PaletteBlocks.tint(), x + inset, y + inset, size - 2 * inset, 1);
        } else if (size == 16) Widgets.blockIcon(g, stack, x, y);
        else {
            g.pose().pushMatrix();
            g.pose().translate(x, y);
            g.pose().scale(size / 16f);
            Widgets.blockIcon(g, stack, 0, 0);
            g.pose().popMatrix();
        }
    }

    private record Grid(int perRow, int rows, int cellW, int x, int y, int ideal, int icon, int block) {
        int body() { return ideal + icon + block; }

        int cellH() { return body() + CELL_GAP; }

        int cellX(int i) { return x + i % perRow * cellW; }

        int cellY(int i) { return y + i / perRow * cellH(); }
    }

    private Grid grid(Bounds area, int count, boolean compact) {
        int perRow = Math.max(1, Math.min(count, area.w() / CELL_MIN));
        int rows = (count + perRow - 1) / perRow;
        perRow = (count + rows - 1) / rows;
        int cellW = Math.min(CELL_MAX, area.w() / perRow);
        int x = area.x() + (area.w() - perRow * cellW) / 2;
        if (compact) return new Grid(perRow, rows, cellW, x, area.y() + Math.max(0, (area.h() - rows * (IDEAL_H + 16 + BLOCK_H + CELL_GAP)) / 2), IDEAL_H, 16, BLOCK_H);
        int room = (area.h() - 3 * LEGEND) / rows - CELL_GAP;
        int icon = cellW >= 36 && room >= 32 + 2 * BAR_MAX ? 32 : cellW >= 26 && room >= 24 + 2 * IDEAL_H ? 24 : 16;
        int bar = Math.clamp((room - icon) / 2, BLOCK_H, BAR_MAX);
        int cellH = 2 * bar + icon + CELL_GAP;
        return new Grid(perRow, rows, cellW, x, area.y() + 2 * LEGEND + Math.max(0, (area.h() - 3 * LEGEND - rows * cellH) / 2), bar, icon, bar);
    }

    private void strip(GuiGraphicsExtractor g, Grid grid, float opacity, boolean controls, int mouseX, int mouseY) {
        int weakest = 0;
        for (int i = 1; i < result.size(); i++) if (result.get(i).deltaE > result.get(weakest).deltaE) weakest = i;
        for (int i = 0; i < result.size(); i++) {
            Step s = result.get(i);
            int x = grid.cellX(i), y = grid.cellY(i), iy = y + grid.ideal, by = iy + grid.icon;
            g.fill(x, y, x + grid.cellW, iy, ARGB.multiplyAlpha(0xFF000000 | s.ideal.rgb(), opacity));
            icon(g, s.entry, x + (grid.cellW - grid.icon) / 2, iy, grid.icon);
            g.fill(x, by, x + grid.cellW, by + grid.block, ARGB.multiplyAlpha(0xFF000000 | s.mean.rgb(), opacity));
            boolean weak = i == weakest && s.deltaE > GOOD_UP_TO || s.deltaE > FAIR_UP_TO;
            if (weak && controls) g.fill(x + 1, by + grid.block + 1, x + grid.cellW - 1, by + grid.block + 3, ARGB.multiplyAlpha(rating(s.deltaE), opacity));
            Bounds cell = new Bounds(x, y, grid.cellW, grid.body());
            boolean over = cell.contains(mouseX, mouseY);
            boolean onBack = controls && s.pinned && over && overBack(x, y, grid.cellW, mouseX, mouseY);
            if (s.pinned) {
                int ix = x + (grid.cellW - grid.icon) / 2, corner = iy + grid.icon;
                g.fill(ix, corner - 5, ix + 5, corner, ARGB.multiplyAlpha(0xFF10140F, opacity));
                g.fill(ix, corner - 4, ix + 4, corner, ARGB.multiplyAlpha(0xFFFFFFFF, opacity));
                if (controls) {
                    g.fill(x + grid.cellW - BACK, y, x + grid.cellW, y + BACK, onBack ? Theme.LIT_HOVER : Theme.CHIP);
                    sign(g, x + grid.cellW - BACK + 1, y + 1, UNDO, TEXT);
                }
            }
            if (controls && i == swapStep) g.outline(x, y, grid.cellW, grid.body(), Theme.HIGHLIGHT);
            else if (controls && over && swapStep < 0) g.outline(x, y, grid.cellW, grid.body(), Theme.veil(0x60));
            if (onBack) details(cell, new Detail(tr("gradient.chromastudio.swap.back"), TEXT));
            else if (over) {
                List<Detail> parts = new ArrayList<>(List.of(new Detail(s.entry.name(), TEXT),
                        new Detail(tr("gradient.chromastudio.step.tip", i + 1, s.ideal.hex(), String.format(Locale.ROOT, "%.3f", s.deltaE)), DIM)));
                if (weak) parts.add(new Detail(tr(i == weakest ? "gradient.chromastudio.legend.weakest" : "gradient.chromastudio.badge.poor"), rating(s.deltaE)));
                if (s.pinned) parts.add(new Detail(tr("gradient.chromastudio.step.pinned"), TEXT));
                details(cell, parts.toArray(Detail[]::new));
            }
        }
    }

    private record Detail(Component text, int colour) {}

    private void details(Bounds anchor, Detail... parts) {
        details = List.of(parts);
        detailsFor = anchor;
    }

    private void detailsAsTip(GuiGraphicsExtractor g, int mouseX, int mouseY) {
        if (details.isEmpty()) return;
        List<Component> lines = new ArrayList<>(List.of(details.getFirst().text));
        for (Detail d : details.subList(1, details.size())) lines.add(d.text.copy().withColor(Theme.TIP_DIM));
        g.setTooltipForNextFrame(font(), lines.stream().map(Component::getVisualOrderText).toList(),
                new BelowOrAboveWidgetTooltipPositioner(new ScreenRectangle(detailsFor.x(), detailsFor.y(), detailsFor.w(), detailsFor.h())), mouseX, mouseY, false);
    }

    private static int rating(double deltaE) { return deltaE <= GOOD_UP_TO ? GOOD : deltaE <= FAIR_UP_TO ? FAIR : BAD; }

    private int badgeColour() { return rating(meanDeltaE); }

    private Component badge() {
        String quality = tr(meanDeltaE <= GOOD_UP_TO ? "gradient.chromastudio.badge.good" : meanDeltaE <= FAIR_UP_TO ? "gradient.chromastudio.badge.fair" : "gradient.chromastudio.badge.poor").getString();
        return tr("gradient.chromastudio.badge", String.format(Locale.ROOT, "%.3f", meanDeltaE), quality);
    }

    @Override
    protected void draw(GuiGraphicsExtractor g, Bounds b, int mouseX, int mouseY, float delta) {
        details = List.of();
        for (int i = 0; i < STOPS.size(); i++) {
            int x = stopsArea.x() + i * (CHIP + 1), y = stopsArea.y();
            Object stop = STOPS.get(i);
            g.fill(x, y, x + CHIP, y + Widgets.H, i == selectedStop ? Theme.HIGHLIGHT : BORDER);
            g.fill(x + 1, y + 1, x + CHIP - 1, y + Widgets.H - 1, 0xFF000000 | colourOf(stop).rgb());
            if (stop instanceof Entry e) icon(g, e, x + 1, y, 16);
            if (!draggingStop && mouseX >= x && mouseX < x + CHIP && mouseY >= y && mouseY < y + Widgets.H) {
                Component what = stop instanceof Entry e ? e.name() : Component.literal(colourOf(stop).hex());
                details(new Bounds(x, y, CHIP, Widgets.H), new Detail(what, TEXT), new Detail(tr(STOPS.size() > 2 ? "gradient.chromastudio.stop.tip" : "gradient.chromastudio.stop.drag"), DIM));
            }
        }

        if (draggingStop && Reorder.target(pressedStop, dropGap) != pressedStop) {
            int x = stopsArea.x() + dropGap * (CHIP + 1) - 1;
            g.fill(x - 1, stopsArea.y() - 2, x + 1, stopsArea.bottom() + 2, TEXT);
        }
        if (draggingStop && pressedStop < STOPS.size()) {
            int x = Math.clamp(mouseX - CHIP / 2, stopsArea.x(), stopsArea.right() - CHIP), y = stopsArea.y() + 3;
            g.fill(x, y, x + CHIP, y + Widgets.H, Theme.HIGHLIGHT);
            g.fill(x + 1, y + 1, x + CHIP - 1, y + Widgets.H - 1, 0xFF000000 | colourOf(STOPS.get(pressedStop)).rgb());
        }
        if (filtersOpen) {
            detailsAsTip(g, mouseX, mouseY);
            return;
        }

        g.fill(stripArea.x(), stripArea.y(), stripArea.right(), stripArea.bottom(), WELL);
        Component note = note();
        int noteRoom = stripArea.w();
        if (result.isEmpty()) {
            Component text = problem != null ? problem : tr("gradient.chromastudio.working");
            g.textWithWordWrap(font(), text, stripArea.x() + 8, stripArea.y() + 8, stripArea.w() - 16, problem != null ? FAIR : DIM, false);
        } else {
            boolean overSwap = swapStep >= 0 && swapBox.contains(mouseX, mouseY);
            Grid grid = stepsGrid();
            strip(g, grid, 1, !solving, overSwap || solving ? -1 : mouseX, overSwap || solving ? -1 : mouseY);
            if (solving) g.fill(stripArea.x(), stripArea.y(), stripArea.right(), stripArea.bottom(), ARGB.multiplyAlpha(WELL, 0.6f));
            else {
                if (legend(g, grid)) details = List.of();
                if (swapStep >= 0) swapList(g, mouseX, mouseY);
            }
            int colour = badgeColour();
            Component badge = badge();
            g.fill(stripArea.x(), badgeY + 1, stripArea.x() + 8, badgeY + 9, colour);
            g.text(font(), badge, stripArea.x() + 11, badgeY + 1, colour, false);
            if (mouseX >= stripArea.x() && mouseX < stripArea.x() + 11 + font().width(badge) && mouseY >= badgeY && mouseY < badgeY + 10) {
                g.setComponentTooltipForNextFrame(font(), List.of(tr("gradient.chromastudio.badge.tip", String.format(Locale.ROOT, "%.2f", GOOD_UP_TO), String.format(Locale.ROOT, "%.2f", FAIR_UP_TO))), mouseX, mouseY);
            }
            noteRoom = stripArea.w() - 11 - font().width(badge) - 8;
        }
        if (note != null) {
            String line = fit(note.getString(), noteRoom);
            g.text(font(), line, stripArea.right() - font().width(line), badgeY + 1, noteColour(), false);
        } else if (solving) {
            String line = tr("gradient.chromastudio.working").getString();
            g.text(font(), line, stripArea.right() - font().width(line), badgeY + 1, DIM, false);
        }
        detailsAsTip(g, mouseX, mouseY);
    }

    private boolean legend(GuiGraphicsExtractor g, Grid grid) {
        int top = grid.y - LEGEND + 1, line = Math.max(stripArea.y() + 4, top - 2 * LEGEND), bottom = grid.cellY(result.size() - 1) + grid.body() + 4;
        if (top - LEGEND < stripArea.y() + 1 || bottom + 9 > stripArea.bottom()) return false;
        int left = stripArea.x() + 2, right = stripArea.right() - 2;
        g.text(font(), fit(tr("gradient.chromastudio.legend.wanted").getString(), right - left), left, top, DIM, false);
        String key = tr("gradient.chromastudio.legend.weakest").getString();
        int keyW = 12 + font().width(key);
        g.text(font(), fit(tr("gradient.chromastudio.legend.blocks").getString(), right - left - keyW - 8), left, bottom, DIM, false);
        double weakest = result.stream().mapToDouble(Step::deltaE).max().orElseThrow();
        if (weakest > GOOD_UP_TO && swapStep < 0) {
            g.fill(right - keyW, bottom + 3, right - keyW + 9, bottom + 5, rating(weakest));
            g.text(font(), key, right - font().width(key), bottom, DIM, false);
        }
        List<Detail> parts = details.isEmpty() && swapStep < 0 ? List.of(new Detail(tr("gradient.chromastudio.step.click"), DIM)) : details;
        int x = left;
        for (Detail d : parts) {
            String text = fit(d.text.getString(), right - x);
            g.text(font(), text, x, line, d.colour, false);
            x += font().width(text) + 8;
            if (x >= right) break;
        }
        return true;
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
        strip(g, grid(new Bounds(b.x() + PAD, b.y() + PAD, b.w() - 2 * PAD, b.h() - 2 * PAD - 12), result.size(), true), opacity, false, -1, -1);
        String quality = String.format(Locale.ROOT, "%.3f", meanDeltaE);
        g.text(font(), tr("gradient.chromastudio.badge.short", quality), b.x() + PAD, b.bottom() - PAD - 9, ARGB.multiplyAlpha(badgeColour(), opacity), false);
    }

    @Override
    protected boolean clicked(MouseButtonEvent e, boolean doubled) {
        dropStop();
        boolean left = e.button() == InputConstants.MOUSE_BUTTON_LEFT;
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
            Grid grid = stepsGrid();
            boolean back = overBack(grid.cellX(step), grid.cellY(step), grid.cellW, e.x(), e.y());
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
        if (pressedStop < 0 || e.button() != InputConstants.MOUSE_BUTTON_LEFT) return swapStep >= 0 && swapRows.mouseReleased() || filtersOpen && filterWidgets.mouseReleased(e);
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
