package dev.chromastudio.client.ui.tabs;

import com.mojang.blaze3d.platform.InputConstants;
import dev.chromastudio.client.BiomeMode;
import dev.chromastudio.client.ChromaStudioClient;
import dev.chromastudio.client.FaceChoice;
import dev.chromastudio.client.Keys;
import dev.chromastudio.client.ModSettings;
import dev.chromastudio.client.PaletteSink;
import dev.chromastudio.client.action.Dye;
import dev.chromastudio.client.action.Eyedropper;
import dev.chromastudio.client.action.Give;
import dev.chromastudio.client.action.RecipeViewers;
import dev.chromastudio.client.action.ScreenPicker;
import dev.chromastudio.client.index.BlockIndex;
import dev.chromastudio.client.index.Entry;
import dev.chromastudio.client.index.Kind;
import dev.chromastudio.client.index.Query;
import dev.chromastudio.client.index.Target;
import dev.chromastudio.client.index.TintContext;
import dev.chromastudio.client.index.Tints;
import dev.chromastudio.client.ui.Bounds;
import dev.chromastudio.client.ui.ChromaScreen;
import dev.chromastudio.client.ui.Handoff;
import dev.chromastudio.client.ui.Tab;
import dev.chromastudio.client.ui.Widgets;
import dev.chromastudio.core.color.Badge;
import dev.chromastudio.core.color.Color;
import dev.chromastudio.core.color.Face;
import dev.chromastudio.core.color.Metric;
import dev.chromastudio.core.color.Oklch;
import dev.chromastudio.core.color.Scale;
import dev.chromastudio.core.color.Viewing;
import dev.chromastudio.core.palette.PaletteEntry;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.BooleanSupplier;
import java.util.function.Predicate;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.ARGB;
import net.minecraft.util.Util;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Block;

/**
 * The Match tab: on the left the target and the ways to set it, on the right the controls, the filters and the
 * blocks closest to the target. Everything is placed from the tab's area, so it follows the window as it is resized.
 */
public final class MatchTab extends Tab {
    private static final int PAD = 3, ROW = 18, CELL = 20, HEAD = 13, GAP = 2, LINE = Widgets.H + 1, STATUS_H = 10;
    private static final int TEXT = 0xFFE4E9EF, DIM = 0xFF9AA5B3, GREYED = 0xFF4E5560, BAD = 0xFFFF6B6B,
            PANEL = 0xFF161A20, BORDER = 0xFF3A424D, WELL = 0xFF0B0D10, BACKING = 0x80101317;
    private static final long STATUS_MS = 2500;
    private static final int RESULT = 0, MEMBER = 1, MAKE = 2;
    private static final int BODY = 0, EXPAND = 1, ADD = 2, GIVE = 3, RESTORE = 4;
    /** How many blocks the swap list offers, and the most lines of it that show at once. */
    private static final int SWAP_OPTIONS = 20, SWAP_LINES = 6;
    private static final int SWAPPED = 0xFFFFB84D;
    /** The signs on a line's small buttons, seven pixels square: add, give and put back. */
    private static final String[] PLUS = {"...#...", "...#...", "...#...", "#######", "...#...", "...#...", "...#..."},
            HAND = {"..#.#..", "..#.#.#", "#.#.#.#", "#.#####", "#######", ".#####.", "..###.."},
            UNDO = {"..#....", ".##....", "######.", ".##...#", "..#...#", "......#", "..####."};
    private static final String ALL = "all";

    /** Searches run here, one at a time, so a slow one never holds a frame up. */
    private static final ExecutorService WORKER = Executors.newSingleThreadExecutor(task -> {
        Thread t = new Thread(task, "Chroma Studio match");
        t.setDaemon(true);
        return t;
    });

    private enum Panel { NONE, FILTERS, BIOMES, FIND_BIOME, DYE }
    private static final int TINT_BADGE = 0xFF6FC45A;

    /** The matches on show, and what they were matched with, which the rows are drawn with too. */
    private record Shown(List<BlockIndex.Match> matches, Target target, Scale scale, TintContext tint, Metric metric) {}

    /**
     * One line of the list: a match, a block of an opened material set, or that set's "Make a palette".
     * {@code original} is the block the search or the set put there, which {@code entry} is too unless the player
     * swapped another in.
     */
    private record Row(int type, Entry entry, Face face, double distance, boolean hasSet, Entry original) {
        boolean swapped() { return entry != original; }
    }

    /** The slim list under a line, of the blocks that could take its place. */
    private static final class SwapList {
        final Row row;
        /** The top and bottom of the line it hangs from. */
        final int rowTop, rowBottom;
        List<BlockIndex.Match> options = List.of();
        boolean searching = true;
        Bounds box;
        Widgets.ScrollList list;

        SwapList(Row row, int rowTop, int rowBottom) {
            this.row = row;
            this.rowTop = rowTop;
            this.rowBottom = rowBottom;
        }

        /** The lines it has: the way back to the original first, when a block was swapped in, then the options. */
        int lines() { return (row.swapped() ? 1 : 0) + Math.max(1, options.size()); }
    }

    private record BiomeFit(ResourceKey<Biome> biome, Color colour, double distance) {}

    private Bounds area = new Bounds(0, 0, 0, 0), slot = area, swatch = area, recent = area, distance = area, results = area, statusLine = area;
    private final Widgets controls = new Widgets();
    private Widgets panelWidgets = new Widgets();
    private EditBox hexField, rgbField, hslField;
    private AbstractWidget findButton;
    /** Set while the fields are being filled in from the colour, so that doesn't count as typing. */
    private boolean filling;
    private final ColourPicker picker = new ColourPicker(MatchState.colour(), this::picked);

    private Widgets.ScrollList list = new Widgets.ScrollList(area, ROW, () -> 0);
    private Widgets.ScrollList panelList = new Widgets.ScrollList(area, ROW, () -> 0);
    private boolean grid;
    private Shown shown;
    private List<Row> rows = List.of();
    private int kindWidth;
    /** The entry whose material set is open under its row, or null. */
    private Entry expanded;
    private Row hovered;
    /** Where the hovered line was drawn, for the swap list to hang from. */
    private int hoveredTop;
    /** The blocks swapped in, by the block each took the place of: in the results, and in the opened material sets. */
    private final Map<Entry, BlockIndex.Match> swaps = new HashMap<>(), setSwaps = new HashMap<>();
    /** The target and index build the swaps were made for. A swap is the next best for one target, so it goes with it. */
    private int swapsRevision = -1, swapsVersion = -1;
    /** The swap list that is open, or null. */
    private SwapList swapList;
    private final Debounce search = new Debounce();
    private int seenRevision = -1, seenVersion = -1;
    private boolean draggingDistance;

    private Panel panel = Panel.NONE;
    private String biomeSearch = "";
    private List<ResourceKey<Biome>> biomes = List.of();
    private List<BiomeFit> fits = List.of();
    private List<ItemStack> dyes = List.of();
    /** Each tinted entry's tints across the biomes, for the strip "All biomes" draws. */
    private final Map<Entry, int[]> ranges = new HashMap<>();

    private Component status;
    private long statusUntil;

    public MatchTab() { super("match"); }

    private static MutableComponent tr(String key, Object... args) { return Component.translatable("match.chromastudio." + key, args); }

    private static Component lower(String prefix, Enum<?> value) {
        return Component.translatable(prefix + value.name().toLowerCase(Locale.ROOT));
    }

    // ---- Layout ----

    @Override
    public void init(Bounds b) {
        area = b;
        // The settings screen may have changed a filter's setting. The controls are built from the filters, so those catch up first.
        MatchState.follow();
        build();
        search.request();
    }

    /** Places a row of controls from the left, starting a new line when one doesn't fit. */
    private static final class Flow {
        final int left, right;
        int x, y;

        Flow(int left, int right, int y) {
            this.left = left;
            this.right = right;
            this.x = left;
            this.y = y;
        }

        /** Where a control this wide goes. Read {@link #y} afterwards. */
        int take(int w) {
            if (x > left && x + w > right) line();
            int at = x;
            x += w + GAP;
            return at;
        }

        /** The width left on this line for a control that stretches, or a whole new line when that is under {@code min}. */
        int rest(int min) {
            if (right - x < min) line();
            return right - x;
        }

        void line() {
            x = left;
            y += LINE;
        }
    }

