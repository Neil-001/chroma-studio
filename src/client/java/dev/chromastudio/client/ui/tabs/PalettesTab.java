package dev.chromastudio.client.ui.tabs;

import static dev.chromastudio.client.ui.Theme.BAD;
import static dev.chromastudio.client.ui.Theme.BORDER;
import static dev.chromastudio.client.ui.Theme.DIM;
import static dev.chromastudio.client.ui.Theme.GOOD;
import static dev.chromastudio.client.ui.Theme.HOVER;
import static dev.chromastudio.client.ui.Theme.SELECTED;
import static dev.chromastudio.client.ui.Theme.TEXT;
import static dev.chromastudio.client.ui.Theme.WELL;

import com.mojang.blaze3d.platform.InputConstants;
import dev.chromastudio.client.ModSettings;
import dev.chromastudio.client.PaletteStore;
import dev.chromastudio.client.action.Capture;
import dev.chromastudio.client.action.Clipboard;
import dev.chromastudio.client.action.HotbarFill;
import dev.chromastudio.client.action.PaletteBlocks;
import dev.chromastudio.client.axiom.AxiomBridge;
import dev.chromastudio.client.index.BlockIndex;
import dev.chromastudio.client.ui.Bounds;
import dev.chromastudio.client.ui.Theme;
import dev.chromastudio.client.ui.Widgets;
import dev.chromastudio.core.color.Color;
import dev.chromastudio.core.export.Layout;
import dev.chromastudio.core.palette.BuildSample;
import dev.chromastudio.core.palette.Palette;
import dev.chromastudio.core.palette.PaletteEntry;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.Supplier;
import java.util.stream.Stream;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.InputWithModifiers;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.util.ARGB;
import net.minecraft.util.Util;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

public final class PalettesTab extends PanelTab {
    private enum Panel { ENTRIES, ANALYSE, DIORAMA }
    private static final int WEIGHT_W = 40, MIN_NAME_W = 60;

    private static final int LIST_ROW = 12, ENTRY_ROW = 18, TITLE_H = 11, LEFT_MIN = 110;
    private static final long SAVE_AFTER_MS = 400;

    private final PaletteStore store = PaletteStore.get();
    private Widgets rows = entryRows();
    private Widgets.ScrollList list;
    private List<Palette> listed = List.of();
    private String search = "";
    private Bounds entriesArea, entriesWell, listWell, titleArea;
    private int weightX = -1, shareRight, labelX, tagsY, notesY;
    private SmallButton copyToEdit;
    private EditBox tagsField, notesField;
    private int[] shownShares = new int[0];
    private Panel panel = Panel.ENTRIES;
    private AnalysePanel analyse;
    private DioramaPanel diorama;
    private boolean dirty;
    private boolean loading;
    private Palette edited;
    private long saveAt;
    private Palette shown;
    private int shownSize;
    private boolean shownReady;

    public PalettesTab() {
        super("palettes");
    }

    private static Widgets entryRows() {
        Widgets rows = new Widgets();
        rows.wheelNeedsClick();
        rows.scrollByRows(ENTRY_ROW);
        return rows;
    }

    private static Component tr(String key, Object... args) { return Component.translatable(key, args); }

    private Component name(Palette p) {
        String preset = store.presetId(p);
        return preset == null ? Component.literal(p.name) : Component.translatableWithFallback("preset.chromastudio." + preset, p.name);
    }

    private Palette selected() { return store.selected(); }

    private void select(Palette p) {
        flush();
        store.select(p);
        showEntries();
    }

    private void showEntries() {
        Palette p = selected();
        if (p != null && !p.preset && !store.saved(p)) say(tr("palettes.chromastudio.save_failed"), BAD);
        panel = Panel.ENTRIES;
        analyse = null;
        if (diorama != null) diorama.close();
        diorama = null;
        rebuild();
    }

    @Override
    protected void layout(Bounds b) {
        Palette p = selected();
        if (p != shown) rows = entryRows();
        shown = p;
        shownSize = p == null ? 0 : p.entries.size();
        shownReady = BlockIndex.get().ready();
        rows.clear();

        int leftW = layoutList(b, p);
        int rx = b.x() + PAD + leftW + PAD + 1, rw = b.right() - PAD - rx;
        int toolRows = layoutToolbar(rx, b.y() + PAD, rw, p);
        titleArea = new Bounds(rx, b.y() + PAD + toolRows * ROW, rw, TITLE_H);
        Bounds body = new Bounds(rx, titleArea.bottom() + GAP, rw, b.bottom() - PAD - titleArea.bottom() - GAP);
        entriesArea = entriesWell = body;
        weightX = -1;
        copyToEdit = null;
        switch (panel) {
            case ANALYSE -> analyse.layout(widgets, body, this::showEntries);
            case DIORAMA -> diorama.layout(widgets, body, this::showEntries, (layout, seed) -> open(new PaletteDialogs.Export(selected(), layout, seed)));
            default -> layoutEntries(body, p);
        }
    }

