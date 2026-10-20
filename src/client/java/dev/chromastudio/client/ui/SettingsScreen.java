package dev.chromastudio.client.ui;

import dev.chromastudio.client.Keys;
import dev.chromastudio.client.ModSettings;
import dev.chromastudio.client.ThemeChoice;
import dev.chromastudio.client.index.BlockIndex;
import dev.chromastudio.client.input.CharacterEvent;
import dev.chromastudio.client.input.KeyEvent;
import dev.chromastudio.client.input.MouseButtonEvent;
import dev.chromastudio.core.color.Color;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Stream;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.options.controls.KeyBindsList;
import net.minecraft.client.gui.screens.options.controls.KeyBindsScreen;
import net.minecraft.network.chat.Component;

/**
 * The mod's settings: one scrolling list, grouped under headings, with a search over it. A change applies straight
 * away and is saved when the screen closes. Each group has a Reset, to its defaults, and an Undo, to what it was when
 * the screen opened; the bar at the bottom has both for everything. The rows come from {@link ModSettings#GROUPS}, so
 * a new setting gets one by being declared there, with its label and tooltip in the language file.
 */
public final class SettingsScreen extends Screen {
    private static final int TOP = 24, BOTTOM = 26, ROW = 20, HEADING = 22, CONTROL_W = 130, M = 12, GAP = 2;
    /**
     * A row with no setting behind it, which opens the mod's key binds. It sits where the recipe key's setting was before
     * that became a key bind. Its label and tooltip come from the language file under this name, for the search.
     */
    private static final String MOD_KEYS = "modKeys";

    private final Screen parent;
    /** Every setting as it was when the screen opened, which Undo goes back to. */
    private final Map<String, Object> atOpen = ModSettings.snapshot();
    /** The rows. The search field and the buttons along the bottom are the screen's own. */
    private final Widgets list = new Widgets();
    private String search = "";
    private EditBox searchField;
    /** Each group's Undo, and the one for everything under the name "". Each is greyed while it has nothing to undo. */
    private final Map<String, Button> undos = new HashMap<>();
    private boolean noRows;
    /** Set when the rows no longer show the settings, to lay them out again before the next frame rather than under a click. */
    private boolean stale;
    /** The confirmation or the colour dialog, while one is open. */
    private Popup popup;

    /** {@code parent} is the screen to go back to, or null to return to the game. */
    public SettingsScreen(Screen parent) {
        super(Component.translatable("settings.chromastudio.title"));
        this.parent = parent;
        list.wheelNeedsClick();
    }

    private int columnWidth() { return Math.min(340, width - 2 * M); }
    private int left() { return (width - columnWidth()) / 2; }

    private static Component tr(String key) { return Component.translatable("settings.chromastudio." + key); }

    @Override
    protected void init() {
        Theme.sync();
        int colW = columnWidth(), left = left();
        // The title keeps what the search field can spare, and the field at least half the row.
        int titleW = Math.min(font.width(title), colW / 2 - 8);
        searchField = new EditBox(font, left + titleW + 8, 4, colW - titleW - 8, Widgets.H, tr("search"));
        searchField.setMaxLength(64);
        searchField.setHint(tr("search"));
        searchField.setValue(search);
        searchField.setResponder(text -> {
            search = text;
            buildList();
        });
        addRenderableWidget(searchField);

        // Bottom bar: each button as wide as its text needs, or all of them narrower when a translation's won't fit.
        Component keys = tr("keys"), undoAll = tr("undo_all"), resetAll = tr("reset_all"), done = Component.translatable("gui.done");
        int[] w = {Widgets.buttonWidth(keys) + 6, Widgets.buttonWidth(undoAll), Widgets.buttonWidth(resetAll), Widgets.buttonWidth(done) + 14};
        int room = colW - 3 * GAP, wanted = w[0] + w[1] + w[2] + w[3];
        if (wanted > room) for (int i = 0; i < w.length; i++) w[i] = w[i] * room / wanted;
        int by = height - 22, right = left + colW;
        addRenderableWidget(Button.builder(keys, button -> openKeys()).bounds(left, by, w[0], 18).tooltip(Tooltip.create(tr("keys.tip"))).build());
        addRenderableWidget(Button.builder(done, button -> onClose()).bounds(right - w[3], by, w[3], 18).build());
        addRenderableWidget(Button.builder(resetAll, button -> open(new Confirm())).bounds(right - w[3] - GAP - w[2], by, w[2], 18)
                .tooltip(Tooltip.create(tr("reset_all.tip"))).build());
        Button undo = addRenderableWidget(Button.builder(undoAll, button -> {
            ModSettings.restore(atOpen);
            applied(true);
        }).bounds(right - w[3] - 2 * GAP - w[2] - w[1], by, w[1], 18).build());

        buildList();
        undos.put("", undo);
        showUndo("", undo);
        if (popup != null) popup.layout(width, height);
    }