    private void build() {
        controls.clear();
        swapList = null;
        Bounds b = area;
        Font font = Minecraft.getInstance().font;
        TargetModel model = picker.model;

        // The left column: the target.
        int lw = Math.clamp(b.w() * 36 / 100, 146, 210), x = b.x() + PAD, w = lw - 2 * PAD, y = b.y() + PAD;
        slot = new Bounds(x, y - 1, 18, 18);
        swatch = new Bounds(x + 20, y, 16, 16);
        hexField = controls.field(x + 38, y, w - 38, model.hex(), text -> typed(hexField, text, model::typeHex));
        hexField.setMaxLength(7);
        hexField.setTooltip(Tooltip.create(tr("hex.tip")));
        y += ROW;
        int half = (w - GAP) / 2;
        rgbField = controls.field(x, y, half, model.rgbText(), text -> typed(rgbField, text, model::typeRgb));
        rgbField.setMaxLength(13);
        rgbField.setTooltip(Tooltip.create(tr("rgb.tip")));
        hslField = controls.field(x + half + GAP, y, w - half - GAP, model.hslText(), text -> typed(hslField, text, model::typeHsl));
        hslField.setMaxLength(13);
        hslField.setTooltip(Tooltip.create(tr("hsl.tip")));
        y += ROW;

        int by = b.bottom() - PAD - Widgets.H, bx = x;
        Component block = tr("pick_block"), screen = tr("pick_screen"), dye = tr("dye");
        AbstractWidget blockButton = controls.button(bx, by, block, this::armEyedropper);
        blockButton.setTooltip(Tooltip.create(tr("pick_block.tip", Keys.EYEDROPPER.getTranslatedKeyMessage())));
        bx += blockButton.getWidth() + GAP;
        AbstractWidget screenButton = controls.button(bx, by, screen, () -> ScreenPicker.arm(rgb -> {
            MatchState.setColour(Color.ofRgb(rgb));
            MatchState.remember();
        }));
        screenButton.setTooltip(Tooltip.create(tr("pick_screen.tip")));
        bx += screenButton.getWidth() + GAP;
        AbstractWidget dyeButton = controls.button(bx, by, dye, () -> toggle(Panel.DYE));
        Optional<Component> noGive = Give.whyNot();
        if (noGive.isPresent()) Widgets.disable(dyeButton, noGive.get());
        else dyeButton.setTooltip(Tooltip.create(tr("dye.tip")));

        int recentY = by - 12, slidersY = recentY - 3 * (ColourPicker.SLIDER_H + 2) - 1;
        recent = new Bounds(x, recentY, w, 10);
        picker.layout(new Bounds(x, y, w, Math.max(8, slidersY - 4 - y)), new Bounds(x, slidersY, w, 3 * (ColourPicker.SLIDER_H + 2)));

        // The right column: the controls, the filters, then the results in what is left.
        Flow flow = new Flow(b.x() + lw, b.right() - PAD, b.y() + PAD);
        List<FaceChoice> faces = List.of(FaceChoice.values());
        int faceW = widest(faces.stream().map(f -> lower("option.chromastudio.facechoice.", f)).toList());
        controls.cycler(flow.take(faceW), flow.y, faceW, null, faces, ModSettings.defaultFace, f -> lower("option.chromastudio.facechoice.", f), f -> {
            ModSettings.defaultFace = f;
            MatchState.changed();
        }, tr("face.tip"));
        List<Metric> metrics = List.of(Metric.values());
        int metricW = widest(metrics.stream().map(m -> lower("option.chromastudio.metric.", m)).toList());
        controls.cycler(flow.take(metricW), flow.y, metricW, null, metrics, MatchState.metric(), m -> lower("option.chromastudio.metric.", m), m -> {
            ModSettings.metric = m.name();
            MatchState.changed();
        }, tr("metric.tip"));
        Component gridLabel = tr("grid");
        controls.add(new Chip(flow.take(Chip.width(gridLabel)), flow.y, gridLabel, () -> grid, () -> {
            grid = !grid;
            build();
        })).setTooltip(Tooltip.create(tr("grid.tip")));
        int distanceW = flow.rest(70);
        distance = new Bounds(flow.x, flow.y, distanceW, Widgets.H);

        flow.line();
        List<BiomeMode> modes = List.of(BiomeMode.values());
        int modeW = widest(modes.stream().map(m -> (Component) tr("biome." + m.name().toLowerCase(Locale.ROOT))).toList());
        controls.cycler(flow.take(modeW), flow.y, modeW, null, modes, ModSettings.biomeMode, m -> tr("biome." + m.name().toLowerCase(Locale.ROOT)), m -> {
            ModSettings.biomeMode = m;
            if (panel == Panel.BIOMES) panel = Panel.NONE;
            MatchState.changed();
            build();
        }, tr("biome.tip"));
        Component shading = tr("shading");
        controls.add(new Chip(flow.take(Chip.width(shading)), flow.y, shading, () -> ModSettings.shading, () -> {
            ModSettings.shading = !ModSettings.shading;
            MatchState.changed();
        })).setTooltip(Tooltip.create(Component.translatable("settings.chromastudio.shading.tip")));
        Component find = tr("find_biome");
        findButton = controls.button(flow.take(Widgets.buttonWidth(find)), flow.y, find, () -> toggle(Panel.FIND_BIOME));
        findState();
        int biomeW = flow.rest(60);
        String biomePath = ModSettings.biome.substring(ModSettings.biome.indexOf(':') + 1);
        AbstractWidget biomeButton = controls.button(flow.x, flow.y, biomeW, Component.literal(ellipsis(font, biomePath, biomeW - 8)), () -> toggle(Panel.BIOMES));
        if (ModSettings.biomeMode != BiomeMode.CHOSEN) Widgets.disable(biomeButton, tr("biome.pick.off"));
        else biomeButton.setTooltip(Tooltip.create(tr("biome.pick.tip", ModSettings.biome)));

        flow.line();
        MatchFilters filters = MatchState.filters();
        List<String> kinds = new ArrayList<>(List.of(ALL));
        for (Kind k : Kind.values()) kinds.add(k.name());
        int kindW = widest(kinds.stream().map(MatchTab::kindName).toList());
        controls.cycler(flow.take(kindW), flow.y, kindW, null, kinds, filters.kind == null ? ALL : filters.kind.name(), MatchTab::kindName, k -> {
            filters.kind = k.equals(ALL) ? null : Kind.valueOf(k);
            MatchState.changed();
        }, tr("filter.kind.tip"));
        chip(flow, "filter.cube", () -> filters.fullCube, () -> filters.fullCube = !filters.fullCube);
        chip(flow, "filter.solid", () -> filters.solid, () -> filters.solid = !filters.solid);
        chip(flow, "filter.opaque", () -> filters.opaque, () -> filters.opaque = !filters.opaque);
        Component more = tr("filter.more");
        controls.add(new Chip(flow.take(Chip.width(more)), flow.y, more, () -> panel == Panel.FILTERS || filters.extras() > 0, () -> toggle(Panel.FILTERS)))
                .setTooltip(Tooltip.create(tr("filter.more.tip")));

        int top = flow.y + LINE + 1;
        // The status line has the strip under the results to itself, so what it says never lies over a row.
        statusLine = new Bounds(flow.left, b.bottom() - PAD - STATUS_H, flow.right - flow.left, STATUS_H);
        results = new Bounds(flow.left, top, flow.right - flow.left, Math.max(ROW, statusLine.y() - 1 - top));
        list = grid ? new Widgets.ScrollList(results, CELL, () -> shown == null ? 0 : (shown.matches.size() + columns() - 1) / columns())
                : new Widgets.ScrollList(results, ROW, () -> rows.size());
        buildPanel();
    }

    private void chip(Flow flow, String key, BooleanSupplier on, Runnable flip) {
        Component label = tr(key);
        controls.add(new Chip(flow.take(Chip.width(label)), flow.y, label, on, () -> {
            flip.run();
            MatchState.changed();
        })).setTooltip(Tooltip.create(tr(key + ".tip")));
    }

    private static int widest(List<Component> labels) {
        int w = 0;
        for (Component label : labels) w = Math.max(w, Widgets.tw(label));
        return w + 10;
    }

    private static Component kindName(String kind) { return Component.translatable("kind.chromastudio." + kind.toLowerCase(Locale.ROOT)); }

    static String ellipsis(Font font, String text, int width) {
        if (font.width(text) <= width) return text;
        return font.plainSubstrByWidth(text, Math.max(0, width - font.width("…"))) + "…";
    }

