package dev.chromastudio.client.ui.tabs;

import com.mojang.blaze3d.platform.InputConstants;
import dev.chromastudio.client.ChromaStudioClient;
import dev.chromastudio.client.ModSettings;
import dev.chromastudio.client.PaletteSink;
import dev.chromastudio.client.action.HotbarFill;
import dev.chromastudio.client.action.PaletteBlocks;
import dev.chromastudio.client.index.BlockIndex;
import dev.chromastudio.client.index.Entry;
import dev.chromastudio.client.index.Kind;
import dev.chromastudio.client.ui.Bounds;
import dev.chromastudio.client.ui.Handoff;
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
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
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
import net.minecraft.world.level.block.Blocks;

/**
 * The Gradient tab: a row of stops, each a colour or a block; the steps, easing, hue arc, spread and kind controls; a
 * strip of the ideal colours over the blocks chosen for them; a quality badge; and what can be done with the result.
 * The blocks are chosen on a worker thread, and only the newest request's answer is kept. They come from the blocks
 * that pass the Match tab's filters: the kind control here is that tab's own, and "More…" opens the rest of them.
 */
public final class GradientTab extends PanelTab {
    private static final int MAX_STOPS = 8, CHIP = 18, CELL_MIN = 18, CELL_MAX = 40, IDEAL_H = 10, BLOCK_H = 7, CELL_H = IDEAL_H + 16 + BLOCK_H + 4;
    /** The bands of the quality badge, as the mean ΔE in OKLab. */
    private static final double GOOD_UP_TO = 0.02, FAIR_UP_TO = 0.06;
    private static final String ALL_KINDS = "all";

    /** The stops, each a {@link Color} or an {@link Entry}. They and the controls last the session, across windows. */
    private static final List<Object> STOPS = new ArrayList<>(List.of(Color.fromHex("#1F2A44"), Color.fromHex("#E9D8A6")));
    private static int selectedStop;
    private static int steps = ModSettings.gradientSteps;
    private static Easing easing = named(Easing.class, ModSettings.easing, Easing.SMOOTH);
    private static HueArc arc = named(HueArc.class, ModSettings.hueArc, HueArc.SHORTEST);
    private static boolean spread = ModSettings.spreadMatching;
    private static String hex = "";

    private static final ExecutorService WORKER = Executors.newSingleThreadExecutor(task -> {
        Thread t = new Thread(task, "Chroma Studio gradient");
        t.setDaemon(true);
        return t;
    });
    /** Each solve takes the next number, and its answer is dropped when a later one has been asked for. */
    private static final AtomicInteger ASKED = new AtomicInteger();

    /** One step of the answer: the colour wanted, the block chosen and how far apart they are, as ΔE in OKLab. */
    private record Step(Color ideal, Entry entry, Color mean, double deltaE) {}

    private List<Step> result = List.of();
    private double meanDeltaE;
    /** Why there's no answer, or null while there is one or one is on its way. */
    private Component problem;
    private boolean solving;
    /** The index build the last solve read, by {@link #build}. */
    private Object solvedBuild;
    /** The {@link #inputs} the last solve was asked with. */
    private String solvedFor = "";
    private Bounds stopsArea, stripArea;
    private int badgeY;
    /** Where the count of the filters other than the kind is written, beside "More…". */
    private Bounds filterNote;
    /** The filters behind "More…", which lie over the strip while they are open. */
    private boolean filtersOpen;
    private final Widgets filterWidgets = new Widgets();
    /** The area the filters' controls were last laid out for. They are only laid out again for another, so a number being typed isn't lost. */
    private Bounds filtersFor;

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

    // ---- Layout ----

