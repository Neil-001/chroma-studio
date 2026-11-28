package dev.chromastudio.client.ui.tabs;

import static dev.chromastudio.client.ui.Theme.BACKING;
import static dev.chromastudio.client.ui.Theme.BORDER;
import static dev.chromastudio.client.ui.Theme.DIM;
import static dev.chromastudio.client.ui.Theme.GREYED;
import static dev.chromastudio.client.ui.Theme.PANEL;
import static dev.chromastudio.client.ui.Theme.TEXT;
import static dev.chromastudio.client.ui.Theme.WELL;

import com.mojang.blaze3d.platform.InputConstants;
import dev.chromastudio.client.BiomeMode;
import dev.chromastudio.client.ChromaStudioClient;
import dev.chromastudio.client.ColourFormat;
import dev.chromastudio.client.FaceChoice;
import dev.chromastudio.client.Keys;
import dev.chromastudio.client.ModSettings;
import dev.chromastudio.client.PaletteSink;
import dev.chromastudio.client.PaletteStore;
import dev.chromastudio.client.action.Clipboard;
import dev.chromastudio.client.action.Dye;
import dev.chromastudio.client.action.Eyedropper;
import dev.chromastudio.client.action.Give;
import dev.chromastudio.client.action.RecipeViewers;
import dev.chromastudio.client.action.ScreenPicker;
import dev.chromastudio.client.axiom.AxiomBridge;
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
import dev.chromastudio.client.ui.Theme;
import dev.chromastudio.client.ui.Widgets;
import dev.chromastudio.core.color.Badge;
import dev.chromastudio.core.color.Color;
import dev.chromastudio.core.color.Face;
import dev.chromastudio.core.color.Metric;
import dev.chromastudio.core.color.Oklch;
import dev.chromastudio.core.color.Scale;
import dev.chromastudio.core.color.Viewing;
import dev.chromastudio.core.palette.Palette;
import dev.chromastudio.core.palette.PaletteEntry;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.BooleanSupplier;
import java.util.function.Predicate;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.ARGB;
import net.minecraft.util.Util;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;

public final class MatchTab extends Tab {
    private static final int PAD = 3, ROW = 18, CELL = 34, CELL_H = CELL + 10, HEAD = 13, GAP = 2, LINE = Widgets.H + 1, STATUS_H = 10;
    private static final long STATUS_MS = 2500;
    private static final int RESULT = 0, MEMBER = 1, MAKE = 2;
    private static final int BODY = 0, EXPAND = 1, ADD = 2;
    private static final String ALL = "all";

    private static final ExecutorService WORKER = Executors.newSingleThreadExecutor(task -> {
        Thread t = new Thread(task, "Chroma Studio match");
        t.setDaemon(true);
        return t;
    });

    private enum Panel { NONE, FILTERS, BIOMES, FIND_BIOME, DYE }

    private record Shown(List<BlockIndex.Match> matches, Target target, Scale scale, TintContext tint, Metric metric) {}

    private record Row(int type, Entry entry, Face face, double distance, boolean hasSet) {}

    private record BiomeFit(ResourceKey<Biome> biome, Color colour, double distance) {}

    private Bounds area = new Bounds(0, 0, 0, 0), slot = area, swatch = area, recent = area, distance = area, results = area, statusLine = area, panelArea = area, shownArea = area;
    private final Widgets controls = new Widgets();
    private Widgets panelWidgets = new Widgets();
    private EditBox hexField;
    private final EditBox[] formatFields = new EditBox[2];
    private final ColourFormat[] fieldFormats = new ColourFormat[2];
    private AbstractWidget findButton;
    private boolean filling;
    private final ColourPicker picker = new ColourPicker(MatchState.colour(), this::picked);

    private Widgets.ScrollList list = new Widgets.ScrollList(area, ROW, () -> 0);
    private Widgets.ScrollList panelList = new Widgets.ScrollList(area, ROW, () -> 0);
    private boolean grid;
    private Shown shown;
    private List<Row> rows = List.of();
    private int kindWidth;
    private Entry expanded;
    private Row hovered;
    private final Debounce search = new Debounce();
    private int seenRevision = -1, seenVersion = -1;
    private boolean draggingDistance;

    private Panel panel = Panel.NONE;
    private String biomeSearch = "";
    private List<ResourceKey<Biome>> biomes = List.of();
    private List<BiomeFit> fits = List.of();
    private List<ItemStack> dyes = List.of();
    private List<String> hint = List.of();
    private int hintRgb;
    private final Map<ResourceKey<Biome>, int[]> biomeTints = new HashMap<>();
    private final Map<Entry, int[]> ranges = new HashMap<>();

    private Component status;
    private long statusUntil;
    private String cameFrom;
    private Palette cameFromPalette;
    private Bounds backLink = new Bounds(0, 0, 0, 0);

    public MatchTab() { super("match"); }

    private static MutableComponent tr(String key, Object... args) { return Component.translatable("match.chromastudio." + key, args); }

    private static Component lower(String prefix, Enum<?> value) {
        return Component.translatable(prefix + value.name().toLowerCase(Locale.ROOT));
    }

    @Override
    public void init(Bounds b) {
        area = b;
        MatchState.follow();
        build();
        search.request();
    }

    private static final class Flow {
        final int left, right;
        int x, y;

        Flow(int left, int right, int y) {
            this.left = left;
            this.right = right;
            this.x = left;
            this.y = y;
        }

        int take(int w) {
            if (x > left && x + w > right) line();
            int at = x;
            x += w + GAP;
            return at;
        }

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
        Bounds b = area;
        Font font = Minecraft.getInstance().font;
        TargetModel model = picker.model;

        int lw = Math.clamp(b.w() * 36 / 100, 146, 210), x = b.x() + PAD, w = lw - 2 * PAD, y = b.y() + PAD;
        slot = new Bounds(x, y - 1, 18, 18);
        swatch = new Bounds(x + 20, y, 16, 16);
        Component axiom = tr("axiom");
        boolean withAxiom = AxiomBridge.isPresent();
        int axiomW = Widgets.buttonWidth(axiom);
        hexField = controls.field(x + 38, y, w - 38 - (withAxiom ? axiomW + GAP : 0), model.hex(), text -> typed(hexField, "hex", text, model::typeHex));
        hexField.setMaxLength(7);
        showFieldState(hexField, "hex", true);
        if (withAxiom) {
            AbstractWidget axiomButton = controls.button(x + w - axiomW, y, axiomW, axiom, this::takeAxiom);
            Optional<Component> noAxiom = AxiomBridge.activeBlockUnavailable();
            if (noAxiom.isPresent()) Widgets.disable(axiomButton, noAxiom.get());
            else axiomButton.setTooltip(Tooltip.create(AxiomBridge.activeBlockTip()));
        }
        y += ROW;
        fieldFormats[0] = ModSettings.colourFormat == ColourFormat.RGB ? ColourFormat.OKLCH : ColourFormat.RGB;
        fieldFormats[1] = ModSettings.colourFormat == ColourFormat.HSL ? ColourFormat.OKLCH : ColourFormat.HSL;
        int half = (w - GAP) / 2, oklchW = font.width("0.00,.000,000") + 8;
        int firstW = fieldFormats[0] == ColourFormat.OKLCH ? Math.max(half, oklchW)
                : fieldFormats[1] == ColourFormat.OKLCH ? Math.min(half, w - GAP - oklchW) : half;
        for (int i = 0; i < 2; i++) {
            int at = i;
            ColourFormat format = fieldFormats[i];
            String name = format.name().toLowerCase(Locale.ROOT);
            EditBox field = controls.field(i == 0 ? x : x + firstW + GAP, y, i == 0 ? firstW : w - firstW - GAP, model.text(format),
                    text -> typed(formatFields[at], name, text, t -> model.type(format, t)));
            field.setMaxLength(16);
            showFieldState(field, name, true);
            formatFields[i] = field;
        }
        y += ROW;