    private int layoutList(Bounds b, Palette p) {
        record Action(String key, Runnable run, Component unavailable) {}
        Component needsOne = tr("palettes.chromastudio.need_palette"), presetFixed = tr("palettes.chromastudio.preset_fixed");
        Component editable = p == null ? needsOne : p.preset ? presetFixed : null;
        List<Action> top = List.of(
                new Action("new", () -> open(new PaletteDialogs.Name(tr("palettes.chromastudio.new.title"), tr("palettes.chromastudio.create"), "",
                        name -> store.taken(name, null), name -> { store.create(name, List.of()); showEntries(); })), null),
                new Action("rename", () -> open(new PaletteDialogs.Name(tr("palettes.chromastudio.rename.title"), tr("palettes.chromastudio.rename"), p.name,
                        name -> store.taken(name, p), name -> { flush(); store.rename(p, name); rebuild(); })), editable),
                new Action("delete", () -> open(new PaletteDialogs.Confirm(tr("palettes.chromastudio.delete.title"), tr("palettes.chromastudio.delete.question"), p.name,
                        List.of(new PaletteDialogs.Confirm.Row(null, null, tr("palettes.chromastudio.count", p.entries.size()))), tr("palettes.chromastudio.delete"), () -> { dirty = false; store.delete(p); say(tr("palettes.chromastudio.deleted", p.name)); showEntries(); }, null)), editable));
        List<Action> bottom = List.of(
                new Action("import", () -> open(new PaletteDialogs.Import(made -> { say(tr("palettes.chromastudio.imported", made.name), GOOD); showEntries(); })), null),
                new Action("share", () -> {
                    Clipboard.copy(PaletteDialogs.share(p)).ifPresentOrElse(why -> say(why, BAD), () -> say(tr("palettes.chromastudio.shared"), GOOD));
                }, p == null ? needsOne : p.entries.isEmpty() ? tr("palettes.chromastudio.need_entries") : null),
                new Action("copy", () -> { flush(); store.copy(p); showEntries(); }, p == null ? needsOne : null));

        int leftW = LEFT_MIN;
        for (List<Action> row : List.of(top, bottom)) {
            int w = (row.size() - 1) * GAP;
            for (Action a : row) w += Widgets.buttonWidth(tr("palettes.chromastudio." + a.key));
            leftW = Math.max(leftW, w);
        }
        int x0 = b.x() + PAD, y = b.bottom() - PAD - 2 * ROW + GAP;
        for (List<Action> row : List.of(top, bottom)) {
            int x = x0;
            for (Action a : row) {
                Component label = tr("palettes.chromastudio." + a.key);
                Button button = widgets.button(x, y, label, a.run);
                if (a.unavailable != null) Widgets.disable(button, a.unavailable);
                else if (a.key.equals("copy")) button.setTooltip(Tooltip.create(tr("palettes.chromastudio.copy.tip")));
                x += Widgets.buttonWidth(label) + GAP;
            }
            y += ROW;
        }

        filter();
        listWell = new Bounds(x0, b.y() + PAD + ROW, leftW, b.bottom() - PAD - 3 * ROW - b.y() - PAD);
        Bounds area = new Bounds(listWell.x(), listWell.y(), leftW, listWell.h() - listWell.h() % LIST_ROW);
        if (list == null) list = new Widgets.ScrollList(area, LIST_ROW, () -> listed.size());
        else list.area = area;
        if (listed.contains(p)) list.reveal(listed.indexOf(p));
        EditBox field = widgets.search(x0, b.y() + PAD, leftW, tr("palettes.chromastudio.search"), s -> {
            search = s;
            filter();
        });
        field.setValue(search);
        return leftW;
    }

    private void filter() {
        List<Palette> all = new ArrayList<>();
        for (Palette own : store.own()) if (kept(own)) all.add(own);
        List<Palette> presets = store.presets().stream().filter(this::kept).toList();
        if (!presets.isEmpty()) all.add(null);
        all.addAll(presets);
        listed = all;
    }