    private int columns() { return Math.max(1, (results.w() - 5) / CELL); }

    // ---- Panels: what opens over the results ----

    private void toggle(Panel which) {
        panel = panel == which ? Panel.NONE : which;
        build();
    }

    private Bounds panelBody() { return new Bounds(results.x() + 1, results.y() + HEAD, results.w() - 2, results.h() - HEAD - 1); }

    private void buildPanel() {
        panelWidgets = new Widgets();
        Bounds body = panelBody();
        panelList = new Widgets.ScrollList(body, ROW, () -> 0);
        switch (panel) {
            case FILTERS -> FilterControls.build(panelWidgets, body, false, this::build);
            case BIOMES -> {
                biomes = Tints.biomes();
                EditBox box = panelWidgets.search(body.x() + 2, body.y() + 1, body.w() - 4, tr("biome.search"), text -> biomeSearch = text.trim().toLowerCase(Locale.ROOT));
                box.setValue(biomeSearch);
                panelWidgets.setFocused(box);
                panelList = new Widgets.ScrollList(new Bounds(body.x(), body.y() + LINE + 1, body.w(), body.h() - LINE - 1), 11, () -> matchingBiomes().size());
            }
            case FIND_BIOME -> {
                fits = whyNoFind() == null ? fits() : List.of();
                panelList = new Widgets.ScrollList(body, ROW, () -> fits.size());
            }
            case DYE -> {
                dyes = Dye.items(MatchState.colour().rgb());
                panelList = new Widgets.ScrollList(body, ROW, () -> dyes.size());
            }
            default -> { }
        }
    }

    private List<ResourceKey<Biome>> matchingBiomes() {
        if (biomeSearch.isEmpty()) return biomes;
        return biomes.stream().filter(k -> k.identifier().toString().contains(biomeSearch)).toList();
    }

    /** Greys "Find the biome" out with the reason while it can't run. It depends on the target, so it is set again whenever that changes. */
    private void findState() {
        if (findButton == null) return;
        Component why = whyNoFind();
        findButton.active = why == null;
        findButton.setTooltip(Tooltip.create(why != null ? why : tr("find_biome.tip")));
    }

    /** Why "Find the biome" can't run, or null when it can. */
    private static Component whyNoFind() {
        Entry e = MatchState.entry();
        if (e == null || !e.tinted()) return tr("find_biome.no_block");
        if (MatchState.lastFlat() == null) return tr("find_biome.no_colour");
        return null;
    }

    /** The five biomes that bring the target block's face closest to the last flat colour matched. */
    private static List<BiomeFit> fits() {
        Entry e = MatchState.entry();
        Color wanted = MatchState.lastFlat();
        Metric metric = MatchState.metric();
        List<BiomeFit> all = new ArrayList<>();
        for (ResourceKey<Biome> key : Tints.biomes()) {
            Color c = Target.of(e, MatchState.entryState(), MatchState.entryFace(), TintContext.biome(key)).colour();
            all.add(new BiomeFit(key, c, metric.distance(wanted, c)));
        }
        all.sort(Comparator.comparingDouble(BiomeFit::distance).thenComparing(f -> f.biome.identifier().toString()));
        return List.copyOf(all.subList(0, Math.min(5, all.size())));
    }

    private void chooseBiome(ResourceKey<Biome> key) {
        ModSettings.biomeMode = BiomeMode.CHOSEN;
        ModSettings.biome = key.identifier().toString();
        panel = Panel.NONE;
        MatchState.changed();
        build();
    }

    // ---- Keeping in step ----

    /** The fields follow a colour that came from anywhere but the field being typed in. */
    private void fill(EditBox except) {
        if (hexField == null) return;
        TargetModel model = picker.model;
        filling = true;
        if (hexField != except) hexField.setValue(model.hex());
        if (rgbField != except) rgbField.setValue(model.rgbText());
        if (hslField != except) hslField.setValue(model.hslText());
        for (EditBox f : List.of(hexField, rgbField, hslField)) if (f != except) f.setTextColor(EditBox.DEFAULT_TEXT_COLOR);
        filling = false;
    }

    /**
     * A field's text changed. Text that isn't a colour shows red and changes nothing. Text the tab put there itself
     * isn't read back: a colour rounded to whole numbers and taken again would drift.
     */
    private void typed(EditBox field, String text, Predicate<String> take) {
        if (filling || field == null) return;
        boolean valid = take.test(text);
        field.setTextColor(valid ? EditBox.DEFAULT_TEXT_COLOR : BAD);
        if (!valid) return;
        fill(field);
        adopt();
    }

    private void picked(boolean settled) {
        fill(null);
        adopt();
        if (settled) MatchState.remember();
    }

    /** Makes the pickers' colour the target, without them being set back from it. */
    private void adopt() {
        MatchState.setColour(picker.model.colour());
        seenRevision = MatchState.revision();
        search.request();
    }

    private void status(Component message) {
        status = message;
        statusUntil = Util.getMillis() + STATUS_MS;
    }

    /** Takes what another tab or {@code /chroma} left for this one, follows the target, and starts a search when one is due. */
    private void sync() {
        BlockIndex index = BlockIndex.get();
        MatchState.follow();
        if (Handoff.matchTarget != null || Handoff.matchFilter != null) {
            Object target = Handoff.matchTarget;
            String filter = Handoff.matchFilter;
            Handoff.matchTarget = null;
            Handoff.matchFilter = null;
            Oklch around = target instanceof Color c ? c.oklch() : target instanceof Oklch o ? o : null;
            if (filter != null && !filter.isEmpty()) {
                MatchState.resetFilters();
                MatchState.filters().preset(filter, around);
                build();
            }
            if (target instanceof Entry e) MatchState.setEntry(e, Face.ANY, null);
            else if (around != null) {
                MatchState.setColour(target instanceof Color c ? c : Color.ofOklch(around));
                MatchState.remember();
            }
        }
        Object pending = ChromaScreen.pendingTarget;
        if (pending instanceof Integer rgb) {
            ChromaScreen.pendingTarget = null;
            MatchState.setColour(Color.ofRgb(rgb));
            MatchState.remember();
        } else if (pending instanceof String id && index.ready()) {
            // A block has to wait for the index; a colour doesn't.
            ChromaScreen.pendingTarget = null;
            Identifier key = Identifier.tryParse(id);
            List<Entry> states = key == null ? List.<Entry>of() : BuiltInRegistries.BLOCK.getOptional(key).map(index::statesOf).orElse(List.of());
            if (states.isEmpty()) status(tr("not_indexed", id));
            else MatchState.setEntry(states.getFirst(), Face.ANY, null);
        } else if (pending != null && !(pending instanceof String)) ChromaScreen.pendingTarget = null;

        if (MatchState.revision() != seenRevision) {
            seenRevision = MatchState.revision();
            if (!picker.model.colour().equals(MatchState.colour())) {
                picker.model.setColour(MatchState.colour());
                fill(null);
            }
            ranges.clear();
            findState();
            search.request();
        }
        if (index.version() != seenVersion) {
            seenVersion = index.version();
            expanded = null;
            ranges.clear();
            search.request();
        }
        int run = search.poll();
        if (run >= 0) start(run);
    }

    private void start(int run) {
        BlockIndex index = BlockIndex.get();
        if (!index.ready()) return;
        if (MatchState.revision() != swapsRevision || index.version() != swapsVersion) {
            swapsRevision = MatchState.revision();
            swapsVersion = index.version();
            swaps.clear();
            setSwaps.clear();
            swapList = null;
        }
        Target target = MatchState.target();
        Query query = MatchState.query(ModSettings.resultCount);
        Minecraft mc = Minecraft.getInstance();
        WORKER.execute(() -> {
            List<BlockIndex.Match> found;
            try {
                found = index.closest(target, query);
            } catch (RuntimeException e) {
                ChromaStudioClient.LOGGER.warn("A match search failed", e);
                found = List.of();
            }
            List<BlockIndex.Match> matches = found;
            mc.execute(() -> {
                // An answer to a search that has since been asked again is out of date.
                if (!search.current(run)) return;
                shown = new Shown(matches, target, query.scale, query.tint, query.metric);
                swapList = null;
                rows();
            });
        });
    }