        int by = b.bottom() - PAD - Widgets.H, bx = x;
        Component block = tr("pick_block"), screen = tr("pick_screen"), dye = tr("dye");
        AbstractWidget blockButton = controls.button(bx, by, block, this::armEyedropper);
        blockButton.setTooltip(Tooltip.create(Component.translatable("eyedropper.chromastudio.armed.how", Keys.EYEDROPPER.getTranslatedKeyMessage())));
        bx += blockButton.getWidth() + GAP;
        AbstractWidget screenButton = controls.button(bx, by, screen, () -> ScreenPicker.arm(rgb -> {
            MatchState.setColour(Color.ofRgb(rgb));
            MatchState.remember();
        }));
        screenButton.setTooltip(Tooltip.create(Component.translatable("picker.chromastudio.hint")));
        bx += screenButton.getWidth() + GAP;
        AbstractWidget dyeButton = controls.add(new Chip(bx, by, dye, () -> panel == Panel.DYE, () -> toggle(Panel.DYE)));
        Optional<Component> noGive = Give.whyNot();
        if (noGive.isPresent()) Widgets.disable(dyeButton, noGive.get());

        int recentY = by - 12, slidersY = recentY - 3 * (ColourPicker.SLIDER_H + 2) - 1;
        recent = new Bounds(x, recentY, w, 10);
        picker.layout(new Bounds(x, y, w, Math.max(8, slidersY - 4 - y)), new Bounds(x, slidersY, w, 3 * (ColourPicker.SLIDER_H + 2)));

        Flow flow = new Flow(b.x() + lw, b.right() - PAD, b.y() + PAD);
        int room = flow.right - flow.left;
        MatchFilters filters = MatchState.filters();
        List<String> kinds = new ArrayList<>(List.of(ALL));
        for (Kind k : Kind.values()) kinds.add(k.name());
        int kindW = widest(kinds.stream().map(MatchTab::kindName).toList());
        Component more = filters.extras() > 0 && panel != Panel.FILTERS ? tr("filter.more.count", filters.extras()) : tr("filter.more"), gridLabel = tr("grid");
        int filterRow = kindW + Chip.width(more) + 4 * GAP;
        for (String key : List.of("filter.cube", "filter.solid", "filter.opaque")) filterRow += Chip.width(tr(key));
        boolean gridWithFilters = filterRow + GAP + Chip.width(gridLabel) <= room;

        List<FaceChoice> faces = List.of(FaceChoice.values());
        List<Component> faceNames = faces.stream().map(f -> lower("option.chromastudio.facechoice.", f)).toList();
        List<Metric> metrics = List.of(Metric.values());
        List<Component> metricNames = metrics.stream().map(m -> lower("option.chromastudio.metric.", m)).toList();
        int gridRoom = gridWithFilters ? 0 : Chip.width(gridLabel) + GAP;
        boolean labelFirst = widest(tr("face.label"), faceNames) + widest(tr("metric.label"), metricNames) + 2 * GAP + gridRoom + 70 <= room;
        Component faceLabel = labelFirst ? tr("face.label") : null;
        int faceW = labelFirst ? widest(faceLabel, faceNames) : widest(faceNames);
        controls.cycler(flow.take(faceW), flow.y, faceW, faceLabel, faces, ModSettings.defaultFace, f -> lower("option.chromastudio.facechoice.", f), f -> {
            ModSettings.defaultFace = f;
            MatchState.changed();
        }, tr("face.tip"));
        Component metricLabel = labelFirst ? tr("metric.label") : null;
        int metricW = labelFirst ? widest(metricLabel, metricNames) : widest(metricNames);
        controls.cycler(flow.take(metricW), flow.y, metricW, metricLabel, metrics, MatchState.metric(), m -> lower("option.chromastudio.metric.", m), m -> {
            ModSettings.metric = m.name();
            MatchState.changed();
        }, tr("metric.tip"));
        if (!gridWithFilters) gridChip(flow, gridLabel);
        int distanceW = flow.rest(70);
        distance = new Bounds(flow.x, flow.y, distanceW, Widgets.H);

        flow.line();
        List<BiomeMode> modes = List.of(BiomeMode.values());
        List<Component> modeNames = modes.stream().map(m -> (Component) tr("biome." + m.name().toLowerCase(Locale.ROOT))).toList();
        Component shading = tr("shading"), find = tr("find_biome");
        boolean labelMode = widest(tr("biome.label"), modeNames) + Chip.width(shading) + Chip.width(find) + 3 * GAP + 60 <= room;
        Component modeLabel = labelMode ? tr("biome.label") : null;
        int modeW = labelMode ? widest(modeLabel, modeNames) : widest(modeNames);
        controls.cycler(flow.take(modeW), flow.y, modeW, modeLabel, modes, ModSettings.biomeMode, m -> tr("biome." + m.name().toLowerCase(Locale.ROOT)), m -> {
            ModSettings.biomeMode = m;
            if (panel == Panel.BIOMES) panel = Panel.NONE;
            MatchState.changed();
            build();
        }, tr("biome.tip"));
        controls.add(new Chip(flow.take(Chip.width(shading)), flow.y, shading, () -> ModSettings.shading, () -> {
            ModSettings.shading = !ModSettings.shading;
            MatchState.changed();
        })).setTooltip(Tooltip.create(Component.translatable("settings.chromastudio.shading.tip")));
        findButton = controls.add(new Chip(flow.take(Chip.width(find)), flow.y, find, () -> panel == Panel.FIND_BIOME, () -> toggle(Panel.FIND_BIOME)));
        findState();
        int biomeW = flow.rest(60);
        Identifier chosen = Identifier.tryParse(ModSettings.biome);
        String biomeText = chosen == null ? ModSettings.biome : biomeName(chosen).getString();
        AbstractWidget biomeButton = controls.button(flow.x, flow.y, biomeW, Component.literal(ellipsis(font, biomeText, biomeW - 8)), this::pickBiome);
        biomeButton.setTooltip(Tooltip.create(Component.literal(ModSettings.biome)));