    @Override
    protected void layout(Bounds b) {
        // The settings screen may have changed a filter's setting. The kind control is built from the filters, so those catch up first.
        MatchState.follow();
        // A block sent over from a Match result becomes a stop, once.
        boolean seeded = Handoff.gradientSeed instanceof Entry;
        if (Handoff.gradientSeed instanceof Entry seed) addStop(seed);
        Handoff.gradientSeed = null;
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
        else addButton.setTooltip(Tooltip.create(tr("gradient.chromastudio.stop.add.tip")));
        x += addW + GAP;
        Entry held = inHand();
        Button handButton = widgets.button(x, y, hand, () -> {
            addStop(inHand());
            changed();
        });
        if (full != null) Widgets.disable(handButton, full);
        else if (held == null) Widgets.disable(handButton, tr("palettes.chromastudio.generate.in_hand.none"));
        else handButton.setTooltip(Tooltip.create(tr("gradient.chromastudio.stop.in_hand.tip")));

        x = b.x() + PAD;
        y += ROW;
        Component stepsLabel = tr("gradient.chromastudio.steps");
        widgets.label(x, y + 4, stepsLabel, DIM);
        x += Widgets.tw(stepsLabel) + 4;
        widgets.spin(x, y, 38, steps, 1, ModSettings.min("gradientSteps"), ModSettings.max("gradientSteps"), true, v -> {
            if ((int) v == steps) return;
            steps = (int) v;
            solve();
        }).setTooltip(Tooltip.create(tr("gradient.chromastudio.steps.tip")));
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
        // The Match tab's kind filter and its "More…", on the same filters.
        MatchFilters filters = MatchState.filters();
        Component more = tr("match.chromastudio.filter.more");
        List<String> kinds = new ArrayList<>(List.of(ALL_KINDS));
        for (Kind k : Kind.values()) kinds.add(k.name());
        w = Math.min(widest(kinds.stream().map(GradientTab::kindName).toList()), b.right() - PAD - x - GAP - Chip.width(more));
        widgets.cycler(x, y, w, null, kinds, filters.kind == null ? ALL_KINDS : filters.kind.name(), GradientTab::kindName, v -> {
            filters.kind = v.equals(ALL_KINDS) ? null : Kind.valueOf(v);
            MatchState.changed();
            solve();
        }, tr("match.chromastudio.filter.kind.tip"));
        x += w + GAP;
        widgets.add(new Chip(x, y, more, () -> filtersOpen || filters.besidesKind() > 0, () -> {
            filtersOpen = !filtersOpen;
            rebuild();
        })).setTooltip(Tooltip.create(tr("gradient.chromastudio.filters.tip")));
        x += Chip.width(more) + GAP + 2;
        filterNote = new Bounds(x, y, Math.max(0, b.right() - PAD - x), Widgets.H);

        // The actions sit along the bottom, and the strip and its badge take what's between.
        int actionsY = b.bottom() - PAD - Widgets.H;
        Component none = result.isEmpty() ? (problem != null ? problem : tr("gradient.chromastudio.working")) : null;
        x = b.x() + PAD;
        x = action(x, actionsY, "hotbar", () -> open(new PaletteDialogs.Hotbar(entries())), none != null ? none : HotbarFill.unavailable().orElse(null));
        x = action(x, actionsY, "copy", () -> {
            Minecraft.getInstance().keyboardHandler.setClipboard(entries().stream().map(PaletteEntry::idWithProperties).collect(Collectors.joining("\n")));
            say(tr("gradient.chromastudio.copied", result.size()), GOOD);
        }, none);
        x = action(x, actionsY, "export", () -> open(new PaletteDialogs.Export(asPalette(), Layout.STRIP, System.nanoTime())), none);
        x = action(x, actionsY, "save", () -> {
            Palette p = asPalette();
            PaletteSink sink = PaletteSink.current();
            if (sink != null && sink.createPalette(p.name, p.entries)) say(tr("gradient.chromastudio.saved", p.name), GOOD);
            else say(tr("capture.chromastudio.failed"), BAD);
        }, none);
        action(x, actionsY, "swap", () -> {
            Collections.reverse(STOPS);
            selectedStop = STOPS.size() - 1 - selectedStop;
            changed();
        }, null);

        badgeY = actionsY - GAP - 10;
        int top = y + ROW + GAP;
        stripArea = new Bounds(b.x() + PAD, top, b.w() - 2 * PAD, badgeY - GAP - top);
        Bounds body = filtersBody();
        if (filtersOpen && !body.equals(filtersFor)) buildFilters();
        if (!filtersOpen) filtersFor = null;
        // A new stop, or settings changed on the settings screen, make the answer on show a stale one.
        if (seeded || stale() || result.isEmpty() && problem == null && !solving) solve();
    }