    /** Works the list's lines out from the matches, with the open material set under its block. */
    private void rows() {
        List<Row> out = new ArrayList<>();
        BlockIndex index = BlockIndex.get();
        if (shown != null) {
            for (BlockIndex.Match found : shown.matches) {
                BlockIndex.Match m = swaps.getOrDefault(found.entry(), found);
                List<Entry> set = index.materialSet(m.entry());
                out.add(new Row(RESULT, m.entry(), m.face(), m.distance(), set.size() > 1, found.entry()));
                if (!sameBlock(expanded, m.entry()) || grid) continue;
                for (Entry e : set) {
                    BlockIndex.Match swap = setSwaps.get(e);
                    if (swap != null) {
                        out.add(new Row(MEMBER, swap.entry(), swap.face(), swap.distance(), false, e));
                        continue;
                    }
                    Face face = e.bestFace(shown.target, shown.scale, shown.metric, shown.tint);
                    double d = shown.target.distribution(shown.scale).emd(e.face(face, shown.tint).distribution(shown.scale), shown.metric);
                    out.add(new Row(MEMBER, e, face, d, false, e));
                }
                out.add(new Row(MAKE, m.entry(), m.face(), 0, false, m.entry()));
            }
        }
        rows = out;
        Font font = Minecraft.getInstance().font;
        kindWidth = 0;
        for (Row row : out) kindWidth = Math.max(kindWidth, font.width(kindName(row.entry.kind().name())));
    }

    // ---- Drawing ----

    @Override
    public void render(GuiGraphicsExtractor g, Bounds b, int mouseX, int mouseY, float delta) {
        sync();
        Font font = Minecraft.getInstance().font;
        hovered = null;
        List<Component> tip = new ArrayList<>();
        target(g, font, 1, mouseX, mouseY, tip);
        controls.render(g, mouseX, mouseY, delta);
        distance(g, font, mouseX, mouseY, tip);
        // While the swap list is open the lines under it don't react to the cursor.
        if (panel != Panel.NONE) panel(g, font, mouseX, mouseY, delta, tip);
        else if (swapList == null) results(g, font, 1, mouseX, mouseY, tip);
        else {
            results(g, font, 1, -1, -1, tip);
            swapList(g, font, mouseX, mouseY, tip);
        }

        if (status != null && Util.getMillis() < statusUntil) {
            g.text(font, ellipsis(font, status.getString(), statusLine.w() - 4), statusLine.x() + 2, statusLine.y() + 1, TEXT, false);
        }
        if (!tip.isEmpty() && !picker.dragging() && !draggingDistance) g.setComponentTooltipForNextFrame(font, tip, mouseX, mouseY);
    }

    @Override
    public void renderPinned(GuiGraphicsExtractor g, Bounds b, float opacity) {
        sync();
        Font font = Minecraft.getInstance().font;
        List<Component> none = new ArrayList<>();
        target(g, font, opacity, -1, -1, none);
        g.text(font, picker.model.hex(), swatch.right() + 4, swatch.y() + 4, ARGB.multiplyAlpha(TEXT, opacity), false);
        results(g, font, opacity, -1, -1, none);
    }

    /** The left column but for its widgets: the slot, the swatch, the pickers and the recent colours. */
    private void target(GuiGraphicsExtractor g, Font font, float opacity, int mouseX, int mouseY, List<Component> tip) {
        Entry entry = MatchState.entry();
        // Fullscreen has no panel of its own, only the blurred world, so everything outside the results gets a backing to be read against.
        g.fill(area.x(), area.y(), results.x() - 1, area.bottom(), ARGB.multiplyAlpha(BACKING, opacity));
        g.fill(results.x() - 1, area.y(), area.right(), results.y(), ARGB.multiplyAlpha(BACKING, opacity));
        g.fill(results.x() - 1, results.bottom(), area.right(), area.bottom(), ARGB.multiplyAlpha(BACKING, opacity));
        g.fill(slot.x(), slot.y(), slot.right(), slot.bottom(), ARGB.multiplyAlpha(BORDER, opacity));
        g.fill(slot.x() + 1, slot.y() + 1, slot.right() - 1, slot.bottom() - 1, ARGB.multiplyAlpha(WELL, opacity));
        if (entry != null) Widgets.blockIcon(g, entry.block(), slot.x() + 1, slot.y() + 1);
        if (slot.contains(mouseX, mouseY)) {
            g.fill(slot.x() + 1, slot.y() + 1, slot.right() - 1, slot.bottom() - 1, 0x40FFFFFF);
            if (entry != null) tip.add(targetName());
            tip.add(tr("slot.tip").withColor((DIM) & 0xFFFFFF));
            tip.add(tr("slot.tip.scroll").withColor((DIM) & 0xFFFFFF));
        }
        g.fill(swatch.x(), swatch.y(), swatch.right(), swatch.bottom(), ARGB.multiplyAlpha(0xFF000000, opacity));
        g.fill(swatch.x() + 1, swatch.y() + 1, swatch.right() - 1, swatch.bottom() - 1, ARGB.multiplyAlpha(0xFF000000 | MatchState.colour().rgb(), opacity));
        if (swatch.contains(mouseX, mouseY)) {
            tip.add(entry == null ? tr("target.colour", MatchState.colour().hex())
                    : tr("target.block", targetName(), Eyedropper.faceName(MatchState.entryFace()), MatchState.colour().hex()));
        }
        picker.render(g, font, opacity);

        List<Integer> colours = MatchState.recent();
        int cw = recent.w() / RecentColours.KEPT;
        for (int i = 0; i < RecentColours.KEPT; i++) {
            int rx = recent.x() + i * cw;
            if (i < colours.size()) {
                g.fill(rx, recent.y(), rx + cw - 1, recent.bottom(), ARGB.multiplyAlpha(0xFF000000, opacity));
                g.fill(rx + 1, recent.y() + 1, rx + cw - 2, recent.bottom() - 1, ARGB.multiplyAlpha(0xFF000000 | colours.get(i), opacity));
                if (mouseX >= rx && mouseX < rx + cw - 1 && mouseY >= recent.y() && mouseY < recent.bottom()) {
                    tip.add(tr("recent.tip", String.format("#%06X", colours.get(i))));
                }
            } else g.outline(rx, recent.y(), cw - 1, recent.h(), ARGB.multiplyAlpha(0xFF262C34, opacity));
        }
    }

    /** The target block's name with the state that is matched, which may not be its entry's own. */
    private static Component targetName() {
        String properties = MatchState.entryProperties();
        return properties.isEmpty() ? MatchState.entry().name() : tr("name_with_state", MatchState.entry().name(), properties);
    }

    private static Component entryName(Entry e) {
        return e.properties().isEmpty() ? e.name() : tr("name_with_state", e.name(), e.properties());
    }

    // The viewing distance slider runs from 1 block to far along a logarithmic scale, as each halving of a
    // texture's detail takes twice the distance.
    private static double distanceAt(int blocks) { return Math.log(blocks) / Math.log(ModSettings.VIEW_FAR); }

    private static int blocksAt(double t) { return Math.clamp(Math.round(Math.exp(t * Math.log(ModSettings.VIEW_FAR))), 1, ModSettings.VIEW_FAR); }

    private void distance(GuiGraphicsExtractor g, Font font, int mouseX, int mouseY, List<Component> tip) {
        Bounds d = distance;
        Minecraft mc = Minecraft.getInstance();
        boolean over = d.contains(mouseX, mouseY) || draggingDistance;
        g.fill(d.x(), d.y(), d.right(), d.bottom(), BORDER);
        g.fill(d.x() + 1, d.y() + 1, d.right() - 1, d.bottom() - 1, WELL);
        int track = d.w() - 6, x0 = d.x() + 3;
        for (double mark : Viewing.blockMarks(mc.getWindow().getHeight(), mc.options.fov().get())) {
            if (mark < 1 || mark >= ModSettings.VIEW_FAR) continue;
            int mx = x0 + (int) Math.round(Math.log(mark) / Math.log(ModSettings.VIEW_FAR) * track);
            g.fill(mx, d.bottom() - 4, mx + 1, d.bottom() - 1, DIM);
        }
        int kx = x0 + (int) Math.round(distanceAt(ModSettings.viewingDistance) * track);
        g.fill(kx - 1, d.y() + 1, kx + 2, d.bottom() - 1, over ? 0xFFFFFFFF : 0xFF8EC2F2);
        boolean far = ModSettings.viewingDistance >= ModSettings.VIEW_FAR;
        Component label = far ? tr("distance.far") : ModSettings.viewingDistance == 1 ? tr("distance.block") : tr("distance.blocks", ModSettings.viewingDistance);
        // The label keeps clear of the handle: on whichever side has the room.
        int lw = font.width(label), lx = kx < d.x() + d.w() / 2 ? d.right() - lw - 4 : d.x() + 4;
        g.text(font, label, lx, d.y() + 4, TEXT, false);
        if (d.contains(mouseX, mouseY)) {
            tip.add(tr("distance.tip"));
            tip.add(tr("distance.scale", MatchState.scale().texels).withColor((DIM) & 0xFFFFFF));
        }
    }