    /** What a setting's row says, for the search: its label and its tooltip. */
    private static String text(String name) { return tr(name).getString() + " " + tr(name + ".tip").getString(); }

    /** Lays the rows out for the search as it stands. */
    private void buildList() {
        stale = false;
        list.clear();
        undos.keySet().removeIf(group -> !group.isEmpty());
        Map<String, List<String>> rows = new LinkedHashMap<>();
        ModSettings.GROUPS.forEach((group, names) -> rows.put(group, names.stream().filter(name -> !ModSettings.WINDOW_MEMORY.contains(name)).toList()));
        rows.computeIfPresent("integrations", (key, names) -> Stream.concat(Stream.of(MOD_KEYS), names.stream()).toList());
        Map<String, List<String>> shown = SettingsFilter.filter(rows, search, SettingsScreen::text);
        noRows = shown.isEmpty();

        int colW = columnWidth(), left = left(), y = TOP + 2;
        for (var group : shown.entrySet()) {
            String id = group.getKey();
            list.label(left, y + 8, tr("group." + id), Theme.STRONG);
            Component reset = tr("reset"), undo = tr("undo");
            int x = left + colW - Widgets.buttonWidth(reset);
            list.button(x, y + 4, reset, () -> {
                ModSettings.reset(id);
                applied(id.equals("window"));
            }).setTooltip(Tooltip.create(tr("reset.tip")));
            x -= GAP + Widgets.buttonWidth(undo);
            undos.put(id, list.button(x, y + 4, undo, () -> {
                ModSettings.restore(id, atOpen);
                applied(id.equals("window"));
            }));
            showUndo(id, undos.get(id));
            if (id.equals("index")) {
                // The cache is keyed by the packs' names, so it can't tell that a pack's files changed where they are.
                Component refresh = tr("refresh");
                list.button(x - GAP - Widgets.buttonWidth(refresh), y + 4, refresh, () -> BlockIndex.get().rebuild()).setTooltip(Tooltip.create(tr("refresh.tip")));
            }
            y += HEADING;
            for (String name : group.getValue()) {
                list.label(left + 4, y + 4, tr(name), Theme.SOFT);
                control(name, left + colW - CONTROL_W, y, tr(name + ".tip"));
                y += ROW;
            }
            y += 4;
        }
        list.viewport(new Bounds(0, TOP, width, height - TOP - BOTTOM), y - TOP);
    }

    /**
     * Settings changed other than through their own controls: a reset or an undo. The rows are laid out again to show
     * them, and the window behind takes its place and size again when those were among them.
     */
    private void applied(boolean window) {
        stale = true;
        if (window && parent instanceof ChromaScreen screen) screen.reloadWindow();
    }

    private static boolean changed(String group, Map<String, Object> since) {
        return group.isEmpty() ? ModSettings.changedSince(since) : ModSettings.changedSince(group, since);
    }

    /** Greys an Undo out while it has nothing to undo, and has its tooltip say so. */
    private void showUndo(String group, Button undo) {
        undo.active = changed(group, atOpen);
        String key = group.isEmpty() ? "undo_all" : "undo";
        undo.setTooltip(Tooltip.create(tr(undo.active ? key + ".tip" : key + ".none")));
    }

    private void open(Popup p) {
        popup = p;
        setFocused(null);
        list.setFocused(null);
        p.layout(width, height);
    }

    /** Asks before every setting goes back to its default. */
    private final class Confirm extends Popup {
        private static final int W = 200;

        Confirm() {
            super(tr("reset_all"), W, TITLE_H + 7 + 2 * 9 + 4 + Widgets.H, () -> popup = null);
        }

        @Override
        protected void build(Bounds in) {
            Component yes = tr("reset_all"), no = Component.translatable("gui.cancel");
            int noW = Widgets.buttonWidth(no), yesW = Widgets.buttonWidth(yes), y = in.bottom() - Widgets.H;
            widgets.button(in.right() - noW, y, no, this::close);
            widgets.button(in.right() - noW - GAP - yesW, y, yes, this::confirm);
        }

        @Override
        protected void draw(GuiGraphics g, Bounds in, int mouseX, int mouseY) {
            g.drawWordWrap(font, tr("reset_all.confirm"), in.x(), in.y(), in.w(), Theme.TEXT);
        }

        @Override
        protected void confirm() {
            ModSettings.reset();
            applied(true);
            close();
        }
    }

    /** A square of the colour a setting holds, in its row. It scrolls with the rows and takes no input. */
    private static final class Swatch extends AbstractWidget {
        private final String setting;