        flow.line();
        controls.cycler(flow.take(kindW), flow.y, kindW, null, kinds, filters.kind == null ? ALL : filters.kind.name(), MatchTab::kindName, k -> {
            filters.kind = k.equals(ALL) ? null : Kind.valueOf(k);
            MatchState.changed();
        }, tr("filter.kind.tip"));
        chip(flow, "filter.cube", () -> filters.fullCube, () -> filters.fullCube = !filters.fullCube);
        chip(flow, "filter.solid", () -> filters.solid, () -> filters.solid = !filters.solid);
        chip(flow, "filter.opaque", () -> filters.opaque, () -> filters.opaque = !filters.opaque);
        controls.add(new Chip(flow.take(Chip.width(more)), flow.y, more, () -> panel == Panel.FILTERS || filters.extras() > 0, () -> toggle(Panel.FILTERS)))
                .setTooltip(Tooltip.create(tr("filter.more.tip")));
        if (gridWithFilters) gridChip(flow, gridLabel);

        int top = flow.y + LINE + 1;
        statusLine = new Bounds(flow.left, b.bottom() - PAD - STATUS_H, flow.right - flow.left, STATUS_H);
        results = new Bounds(flow.left, top, flow.right - flow.left, Math.max(ROW, statusLine.y() - 1 - top));
        buildPanel();
        placeResults();
    }

    private void placeResults() {
        int shownTop = panel == Panel.NONE ? results.y() : Math.min(results.bottom(), panelArea.bottom() + 2);
        shownArea = new Bounds(results.x(), shownTop, results.w(), results.bottom() - shownTop);
        list = resultList(shownArea);
    }

    private Widgets.ScrollList resultList(Bounds where) {
        return grid ? new Widgets.ScrollList(where, CELL_H, () -> shown == null ? 0 : (shown.matches.size() + columns() - 1) / columns())
                : new Widgets.ScrollList(where, ROW, () -> rows.size());
    }

    private void gridChip(Flow flow, Component label) {
        controls.add(new Chip(flow.take(Chip.width(label)), flow.y, label, () -> grid, () -> {
            grid = !grid;
            build();
        }));
    }

    private void chip(Flow flow, String key, BooleanSupplier on, Runnable flip) {
        Component label = tr(key);
        controls.add(new Chip(flow.take(Chip.width(label)), flow.y, label, on, () -> {
            flip.run();
            MatchState.changed();
        }));
    }

    private static int widest(List<Component> labels) {
        int w = 0;
        for (Component label : labels) w = Math.max(w, Widgets.tw(label));
        return w + 10;
    }

    private static int widest(Component name, List<Component> values) {
        return widest(values.stream().<Component>map(v -> CommonComponents.optionNameValue(name, v)).toList());
    }

    private static Component kindName(String kind) { return Component.translatable("kind.chromastudio." + kind.toLowerCase(Locale.ROOT)); }

    static String ellipsis(Font font, String text, int width) {
        if (font.width(text) <= width) return text;
        return font.plainSubstrByWidth(text, Math.max(0, width - font.width("…"))) + "…";
    }

    private int columns() { return Math.max(1, (results.w() - 5) / CELL); }

    private void hint(Component text, Color swatch) {
        Font font = Minecraft.getInstance().font;
        int w = results.w() - 28;
        List<String> lines = font.getSplitter().splitLines(text, w, Style.EMPTY).stream().map(FormattedText::getString).toList();
        hint = lines.size() <= 2 ? lines : List.of(lines.getFirst(), ellipsis(font, String.join(" ", lines.subList(1, lines.size())), w));
        hintRgb = swatch.rgb();
    }

    private void pickBiome() {
        if (ModSettings.biomeMode != BiomeMode.CHOSEN) {
            ModSettings.biomeMode = BiomeMode.CHOSEN;
            MatchState.changed();
            panel = Panel.NONE;
        }
        toggle(Panel.BIOMES);
    }

    private void toggle(Panel which) {
        panel = panel == which ? Panel.NONE : which;
        if (panel == Panel.BIOMES) biomeSearch = "";
        build();
    }

    private Bounds panelBody() { return new Bounds(panelArea.x() + 1, panelArea.y() + HEAD, panelArea.w() - 2, panelArea.h() - HEAD - 1); }

    private void panelData() {
        hint = List.of();
        switch (panel) {
            case FIND_BIOME -> {
                fits = whyNoFind() == null ? fits() : List.of();
                if (!fits.isEmpty()) hint(tr("find_biome.hint", MatchState.entry().name(), MatchState.lastFlat().hex()), MatchState.lastFlat());
            }
            case DYE -> {
                dyes = Dye.items(MatchState.colour().rgb());
                hint(tr("dye.hint", MatchState.colour().hex()), MatchState.colour());
            }
            default -> { }
        }
    }

    private void refreshPanel() {
        if (panel != Panel.FIND_BIOME && panel != Panel.DYE) return;
        int lines = hint.size();
        panelData();
        if (hint.size() == lines) panelList.area = belowHint();
        else {
            buildPanel();
            placeResults();
        }
    }

    private Bounds belowHint() {
        Bounds body = panelBody();
        int hinted = hint.isEmpty() ? 0 : hint.size() * 10 + 4;
        return new Bounds(body.x(), body.y() + hinted, body.w(), body.h() - hinted);
    }

    private void buildPanel() {
        panelWidgets = new Widgets();
        panelData();
        int hinted = hint.isEmpty() ? 0 : hint.size() * 10 + 4;
        int most = results.h() - 2 * (grid ? CELL_H : ROW) - 2, content = switch (panel) {
            case FIND_BIOME -> HEAD + (fits.isEmpty() ? 24 : hinted + fits.size() * ROW) + 2;
            case DYE -> HEAD + hinted + dyes.size() * ROW + 2;
            default -> most;
        };
        int least = switch (panel) {
            case BIOMES -> HEAD + LINE + 1 + 3 * 11;
            case FIND_BIOME, DYE -> HEAD + hinted + ROW + 1;
            default -> HEAD + ROW + 2;
        };
        int height = most < least ? results.h() : Math.max(HEAD + ROW, Math.min(content, most));
        panelArea = new Bounds(results.x(), results.y(), results.w(), panel == Panel.NONE ? 0 : height);
        Bounds body = panelBody(), belowHint = belowHint();
        panelList = new Widgets.ScrollList(body, ROW, () -> 0);
        switch (panel) {
            case FILTERS -> FilterControls.build(panelWidgets, body, false, this::build);
            case BIOMES -> {
                biomes = Tints.biomes().stream().sorted(Comparator.comparing((ResourceKey<Biome> k) -> biomeName(k.identifier()).getString())).toList();
                biomeTints.clear();
                List<Entry> grass = BlockIndex.get().statesOf(Blocks.GRASS_BLOCK), leaves = BlockIndex.get().statesOf(Blocks.OAK_LEAVES);
                for (ResourceKey<Biome> key : biomes) {
                    TintContext tint = TintContext.biome(key);
                    biomeTints.put(key, new int[] {
                            grass.isEmpty() ? -1 : Target.of(grass.getFirst(), Face.UP, tint).colour().rgb(),
                            leaves.isEmpty() ? -1 : Target.of(leaves.getFirst(), Face.UP, tint).colour().rgb()});
                }
                EditBox box = panelWidgets.search(body.x() + 2, body.y() + 1, body.w() - 4, tr("biome.search"), text -> {
                    biomeSearch = text.trim().toLowerCase(Locale.ROOT);
                    panelList.reveal(0);
                });
                box.setValue(biomeSearch);
                panelWidgets.setFocused(box);
                panelList = new Widgets.ScrollList(new Bounds(body.x(), body.y() + LINE + 1, body.w(), body.h() - LINE - 1), 11, () -> matchingBiomes().size());
                List<ResourceKey<Biome>> listed = matchingBiomes();
                int at = listed.stream().map(k -> k.identifier().toString()).toList().indexOf(ModSettings.biome);
                if (at >= 0) panelList.reveal(Math.min(at + (body.h() - LINE - 1) / 22, listed.size() - 1));
            }
            case FIND_BIOME -> panelList = new Widgets.ScrollList(belowHint, ROW, () -> fits.size());
            case DYE -> panelList = new Widgets.ScrollList(belowHint, ROW, () -> dyes.size());
            default -> { }
        }
    }