    private void setDistance(double mx) {
        int blocks = blocksAt(Math.clamp((mx - distance.x() - 3) / Math.max(1, distance.w() - 6), 0, 1));
        if (blocks == ModSettings.viewingDistance) return;
        ModSettings.viewingDistance = blocks;
        MatchState.changed();
    }

    private void results(GuiGraphicsExtractor g, Font font, float opacity, int mouseX, int mouseY, List<Component> tip) {
        BlockIndex index = BlockIndex.get();
        Bounds r = results;
        g.fill(r.x(), r.y(), r.right(), r.bottom(), ARGB.multiplyAlpha(0x60000000, opacity));
        if (!index.ready() || shown == null) {
            Component text = index.ready() ? tr("searching") : tr("building", Math.round(index.progress() * 100));
            g.centeredText(font, text, r.x() + r.w() / 2, r.y() + r.h() / 2 - 10, ARGB.multiplyAlpha(DIM, opacity));
            if (!index.ready()) bar(g, r.x() + 20, r.y() + r.h() / 2 + 4, r.w() - 40, index.progress(), opacity);
            return;
        }
        if (shown.matches.isEmpty()) {
            g.centeredText(font, tr("no_results"), r.x() + r.w() / 2, r.y() + r.h() / 2 - 4, ARGB.multiplyAlpha(DIM, opacity));
            return;
        }
        if (grid) {
            int cols = columns();
            list.render(g, mouseX, mouseY, (g2, line, x, y, w, h, over) -> {
                for (int c = 0; c < cols && line * cols + c < shown.matches.size(); c++) {
                    Row cell = cell(line * cols + c);
                    int cx = x + 2 + c * CELL;
                    boolean on = over && mouseX >= cx && mouseX < cx + CELL;
                    Thumbnails.draw(g2, cell.entry, cell.face, shown.scale, shown.tint, cx + 2, y + 2, 16, opacity);
                    if (cell.swapped()) swapMark(g2, cx + 2, y + 2, opacity);
                    if (on) {
                        g2.outline(cx + 1, y + 1, 18, 18, 0xFFFFFFFF);
                        hovered = cell;
                        describe(hovered, BODY, tip);
                    }
                }
            });
        } else {
            list.render(g, mouseX, mouseY, (g2, i, x, y, w, h, over) -> row(g2, font, rows.get(i), x, y, w, h, over, opacity, mouseX, tip));
        }
        // A later build shows as a thin bar while the earlier one still answers.
        if (index.building()) bar(g, r.x(), r.y(), r.w(), index.progress(), opacity);
    }

    private static void bar(GuiGraphicsExtractor g, int x, int y, int w, float progress, float opacity) {
        g.fill(x, y, x + w, y + 2, ARGB.multiplyAlpha(0xFF262C34, opacity));
        g.fill(x, y, x + Math.round(w * Math.clamp(progress, 0, 1)), y + 2, ARGB.multiplyAlpha(0xFF8EC2F2, opacity));
    }

    /** A tile of the grid as a line: the match there, or the block swapped in for it. */
    private Row cell(int i) {
        BlockIndex.Match found = shown.matches.get(i), m = swaps.getOrDefault(found.entry(), found);
        return new Row(RESULT, m.entry(), m.face(), m.distance(), false, found.entry());
    }

    /**
     * Which part of a line the cursor is over: the expander at its left, the small buttons at its right (add, give
     * and, on a line with a block swapped in, put back), or the rest.
     */
    private static int zone(Row row, int x, int w, double mx) {
        if (row.type == MAKE) return BODY;
        if (row.hasSet && mx < x + 10) return EXPAND;
        if (mx >= x + w - 13) return ADD;
        if (mx >= x + w - 26) return GIVE;
        return row.swapped() && mx >= x + w - 39 ? RESTORE : BODY;
    }

    /** One of a line's small buttons, its right edge at {@code right}. */
    private static void rowButton(GuiGraphicsExtractor g, int right, int y, String[] sign, boolean lit, boolean can, float opacity) {
        g.fill(right - 11, y + 3, right, y + 14, ARGB.multiplyAlpha(lit && can ? 0xFF3F78B0 : 0xFF262C34, opacity));
        int colour = ARGB.multiplyAlpha(can ? TEXT : GREYED, opacity);
        for (int j = 0; j < sign.length; j++)
            for (int i = 0; i < sign[j].length(); i++)
                if (sign[j].charAt(i) == '#') g.fill(right - 9 + i, y + 5 + j, right - 8 + i, y + 6 + j, colour);
    }

    /** The mark of a block swapped in: an amber corner at the bottom left of its icon, as the tint's green one is at the right. */
    private static void swapMark(GuiGraphicsExtractor g, int x, int y, float opacity) {
        g.fill(x, y + 11, x + 5, y + 16, ARGB.multiplyAlpha(0xFF10140F, opacity));
        g.fill(x, y + 12, x + 4, y + 16, ARGB.multiplyAlpha(SWAPPED, opacity));
    }

    private void row(GuiGraphicsExtractor g, Font font, Row row, int x, int y, int w, int h, boolean over, float opacity, int mouseX, List<Component> tip) {
        int zone = over ? zone(row, x, w, mouseX) : -1;
        if (row.type != RESULT) g.fill(x, y, x + w, y + h, ARGB.multiplyAlpha(0x38000000, opacity));
        if (over || swapList != null && swapList.row == row) g.fill(x, y, x + w, y + h, 0x20FFFFFF);
        int text = ARGB.multiplyAlpha(TEXT, opacity), dim = ARGB.multiplyAlpha(DIM, opacity);
        if (row.type == MAKE) {
            boolean can = PaletteSink.current() != null;
            Component label = tr("make_palette", BlockIndex.get().materialSet(row.entry).size());
            g.text(font, label, x + 28, y + 5, can ? (over ? 0xFFFFFFFF : ARGB.multiplyAlpha(0xFF8EC2F2, opacity)) : ARGB.multiplyAlpha(GREYED, opacity), false);
            if (over) tip.add(can ? tr("make_palette.tip") : tr("no_palette"));
            return;
        }
        Entry e = row.entry;
        int left = x + (row.type == MEMBER ? 12 : 1);
        if (row.hasSet) triangle(g, left + 2, y + h / 2, sameBlock(expanded, e), zone == EXPAND ? 0xFFFFFFFF : dim);
        left += 9;
        ItemStack icon = new ItemStack(e.block());
        if (icon.isEmpty()) Widgets.swatch(g, left + 2, y + 3, 12, 12, e.face(row.face, shown.tint).mean(shown.scale).rgb());
        else Widgets.blockIcon(g, icon, left, y + 1);
        // The tint badge: a green corner on the icon of a block the game tints, as it does grass by its biome.
        if (e.tinted()) {
            g.fill(left + 11, y + 11, left + 16, y + 16, ARGB.multiplyAlpha(0xFF10140F, opacity));
            g.fill(left + 12, y + 12, left + 16, y + 16, ARGB.multiplyAlpha(TINT_BADGE, opacity));
        }
        if (row.swapped()) swapMark(g, left, y, opacity);
        left += 18;

        int right = x + w - 1;
        rowButton(g, right, y, PLUS, zone == ADD, PaletteSink.current() != null, opacity);
        right -= 13;
        rowButton(g, right, y, HAND, zone == GIVE, Give.whyNot().isEmpty(), opacity);
        right -= 13;
        if (row.swapped()) {
            rowButton(g, right, y, UNDO, zone == RESTORE, true, opacity);
            right -= 13;
        }
        var stats = e.face(row.face, shown.tint);
        Thumbnails.draw(g, e, row.face, shown.scale, shown.tint, right - 16, y + 1, 16, opacity);
        right -= 18;
        g.fill(right - 12, y + 3, right, y + 15, ARGB.multiplyAlpha(0xFF000000, opacity));
        g.fill(right - 11, y + 4, right - 1, y + 14, ARGB.multiplyAlpha(0xFF000000 | stats.mean(shown.scale).rgb(), opacity));
        right -= 15;
        // Every line gives the kind the width of the widest on show, so the columns to its left line up.
        right -= kindWidth;
        g.text(font, kindName(e.kind().name()), right, y + 5, dim, false);
        right -= 10;
        badge(g, right, y + 5, stats.badge(), dim);
        right -= 4;
        if (ModSettings.showDeltaE) {
            String de = deltaE(row.distance, shown.metric);
            right -= font.width(de);
            g.text(font, de, right, y + 5, text, false);
            right -= 4;
        }
        String name = e.name().getString(), note = e.properties().isEmpty() ? "" : ", " + e.properties();
        String fitted = ellipsis(font, name + note, right - left);
        String shownName = fitted.length() >= name.length() ? name : fitted;
        g.text(font, shownName, left, y + 5, text, false);
        if (fitted.length() > name.length()) g.text(font, fitted.substring(name.length()), left + font.width(name), y + 5, dim, false);

        if (ModSettings.biomeMode == BiomeMode.ALL && e.tinted()) strip(g, e, row.face, left, y + h - 3, Math.max(8, right - left), opacity);
        if (over) {
            hovered = row;
            hoveredTop = y;
            describe(row, zone, tip);
        }
    }