        Swatch(int x, int y, String setting) {
            super(x, y, Widgets.H, Widgets.H, Component.empty());
            this.setting = setting;
            active = false;
        }

        @Override
        protected void renderWidget(GuiGraphics g, int mouseX, int mouseY, float delta) {
            Widgets.swatch(g, getX(), getY(), width, height, Theme.base(ThemeChoice.CUSTOM, (String) ModSettings.get(setting)));
        }

        @Override
        protected void updateWidgetNarration(NarrationElementOutput builder) { }
    }

    /** The control for one setting, chosen by the setting's type, with its tooltip. */
    private void control(String name, int x, int y, Component tip) {
        if (name.equals(MOD_KEYS)) {
            list.button(x, y, CONTROL_W, tr("keys"), this::openKeys).setTooltip(Tooltip.create(tip));
            return;
        }
        AbstractWidget made = control(name, x, y, tip, ModSettings.type(name));
        // An option button already has its tooltip, and would lose one set here the first time it's clicked. A greyed
        // control's tooltip says why it is.
        if (!(made instanceof CycleButton) && made.active) made.setTooltip(Tooltip.create(tip));
    }

    private AbstractWidget control(String name, int x, int y, Component tip, Class<?> type) {
        Object value = ModSettings.get(name);
        // An on or off that chooses between two named things reads better as the names.
        if (name.equals("dioramaPerspective"))
            return list.cycler(x, y, CONTROL_W, null, List.of(false, true), (Boolean) value,
                    v -> Component.translatable("option.chromastudio.projection." + (v ? "perspective" : "orthographic")), v -> ModSettings.set(name, v), tip);
        if (type == boolean.class) return list.toggle(x, y, CONTROL_W, null, (Boolean) value, v -> ModSettings.set(name, v), tip);
        if (type == int.class || type == double.class) {
            boolean whole = type == int.class;
            return list.spin(x, y, CONTROL_W, ((Number) value).doubleValue(), whole ? 1 : 0.05, ModSettings.min(name), ModSettings.max(name),
                    whole, v -> ModSettings.set(name, v));
        }
        if (type.isEnum()) {
            String prefix = "option.chromastudio." + type.getSimpleName().toLowerCase(Locale.ROOT) + ".";
            return list.cycler(x, y, CONTROL_W, null, List.of((Object[]) type.getEnumConstants()), value,
                    v -> Component.translatable(prefix + ((Enum<?>) v).name().toLowerCase(Locale.ROOT)), v -> {
                        ModSettings.set(name, v);
                        // The rows are drawn in the theme's colours, and the custom colour's row is greyed by the theme.
                        if (name.equals("theme")) stale = true;
                    }, tip);
        }
        String text = (String) value;
        if (ModSettings.names(name) != null)
            return list.cycler(x, y, CONTROL_W, null, ModSettings.names(name), text,
                    v -> Component.translatable("option.chromastudio." + name.toLowerCase(Locale.ROOT) + "." + v.toLowerCase(Locale.ROOT)),
                    v -> ModSettings.set(name, v), tip);
        if (name.equals("startTab")) {
            List<String> ids = new ArrayList<>(ChromaScreen.tabIds());
            if (!ids.contains(text)) ids.add(text);   // a tab from a mod that's gone: keep it listed rather than change it silently
            return list.cycler(x, y, CONTROL_W, null, ids, text, v -> Component.translatable("tab.chromastudio." + v), v -> ModSettings.set(name, v), tip);
        }
        if (name.equals("customColour")) {
            list.add(new Swatch(x, y, name));
            Button pick = list.button(x + Widgets.H + GAP, y, CONTROL_W - Widgets.H - GAP, Component.literal(text.toUpperCase(Locale.ROOT)),
                    () -> open(new ColourDialog(tr(name), Color.ofRgb(Theme.base(ThemeChoice.CUSTOM, ModSettings.customColour)), c -> {
                        ModSettings.set(name, c.hex());
                        stale = true;
                    }, () -> popup = null)));
            return ModSettings.theme == ThemeChoice.CUSTOM ? pick : Widgets.disable(pick, tr(name + ".off"));
        }
        // Free text. Text the setting can't hold shows red and changes nothing.
        EditBox[] box = new EditBox[1];
        box[0] = list.field(x, y, CONTROL_W, text, v -> box[0].setTextColor(ModSettings.set(name, v) ? EditBox.DEFAULT_TEXT_COLOR : Theme.FIELD_BAD));
        return box[0];
    }

