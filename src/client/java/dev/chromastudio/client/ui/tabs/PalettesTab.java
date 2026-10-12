package dev.chromastudio.client.ui.tabs;

import com.mojang.blaze3d.platform.InputConstants;
import dev.chromastudio.client.ModSettings;
import dev.chromastudio.client.PaletteStore;
import dev.chromastudio.client.action.Capture;
import dev.chromastudio.client.action.HotbarFill;
import dev.chromastudio.client.action.PaletteBlocks;
import dev.chromastudio.client.index.BlockIndex;
import dev.chromastudio.client.ui.Bounds;
import dev.chromastudio.client.ui.ChromaScreen;
import dev.chromastudio.client.ui.Widgets;
import dev.chromastudio.core.color.Color;
import dev.chromastudio.core.export.Layout;
import dev.chromastudio.core.palette.Palette;
import dev.chromastudio.core.palette.PaletteEntry;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.util.ARGB;
import net.minecraft.util.Util;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * The Palettes tab. On the left, the player's palettes and the presets, with the buttons that make, name, delete,
 * import and share them. On the right, the toolbar over the selected palette's entries, which the Analyse and Diorama
 * panels replace while they're open.
 */
public final class PalettesTab extends PanelTab {
    private enum Panel { ENTRIES, ANALYSE, DIORAMA }

    private static final int LIST_ROW = 12, ENTRY_ROW = 18, TITLE_H = 11, LEFT_MIN = 110;
    /** How long after the last change a palette is written, so typing a weight or a note doesn't write on every key. */
    private static final long SAVE_AFTER_MS = 400;

    private final PaletteStore store = PaletteStore.get();
    /** The entries' rows, which scroll on their own. */
    private final Widgets rows = new Widgets();
    private Widgets.ScrollList list;
    /** A palette for each row of the list, or null for the heading over the presets. */
    private List<Palette> listed = List.of();
    private Bounds entriesArea, titleArea;
    private Panel panel = Panel.ENTRIES;
    private AnalysePanel analyse;
    private DioramaPanel diorama;
    private boolean dirty;
    private long saveAt;
    /** What the layout was last built for, so a change from outside the tab, such as an added block, lays it out again. */
    private Palette shown;
    private int shownSize;
    private boolean shownReady;

    public PalettesTab() { super("palettes"); }

    private static Component tr(String key, Object... args) { return Component.translatable(key, args); }