    private static boolean sameBlock(Entry a, Entry b) { return a != null && b != null && a.id().equals(b.id()); }

    /** A small arrowhead: down when its set is open, right when shut. */
    private static void triangle(GuiGraphicsExtractor g, int x, int cy, boolean open, int colour) {
        for (int i = 0; i < 3; i++) {
            if (open) g.fill(x + i, cy - 1 + i, x + 5 - i, cy + i, colour);
            else g.fill(x + i, cy - 2 + i, x + i + 1, cy + 3 - i, colour);
        }
    }

    /** The texture badge as a little picture: one flat square, a few specks, or a checker. */
    private static void badge(GuiGraphicsExtractor g, int x, int y, Badge badge, int colour) {
        g.outline(x, y, 7, 7, colour);
        if (badge == Badge.FLAT) return;
        for (int j = 0; j < 5; j++)
            for (int i = 0; i < 5; i++) {
                boolean on = badge == Badge.BUSY ? (i + j) % 2 == 0 : i == 1 && j == 1 || i == 3 && j == 2 || i == 1 && j == 3;
                if (on) g.fill(x + 1 + i, y + 1 + j, x + 2 + i, y + 2 + j, colour);
            }
    }

    /** The tints a block takes across every biome, darkest first, as a strip under its name. */
    private void strip(GuiGraphicsExtractor g, Entry e, Face face, int x, int y, int w, float opacity) {
        int[] tints = ranges.computeIfAbsent(e, _ -> {
            int[] all = Tints.tintsAcross(e, face).stream().filter(t -> t != TintContext.NO_TINT).mapToInt(Integer::intValue).distinct().toArray();
            return Arrays.stream(all).boxed().sorted(Comparator.comparingDouble(t -> Color.ofRgb(t).oklch().l())).mapToInt(Integer::intValue).toArray();
        });
        for (int i = 0; i < tints.length; i++) {
            g.fill(x + i * w / tints.length, y, x + (i + 1) * w / tints.length, y + 2, ARGB.multiplyAlpha(0xFF000000 | tints[i], opacity));
        }
    }

    private static String deltaE(double d, Metric metric) {
        return String.format(Locale.ROOT, metric.justNoticeable() < 1 ? "%.3f" : "%.1f", d);
    }

    /** The tooltip for the line under the cursor: what it is, then what each click does. */
    private void describe(Row row, int zone, List<Component> tip) {
        if (zone == EXPAND) {
            tip.add(tr("set.tip"));
            return;
        }
        if (zone == ADD) {
            tip.add(PaletteSink.current() != null ? tr("add.tip") : tr("no_palette"));
            return;
        }
        Optional<Component> noGive = Give.whyNot();
        if (zone == GIVE) {
            tip.add(noGive.orElseGet(() -> tr("give.tip")));
            return;
        }
        if (zone == RESTORE) {
            tip.add(tr("swap.restore.tip", row.original.name()));
            return;
        }
        Entry e = row.entry;
        facts(e, row.face, row.distance, tip);
        if (row.swapped()) tip.add(tr("row.swapped", row.original.name()).withColor(SWAPPED & 0xFFFFFF));
        // A tile of the grid has no buttons, so there a click still gives the block.
        if (!grid) tip.add(tr("row.swap").withColor((DIM) & 0xFFFFFF));
        else tip.add(noGive.isPresent() ? tr("row.give.off", noGive.get()).withColor(GREYED) : tr("row.give").withColor((DIM) & 0xFFFFFF));
        tip.add(tr("row.copy").withColor((DIM) & 0xFFFFFF));
        tip.add(tr("row.gradient").withColor((DIM) & 0xFFFFFF));
        if (grid) tip.add(tr("row.add").withColor((PaletteSink.current() != null ? DIM : GREYED) & 0xFFFFFF));
        tip.add(tr("row.recipe", recipeKeyName()).withColor((RecipeViewers.available() ? DIM : GREYED) & 0xFFFFFF));
    }

    /** What a block is and how it matched, as a tooltip's first lines. */
    private void facts(Entry e, Face face, double distance, List<Component> tip) {
        var stats = e.face(face, shown.tint);
        tip.add(entryName(e));
        tip.add(Component.literal(e.toString()).withColor((DIM) & 0xFFFFFF));
        tip.add(tr("row.facts", deltaE(distance, shown.metric), Eyedropper.faceName(face), lower("badge.chromastudio.", stats.badge()),
                kindName(e.kind().name()), stats.mean(shown.scale).hex()).withColor((DIM) & 0xFFFFFF));
        if (e.tinted()) tip.add(tr("row.tinted").withColor(TINT_BADGE & 0xFFFFFF));
    }

    // ---- The swap list ----

    /**
     * Opens the slim list under a line, and asks for what goes in it: the blocks of the line's kind that come
     * closest to the target and aren't on show already. They are searched for with the filters as they stand.
     */
    private void openSwap(Row row, int rowTop) {
        SwapList opened = new SwapList(row, rowTop, rowTop + ROW);
        swapList = opened;
        place(opened);
        Set<String> onShow = new TreeSet<>();
        for (Row other : rows) {
            if (other.type == MAKE) continue;
            onShow.add(other.entry.toString());
            onShow.add(other.original.toString());
        }
        BlockIndex index = BlockIndex.get();
        Target target = MatchState.target();
        Query query = MatchState.query(SWAP_OPTIONS + onShow.size());
        query.kinds.clear();
        query.kinds.add(row.original.kind());
        Minecraft mc = Minecraft.getInstance();
        WORKER.execute(() -> {
            List<BlockIndex.Match> options = new ArrayList<>();
            try {
                for (BlockIndex.Match m : index.closest(target, query)) {
                    if (options.size() < SWAP_OPTIONS && !onShow.contains(m.entry().toString())) options.add(m);
                }
            } catch (RuntimeException e) {
                ChromaStudioClient.LOGGER.warn("A search for blocks to swap in failed", e);
            }
            mc.execute(() -> {
                if (swapList != opened) return;
                opened.options = options;
                opened.searching = false;
                place(opened);
            });
        });
    }

    /** Sizes the swap list to its lines and hangs it under its line, or over it when there's more room there. */
    private void place(SwapList s) {
        int below = results.bottom() - s.rowBottom, above = s.rowTop - results.y();
        boolean under = below >= Math.min(s.lines(), 3) * ROW + 2 || below >= above;
        int lines = Math.clamp((under ? below : above) / ROW, 1, Math.min(SWAP_LINES, s.lines())), h = lines * ROW + 2;
        int w = Math.min(results.w() - 8, Math.max(140, results.w() * 3 / 5)), x = Math.min(results.x() + 20, results.right() - w - 4);
        s.box = new Bounds(x, under ? s.rowBottom : s.rowTop - h, w, h);
        s.list = new Widgets.ScrollList(new Bounds(x + 1, s.box.y() + 1, w - 2, h - 2), ROW, s::lines);
    }