    private boolean kept(Palette p) { return PaletteSearch.matches(search, name(p).getString(), p.tags); }

    private static Component whole(String text, Component reason) {
        if (text == null || text.isBlank()) return reason;
        return Component.empty().append(text).append("\n").append(reason.copy().withColor(Theme.TIP_DIM));
    }

    private record Tool(String key, ItemStack icon, Runnable run, Component unavailable, boolean lit) {}

    private int layoutToolbar(int x0, int y, int width, Palette p) {
        Component needsBlocks = p == null ? tr("palettes.chromastudio.need_palette") : p.entries.isEmpty() ? tr("palettes.chromastudio.need_entries") : null;
        Component needsIndex = PaletteDialogs.indexReady() ? null : tr("palettes.chromastudio.index_building");
        Component noAxiom = AxiomBridge.whyNot().orElse(null);
        List<Tool> tools = Stream.of(
                new Tool("generate", new ItemStack(Items.AMETHYST_SHARD), () -> open(new PaletteDialogs.Generate(MatchState.colour(), _ -> showEntries())), needsIndex, false),
                new Tool("capture", new ItemStack(Items.SPYGLASS), this::capture, Capture.unavailable().orElse(null), Capture.armed()),
                new Tool("image", new ItemStack(Items.PAINTING), () -> open(new PaletteDialogs.Image(_ -> showEntries())), needsIndex, false),
                new Tool("analyse", new ItemStack(Items.WRITABLE_BOOK), () -> togglePanel(Panel.ANALYSE), needsBlocks != null ? needsBlocks : needsIndex, panel == Panel.ANALYSE),
                new Tool("diorama", new ItemStack(Items.BRICKS), () -> togglePanel(Panel.DIORAMA), needsBlocks != null ? needsBlocks : needsIndex, panel == Panel.DIORAMA),
                new Tool("export", new ItemStack(Items.PAPER), () -> open(new PaletteDialogs.Export(p, Layout.valueOf(ModSettings.defaultLayout.name()), System.nanoTime())), needsBlocks, false),
                new Tool("worldedit", new ItemStack(Items.WOODEN_AXE), () -> open(new PaletteDialogs.WorldEdit(p)), needsBlocks, false),
                new Tool("axiom", new ItemStack(Items.BRUSH), () -> open(new PaletteDialogs.Axiom(p)),
                        noAxiom != null ? noAxiom : needsBlocks != null ? needsBlocks : needsIndex, false),
                new Tool("hotbar", new ItemStack(Items.CHEST), () -> open(new PaletteDialogs.Hotbar(p.entries)),
                        needsBlocks != null ? needsBlocks : HotbarFill.unavailable().orElse(null), false))
                .filter(t -> !t.key.equals("axiom") || AxiomBridge.isPresent()).toList();
        int[] widths = tools.stream().mapToInt(t -> ToolButton.labelledWidth(tr("palettes.chromastudio.tool." + t.key))).toArray();
        int n = tools.size(), cols = (n + 1) / 2, oneRow = (n - 1) * GAP, grid = (cols - 1) * GAP;
        int[] columns = new int[cols];
        for (int i = 0; i < n; i++) {
            oneRow += widths[i];
            columns[i % cols] = Math.max(columns[i % cols], widths[i]);
        }
        for (int c : columns) grid += c;
        boolean inGrid = oneRow > width && grid <= width, labels = oneRow <= width || inGrid;
        if (inGrid) for (int c = 0; c < cols; c++) columns[c] += (width - grid) / cols;
        int x = x0;
        for (int i = 0; i < n; i++) {
            Tool t = tools.get(i);
            Component label = tr("palettes.chromastudio.tool." + t.key);
            if (inGrid && i % cols == 0 && i > 0) {
                x = x0;
                y += ROW;
            }
            int w = !labels ? ToolButton.ICON : inGrid ? columns[i % cols] : widths[i];
            ToolButton button = widgets.add(new ToolButton(x, y, w, t.icon, label, labels, t.lit, t.run));
            if (t.unavailable != null) Widgets.disable(button, labels ? t.unavailable : Component.empty().append(label).append("\n").append(t.unavailable));
            else if (t.key.equals("capture")) {
                Component tip = tr(t.lit ? "palettes.chromastudio.tool.capture.cancel" : "palettes.chromastudio.tool.capture.tip");
                button.setTooltip(Tooltip.create(labels ? tip : Component.empty().append(label).append("\n").append(tip.copy().withColor(Theme.TIP_DIM))));
            }
            else if (!labels) button.setTooltip(Tooltip.create(label));
            x += w + GAP;
        }
        return inGrid ? 2 : 1;
    }