    private List<ResourceKey<Biome>> matchingBiomes() {
        if (biomeSearch.isEmpty()) return biomes;
        return biomes.stream().filter(k -> k.identifier().toString().contains(biomeSearch)
                || biomeName(k.identifier()).getString().toLowerCase(Locale.ROOT).contains(biomeSearch)).toList();
    }

    private static Component biomeName(Identifier id) {
        String key = Util.makeDescriptionId("biome", id);
        return Language.getInstance().has(key) ? Component.translatable(key) : Component.literal(id.toString());
    }

    private void findState() {
        if (findButton == null) return;
        Component why = whyNoFind();
        findButton.active = why == null;
        findButton.setTooltip(why != null ? Tooltip.create(why) : null);
    }

    private static Component whyNoFind() {
        Entry e = MatchState.entry();
        if (e == null || !e.tinted()) return tr("find_biome.no_block");
        if (MatchState.lastFlat() == null) return tr("find_biome.no_colour");
        return null;
    }

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

    private void fill(EditBox except) {
        if (hexField == null) return;
        TargetModel model = picker.model;
        filling = true;
        if (hexField != except) {
            hexField.setValue(model.hex());
            showFieldState(hexField, "hex", true);
        }
        for (int i = 0; i < formatFields.length; i++) {
            EditBox field = formatFields[i];
            if (field == except) continue;
            ColourFormat format = fieldFormats[i];
            field.setValue(model.text(format));
            showFieldState(field, format.name().toLowerCase(Locale.ROOT), true);
        }
        filling = false;
    }

    private static void showFieldState(EditBox field, String format, boolean valid) {
        field.setTextColor(valid ? EditBox.DEFAULT_TEXT_COLOR : Theme.FIELD_BAD);
        String key = "colour.chromastudio." + format;
        Component tip = valid ? Component.translatable(key)
                : format.equals("oklch") ? Component.translatable(key + ".bad", TargetModel.CHROMA_MAX) : Component.translatable(key + ".bad");
        field.setTooltip(Tooltip.create(tip));
    }

    private void typed(EditBox field, String format, String text, Predicate<String> take) {
        if (filling || field == null) return;
        boolean valid = take.test(text);
        showFieldState(field, format, valid);
        if (!valid) return;
        fill(field);
        adopt();
    }

    private void picked(boolean settled) {
        fill(null);
        adopt();
        if (settled) MatchState.remember();
    }

    private void adopt() {
        MatchState.setColour(picker.model.colour());
        seenRevision = MatchState.revision();
        refreshPanel();
        search.request();
    }

    private void status(Component message) {
        status = message;
        statusUntil = Util.getMillis() + STATUS_MS;
    }