    private void swapList(GuiGraphicsExtractor g, Font font, int mouseX, int mouseY, List<Component> tip) {
        SwapList s = swapList;
        Bounds box = s.box;
        g.fill(box.x(), box.y(), box.right(), box.bottom(), PANEL);
        g.outline(box.x(), box.y(), box.w(), box.h(), 0xFF8EC2F2);
        int back = s.row.swapped() ? 1 : 0;
        s.list.render(g, mouseX, mouseY, (g2, i, x, y, w, h, over) -> {
            if (over) g2.fill(x, y, x + w, y + h, 0x20FFFFFF);
            if (i < back) {
                Entry original = s.row.original;
                Widgets.blockIcon(g2, original.block(), x + 2, y + 1);
                g2.text(font, ellipsis(font, tr("swap.back", original.name()).getString(), w - 26), x + 22, y + 5, SWAPPED, false);
                if (over) tip.add(tr("swap.restore.tip", original.name()));
            } else if (s.options.isEmpty()) {
                g2.text(font, ellipsis(font, (s.searching ? tr("searching") : tr("swap.none")).getString(), w - 8), x + 4, y + 5, DIM, false);
            } else {
                BlockIndex.Match m = s.options.get(i - back);
                Entry e = m.entry();
                ItemStack icon = new ItemStack(e.block());
                if (icon.isEmpty()) Widgets.swatch(g2, x + 4, y + 3, 12, 12, e.face(m.face(), shown.tint).mean(shown.scale).rgb());
                else Widgets.blockIcon(g2, icon, x + 2, y + 1);
                String de = deltaE(m.distance(), shown.metric);
                g2.text(font, de, x + w - 4 - font.width(de), y + 5, DIM, false);
                String name = e.properties().isEmpty() ? e.name().getString() : e.name().getString() + ", " + e.properties();
                g2.text(font, ellipsis(font, name, w - 30 - font.width(de)), x + 22, y + 5, TEXT, false);
                if (over) {
                    facts(e, m.face(), m.distance(), tip);
                    tip.add(tr("swap.use").withColor((DIM) & 0xFFFFFF));
                }
            }
        });
    }

    /** A click inside the swap list: on a line, it puts that block in the place of the one the list hangs from. */
    private void swapClicked(MouseButtonEvent e) {
        SwapList s = swapList;
        if (s.list.mouseClicked(e) || e.button() != InputConstants.MOUSE_BUTTON_LEFT) return;
        int i = s.list.rowAt(e.x(), e.y()) - (s.row.swapped() ? 1 : 0);
        if (i == -1 && s.row.swapped()) restore(s.row);
        else if (i >= 0 && i < s.options.size()) {
            BlockIndex.Match chosen = s.options.get(i);
            (s.row.type == MEMBER ? setSwaps : swaps).put(s.row.original, chosen);
            status(tr("swap.done", s.row.entry.name(), chosen.entry().name()));
            swapList = null;
            rows();
        }
    }

    /** Puts a line's own block back where another was swapped in. */
    private void restore(Row row) {
        (row.type == MEMBER ? setSwaps : swaps).remove(row.original);
        status(tr("swap.undone", row.original.name()));
        swapList = null;
        rows();
    }

    private static Component recipeKeyName() {
        try {
            return InputConstants.getKey(ModSettings.recipeKey).getDisplayName();
        } catch (RuntimeException _) {
            return Component.literal(ModSettings.recipeKey);
        }
    }

    private void panel(GuiGraphicsExtractor g, Font font, int mouseX, int mouseY, float delta, List<Component> tip) {
        Bounds r = results;
        g.fill(r.x(), r.y(), r.right(), r.bottom(), PANEL);
        g.outline(r.x(), r.y(), r.w(), r.h(), BORDER);
        g.text(font, tr("panel." + panel.name().toLowerCase(Locale.ROOT)), r.x() + 5, r.y() + 3, TEXT, false);
        Bounds close = closeButton();
        boolean overClose = close.contains(mouseX, mouseY);
        int cx = close.x() + close.w() / 2, cy = close.y() + close.h() / 2, c = overClose ? 0xFFFFFFFF : DIM;
        for (int i = -3; i <= 2; i++) {
            g.fill(cx + i, cy + i, cx + i + 1, cy + i + 1, c);
            g.fill(cx + i, cy - 1 - i, cx + i + 1, cy - i, c);
        }
        panelWidgets.render(g, mouseX, mouseY, delta);
        switch (panel) {
            case BIOMES -> {
                List<ResourceKey<Biome>> listed = matchingBiomes();
                panelList.render(g, mouseX, mouseY, (g2, i, x, y, w, h, over) -> {
                    String id = listed.get(i).identifier().toString();
                    if (over) g2.fill(x, y, x + w, y + h, 0x20FFFFFF);
                    g2.text(font, ellipsis(font, id, w - 8), x + 4, y + 2, id.equals(ModSettings.biome) ? 0xFF8EC2F2 : TEXT, false);
                });
                if (listed.isEmpty()) g.centeredText(font, tr("biome.none"), r.x() + r.w() / 2, r.y() + r.h() / 2, DIM);
            }
            case FIND_BIOME -> {
                panelList.render(g, mouseX, mouseY, (g2, i, x, y, w, h, over) -> {
                    BiomeFit fit = fits.get(i);
                    if (over) g2.fill(x, y, x + w, y + h, 0x20FFFFFF);
                    Widgets.swatch(g2, x + 4, y + 3, 12, 12, fit.colour.rgb());
                    String de = deltaE(fit.distance, MatchState.metric());
                    g2.text(font, de, x + w - 4 - font.width(de), y + 5, DIM, false);
                    g2.text(font, ellipsis(font, fit.biome.identifier().toString(), w - 32 - font.width(de)), x + 20, y + 5, TEXT, false);
                    if (over) tip.add(tr("find_biome.use"));
                });
                if (fits.isEmpty()) {
                    Component why = whyNoFind();
                    g.centeredText(font, why != null ? why : tr("biome.none"), r.x() + r.w() / 2, r.y() + r.h() / 2, DIM);
                }
            }
            case DYE -> {
                Optional<Component> noGive = Give.whyNot();
                panelList.render(g, mouseX, mouseY, (g2, i, x, y, w, h, over) -> {
                    ItemStack stack = dyes.get(i);
                    if (over) g2.fill(x, y, x + w, y + h, 0x20FFFFFF);
                    g2.fakeItem(stack, x + 3, y + 1);
                    g2.text(font, ellipsis(font, stack.getHoverName().getString(), w - 28), x + 24, y + 5, noGive.isPresent() ? GREYED : TEXT, false);
                    if (over) tip.add(noGive.isPresent() ? noGive.get() : tr("dye.give", MatchState.colour().hex()));
                });
            }
            default -> { }
        }
    }

    private Bounds closeButton() { return new Bounds(results.right() - HEAD - 1, results.y() + 1, HEAD, HEAD - 1); }

    // ---- Input ----

    @Override
    public boolean mouseClicked(MouseButtonEvent e, boolean doubled) {
        double mx = e.x(), my = e.y();
        boolean left = e.button() == InputConstants.MOUSE_BUTTON_LEFT;
        if (panel != Panel.NONE && results.contains(mx, my)) {
            controls.setFocused(null);
            return panelClicked(e, doubled, left);
        }
        panelWidgets.setFocused(null);
        // A click outside the open swap list shuts it and then does what it would have done, but for opening the list again.
        Row shut = null;
        if (swapList != null) {
            if (swapList.box.contains(mx, my)) {
                swapClicked(e);
                return true;
            }
            shut = swapList.row;
            swapList = null;
        }
        if (controls.mouseClicked(e, doubled)) return true;
        if (left && picker.mouseClicked(mx, my)) return true;
        if (left && distance.contains(mx, my)) {
            draggingDistance = true;
            setDistance(mx);
            return true;
        }
        if (left && slot.contains(mx, my)) {
            takeHeld();
            return true;
        }
        if (left && recent.contains(mx, my)) {
            List<Integer> colours = MatchState.recent();
            int i = (int) ((mx - recent.x()) / (recent.w() / RecentColours.KEPT));
            if (i >= 0 && i < colours.size()) MatchState.setColour(Color.ofRgb(colours.get(i)));
            return true;
        }
        if (!results.contains(mx, my) || shown == null) return false;
        if (list.mouseClicked(e)) return true;
        int line = list.rowAt(mx, my);
        if (line < 0) return false;
        if (grid) {
            int col = (int) ((mx - results.x() - 2) / CELL), i = line * columns() + col;
            if (col < 0 || col >= columns() || i >= shown.matches.size()) return false;
            act(cell(i), Minecraft.getInstance().hasControlDown() ? ADD : BODY, e);
        } else if (line < rows.size()) {
            Row row = rows.get(line);
            int w = results.w(), zone = zone(row, results.x(), w - (rows.size() * ROW > results.h() ? 5 : 0), mx);
            if (zone == BODY && row.type != MAKE && left && !e.hasShiftDown()) {
                // The line was drawn under the cursor a frame ago, which is where the list hangs from.
                if (row != shut) openSwap(row, hovered == row ? hoveredTop : (int) my - ROW / 2);
            } else act(row, zone, e);
        }
        return true;
    }