    private void capture() {
        if (Capture.armed()) {
            Capture.cancel();
            rebuild();
            return;
        }
        flush();
        Capture.arm();
        Minecraft.getInstance().gui.setScreen(null);
    }

    @Override
    protected boolean dismissPanel() {
        if (panel == Panel.ENTRIES) return false;
        showEntries();
        return true;
    }

    private void togglePanel(Panel which) {
        if (panel == which) {
            showEntries();
            return;
        }
        flush();
        showEntries();
        panel = which;
        if (which == Panel.ANALYSE) analyse = new AnalysePanel(selected());
        else diorama = new DioramaPanel(selected());
        rebuild();
    }

    private void layoutEntries(Bounds body, Palette p) {
        notesY = body.bottom() - Widgets.H;
        tagsY = notesY - ROW;
        entriesWell = new Bounds(body.x(), body.y(), body.w(), tagsY - GAP - body.y());
        entriesArea = new Bounds(body.x(), body.y(), body.w(), entriesWell.h() - entriesWell.h() % ENTRY_ROW);
        Component fixed = p == null ? tr("palettes.chromastudio.need_palette") : p.preset ? tr("palettes.chromastudio.preset_fixed") : null;

        labelX = body.x();
        int fieldX = labelX + Math.max(Widgets.tw(tr("palettes.chromastudio.tags.label")), Widgets.tw(tr("palettes.chromastudio.notes.label"))) + 5;
        int fieldW = body.right() - fieldX;
        EditBox tags = widgets.search(fieldX, tagsY, fieldW, hint("palettes.chromastudio.tags.hint", fieldW), s -> {
            if (loading || p == null || p.preset) return;
            p.tags = new ArrayList<>(Arrays.stream(s.split(",")).map(String::strip).filter(t -> !t.isEmpty()).toList());
            tagsField.setTooltip(Tooltip.create(whole(s, tr("palettes.chromastudio.tags.tip"))));
            touch();
        });
        EditBox notes = widgets.search(fieldX, notesY, fieldW, hint("palettes.chromastudio.notes.hint", fieldW), s -> {
            if (loading || p == null || p.preset) return;
            p.notes = s;
            notesField.setTooltip(Tooltip.create(whole(s, tr("palettes.chromastudio.notes.tip"))));
            touch();
        });
        tagsField = tags;
        notesField = notes;
        tags.setMaxLength(Integer.MAX_VALUE);
        notes.setMaxLength(Integer.MAX_VALUE);
        if (p != null) {
            loading = true;
            tags.setValue(String.join(", ", p.tags));
            notes.setValue(p.notes == null ? "" : p.notes);
            tags.moveCursorToStart(false);
            notes.moveCursorToStart(false);
            loading = false;
        }
        if (fixed != null) {
            Widgets.disable(tags, whole(String.join(", ", p == null ? List.of() : p.tags), fixed));
            Widgets.disable(notes, whole(p == null ? "" : p.notes, fixed));
        } else {
            tags.setTooltip(Tooltip.create(whole(String.join(", ", p.tags), tr("palettes.chromastudio.tags.tip"))));
            notes.setTooltip(Tooltip.create(whole(p.notes, tr("palettes.chromastudio.notes.tip"))));
        }
        if (p == null) return;

        int n = p.entries.size();
        int left = entriesArea.x(), right = entriesArea.right() - 6;
        int removeX = right - 12, shareX = removeX - 3 - font().width("100%"), spinX = shareX - 3 - WEIGHT_W, cellX = left + 13;
        if (n > 0) weightX = spinX + WEIGHT_W - 9;
        shareRight = removeX - 3;
        if (p.preset) {
            Component label = tr("palettes.chromastudio.copy_to_edit");
            int w = Widgets.tw(label) + 6;
            int bx = (n > 0 ? weightX - Widgets.tw(tr("palettes.chromastudio.entry.weight")) - 4 : titleArea.right() - 3) - w;
            if (bx - 4 - titleArea.x() - 3 >= Math.min(Widgets.tw(name(p)), MIN_NAME_W)) {
                copyToEdit = widgets.add(new SmallButton(bx, titleArea.y() - 1, w, TITLE_H + 2, label, () -> { flush(); store.copy(p); showEntries(); }));
                copyToEdit.setTooltip(Tooltip.create(tr("palettes.chromastudio.copy.tip")));
            }
        }
        for (int i = 0; i < n; i++) {
            int index = i, y = entriesArea.y() + i * ENTRY_ROW;
            PaletteEntry e = p.entries.get(i);
            rows.add(new EntryCell(cellX, y, spinX - 3 - cellX, ENTRY_ROW - 1, e, index, i % 2 == 1 ? left : 0, right, shareRight, () -> shownShares));
            IconButton up = rows.add(new IconButton(left + 1, y + 1, 10, 8, IconButton.Glyph.UP, tr("palettes.chromastudio.entry.up"), () -> move(index, -1)));
            IconButton down = rows.add(new IconButton(left + 1, y + 9, 10, 8, IconButton.Glyph.DOWN, tr("palettes.chromastudio.entry.down"), () -> move(index, 1)));
            if (p.preset) rows.add(new EntryCell.Weight(spinX, y, WEIGHT_W, ENTRY_ROW - 1, e.weight(), fixed));
            EditBox weight = p.preset ? null : rows.spin(spinX, y + 1, WEIGHT_W, e.weight(), 1, 1, BuildSample.MAX_WEIGHT, true, v -> {
                p.entries.set(index, p.entries.get(index).withWeight((int) v));
                touch();
            });
            IconButton remove = rows.add(new IconButton(removeX, y + 1, 12, Widgets.H, IconButton.Glyph.REMOVE, tr("palettes.chromastudio.entry.remove"), () -> {
                p.entries.remove(index);
                changed();
            }));
            if (p.preset) {
                for (AbstractWidget w : List.of(up, down, remove)) Widgets.disable(w, fixed);
                continue;
            }
            weight.setTooltip(Tooltip.create(tr("palettes.chromastudio.entry.weight.tip", 1, BuildSample.MAX_WEIGHT)));
            if (i == 0) Widgets.disable(up, tr("palettes.chromastudio.entry.first"));
            if (i == n - 1) Widgets.disable(down, tr("palettes.chromastudio.entry.last"));
        }
        rows.viewport(entriesArea, n * ENTRY_ROW);
    }