    private void sync() {
        BlockIndex index = BlockIndex.get();
        MatchState.follow();
        if (Handoff.matchTarget != null || Handoff.matchFilter != null) {
            Object target = Handoff.matchTarget;
            String filter = Handoff.matchFilter;
            Handoff.matchTarget = null;
            Handoff.matchFilter = null;
            Oklch around = target instanceof Color c ? c.oklch() : target instanceof Oklch o ? o : null;
            Palette from = PaletteStore.get().selected();
            cameFromPalette = filter != null ? from : null;
            cameFrom = cameFromPalette != null ? PaletteStore.get().shownName(from) : null;
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
            refreshPanel();
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
                if (!search.current(run)) return;
                shown = new Shown(matches, target, query.scale, query.tint, query.metric);
                rows();
            });
        });
    }

    private void rows() {
        List<Row> out = new ArrayList<>();
        BlockIndex index = BlockIndex.get();
        if (shown != null) {
            for (BlockIndex.Match m : shown.matches) {
                List<Entry> set = index.materialSet(m.entry());
                out.add(new Row(RESULT, m.entry(), m.face(), m.distance(), set.size() > 1));
                if (!sameBlock(expanded, m.entry()) || grid) continue;
                for (Entry e : set) {
                    Face face = e.bestFace(shown.target, shown.scale, shown.metric, shown.tint);
                    double d = shown.target.distribution(shown.scale).emd(e.face(face, shown.tint).distribution(shown.scale), shown.metric);
                    out.add(new Row(MEMBER, e, face, d, false));
                }
                out.add(new Row(MAKE, m.entry(), m.face(), 0, false));
            }
        }
        rows = out;
        Font font = Minecraft.getInstance().font;
        kindWidth = 0;
        for (Row row : out) kindWidth = Math.max(kindWidth, font.width(kindName(row.entry.kind().name())));
    }

    @Override
    public void render(GuiGraphics g, Bounds b, int mouseX, int mouseY, float delta) {
        sync();
        Font font = Minecraft.getInstance().font;
        hovered = null;
        List<Component> tip = new ArrayList<>();
        target(g, font, 1, mouseX, mouseY, tip);
        controls.render(g, mouseX, mouseY, delta);
        distance(g, font, mouseX, mouseY, tip);
        results(g, font, 1, mouseX, mouseY, tip);
        if (panel != Panel.NONE) panel(g, font, mouseX, mouseY, delta, tip);

        statusLine(g, font, mouseX, mouseY);
        if (!tip.isEmpty() && !picker.dragging() && !draggingDistance) g.setComponentTooltipForNextFrame(font, tip, mouseX, mouseY);
    }

    private void statusLine(GuiGraphics g, Font font, int mouseX, int mouseY) {
        int x = statusLine.x() + 2, y = statusLine.y() + 1, right = statusLine.right() - 2;
        backLink = new Bounds(0, 0, 0, 0);
        if (status != null && Util.getMillis() < statusUntil) {
            g.drawString(font, ellipsis(font, status.getString(), right - x), x, y, TEXT, false);
            return;
        }
        if (cameFrom != null) {
            String back = ellipsis(font, tr("back", cameFrom).getString(), (right - x) / 2);
            backLink = new Bounds(x - 2, statusLine.y(), font.width(back) + 4, statusLine.h());
            g.drawString(font, back, x, y, backLink.contains(mouseX, mouseY) ? Theme.STRONG : Theme.HIGHLIGHT, false);
            x = backLink.right() + 6;
        }
        Palette target = PaletteStore.get().selected();
        boolean strip = cameFrom == null && ModSettings.biomeMode == BiomeMode.ALL && panel == Panel.NONE && !grid;
        String adds = target == null ? "" : ellipsis(font, (target.preset ? tr("add.new") : tr("add.target", PaletteStore.get().shownName(target))).getString(),
                strip ? (right - x) / 2 : right - x);
        int addsW = adds.isEmpty() ? 0 : font.width(adds) + 6;
        if (strip) {
            g.drawString(font, ellipsis(font, tr("biome.all.hint").getString(), right - x - addsW), x, y, Theme.SOFT, false);
            x = right - addsW + 6;
        }
        if (!adds.isEmpty() && right - x >= font.width(adds)) g.drawString(font, adds, right - font.width(adds), y, Theme.SOFT, false);
    }

    @Override
    public void renderPinned(GuiGraphics g, Bounds b, float opacity) {
        sync();
        Font font = Minecraft.getInstance().font;
        List<Component> none = new ArrayList<>();
        target(g, font, opacity, -1, -1, none);
        g.drawString(font, picker.model.hex(), swatch.right() + 4, swatch.y() + 4, ARGB.multiplyAlpha(TEXT, opacity), false);
        Bounds keptArea = shownArea;
        Widgets.ScrollList keptList = list;
        if (panel != Panel.NONE) {
            shownArea = results;
            list = resultList(results);
        }
        results(g, font, opacity, -1, -1, none);
        shownArea = keptArea;
        list = keptList;
    }

    private int recentWidth() { return recent.w() / RecentColours.KEPT; }

    private void target(GuiGraphics g, Font font, float opacity, int mouseX, int mouseY, List<Component> tip) {
        Entry entry = MatchState.entry();
        g.fill(area.x(), area.y(), results.x() - 1, area.bottom(), ARGB.multiplyAlpha(BACKING, opacity));
        g.fill(results.x() - 1, area.y(), area.right(), results.y(), ARGB.multiplyAlpha(BACKING, opacity));
        g.fill(results.x() - 1, results.bottom(), area.right(), area.bottom(), ARGB.multiplyAlpha(BACKING, opacity));
        g.fill(slot.x(), slot.y(), slot.right(), slot.bottom(), ARGB.multiplyAlpha(BORDER, opacity));
        g.fill(slot.x() + 1, slot.y() + 1, slot.right() - 1, slot.bottom() - 1, ARGB.multiplyAlpha(WELL, opacity));
        if (entry != null) Widgets.blockIcon(g, entry.block(), slot.x() + 1, slot.y() + 1);
        if (slot.contains(mouseX, mouseY)) {
            g.fill(slot.x() + 1, slot.y() + 1, slot.right() - 1, slot.bottom() - 1, Theme.veil(0x40));
            if (entry != null) tip.add(targetName());
            tip.add(tr("slot.tip").withColor(Theme.TIP_DIM));
            tip.add(tr("slot.tip.scroll").withColor(Theme.TIP_DIM));
        }
        g.fill(swatch.x(), swatch.y(), swatch.right(), swatch.bottom(), ARGB.multiplyAlpha(0xFF000000, opacity));
        g.fill(swatch.x() + 1, swatch.y() + 1, swatch.right() - 1, swatch.bottom() - 1, ARGB.multiplyAlpha(0xFF000000 | MatchState.colour().rgb(), opacity));
        if (swatch.contains(mouseX, mouseY)) {
            tip.add(entry == null ? tr("target.colour", MatchState.colour().hex())
                    : tr("target.block", Eyedropper.faceName(MatchState.entryFace()), MatchState.colour().hex()));
        }
        picker.render(g, font, opacity);

        List<Integer> colours = MatchState.recent();
        int cw = recentWidth();
        for (int i = 0; i < RecentColours.KEPT; i++) {
            int rx = recent.x() + i * cw;
            if (i < colours.size()) {
                g.fill(rx, recent.y(), rx + cw - 1, recent.bottom(), ARGB.multiplyAlpha(0xFF000000, opacity));
                g.fill(rx + 1, recent.y() + 1, rx + cw - 2, recent.bottom() - 1, ARGB.multiplyAlpha(0xFF000000 | colours.get(i), opacity));
                if (mouseX >= rx && mouseX < rx + cw - 1 && mouseY >= recent.y() && mouseY < recent.bottom()) {
                    tip.add(Component.literal(Color.ofRgb(colours.get(i)).hex()));
                }
            } else g.renderOutline(rx, recent.y(), cw - 1, recent.h(), ARGB.multiplyAlpha(Theme.CHIP, opacity));
        }
    }

    private static Component targetName() {
        String properties = MatchState.entryProperties();
        return properties.isEmpty() ? MatchState.entry().name() : tr("name_with_state", MatchState.entry().name(), properties);
    }

    private static Component entryName(Entry e) {
        return e.properties().isEmpty() ? e.name() : tr("name_with_state", e.name(), e.properties());
    }

    private static double distanceAt(int blocks) { return Math.log(blocks) / Math.log(ModSettings.VIEW_FAR); }

    private static int blocksAt(double t) { return Math.clamp(Math.round(Math.exp(t * Math.log(ModSettings.VIEW_FAR))), 1, ModSettings.VIEW_FAR); }

    private void distance(GuiGraphics g, Font font, int mouseX, int mouseY, List<Component> tip) {
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
        g.fill(kx - 1, d.y() + 1, kx + 2, d.bottom() - 1, over ? Theme.STRONG : Theme.HIGHLIGHT);
        boolean far = ModSettings.viewingDistance >= ModSettings.VIEW_FAR;
        Component label = far ? tr("distance.far") : ModSettings.viewingDistance == 1 ? tr("distance.block") : tr("distance.blocks", ModSettings.viewingDistance);
        Widgets.centred(g, Component.literal(ellipsis(font, label.getString(), d.w() - 6)), d.x() + d.w() / 2, d.y() + 4, TEXT);
        if (d.contains(mouseX, mouseY)) {
            tip.add(tr("distance.tip"));
            tip.add(tr("distance.scale", MatchState.scale().texels).withColor(Theme.TIP_DIM));
        }
    }

    private void setDistance(double mx) {
        int blocks = blocksAt(Math.clamp((mx - distance.x() - 3) / Math.max(1, distance.w() - 6), 0, 1));
        if (blocks == ModSettings.viewingDistance) return;
        ModSettings.viewingDistance = blocks;
        MatchState.changed();
    }

    private void results(GuiGraphics g, Font font, float opacity, int mouseX, int mouseY, List<Component> tip) {
        BlockIndex index = BlockIndex.get();
        Bounds r = shownArea;
        if (panel != Panel.NONE) g.fill(r.x(), results.y(), r.right(), r.y(), ARGB.multiplyAlpha(BACKING, opacity));
        g.fill(r.x(), r.y(), r.right(), r.bottom(), ARGB.multiplyAlpha(Theme.sink(0x60), opacity));
        if (!index.ready() || shown == null) {
            Component text = index.ready() ? tr("searching") : tr("building", Math.round(index.progress() * 100));
            Widgets.centred(g, text, r.x() + r.w() / 2, r.y() + r.h() / 2 - 10, ARGB.multiplyAlpha(DIM, opacity));
            if (!index.ready()) bar(g, r.x() + 20, r.y() + r.h() / 2 + 4, r.w() - 40, index.progress(), opacity);
            return;
        }
        if (shown.matches.isEmpty()) {
            Widgets.centred(g, tr("no_results"), r.x() + r.w() / 2, r.y() + r.h() / 2 - 4, ARGB.multiplyAlpha(DIM, opacity));
            return;
        }
        if (grid) {
            int cols = columns();
            list.render(g, mouseX, mouseY, (g2, line, x, y, w, h, over) -> {
                for (int c = 0; c < cols && line * cols + c < shown.matches.size(); c++) {
                    BlockIndex.Match m = shown.matches.get(line * cols + c);
                    int cx = x + 2 + c * CELL, tile = CELL - 2;
                    boolean on = over && mouseX >= cx && mouseX < cx + CELL;
                    int mean = m.entry().face(m.face(), shown.tint).mean(shown.scale).rgb();
                    g2.fill(cx + 1, y + 1, cx + 1 + tile, y + 1 + tile, ARGB.multiplyAlpha(0xFF000000, opacity));
                    g2.fill(cx + 2, y + 2, cx + tile, y + tile, ARGB.multiplyAlpha(0xFF000000 | mean, opacity));
                    Thumbnails.draw(g2, m.entry(), m.face(), shown.scale, shown.tint, cx + 2, y + 2, tile - 2, opacity);
                    Widgets.blockIcon(g2, m.entry().block(), cx + 1, y + 1);
                    if (ModSettings.showDeltaE) Widgets.centred(g2, Component.literal(deltaE(m.distance(), shown.metric)), cx + 1 + tile / 2, y + tile + 1, ARGB.multiplyAlpha(on ? TEXT : DIM, opacity));
                    if (on) {
                        g2.renderOutline(cx, y, tile + 2, tile + 2, Theme.STRONG);
                        hovered = new Row(RESULT, m.entry(), m.face(), m.distance(), false);
                        describe(hovered, BODY, tip);
                    }
                }
            });
        } else {
            list.render(g, mouseX, mouseY, (g2, i, x, y, w, h, over) -> row(g2, font, rows.get(i), x, y, w, h, over, opacity, mouseX, tip));
        }
        if (index.building()) bar(g, r.x(), r.y(), r.w(), index.progress(), opacity);
    }

    private static void bar(GuiGraphics g, int x, int y, int w, float progress, float opacity) {
        g.fill(x, y, x + w, y + 2, ARGB.multiplyAlpha(Theme.CHIP, opacity));
        g.fill(x, y, x + Math.round(w * Math.clamp(progress, 0, 1)), y + 2, ARGB.multiplyAlpha(Theme.HIGHLIGHT, opacity));
    }

    private static int zone(Row row, int x, int w, double mx) {
        if (row.type == MAKE) return BODY;
        if (row.hasSet && mx < x + 10) return EXPAND;
        return mx >= x + w - 13 ? ADD : BODY;
    }

    private void row(GuiGraphics g, Font font, Row row, int x, int y, int w, int h, boolean over, float opacity, int mouseX, List<Component> tip) {
        int zone = over ? zone(row, x, w, mouseX) : -1;
        if (row.type != RESULT) g.fill(x, y, x + w, y + h, ARGB.multiplyAlpha(Theme.sink(0x38), opacity));
        if (over) {
            g.fill(x, y, x + w, y + h, Theme.veil(0x30));
            g.renderOutline(x, y, w, h, ARGB.multiplyAlpha(Theme.HIGHLIGHT, opacity));
        }
        int text = ARGB.multiplyAlpha(TEXT, opacity), dim = ARGB.multiplyAlpha(DIM, opacity);
        if (row.type == MAKE) {
            boolean can = PaletteSink.current() != null;
            Component label = tr("make_palette", BlockIndex.get().materialSet(row.entry).size());
            g.drawString(font, label, x + 28, y + 5, can ? (over ? Theme.STRONG : ARGB.multiplyAlpha(Theme.HIGHLIGHT, opacity)) : ARGB.multiplyAlpha(GREYED, opacity), false);
            if (over && !can) tip.add(tr("no_palette"));
            return;
        }
        Entry e = row.entry;
        int left = x + (row.type == MEMBER ? 12 : 1);
        if (row.hasSet) triangle(g, left + 2, y + h / 2, sameBlock(expanded, e), zone == EXPAND ? Theme.STRONG : dim);
        left += 9;
        ItemStack icon = new ItemStack(e.block());
        if (icon.isEmpty()) Widgets.swatch(g, left + 2, y + 3, 12, 12, e.face(row.face, shown.tint).mean(shown.scale).rgb());
        else Widgets.blockIcon(g, icon, left, y + 1);
        if (e.tinted()) {
            g.fill(left + 11, y + 11, left + 16, y + 16, ARGB.multiplyAlpha(0xFF10140F, opacity));
            g.fill(left + 12, y + 12, left + 16, y + 16, ARGB.multiplyAlpha(0xFF000000 | Theme.TIP_TINT, opacity));
        }
        left += 18;

        int right = x + w - 1;
        boolean can = PaletteSink.current() != null;
        g.fill(right - 11, y + 3, right, y + 14, ARGB.multiplyAlpha(zone == ADD && can ? Theme.LIT_HOVER : Theme.CHIP, opacity));
        g.fill(right - 9, y + 8, right - 2, y + 9, ARGB.multiplyAlpha(can ? TEXT : GREYED, opacity));
        g.fill(right - 6, y + 5, right - 5, y + 12, ARGB.multiplyAlpha(can ? TEXT : GREYED, opacity));
        right -= 13;
        var stats = e.face(row.face, shown.tint);
        Thumbnails.draw(g, e, row.face, shown.scale, shown.tint, right - 16, y + 1, 16, opacity);
        right -= 18;
        g.fill(right - 12, y + 3, right, y + 15, ARGB.multiplyAlpha(0xFF000000, opacity));
        g.fill(right - 11, y + 4, right - 1, y + 14, ARGB.multiplyAlpha(0xFF000000 | stats.mean(shown.scale).rgb(), opacity));
        right -= 15;
        right -= kindWidth;
        g.drawString(font, kindName(e.kind().name()), right, y + 5, dim, false);
        right -= 10;
        badge(g, right, y + 5, stats.badge(), dim);
        right -= 4;
        if (ModSettings.showDeltaE) {
            String de = deltaE(row.distance, shown.metric);
            right -= font.width(de);
            g.drawString(font, de, right, y + 5, text, false);
            right -= 4;
        }
        String name = e.name().getString(), note = e.properties().isEmpty() ? "" : ", " + e.properties();
        String fitted = ellipsis(font, name + note, right - left);
        String shownName = fitted.length() >= name.length() ? name : fitted;
        g.drawString(font, shownName, left, y + 5, text, false);
        if (fitted.length() > name.length()) g.drawString(font, fitted.substring(name.length()), left + font.width(name), y + 5, dim, false);

        if (ModSettings.biomeMode == BiomeMode.ALL && e.tinted()) strip(g, e, row.face, left, y + h - 3, Math.max(8, right - left), opacity);
        if (over) {
            hovered = row;
            describe(row, zone, tip);
        }
    }

    private static boolean sameBlock(Entry a, Entry b) { return a != null && b != null && a.id().equals(b.id()); }

    private static void triangle(GuiGraphics g, int x, int cy, boolean open, int colour) {
        for (int i = 0; i < 3; i++) {
            if (open) g.fill(x + i, cy - 1 + i, x + 5 - i, cy + i, colour);
            else g.fill(x + i, cy - 2 + i, x + i + 1, cy + 3 - i, colour);
        }
    }

    private static void badge(GuiGraphics g, int x, int y, Badge badge, int colour) {
        g.renderOutline(x, y, 7, 7, colour);
        if (badge == Badge.FLAT) return;
        for (int j = 0; j < 5; j++)
            for (int i = 0; i < 5; i++) {
                boolean on = badge == Badge.BUSY ? (i + j) % 2 == 0 : i == 1 && j == 1 || i == 3 && j == 2 || i == 1 && j == 3;
                if (on) g.fill(x + 1 + i, y + 1 + j, x + 2 + i, y + 2 + j, colour);
            }
    }

    private void strip(GuiGraphics g, Entry e, Face face, int x, int y, int w, float opacity) {
        int[] tints = ranges.computeIfAbsent(e, key -> {
            int[] all = Tints.tintsAcross(e, face).stream().filter(t -> t != TintContext.NO_TINT).mapToInt(Integer::intValue).distinct().toArray();
            return Arrays.stream(all).boxed().sorted(Comparator.comparingDouble(t -> Color.ofRgb(t).oklch().l())).mapToInt(Integer::intValue).toArray();
        });
        for (int i = 0; i < tints.length; i++) {
            g.fill(x + i * w / tints.length, y, x + (i + 1) * w / tints.length, y + 3, ARGB.multiplyAlpha(0xFF000000 | tints[i], opacity));
        }
    }

    private static String deltaE(double d, Metric metric) {
        return String.format(Locale.ROOT, metric.justNoticeable() < 1 ? "%.3f" : "%.1f", d);
    }

    private void describe(Row row, int zone, List<Component> tip) {
        if (zone == EXPAND) {
            tip.add(tr("set.tip"));
            return;
        }
        if (zone == ADD) {
            tip.add(PaletteSink.current() != null ? tr("add.tip") : tr("no_palette"));
            return;
        }
        Entry e = row.entry;
        var stats = e.face(row.face, shown.tint);
        tip.add(entryName(e));
        tip.add(Component.literal(e.toString()).withColor(Theme.TIP_DIM));
        tip.add(tr("row.match", deltaE(row.distance, shown.metric), Eyedropper.faceName(row.face)).withColor(Theme.TIP_DIM));
        tip.add(tr("row.texture", lower("badge.chromastudio.", stats.badge()), kindName(e.kind().name()), stats.mean(shown.scale).hex()).withColor(Theme.TIP_DIM));
        if (e.tinted()) tip.add(tr("row.tinted").withColor(Theme.TIP_TINT));
        Optional<Component> noGive = Give.whyNot();
        tip.add(noGive.orElseGet(() -> tr("row.give")).copy().withColor(noGive.isPresent() ? Theme.TIP_GREYED : Theme.TIP_DIM));
        tip.add(tr("row.copy").withColor(Theme.TIP_DIM));
        tip.add(tr("row.gradient").withColor(Theme.TIP_DIM));
        if (grid) tip.add(tr("row.add").withColor(PaletteSink.current() != null ? Theme.TIP_DIM : Theme.TIP_GREYED));
        tip.add(RecipeViewers.available() ? tr("row.recipe", Keys.RECIPE.getTranslatedKeyMessage()).withColor(Theme.TIP_DIM)
                : tr("recipe.none").withColor(Theme.TIP_GREYED));
    }

    private void panel(GuiGraphics g, Font font, int mouseX, int mouseY, float delta, List<Component> tip) {
        Bounds r = panelArea;
        g.fill(r.x(), r.y(), r.right(), r.bottom(), PANEL);
        g.renderOutline(r.x(), r.y(), r.w(), r.h(), BORDER);
        Bounds body = panelBody();
        g.fill(body.x(), body.y() - 1, body.right(), body.y(), BORDER);
        g.drawString(font, tr("panel." + panel.name().toLowerCase(Locale.ROOT)), r.x() + 5, r.y() + 3, TEXT, false);
        Component legend = panel == Panel.BIOMES ? tr("biome.legend") : panel == Panel.FIND_BIOME && !fits.isEmpty() ? tr("column.delta_e") : null;
        if (legend != null) g.drawString(font, legend, r.right() - HEAD - 6 - font.width(legend), r.y() + 3, Theme.SOFT, false);
        Bounds close = closeButton();
        boolean overClose = close.contains(mouseX, mouseY);
        int cx = close.x() + close.w() / 2, cy = close.y() + close.h() / 2, c = overClose ? Theme.STRONG : DIM;
        for (int i = -3; i <= 2; i++) {
            g.fill(cx + i, cy + i, cx + i + 1, cy + i + 1, c);
            g.fill(cx + i, cy - 1 - i, cx + i + 1, cy - i, c);
        }
        panelWidgets.render(g, mouseX, mouseY, delta);
        if (!hint.isEmpty()) {
            Widgets.swatch(g, body.x() + 6, body.y() + 2, 10, 10, hintRgb);
            for (int i = 0; i < hint.size(); i++) g.drawString(font, hint.get(i), body.x() + 24, body.y() + 3 + i * 10, Theme.SOFT, false);
        }
        switch (panel) {
            case BIOMES -> {
                List<ResourceKey<Biome>> listed = matchingBiomes();
                panelList.render(g, mouseX, mouseY, (g2, i, x, y, w, h, over) -> {
                    Identifier id = listed.get(i).identifier();
                    boolean current = id.toString().equals(ModSettings.biome);
                    if (current) g2.fill(x, y, x + w, y + h, Theme.veil(0x18));
                    if (over) g2.fill(x, y, x + w, y + h, Theme.veil(0x20));
                    String mod = id.getNamespace().equals(Identifier.DEFAULT_NAMESPACE) ? "" : id.getNamespace();
                    int modW = mod.isEmpty() ? 0 : font.width(mod) + 6;
                    int[] tints = biomeTints.getOrDefault(listed.get(i), new int[] {-1, -1});
                    for (int t = 0; t < 2; t++) if (tints[t] != -1) Widgets.swatch(g2, x + 4 + t * 10, y + 1, 9, 9, tints[t]);
                    g2.drawString(font, ellipsis(font, biomeName(id).getString(), w - 30 - modW), x + 26, y + 2, current ? Theme.HIGHLIGHT : TEXT, false);
                    if (!mod.isEmpty()) g2.drawString(font, mod, x + w - 4 - font.width(mod), y + 2, DIM, false);
                });
                if (listed.isEmpty()) Widgets.centred(g, tr("biome.none"), r.x() + r.w() / 2, r.y() + r.h() / 2, DIM);
            }
            case FIND_BIOME -> {
                panelList.render(g, mouseX, mouseY, (g2, i, x, y, w, h, over) -> {
                    BiomeFit fit = fits.get(i);
                    if (over) g2.fill(x, y, x + w, y + h, Theme.veil(0x20));
                    Widgets.swatch(g2, x + 4, y + 3, 12, 12, fit.colour.rgb());
                    String de = deltaE(fit.distance, MatchState.metric());
                    g2.drawString(font, de, x + w - 4 - font.width(de), y + 5, DIM, false);
                    g2.drawString(font, ellipsis(font, biomeName(fit.biome.identifier()).getString(), w - 32 - font.width(de)), x + 20, y + 5, TEXT, false);
                });
                if (fits.isEmpty()) {
                    Component why = whyNoFind();
                    Widgets.centred(g, why != null ? why : tr("biome.none"), r.x() + r.w() / 2, r.y() + r.h() / 2, DIM);
                }
            }
            case DYE -> {
                Optional<Component> noGive = Give.whyNot();
                panelList.render(g, mouseX, mouseY, (g2, i, x, y, w, h, over) -> {
                    ItemStack stack = dyes.get(i);
                    if (over) g2.fill(x, y, x + w, y + h, Theme.veil(0x20));
                    g2.renderFakeItem(stack, x + 3, y + 1);
                    g2.drawString(font, ellipsis(font, stack.getHoverName().getString(), w - 28), x + 24, y + 5, noGive.isPresent() ? GREYED : TEXT, false);
                    if (over && noGive.isPresent()) tip.add(noGive.get());
                });
            }
            default -> { }
        }
    }

    private Bounds closeButton() { return new Bounds(panelArea.right() - HEAD - 1, panelArea.y() + 1, HEAD, HEAD - 1); }

    @Override
    public boolean mouseClicked(MouseButtonEvent e, boolean doubled) {
        double mx = e.x(), my = e.y();
        boolean left = e.button() == InputConstants.MOUSE_BUTTON_LEFT;
        if (panel != Panel.NONE && panelArea.contains(mx, my)) {
            controls.setFocused(null);
            return panelClicked(e, doubled, left);
        }
        panelWidgets.setFocused(null);
        if (left && backLink.contains(mx, my)) {
            if (PaletteStore.get().all().contains(cameFromPalette)) PaletteStore.get().select(cameFromPalette);
            ChromaScreen screen = ChromaScreen.current();
            if (screen != null) screen.select("palettes");
            return true;
        }
        if (controls.mouseClicked(e, doubled)) return true;
        if (Keys.RECIPE.matchesMouse(e) && lookUpRecipe()) return true;
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
            int i = (int) ((mx - recent.x()) / recentWidth());
            if (i >= 0 && i < colours.size()) MatchState.setColour(Color.ofRgb(colours.get(i)));
            return true;
        }
        if (!shownArea.contains(mx, my) || shown == null) return false;
        if (list.mouseClicked(e)) return true;
        int line = list.rowAt(mx, my);
        if (line < 0) return false;
        if (grid) {
            int col = (int) ((mx - results.x() - 2) / CELL), i = line * columns() + col;
            if (col < 0 || col >= columns() || i >= shown.matches.size()) return false;
            BlockIndex.Match m = shown.matches.get(i);
            act(new Row(RESULT, m.entry(), m.face(), m.distance(), false), Minecraft.getInstance().hasControlDown() ? ADD : BODY, e);
        } else if (line < rows.size()) {
            Row row = rows.get(line);
            int w = results.w();
            act(row, zone(row, results.x(), w - (rows.size() * ROW > list.area.h() ? Widgets.BAR_W + 2 : 0), mx), e);
        }
        return true;
    }

    private void act(Row row, int zone, MouseButtonEvent e) {
        Entry entry = row.entry;
        if (row.type == MAKE) {
            PaletteSink sink = PaletteSink.current();
            if (sink == null) {
                status(tr("no_palette"));
                return;
            }
            List<PaletteEntry> entries = BlockIndex.get().materialSet(entry).stream().map(m -> new PaletteEntry(m.id(), m.properties())).toList();
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
        } else if (e.hasShiftDown()) {
            status(Clipboard.copy(entry.toString()).orElseGet(() -> tr("copied", entry.toString())));
        } else {
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
            status(tr("matched", states.getFirst().name()));
        }
    }

    private void takeAxiom() { status(AxiomBridge.takeActiveBlock().message()); }

    private void armEyedropper() {
        Eyedropper.arm();
        Minecraft.getInstance().setScreen(null);
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent e) {
        boolean took = picker.mouseReleased();
        if (draggingDistance) {
            draggingDistance = false;
            took = true;
        }
        took |= list.mouseReleased();
        took |= panelList.mouseReleased();
        took |= controls.mouseReleased(e);
        took |= panelWidgets.mouseReleased(e);
        return took;
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent e, double dx, double dy) {
        if (picker.mouseDragged(e.x(), e.y())) return true;
        if (draggingDistance) {
            setDistance(e.x());
            return true;
        }
        if (panel != Panel.NONE && (panelList.mouseDragged(e) || panelWidgets.mouseDragged(e, dx, dy))) return true;
        return list.mouseDragged(e) || controls.mouseDragged(e, dx, dy);
    }

    @Override
    public boolean mouseScrolled(double x, double y, double h, double v) {
        if (v == 0) return false;
        if (panel != Panel.NONE && panelArea.contains(x, y)) return panelWidgets.mouseScrolled(x, y, h, v) || panelList.mouseScrolled(x, y, v);
        if (picker.mouseScrolled(x, y, v)) return true;
        if (distance.contains(x, y)) {
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
                fill(null);
                controls.setFocused(null);
                return true;
            }
            controls.keyPressed(e);
            return true;
        }
        if (Keys.RECIPE.matches(e) && lookUpRecipe()) return true;
        return controls.keyPressed(e);
    }

    private boolean lookUpRecipe() {
        if (hovered == null || hovered.type == MAKE) return false;
        ItemStack stack = new ItemStack(hovered.entry.block());
        if (!RecipeViewers.available()) status(tr("recipe.none"));
        else if (!RecipeViewers.show(stack)) status(tr("recipe.failed", hovered.entry.name()));
        return true;
    }

    @Override
    public boolean dismiss() {
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
        cameFrom = null;
        cameFromPalette = null;
        picker.close();
        Thumbnails.clear();
    }
}