    /**
     * Opens the game's key binds screen. It has no way to show one category, so this scrolls its list to the mod's
     * heading. 1.21.1's headings hold no widget with their text, so this finds the heading's row the way the list
     * builds it: every key, sorted, with a heading before each new category, every row 20 pixels high.
     */
    private void openKeys() {
        KeyBindsScreen keys = new KeyBindsScreen(this, minecraft.options);
        minecraft.setScreen(keys);
        KeyMapping[] mappings = minecraft.options.keyMappings.clone();
        Arrays.sort(mappings);
        int row = 0;
        String category = null;
        for (KeyMapping k : mappings) {
            if (!k.getCategory().equals(category)) {
                category = k.getCategory();
                if (category.equals(Keys.CATEGORY)) break;
                row++;
            }
            row++;
        }
        for (GuiEventListener child : keys.children())
            if (child instanceof KeyBindsList binds) binds.setScrollAmount(row * 20);
    }

    @Override
    public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float delta) {
        super.renderBackground(g, mouseX, mouseY, delta);
        g.fill(0, 0, width, height, Theme.WINDOW);
        g.fill(0, 0, width, TOP - 1, Theme.BAR);
        g.fill(0, height - BOTTOM + 1, width, height, Theme.BAR);
        g.fill(0, TOP - 1, width, TOP, Theme.BORDER);
        g.fill(0, height - BOTTOM, width, height - BOTTOM + 1, Theme.BORDER);
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float delta) {
        Theme.sync();
        if (stale) buildList();
        undos.forEach((group, undo) -> {
            if (changed(group, atOpen) != undo.active) showUndo(group, undo);
        });
        // Under a popup nothing reacts to the cursor.
        int mx = popup == null ? mouseX : -1, my = popup == null ? mouseY : -1;
        super.render(g, mx, my, delta);
        int left = left();
        g.drawString(font, font.plainSubstrByWidth(title.getString(), searchField.getX() - 8 - left), left, 8, Theme.STRONG, false);
        list.render(g, mx, my, delta);
        Component none = tr("search.none");
        if (noRows) g.drawString(font, none, (width - font.width(none)) / 2, TOP + 16, Theme.DIM, false);
        if (popup != null) popup.render(g, mouseX, mouseY, delta);
    }

    public boolean mouseClicked(MouseButtonEvent event, boolean doubled) {
        if (popup != null) return popup.mouseClicked(event, doubled);
        if (list.mouseClicked(event, doubled)) {
            setFocused(null);
            return true;
        }
        return super.mouseClicked(event.x(), event.y(), event.button());
    }

    public boolean mouseReleased(MouseButtonEvent event) {
        if (popup != null) return popup.mouseReleased(event);
        return list.mouseReleased(event) || super.mouseReleased(event.x(), event.y(), event.button());
    }

    public boolean mouseDragged(MouseButtonEvent event, double dx, double dy) {
        if (popup != null) return popup.mouseDragged(event, dx, dy);
        return list.mouseDragged(event, dx, dy) || super.mouseDragged(event.x(), event.y(), event.button(), dx, dy);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double h, double v) {
        if (popup != null) return popup.mouseScrolled(mx, my, h, v) || true;
        return list.mouseScrolled(mx, my, h, v) || super.mouseScrolled(mx, my, h, v);
    }

    public boolean keyPressed(KeyEvent event) {
        if (popup != null) return popup.keyPressed(event);
        if (event.isEscape()) {
            // The first Escape only clears a search.
            if (search.isEmpty()) onClose();
            else searchField.setValue("");
            return true;
        }
        return list.keyPressed(event) || super.keyPressed(event.key(), event.scancode(), event.modifiers());
    }

    public boolean charTyped(CharacterEvent event) {
        if (popup != null) return popup.charTyped(event);
        return list.charTyped(event) || super.charTyped((char) event.codepoint(), event.modifiers());
    }

    // 1.21.1 hands input over as plain numbers. These make the events the screen and its controls take.

    @Override
    public boolean mouseClicked(double x, double y, int button) { return mouseClicked(MouseButtonEvent.of(x, y, button), MouseButtonEvent.doubled(x, y, button)); }

    @Override
    public boolean mouseReleased(double x, double y, int button) { return mouseReleased(MouseButtonEvent.of(x, y, button)); }

    @Override
    public boolean mouseDragged(double x, double y, int button, double dx, double dy) { return mouseDragged(MouseButtonEvent.of(x, y, button), dx, dy); }

    @Override
    public boolean keyPressed(int key, int scancode, int modifiers) { return keyPressed(new KeyEvent(key, scancode, modifiers)); }

    @Override
    public boolean charTyped(char c, int modifiers) { return charTyped(new CharacterEvent(c, modifiers)); }

    @Override
    public void removed() {
        if (popup != null) popup.close();
        ModSettings.save();
        super.removed();
    }

    @Override
    public void onClose() { minecraft.setScreen(parent); }
}