    private static Component hint(String key, int fieldW) { return Component.literal(fit(tr(key).getString(), fieldW - 8)); }

    private void move(int index, int by) {
        Palette p = selected();
        PaletteEntry e = p.entries.remove(index);
        p.entries.add(index + by, e);
        changed();
    }

    private void changed() {
        touch();
        flush();
        rebuild();
    }

    private void touch() {
        if (dirty && edited != selected()) flush();
        dirty = true;
        edited = selected();
        saveAt = Util.getMillis() + SAVE_AFTER_MS;
    }

    private void flush() {
        if (!dirty) return;
        dirty = false;
        Palette p = edited;
        edited = null;
        if (p != null && !p.preset && store.own().contains(p) && !store.save(p)) say(tr("palettes.chromastudio.save_failed"), BAD);
    }

    private static final class EntryCell extends AbstractWidget {
        private final int index, stripeFrom, stripeTo, shareRight;
        private final Supplier<int[]> shares;
        private final PaletteEntry entry;
        private final ItemStack icon;
        private final Color colour;
        private final boolean known;

        EntryCell(int x, int y, int w, int h, PaletteEntry entry, int index, int stripeFrom, int stripeTo, int shareRight, Supplier<int[]> shares) {
            super(x, y, w, h, PaletteBlocks.name(entry));
            this.index = index;
            this.stripeFrom = stripeFrom;
            this.stripeTo = stripeTo;
            this.shareRight = shareRight;
            this.shares = shares;
            this.entry = entry;
            icon = PaletteBlocks.icon(entry);
            colour = PaletteBlocks.colour(entry);
            known = PaletteBlocks.state(entry) != null;
            active = false;
            setTooltip(Tooltip.create(known ? Component.literal(entry.idWithProperties()) : Component.translatable("palettes.chromastudio.entry.unknown", entry.idWithProperties())));
        }