    /** Does what a click on a line does. */
    private void act(Row row, int zone, MouseButtonEvent e) {
        Entry entry = row.entry;
        if (row.type == MAKE) {
            PaletteSink sink = PaletteSink.current();
            if (sink == null) {
                status(tr("no_palette"));
                return;
            }
            // The set as it shows: with the blocks the player swapped in.
            List<PaletteEntry> entries = BlockIndex.get().materialSet(entry).stream().map(m -> setSwaps.containsKey(m) ? setSwaps.get(m).entry() : m)
                    .map(m -> new PaletteEntry(m.id(), m.properties())).toList();
            status(sink.createPalette(BlockIndex.get().materialName(entry), entries) ? tr("made_palette", entries.size()) : tr("make_palette.failed"));
            return;
        }
        if (e.button() == InputConstants.MOUSE_BUTTON_RIGHT) {
            Handoff.gradientSeed = entry;
            ChromaScreen screen = ChromaScreen.current();
            if (screen != null) screen.select("gradient");
            return;
        }
        if (e.button() != InputConstants.MOUSE_BUTTON_LEFT) return;
        if (zone == EXPAND) {
            expanded = sameBlock(expanded, entry) ? null : entry;
            rows();
        } else if (zone == ADD) {
            PaletteSink sink = PaletteSink.current();
            if (sink == null) status(tr("no_palette"));
            else {
                sink.add(entry.id(), entry.properties());
                status(tr("added", entryName(entry)));
            }
        } else if (zone == RESTORE) {
            restore(row);
        } else if (e.hasShiftDown() && zone == BODY) {
            Minecraft.getInstance().keyboardHandler.setClipboard(entry.toString());
            status(tr("copied", entry.toString()));
        } else {
            // The hand button in the list, and a plain click on a tile of the grid.
            Optional<Component> why = Give.stack(new ItemStack(entry.block()), -1);
            status(why.orElseGet(() -> tr("gave", entry.name())));
        }
    }

    private boolean panelClicked(MouseButtonEvent e, boolean doubled, boolean left) {
        if (left && closeButton().contains(e.x(), e.y())) {
            toggle(panel);
            return true;
        }
        if (panelWidgets.mouseClicked(e, doubled) || panelList.mouseClicked(e)) return true;
        int i = left ? panelList.rowAt(e.x(), e.y()) : -1;
        if (i < 0) return true;
        switch (panel) {
            case BIOMES -> {
                List<ResourceKey<Biome>> listed = matchingBiomes();
                if (i < listed.size()) chooseBiome(listed.get(i));
            }
            case FIND_BIOME -> {
                if (i < fits.size()) chooseBiome(fits.get(i).biome);
            }
            case DYE -> {
                if (i < dyes.size()) {
                    ItemStack stack = Dye.dyed(dyes.get(i).getItem(), MatchState.colour().rgb());
                    status(Dye.give(stack).orElseGet(() -> tr("gave", stack.getHoverName())));
                }
            }
            default -> { }
        }
        return true;
    }

    /** Makes the block the player is holding the target. */
    private void takeHeld() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        ItemStack held = mc.player.containerMenu.getCarried();
        if (held.isEmpty()) held = mc.player.getInventory().getSelectedItem();
        if (held.isEmpty()) {
            status(tr("slot.empty"));
            return;
        }
        Block block = held.getItem() instanceof BlockItem item ? item.getBlock() : null;
        List<Entry> states = block == null ? List.<Entry>of() : BlockIndex.get().statesOf(block);
        if (states.isEmpty()) status(tr("slot.not_block", held.getHoverName()));
        else {
            MatchState.setEntry(states.getFirst(), Face.ANY, null);
            MatchState.remember();
        }
    }

    private void armEyedropper() {
        Eyedropper.arm();
        Minecraft.getInstance().gui.setScreen(null);
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent e) {
        boolean took = picker.mouseReleased();
        if (draggingDistance) {
            draggingDistance = false;
            took = true;
        }
        if (swapList != null) took |= swapList.list.mouseReleased();
        return list.mouseReleased() | panelList.mouseReleased() | controls.mouseReleased(e) | panelWidgets.mouseReleased(e) | took;
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent e, double dx, double dy) {
        if (picker.mouseDragged(e.x(), e.y())) return true;
        if (draggingDistance) {
            setDistance(e.x());
            return true;
        }
        if (panel != Panel.NONE) return panelList.mouseDragged(e) || panelWidgets.mouseDragged(e, dx, dy);
        if (swapList != null && swapList.list.mouseDragged(e)) return true;
        return list.mouseDragged(e) || controls.mouseDragged(e, dx, dy);
    }

    @Override
    public boolean mouseScrolled(double x, double y, double h, double v) {
        if (v == 0) return false;
        if (panel != Panel.NONE && results.contains(x, y)) return panelWidgets.mouseScrolled(x, y, h, v) || panelList.mouseScrolled(x, y, v);
        if (swapList != null) {
            if (swapList.box.contains(x, y)) return swapList.list.mouseScrolled(x, y, v) || true;
            // Anywhere else the wheel does what it does, and the list would be left hanging from a line that has moved.
            swapList = null;
        }
        if (picker.mouseScrolled(x, y, v)) return true;
        if (distance.contains(x, y)) {
            // A notch is a step along the slider, so it moves a block at a time up close and further when far.
            int next = blocksAt(distanceAt(ModSettings.viewingDistance) + Math.signum(v) * 0.03);
            if (next == ModSettings.viewingDistance) next = (int) Math.clamp(next + Math.signum(v), 1, ModSettings.VIEW_FAR);
            ModSettings.viewingDistance = next;
            MatchState.changed();
            return true;
        }
        if (slot.contains(x, y)) {
            Minecraft mc = Minecraft.getInstance();
            if (mc.player == null) return true;
            Inventory inventory = mc.player.getInventory();
            int size = Inventory.getSelectionSize();
            inventory.setSelectedSlot(((inventory.getSelectedSlot() - (int) Math.signum(v)) % size + size) % size);
            if (!inventory.getSelectedItem().isEmpty()) takeHeld();
            return true;
        }
        return controls.mouseScrolled(x, y, h, v) || list.mouseScrolled(x, y, v);
    }

    @Override
    public boolean keyPressed(KeyEvent e) {
        if (panel != Panel.NONE && panelWidgets.typing()) {
            if (e.isEscape()) return false;
            panelWidgets.keyPressed(e);
            return true;
        }
        if (controls.typing()) {
            if (e.isEscape()) return false;
            if (e.isConfirmation()) {
                MatchState.remember();
                controls.setFocused(null);
                return true;
            }
            controls.keyPressed(e);
            return true;
        }
        if (hovered != null && hovered.type != MAKE && InputConstants.getKey(e).getName().equals(ModSettings.recipeKey)) {
            ItemStack stack = new ItemStack(hovered.entry.block());
            if (!RecipeViewers.available()) status(tr("recipe.none"));
            else if (!RecipeViewers.show(stack)) status(tr("recipe.failed", hovered.entry.name()));
            return true;
        }
        return controls.keyPressed(e);
    }

    /** Escape closes the swap list, or the panel over the results, when one is open. */
    @Override
    public boolean dismiss() {
        if (swapList != null) {
            swapList = null;
            return true;
        }
        if (panel == Panel.NONE) return false;
        toggle(panel);
        return true;
    }

    @Override
    public boolean charTyped(CharacterEvent e) {
        return panel != Panel.NONE && panelWidgets.typing() ? panelWidgets.charTyped(e) : controls.charTyped(e);
    }

    @Override
    public boolean typing() { return controls.typing() || panel != Panel.NONE && panelWidgets.typing(); }

    @Override
    public void closed() {
        picker.close();
        Thumbnails.clear();
    }
}