    /** The filters lie over the strip and the badge under it. */
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
        filterWidgets.clear();
        // "Reset filters" resets the kind too, which the row above shows.
        FilterControls.build(filterWidgets, filtersFor, true, () -> {
            filtersFor = null;
            rebuild();
        });
    }

    /**
     * Something only the index's current build holds, or null before the first. A later build holds another, so this
     * tells a rebuild that finished while the tab wasn't looking, pinned or behind another tab.
     */
    private static Object build() {
        List<Entry> stone = BlockIndex.get().statesOf(Blocks.STONE);
        return stone.isEmpty() ? null : stone.getFirst();
    }

    /** Whether the index or a setting the answer hangs on has changed since the last solve. */
    private boolean stale() { return build() != solvedBuild || !inputs().equals(solvedFor); }

    /** Everything outside the tab that the answer depends on, to tell when it has changed. */
    private static String inputs() {
        return String.join("|", ModSettings.defaultFace.name(), ModSettings.metric, String.valueOf(ModSettings.viewingDistance), ModSettings.biomeMode.name(),
                ModSettings.biome, MatchState.filters().state().toString(), String.valueOf(ModSettings.statesSeparately), PaletteBlocks.scale().name());
    }

    private static int widest(List<Component> labels) { return labels.stream().mapToInt(Widgets::tw).max().orElse(40) + 10; }

    private int action(int x, int y, String key, Runnable run, Component unavailable) {
        Component label = tr("gradient.chromastudio.action." + key);
        Button button = widgets.button(x, y, label, run);
        if (unavailable != null) Widgets.disable(button, unavailable);
        else button.setTooltip(Tooltip.create(tr("gradient.chromastudio.action." + key + ".tip")));
        return x + Widgets.buttonWidth(label) + GAP;
    }

    /** The block the player holds, as the index has it, or null. */
    private static Entry inHand() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return null;
        ItemStack held = mc.player.getMainHandItem();
        return held.getItem() instanceof BlockItem item ? PaletteBlocks.entry(item.getBlock().defaultBlockState()) : null;
    }

    /** Puts a stop after the selected one and selects it. Past the most stops there's room for, it does nothing. */
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

    // ---- Solving ----

    /**
     * Asks for the blocks again. The colours and the candidates are read here, on the client thread, since a tint
     * may need the world; the assignment itself runs on the worker.
     */
    private void solve() {
        int number = ASKED.incrementAndGet();
        solvedFor = inputs();
        solvedBuild = build();
        if (!BlockIndex.get().ready()) {
            fail(tr("palettes.chromastudio.index_building"));
            return;
        }
        Scale scale = PaletteBlocks.scale();
        Metric metric = PaletteBlocks.metric();
        List<Oklch> stops = STOPS.stream().map(s -> colourOf(s).oklch()).toList();
        List<Oklch> ideal = Ramp.of(stops, steps, easing, arc);
        List<Gradient.Candidate> candidates = new ArrayList<>();
        Map<String, Entry> byId = new HashMap<>();
        Map<String, TextureStats> looks = new HashMap<>();
        // One block fills at most one step: the filters give each block that passes them once.
        for (Entry e : MatchState.filters().candidates(BlockIndex.get(), PaletteBlocks.face(), scale, PaletteBlocks.tint())) {
            byId.put(e.id(), e);
            TextureStats stats = PaletteBlocks.stats(e);
            looks.put(e.id(), stats);
            candidates.add(new Gradient.Candidate(e.id(), stats.distribution(scale), stats.spread()));
        }
        if (candidates.size() < steps) {
            fail(tr("gradient.chromastudio.too_few", candidates.size(), steps));
            return;
        }
        boolean matchSpread = spread;
        solving = true;
        problem = null;
        WORKER.execute(() -> {
            List<Step> found = new ArrayList<>();
            double mean = 0;
            try {
                Gradient.Result solved = Gradient.solve(ideal, candidates, metric, matchSpread);
                // The blocks are chosen by the settings' metric, but the badge's bands are OKLab's, so it is measured in that.
                for (int i = 0; i < solved.steps().size(); i++) {
                    Gradient.Candidate c = solved.steps().get(i);
                    Color wanted = Color.ofOklch(solved.ideal().get(i));
                    double deltaE = Distribution.of(wanted).emd(c.distribution(), Metric.OKLAB);
                    mean += deltaE / solved.steps().size();
                    found.add(new Step(wanted, byId.get((String) c.key()), looks.get((String) c.key()).mean(scale), deltaE));
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
                result = List.copyOf(found);
                meanDeltaE = meanOklab;
                rebuild();
            });
        });
    }

    private void fail(Component why) {
        solving = false;
        result = List.of();
        problem = why;
        rebuild();
    }

    // ---- Drawing ----

    private record Grid(int perRow, int rows, int cellW, int x, int y) {}

    /** How the steps sit in the strip: in one row while each has room for an icon, else in as many rows as that takes. */
    private Grid grid(Bounds area, int count) {
        int perRow = Math.max(1, Math.min(count, area.w() / CELL_MIN));
        int rows = (count + perRow - 1) / perRow;
        perRow = (count + rows - 1) / rows;
        int cellW = Math.min(CELL_MAX, area.w() / perRow);
        return new Grid(perRow, rows, cellW, area.x() + (area.w() - perRow * cellW) / 2, area.y() + Math.max(0, (area.h() - rows * CELL_H) / 2));
    }

    private void strip(GuiGraphicsExtractor g, Bounds area, float opacity, int mouseX, int mouseY) {
        Grid grid = grid(area, result.size());
        for (int i = 0; i < result.size(); i++) {
            Step s = result.get(i);
            int x = grid.x + i % grid.perRow * grid.cellW, y = grid.y + i / grid.perRow * CELL_H;
            g.fill(x, y, x + grid.cellW, y + IDEAL_H, ARGB.multiplyAlpha(0xFF000000 | s.ideal.rgb(), opacity));
            Widgets.blockIcon(g, s.entry.block(), x + (grid.cellW - 16) / 2, y + IDEAL_H);
            g.fill(x, y + IDEAL_H + 16, x + grid.cellW, y + IDEAL_H + 16 + BLOCK_H, ARGB.multiplyAlpha(0xFF000000 | s.mean.rgb(), opacity));
            if (mouseX >= x && mouseX < x + grid.cellW && mouseY >= y && mouseY < y + CELL_H - 4) {
                g.setComponentTooltipForNextFrame(font(), List.of(s.entry.name(), tr("gradient.chromastudio.step.tip", i + 1, s.ideal.hex(), String.format(Locale.ROOT, "%.3f", s.deltaE))
                        .copy().withColor(DIM & 0xFFFFFF)), mouseX, mouseY);
            }
        }
    }

    private int badgeColour() { return meanDeltaE <= GOOD_UP_TO ? GOOD : meanDeltaE <= FAIR_UP_TO ? FAIR : BAD; }

    @Override
    protected void draw(GuiGraphicsExtractor g, Bounds b, int mouseX, int mouseY, float delta) {
        for (int i = 0; i < STOPS.size(); i++) {
            int x = stopsArea.x() + i * (CHIP + 1), y = stopsArea.y();
            Object stop = STOPS.get(i);
            g.fill(x, y, x + CHIP, y + Widgets.H, i == selectedStop ? 0xFFFFB84D : BORDER);
            g.fill(x + 1, y + 1, x + CHIP - 1, y + Widgets.H - 1, 0xFF000000 | colourOf(stop).rgb());
            if (stop instanceof Entry e) Widgets.blockIcon(g, e.block(), x + 1, y);
            if (mouseX >= x && mouseX < x + CHIP && mouseY >= y && mouseY < y + Widgets.H) {
                Component what = stop instanceof Entry e ? e.name() : Component.literal(colourOf(stop).hex());
                g.setComponentTooltipForNextFrame(font(), List.of(what, tr("gradient.chromastudio.stop.tip").copy().withColor(DIM & 0xFFFFFF)), mouseX, mouseY);
            }
        }

        int others = MatchState.filters().besidesKind();
        Component count = tr(others == 0 ? "gradient.chromastudio.filters.none" : others == 1 ? "gradient.chromastudio.filters.one" : "gradient.chromastudio.filters.many", others);
        String counted = fit(count.getString(), filterNote.w());
        g.text(font(), counted, filterNote.x(), filterNote.y() + 4, others == 0 ? DIM : FAIR, false);
        if (filterNote.contains(mouseX, mouseY) && mouseX < filterNote.x() + font().width(counted)) {
            g.setComponentTooltipForNextFrame(font(), List.of(count, tr("gradient.chromastudio.filters.note.tip").copy().withColor(DIM & 0xFFFFFF)), mouseX, mouseY);
        }
        if (filtersOpen) return;

        g.fill(stripArea.x(), stripArea.y(), stripArea.right(), stripArea.bottom(), WELL);
        Component note = note();
        if (result.isEmpty()) {
            Component text = problem != null ? problem : tr("gradient.chromastudio.working");
            g.textWithWordWrap(font(), text, stripArea.x() + 8, stripArea.y() + 8, stripArea.w() - 16, problem != null ? FAIR : DIM, false);
        } else {
            strip(g, stripArea, 1, mouseX, mouseY);
            int colour = badgeColour();
            g.fill(stripArea.x(), badgeY + 1, stripArea.x() + 8, badgeY + 9, colour);
            String quality = tr(meanDeltaE <= GOOD_UP_TO ? "gradient.chromastudio.badge.good" : meanDeltaE <= FAIR_UP_TO ? "gradient.chromastudio.badge.fair" : "gradient.chromastudio.badge.poor").getString();
            g.text(font(), tr("gradient.chromastudio.badge", String.format(Locale.ROOT, "%.3f", meanDeltaE), quality), stripArea.x() + 11, badgeY + 1, colour, false);
        }
        if (note != null) {
            String line = fit(note.getString(), stripArea.w() / 2);
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
        g.text(font(), tr("match.chromastudio.panel.filters"), a.x() + 5, a.y() + 3, TEXT, false);
        int cx = close.x() + close.w() / 2, cy = close.y() + close.h() / 2, c = close.contains(mouseX, mouseY) ? 0xFFFFFFFF : DIM;
        for (int i = -3; i <= 2; i++) {
            g.fill(cx + i, cy + i, cx + i + 1, cy + i + 1, c);
            g.fill(cx + i, cy - 1 - i, cx + i + 1, cy - i, c);
        }
        filterWidgets.render(g, mouseX, mouseY, delta);
    }

    /** Pinned, the tab is its strip and badge. */
    @Override
    public void renderPinned(GuiGraphicsExtractor g, Bounds b, float opacity) {
        if (result.isEmpty()) {
            g.centeredText(font(), problem != null ? problem : tr("gradient.chromastudio.working"), b.x() + b.w() / 2, b.y() + b.h() / 2 - 4, ARGB.multiplyAlpha(DIM, opacity));
            return;
        }
        strip(g, new Bounds(b.x() + PAD, b.y() + PAD, b.w() - 2 * PAD, b.h() - 2 * PAD - 12), opacity, -1, -1);
        String quality = String.format(Locale.ROOT, "%.3f", meanDeltaE);
        g.text(font(), tr("gradient.chromastudio.badge.short", quality), b.x() + PAD, b.bottom() - PAD - 9, ARGB.multiplyAlpha(badgeColour(), opacity), false);
    }

    // ---- Input ----

    @Override
    protected boolean clicked(MouseButtonEvent e, boolean doubled) {
        if (filtersOpen) {
            if (e.button() == InputConstants.MOUSE_BUTTON_LEFT && filtersClose().contains(e.x(), e.y())) {
                filtersOpen = false;
                rebuild();
                return true;
            }
            // The group takes a click outside its area as the end of any typing in it.
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
        return true;
    }

    @Override
    protected boolean released(MouseButtonEvent e) { return filtersOpen && filterWidgets.mouseReleased(e); }

    @Override
    protected boolean dragged(MouseButtonEvent e, double dx, double dy) { return filtersOpen && filterWidgets.mouseDragged(e, dx, dy); }

    @Override
    protected boolean scrolled(double x, double y, double h, double v) {
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

    /** Escape closes the filters, when they are open. */
    @Override
    protected boolean dismissPanel() {
        if (!filtersOpen) return false;
        filtersOpen = false;
        rebuild();
        return true;
    }

    @Override
    public void tick() {
        super.tick();
        MatchState.follow();
        // Checked by what is so now, so nothing is missed while a dialog is open or another tab is showing.
        if (!dialogOpen() && stale()) solve();
    }
}