        @Override
        protected void extractWidgetRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta) {
            int x = getX(), y = getY();
            if (stripeFrom > 0) g.fill(stripeFrom, y, stripeTo, y + ENTRY_ROW, Theme.PANEL);
            int[] all = shares.get();
            if (index < all.length) {
                String share = all[index] + "%";
                g.text(font(), share, shareRight - font().width(share), y + 5, DIM, false);
            }
            Widgets.blockIcon(g, icon, x, y + 1);
            int swatch = 12, textX = x + 19, room = width - 19 - (colour != null ? swatch + 5 : 0);
            String name = fit(getMessage().getString(), room);
            g.text(font(), name, textX, y + 5, known ? TEXT : BAD, false);
            int used = font().width(name) + 4;
            if (!entry.properties().isEmpty() && room - used > 20) g.text(font(), fit(entry.properties(), room - used), textX + used, y + 5, DIM, false);
            if (colour != null) {
                int sx = x + width - swatch - 1, sy = y + 3;
                g.fill(sx - 1, sy - 1, sx + swatch + 1, sy + swatch + 1, DIM);
                Widgets.swatch(g, sx, sy, swatch, swatch, colour.rgb());
            }
        }

        @Override
        protected void updateWidgetNarration(NarrationElementOutput builder) { }

        private static final class Weight extends AbstractWidget {
            Weight(int x, int y, int w, int h, int weight, Component why) {
                super(x, y, w, h, Component.literal(String.valueOf(weight)));
                active = false;
                setTooltip(Tooltip.create(why));
            }

            @Override
            protected void extractWidgetRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta) {
                g.text(font(), getMessage(), getX() + 4, getY() + 5, DIM, false);
            }