    /** A palette's name: a preset's is translated, with the name in its file as the fallback. */
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
        panel = Panel.ENTRIES;
        analyse = null;
        if (diorama != null) diorama.close();
        diorama = null;
        rebuild();
    }

    // ---- Layout ----

    @Override
    protected void layout(Bounds b) {
        Palette p = selected();
        shown = p;
        shownSize = p == null ? 0 : p.entries.size();
        shownReady = BlockIndex.get().ready();
        rows.clear();

        int leftW = layoutList(b, p);
        int rx = b.x() + PAD + leftW + PAD + 1, rw = b.right() - PAD - rx;
        layoutToolbar(rx, b.y() + PAD, rw, p);
        titleArea = new Bounds(rx, b.y() + PAD + ROW, rw, TITLE_H);
        Bounds body = new Bounds(rx, titleArea.bottom() + GAP, rw, b.bottom() - PAD - titleArea.bottom() - GAP);
        entriesArea = body;
        switch (panel) {
            case ANALYSE -> analyse.layout(widgets, body, this::showEntries);
            case DIORAMA -> diorama.layout(widgets, body, this::showEntries, (layout, seed) -> open(new PaletteDialogs.Export(selected(), layout, seed)));
            default -> layoutEntries(body, p);
        }
    }

    /** The list of palettes and the two rows of buttons under it. Returns the column's width. */
    private int layoutList(Bounds b, Palette p) {
        record Action(String key, Runnable run, Component unavailable) {}
        Component needsOne = tr("palettes.chromastudio.need_palette"), presetFixed = tr("palettes.chromastudio.preset_fixed");
        Component editable = p == null ? needsOne : p.preset ? presetFixed : null;
        List<Action> top = List.of(
                new Action("new", () -> open(new PaletteDialogs.Name(tr("palettes.chromastudio.new.title"), tr("palettes.chromastudio.create"), "",
                        name -> { store.create(name, List.of()); showEntries(); })), null),
                new Action("rename", () -> open(new PaletteDialogs.Name(tr("palettes.chromastudio.rename.title"), tr("palettes.chromastudio.rename"), p.name,
                        name -> { flush(); store.rename(p, name); rebuild(); })), editable),
                new Action("delete", () -> open(new PaletteDialogs.Confirm(tr("palettes.chromastudio.delete.title"), tr("palettes.chromastudio.delete.question", p.name),
                        tr("palettes.chromastudio.delete"), () -> { dirty = false; store.delete(p); showEntries(); })), editable));
        List<Action> bottom = List.of(
                new Action("import", () -> open(new PaletteDialogs.Import(made -> { say(tr("palettes.chromastudio.imported", made.name), GOOD); showEntries(); })), null),
                new Action("share", () -> {
                    Minecraft.getInstance().keyboardHandler.setClipboard(PaletteDialogs.share(p));
                    say(tr("palettes.chromastudio.shared"), GOOD);
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
                else button.setTooltip(Tooltip.create(tr("palettes.chromastudio." + a.key + ".tip")));
                x += Widgets.buttonWidth(label) + GAP;
            }
            y += ROW;
        }

        List<Palette> all = new ArrayList<>(store.own());
        all.add(null);
        all.addAll(store.presets());
        listed = all;
        Bounds area = new Bounds(x0, b.y() + PAD, leftW, b.bottom() - PAD - 2 * ROW - b.y() - PAD);
        if (list == null) list = new Widgets.ScrollList(area, LIST_ROW, () -> listed.size());
        else list.area = area;
        if (p != null) list.reveal(listed.indexOf(p));
        return leftW;
    }

    private record Tool(String key, ItemStack icon, Runnable run, Component unavailable, boolean lit) {}

    private void layoutToolbar(int x0, int y, int width, Palette p) {
        Component needsBlocks = p == null ? tr("palettes.chromastudio.need_palette") : p.entries.isEmpty() ? tr("palettes.chromastudio.need_entries") : null;
        Component needsIndex = PaletteDialogs.indexReady() ? null : tr("palettes.chromastudio.index_building");
        List<Tool> tools = List.of(
                new Tool("generate", new ItemStack(Items.AMETHYST_SHARD), () -> open(new PaletteDialogs.Generate(matchTarget(), _ -> showEntries())), needsIndex, false),
                new Tool("capture", new ItemStack(Items.SPYGLASS), this::capture, Capture.unavailable().orElse(null), Capture.armed()),
                new Tool("image", new ItemStack(Items.PAINTING), () -> open(new PaletteDialogs.Image(_ -> showEntries())), needsIndex, false),
                new Tool("analyse", new ItemStack(Items.WRITABLE_BOOK), () -> togglePanel(Panel.ANALYSE), needsBlocks != null ? needsBlocks : needsIndex, panel == Panel.ANALYSE),
                new Tool("diorama", new ItemStack(Items.BRICKS), () -> togglePanel(Panel.DIORAMA), needsBlocks != null ? needsBlocks : needsIndex, panel == Panel.DIORAMA),
                new Tool("export", new ItemStack(Items.PAPER), () -> open(new PaletteDialogs.Export(p, Layout.valueOf(ModSettings.defaultLayout.name()), System.nanoTime())), needsBlocks, false),
                new Tool("worldedit", new ItemStack(Items.WOODEN_AXE), () -> open(new PaletteDialogs.WorldEdit(p)), needsBlocks, false),
                new Tool("hotbar", new ItemStack(Items.CHEST), () -> open(new PaletteDialogs.Hotbar(p.entries)),
                        needsBlocks != null ? needsBlocks : HotbarFill.unavailable().orElse(null), false));
        int labelled = (tools.size() - 1) * GAP;
        for (Tool t : tools) labelled += ToolButton.labelledWidth(tr("palettes.chromastudio.tool." + t.key));
        boolean labels = labelled <= width;
        int x = x0;
        for (Tool t : tools) {
            Component label = tr("palettes.chromastudio.tool." + t.key);
            int w = labels ? ToolButton.labelledWidth(label) : ToolButton.ICON;
            ToolButton button = widgets.add(new ToolButton(x, y, w, t.icon, label, labels, t.lit, t.run));
            Component tip = tr("palettes.chromastudio.tool." + t.key + ".tip");
            // With icons alone the tooltip also has to name the tool.
            Component named = labels ? tip : Component.empty().append(label).append("\n").append(tip.copy().withColor(DIM & 0xFFFFFF));
            if (t.unavailable != null) Widgets.disable(button, labels ? t.unavailable : Component.empty().append(label).append("\n").append(t.unavailable));
            else button.setTooltip(Tooltip.create(named));
            x += w + GAP;
        }
    }

    /**
     * The colour the Match tab is matching, for Generate to start from. The Match tab keeps its target to itself
     * until it has a way to share it, so for now this is what {@code /chroma} last asked for, if that was a colour.
     */
    private static Color matchTarget() { return ChromaScreen.pendingTarget instanceof Integer rgb ? Color.ofRgb(rgb) : null; }

    private void capture() {
        if (Capture.armed()) {
            Capture.cancel();
            rebuild();
            return;
        }
        flush();
        Capture.arm();
        // The corners are picked in the world, so the window gets out of the way.
        Minecraft.getInstance().gui.setScreen(null);
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
        int fieldsY = body.bottom() - Widgets.H;
        entriesArea = new Bounds(body.x(), body.y(), body.w(), fieldsY - GAP - body.y());
        Component fixed = p == null ? tr("palettes.chromastudio.need_palette") : p.preset ? tr("palettes.chromastudio.preset_fixed") : null;

        int tagsW = body.w() * 2 / 5;
        EditBox tags = widgets.search(body.x(), fieldsY, tagsW, tr("palettes.chromastudio.tags.hint"), s -> {
            if (p == null || p.preset) return;
            p.tags = new ArrayList<>(Arrays.stream(s.split(",")).map(String::strip).filter(t -> !t.isEmpty()).toList());
            touch();
        });
        EditBox notes = widgets.search(body.x() + tagsW + GAP, fieldsY, body.w() - tagsW - GAP, tr("palettes.chromastudio.notes.hint"), s -> {
            if (p == null || p.preset) return;
            p.notes = s;
            touch();
        });
        notes.setMaxLength(1024);
        if (p != null) {
            // The responders are told of these too, and would mark an untouched palette changed.
            boolean was = dirty;
            tags.setValue(String.join(", ", p.tags));
            notes.setValue(p.notes == null ? "" : p.notes);
            // From the start, so a long line shows its beginning rather than its end.
            tags.moveCursorToStart(false);
            notes.moveCursorToStart(false);
            dirty = was;
        }
        if (fixed != null) {
            Widgets.disable(tags, fixed);
            Widgets.disable(notes, fixed);
        } else {
            tags.setTooltip(Tooltip.create(tr("palettes.chromastudio.tags.tip")));
            notes.setTooltip(Tooltip.create(tr("palettes.chromastudio.notes.tip")));
        }
        if (p == null) return;

        int n = p.entries.size();
        boolean scrolls = n * ENTRY_ROW > entriesArea.h();
        int right = entriesArea.right() - (scrolls ? 6 : 0), x = entriesArea.x();
        int removeX = right - 12, downX = removeX - 13, upX = downX - 13, spinX = upX - 2 - 34;
        for (int i = 0; i < n; i++) {
            int index = i, y = entriesArea.y() + i * ENTRY_ROW;
            PaletteEntry e = p.entries.get(i);
            rows.add(new EntryCell(x, y, spinX - 3 - x, ENTRY_ROW - 1, e));
            EditBox weight = rows.spin(spinX, y + 1, 34, e.weight(), 1, 1, 999, true, v -> {
                p.entries.set(index, p.entries.get(index).withWeight((int) v));
                touch();
            });
            IconButton up = rows.add(new IconButton(upX, y + 1, 12, Widgets.H, IconButton.Glyph.UP, tr("palettes.chromastudio.entry.up"), () -> move(index, -1)));
            IconButton down = rows.add(new IconButton(downX, y + 1, 12, Widgets.H, IconButton.Glyph.DOWN, tr("palettes.chromastudio.entry.down"), () -> move(index, 1)));
            IconButton remove = rows.add(new IconButton(removeX, y + 1, 12, Widgets.H, IconButton.Glyph.REMOVE, tr("palettes.chromastudio.entry.remove"), () -> {
                p.entries.remove(index);
                changed();
            }));
            if (p.preset) for (AbstractWidget w : List.of(weight, up, down, remove)) Widgets.disable(w, fixed);
            else {
                weight.setTooltip(Tooltip.create(tr("palettes.chromastudio.entry.weight.tip")));
                if (i == 0) Widgets.disable(up, tr("palettes.chromastudio.entry.first"));
                if (i == n - 1) Widgets.disable(down, tr("palettes.chromastudio.entry.last"));
            }
        }
        rows.viewport(entriesArea, n * ENTRY_ROW);
    }

    private void move(int index, int by) {
        Palette p = selected();
        PaletteEntry e = p.entries.remove(index);
        p.entries.add(index + by, e);
        changed();
    }

    /** The entries changed in number or order: save and lay the rows out again. */
    private void changed() {
        touch();
        flush();
        rebuild();
    }

    private void touch() {
        dirty = true;
        saveAt = Util.getMillis() + SAVE_AFTER_MS;
    }

    private void flush() {
        if (!dirty) return;
        dirty = false;
        Palette p = selected();
        if (p != null && !p.preset && !store.save(p)) say(tr("palettes.chromastudio.save_failed"), BAD);
    }

    /** One entry's icon, name, state note and colour. It takes no clicks; its tooltip is the block's id. */
    private static final class EntryCell extends AbstractWidget {
        private final PaletteEntry entry;
        private final ItemStack icon;
        private final Color colour;
        private final boolean known;

        EntryCell(int x, int y, int w, int h, PaletteEntry entry) {
            super(x, y, w, h, PaletteBlocks.name(entry));
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
            Widgets.blockIcon(g, icon, x, y + 1);
            int swatch = 12, textX = x + 19, room = width - 19 - (colour != null ? swatch + 3 : 0);
            String name = fit(getMessage().getString(), room);
            g.text(font(), name, textX, y + 5, known ? TEXT : BAD, false);
            int used = font().width(name) + 4;
            if (!entry.properties().isEmpty() && room - used > 20) g.text(font(), fit(entry.properties(), room - used), textX + used, y + 5, DIM, false);
            if (colour != null) Widgets.swatch(g, x + width - swatch, y + 3, swatch, swatch, colour.rgb());
        }

        @Override
        protected void updateWidgetNarration(NarrationElementOutput builder) { }
    }

    // ---- Drawing ----

    @Override
    protected void draw(GuiGraphicsExtractor g, Bounds b, int mouseX, int mouseY, float delta) {
        Palette p = selected();
        g.fill(list.area.x(), list.area.y(), list.area.right(), list.area.bottom(), WELL);
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
            if (row == p) gg.fill(x, y, x + w, y + h, SELECTED);
            else if (hovered) gg.fill(x, y, x + w, y + h, HOVER);
            gg.text(font(), fit(name(row).getString(), w - 6), x + 3, y + 2, row.preset ? DIM : TEXT, false);
        });

        // A note about what just happened takes the whole line for its few seconds; else the name and the count share it.
        Component note = note();
        if (note != null) g.text(font(), fit(note.getString(), titleArea.w()), titleArea.x(), titleArea.y() + 1, noteColour(), false);
        else if (p != null) {
            String count = tr(p.preset ? "palettes.chromastudio.count_preset" : "palettes.chromastudio.count", p.entries.size()).getString();
            g.text(font(), count, titleArea.right() - font().width(count), titleArea.y() + 1, DIM, false);
            g.text(font(), fit(name(p).getString(), titleArea.w() - font().width(count) - 8), titleArea.x(), titleArea.y() + 1, TEXT, false);
        }

        switch (panel) {
            case ANALYSE -> analyse.draw(g, mouseX, mouseY);
            case DIORAMA -> diorama.draw(g, mouseX, mouseY);
            default -> {
                g.fill(entriesArea.x(), entriesArea.y(), entriesArea.right(), entriesArea.bottom(), WELL);
                Component hint = p == null ? tr("palettes.chromastudio.none_selected") : p.entries.isEmpty() ? tr("palettes.chromastudio.empty") : null;
                if (hint != null) g.textWithWordWrap(font(), hint, entriesArea.x() + 8, entriesArea.y() + 8, entriesArea.w() - 16, DIM, false);
            }
        }
    }

    @Override
    protected void drawOver(GuiGraphicsExtractor g, Bounds b, int mouseX, int mouseY, float delta) {
        if (panel == Panel.ENTRIES) rows.render(g, mouseX, mouseY, delta);
    }

    /** Pinned, the tab shows the selected palette's blocks: icon, name and colour, as many as fit. */
    @Override
    public void renderPinned(GuiGraphicsExtractor g, Bounds b, float opacity) {
        Palette p = selected();
        int text = ARGB.multiplyAlpha(TEXT, opacity), dim = ARGB.multiplyAlpha(DIM, opacity);
        if (p == null) {
            g.centeredText(font(), tr("palettes.chromastudio.none_selected_short"), b.x() + b.w() / 2, b.y() + b.h() / 2 - 4, dim);
            return;
        }
        g.text(font(), fit(name(p).getString(), b.w() - 2 * PAD), b.x() + PAD, b.y() + PAD, text, false);
        // Two columns when there's room, so a dozen blocks fit the smallest window.
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

    // ---- Input ----

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
        // Something outside the tab changed what it shows: a block added from Match, a capture, or the index finishing.
        boolean ready = BlockIndex.get().ready();
        if (!dialogOpen() && (p != shown || (p == null ? 0 : p.entries.size()) != shownSize || ready != shownReady)) {
            if (p != shown) showEntries();
            else rebuild();
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