            @Override
            protected void updateWidgetNarration(NarrationElementOutput builder) { defaultButtonNarrationText(builder); }
        }
    }

    static int[] shares(List<PaletteEntry> entries) {
        long total = Math.max(1, entries.stream().mapToLong(PaletteEntry::weight).sum());
        int left = 100;
        int[] shares = new int[entries.size()];
        Integer[] order = new Integer[shares.length];
        for (int i = 0; i < shares.length; i++) {
            shares[i] = (int) (100L * entries.get(i).weight() / total);
            left -= shares[i];
            order[i] = i;
        }
        Arrays.sort(order, (a, b) -> Long.compare(100L * entries.get(b).weight() % total, 100L * entries.get(a).weight() % total));
        for (int i = 0; i < left && i < order.length; i++) shares[order[i]]++;
        return shares;
    }

    private static final class SmallButton extends AbstractButton {
        private final Runnable action;

        SmallButton(int x, int y, int w, int h, Component label, Runnable action) {
            super(x, y, w, h, label);
            this.action = action;
        }

        @Override
        public void onPress(InputWithModifiers input) { action.run(); }

        @Override
        protected void extractContents(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta) {
            g.fill(getX(), getY(), getRight(), getBottom(), isHovered() ? Theme.LIT_HOVER : Theme.LIT);
            Widgets.centred(g, getMessage(), getX() + width / 2, getY() + (height - 8) / 2, Theme.ON_LIT);
        }

        @Override
        protected void updateWidgetNarration(NarrationElementOutput builder) { defaultButtonNarrationText(builder); }
    }

    @Override
    protected void draw(GuiGraphicsExtractor g, Bounds b, int mouseX, int mouseY, float delta) {
        Palette p = selected();
        g.fill(listWell.x(), listWell.y(), listWell.right(), listWell.bottom(), WELL);
        if (listed.size() * LIST_ROW > list.area.h()) track(g, list.area);
        list.render(g, mouseX, mouseY, (gg, i, x, y, w, h, hovered) -> {
            Palette row = listed.get(i);
            if (row == null) {
                gg.fill(x + 2, y + h / 2, x + w - 2, y + h / 2 + 1, BORDER);
                String heading = tr("palettes.chromastudio.presets").getString();
                int tw = font().width(heading);
                gg.fill(x + (w - tw) / 2 - 3, y, x + (w + tw) / 2 + 3, y + h, WELL);
                gg.text(font(), heading, x + (w - tw) / 2, y + 2, DIM, false);
                return;
            }
            if (row == p) {
                gg.fill(x, y, x + w, y + h, SELECTED);
                gg.fill(x, y, x + 2, y + h, Theme.ACCENT);
            } else if (hovered) gg.fill(x, y, x + w, y + h, HOVER);
            String count = String.valueOf(row.entries.size());
            int countW = font().width(count);
            gg.text(font(), count, x + w - 3 - countW, y + 2, DIM, false);
            gg.text(font(), fit(name(row).getString(), w - 10 - countW), x + 4, y + 2, row.preset ? Theme.SOFT : TEXT, false);
        });
        if (listed.isEmpty()) g.textWithWordWrap(font(), tr("palettes.chromastudio.search.none"), list.area.x() + 4, list.area.y() + 4, list.area.w() - 8, DIM, false);

        Component note = note();
        g.fill(titleArea.x(), titleArea.y() - 1, titleArea.right(), titleArea.bottom(), Theme.PANEL);
        if (note != null) g.text(font(), fit(note.getString(), titleArea.w() - 6), titleArea.x() + 3, titleArea.y() + 1, noteColour(), false);
        else if (p != null) title(g, p);
        else if (panel == Panel.ENTRIES) g.text(font(), tr("palettes.chromastudio.none_selected_short"), titleArea.x() + 3, titleArea.y() + 1, DIM, false);
        if (copyToEdit != null) copyToEdit.visible = note == null;

        switch (panel) {
            case ANALYSE -> analyse.draw(g, mouseX, mouseY);
            case DIORAMA -> diorama.draw(g, mouseX, mouseY);
            default -> {
                g.fill(entriesWell.x(), entriesWell.y(), entriesWell.right(), entriesWell.bottom(), WELL);
                track(g, entriesArea);
                g.text(font(), tr("palettes.chromastudio.tags.label"), labelX, tagsY + 4, DIM, false);
                g.text(font(), tr("palettes.chromastudio.notes.label"), labelX, notesY + 4, DIM, false);
                Component hint = p == null ? tr("palettes.chromastudio.none_selected") : p.entries.isEmpty() ? tr("palettes.chromastudio.empty") : null;
                if (hint != null) {
                    Component tools = tr(p == null ? "palettes.chromastudio.tools_need_palette" : "palettes.chromastudio.tools_need_blocks");
                    int w = entriesArea.w() - 16, y = entriesArea.y() + 8;
                    g.textWithWordWrap(font(), hint, entriesArea.x() + 8, y, w, TEXT, false);
                    y += font().wordWrapHeight(hint, w) + 6;
                    g.textWithWordWrap(font(), tools, entriesArea.x() + 8, y, w, DIM, false);
                }
            }
        }
    }

    private void title(GuiGraphicsExtractor g, Palette p) {
        int y = titleArea.y() + 1, x = titleArea.x() + 3, right = titleArea.right() - 3;
        if (panel == Panel.ENTRIES && weightX >= 0) {
            String weight = tr("palettes.chromastudio.entry.weight").getString(), share = tr("palettes.chromastudio.entry.share").getString();
            int wx = weightX - font().width(weight), sx = shareRight - font().width(share);
            g.text(font(), weight, wx, y, DIM, false);
            if (wx + font().width(weight) + 4 <= sx) g.text(font(), share, sx, y, DIM, false);
            right = wx - 8;
        }
        if (copyToEdit != null) right = copyToEdit.getX() - 4;
        String count = tr("palettes.chromastudio.count", p.entries.size()).getString();
        String full = name(p).getString();
        int countW = font().width(count), nameW = font().width(full);
        String badge = p.preset ? tr("palettes.chromastudio.read_only").getString() : "";
        int badgeFull = badge.isEmpty() ? 0 : font().width(badge) + 11;
        if (x + nameW + badgeFull + countW + 10 <= right) {
            g.text(font(), count, right - countW, y, DIM, false);
            right -= countW + 10;
        }
        int room = right - x;
        int badgeW = badge.isEmpty() || Math.min(nameW, MIN_NAME_W) + 4 + font().width(badge) + 6 > room ? 0 : font().width(badge) + 6;
        String name = fit(full, room - (badgeW > 0 ? badgeW + 4 : 0));
        g.text(font(), name, x, y, TEXT, false);
        if (badgeW == 0) return;
        int bx = x + font().width(name) + 4;
        g.fill(bx, titleArea.y() - 1, bx + badgeW, titleArea.bottom(), Theme.CHIP);
        g.text(font(), fit(badge, badgeW - 6), bx + 3, y, Theme.SOFT, false);
    }

    private static void track(GuiGraphicsExtractor g, Bounds area) {
        g.fill(area.right() - Widgets.BAR_W, area.y(), area.right() - 1, area.bottom(), HOVER);
    }

    @Override
    protected void drawOver(GuiGraphicsExtractor g, Bounds b, int mouseX, int mouseY, float delta) {
        if (panel != Panel.ENTRIES) return;
        boolean inside = entriesArea.contains(mouseX, mouseY);
        Palette p = selected();
        shownShares = p == null ? new int[0] : shares(p.entries);
        rows.render(g, inside ? mouseX : -1, inside ? mouseY : -1, delta);
        if (p != null && p.entries.size() * ENTRY_ROW - rows.scrolled() > entriesArea.h())
            g.fillGradient(entriesArea.x(), entriesArea.bottom() - 6, entriesArea.right() - 6, entriesArea.bottom(), WELL & 0xFFFFFF, WELL);
    }

    @Override
    public void renderPinned(GuiGraphicsExtractor g, Bounds b, float opacity) {
        Palette p = selected();
        int text = ARGB.multiplyAlpha(TEXT, opacity), dim = ARGB.multiplyAlpha(DIM, opacity);
        if (p == null) {
            Widgets.centred(g, tr("palettes.chromastudio.none_selected_short"), b.x() + b.w() / 2, b.y() + b.h() / 2 - 4, dim);
            return;
        }
        g.text(font(), fit(name(p).getString(), b.w() - 2 * PAD), b.x() + PAD, b.y() + PAD, text, false);
        int top = b.y() + PAD + TITLE_H, perColumn = Math.max(1, (b.bottom() - PAD - top) / ENTRY_ROW);
        int columns = Math.max(1, Math.min(b.w() / 150, (p.entries.size() + perColumn - 1) / perColumn)), columnW = (b.w() - 2 * PAD) / columns;
        for (int i = 0; i < Math.min(p.entries.size(), perColumn * columns); i++) {
            PaletteEntry e = p.entries.get(i);
            int x = b.x() + PAD + i / perColumn * columnW, y = top + i % perColumn * ENTRY_ROW;
            Widgets.blockIcon(g, PaletteBlocks.icon(e), x, y);
            Color colour = PaletteBlocks.colour(e);
            if (colour != null) g.fill(x + 19, y + 3, x + 29, y + 13, ARGB.multiplyAlpha(0xFF000000 | colour.rgb(), opacity));
            g.text(font(), fit(PaletteBlocks.name(e).getString(), columnW - 36), x + 33, y + 4, text, false);
        }
    }

    @Override
    protected boolean clicked(MouseButtonEvent e, boolean doubled) {
        if (list.mouseClicked(e)) return true;
        int row = list.rowAt(e.x(), e.y());
        if (row >= 0 && e.button() == InputConstants.MOUSE_BUTTON_LEFT) {
            Palette p = listed.get(row);
            if (p != null && p != selected()) select(p);
            return p != null;
        }
        return switch (panel) {
            case ANALYSE -> analyse.clicked(e);
            case DIORAMA -> diorama.clicked(e);
            default -> rows.mouseClicked(e, doubled);
        };
    }

    @Override
    protected boolean released(MouseButtonEvent e) {
        boolean bar = list.mouseReleased();
        boolean body = switch (panel) {
            case ANALYSE -> analyse.released();
            case DIORAMA -> diorama.released();
            default -> rows.mouseReleased(e);
        };
        return body || bar;
    }

    @Override
    protected boolean dragged(MouseButtonEvent e, double dx, double dy) {
        if (list.mouseDragged(e)) return true;
        return switch (panel) {
            case ANALYSE -> analyse.dragged(e);
            case DIORAMA -> diorama.dragged(dx, dy);
            default -> rows.mouseDragged(e, dx, dy);
        };
    }

    @Override
    protected boolean scrolled(double x, double y, double h, double v) {
        if (list.mouseScrolled(x, y, v)) return true;
        return switch (panel) {
            case ANALYSE -> analyse.scrolled(x, y, v);
            case DIORAMA -> false;
            default -> rows.mouseScrolled(x, y, h, v);
        };
    }

    @Override
    protected boolean pressed(KeyEvent e) { return panel == Panel.ENTRIES && rows.keyPressed(e); }

    @Override
    protected boolean typed(CharacterEvent e) { return panel == Panel.ENTRIES && rows.charTyped(e); }

    @Override
    protected boolean typingElsewhere() { return panel == Panel.ENTRIES && rows.typing(); }

    @Override
    public void tick() {
        super.tick();
        if (dirty && Util.getMillis() >= saveAt) flush();
        Palette p = selected();
        boolean ready = BlockIndex.get().ready();
        if (!dialogOpen() && (p != shown || (p == null ? 0 : p.entries.size()) != shownSize || ready != shownReady)) {
            if (p != shown) {
                flush();
                showEntries();
            } else rebuild();
        }
    }

    @Override
    public void closed() {
        flush();
        if (diorama != null) diorama.close();
        diorama = null;
        if (panel == Panel.DIORAMA) panel = Panel.ENTRIES;
    }
}
