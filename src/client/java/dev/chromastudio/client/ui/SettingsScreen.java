package dev.chromastudio.client.ui;

import com.mojang.blaze3d.platform.InputConstants;
import dev.chromastudio.client.Keys;
import dev.chromastudio.client.ModSettings;
import dev.chromastudio.client.ThemeChoice;
import dev.chromastudio.client.axiom.AxiomBridge;
import dev.chromastudio.client.index.BlockIndex;
import dev.chromastudio.core.color.Color;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Stream;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
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
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentUtils;

public final class SettingsScreen extends Screen {
    private static final int TOP = 24, BOTTOM = 26, ROW = 20, HEADING = 22, CONTROL_W = 130, M = 12, GAP = 2, FADE = 6, COUNT_H = 14;
    private static final String MOD_KEYS = "modKeys";

    private final Screen parent;
    private final Map<String, Object> atOpen = ModSettings.snapshot();
    private final Widgets list = new Widgets();
    private String search = "", current;
    private EditBox searchField;
    private final Map<String, Button> undos = new HashMap<>();
    private final Map<String, Button> resets = new HashMap<>();
    private final Map<String, Integer> groupTops = new LinkedHashMap<>();
    private final List<CycleButton<Boolean>> toggles = new ArrayList<>();
    private final List<CycleButton<?>> cyclers = new ArrayList<>();
    private final List<Integer> stops = new ArrayList<>();
    private Button clear, jump;
    private int searchW, contentH, shownRows;
    private boolean noRows;
    private boolean stale;
    private Popup popup;

    public SettingsScreen(Screen parent) {
        super(Component.translatable("settings.chromastudio.title"));
        this.parent = parent;
        list.wheelNeedsClick();
    }

    private int listBottom() { return height - BOTTOM; }
    private int columnWidth() { return Math.min(340, width - 2 * M); }
    private int left() { return (width - columnWidth()) / 2; }

    private static Component tr(String key) { return Component.translatable("settings.chromastudio." + key); }

    @Override
    protected void init() {
        Theme.sync();
        int colW = columnWidth(), left = left();
        int jumpW = ModSettings.GROUPS.keySet().stream().mapToInt(id -> font.width(tr("group." + id))).max().orElse(0) + 28;
        jumpW = Math.min(jumpW, colW * 3 / 8);
        int titleW = Math.min(font.width(title), colW - 8 - GAP - jumpW - 100);
        searchW = colW - titleW - 8 - GAP - jumpW;
        searchField = new EditBox(font, left + titleW + 8, 4, searchW, Widgets.H, tr("search"));
        searchField.setMaxLength(64);
        searchField.setHint(tr("search"));
        searchField.setValue(search);
        searchField.setResponder(text -> {
            search = text;
            buildList();
        });
        addRenderableWidget(searchField);
        clear = addRenderableWidget(Button.builder(Component.literal("×"), _ -> searchField.setValue(""))
                .bounds(searchField.getX() + searchW - Widgets.H, 4, Widgets.H, Widgets.H).tooltip(Tooltip.create(tr("clear"))).build());
        jump = addRenderableWidget(Button.builder(tr("jump"), _ -> open(new Sections()))
                .bounds(left + colW - jumpW, 4, jumpW, Widgets.H).build());

        Component undoAll = tr("undo_all"), resetAll = tr("reset_all"), doneLabel = Component.translatable("gui.done");
        int[] w = {Widgets.buttonWidth(undoAll), Widgets.buttonWidth(resetAll), Widgets.buttonWidth(doneLabel) + 14};
        int room = colW - 2 * GAP - 12, wanted = w[0] + w[1] + w[2];
        if (wanted > room) for (int i = 0; i < w.length; i++) w[i] = w[i] * room / wanted;
        int by = height - 22, right = left + colW;
        Button undo = addRenderableWidget(Button.builder(undoAll, _ -> {
            ModSettings.restore(atOpen);
            applied(true);
        }).bounds(left, by, w[0], 18).build());
        Button reset = addRenderableWidget(Button.builder(resetAll, _ -> open(new Confirm())).bounds(left + w[0] + 3 * GAP, by, w[1], 18).build());
        addRenderableWidget(Button.builder(doneLabel, _ -> onClose()).bounds(right - w[2], by, w[2], 18).build());

        buildList();
        undos.put("", undo);
        resets.put("", reset);
        showUndo("", undo);
        showReset("", reset);
        if (popup != null) popup.layout(width, height);
    }

    private static String text(String name) { return tr(name).getString() + " " + tr(name + ".tip").getString(); }

    private void buildList() {
        stale = false;
        list.clear();
        undos.keySet().removeIf(group -> !group.isEmpty());
        resets.keySet().removeIf(group -> !group.isEmpty());
        groupTops.clear();
        toggles.clear();
        cyclers.clear();
        stops.clear();
        Map<String, List<String>> rows = new LinkedHashMap<>();
        ModSettings.GROUPS.forEach((group, names) -> rows.put(group, names.stream().filter(SettingsScreen::shown).toList()));
        rows.computeIfPresent("window", (_, names) -> Stream.concat(Stream.of(MOD_KEYS), names.stream()).toList());
        Map<String, List<String>> shown = SettingsFilter.filter(rows, search, SettingsScreen::text);
        noRows = shown.isEmpty();
        shownRows = shown.values().stream().mapToInt(List::size).sum();

        int colW = columnWidth(), left = left(), y = TOP + 2;
        for (var group : shown.entrySet()) {
            String id = group.getKey();
            groupTops.put(id, y - TOP - 2);
            stops.add(y - TOP - 2);
            list.add(new Band(left - 4, y + 1, colW + 8, HEADING - 2, null, false, 0, 0));
            list.label(left, y + 8, tr("group." + id), Theme.ACCENT);
            Component reset = tr("reset"), undo = tr("undo");
            int x = left + colW - Widgets.buttonWidth(reset);
            resets.put(id, list.button(x, y + 4, reset, () -> {
                ModSettings.reset(id);
                applied(id.equals("window"));
            }));
            showReset(id, resets.get(id));
            x -= GAP + Widgets.buttonWidth(undo);
            undos.put(id, list.button(x, y + 4, undo, () -> {
                ModSettings.restore(id, atOpen);
                applied(id.equals("window"));
            }));
            showUndo(id, undos.get(id));
            if (id.equals("index")) {
                Component refresh = tr("refresh");
                list.button(x - GAP - Widgets.buttonWidth(refresh), y + 4, refresh, () -> BlockIndex.get().rebuild()).setTooltip(Tooltip.create(tr("refresh.tip")));
            }
            y += HEADING;
            boolean stripe = false;
            for (String name : group.getValue()) {
                stops.add(y - TOP - 3);
                Band band = list.add(new Band(left - 4, y - 1, colW + 8, ROW - 2, name, stripe, left + 4 + font.width(tr(name)), left + colW - CONTROL_W));
                stripe = !stripe;
                AbstractWidget made = control(name, left + colW - CONTROL_W, y, tr(name + ".tip"));
                band.note = note(name);
                band.enabled = made.active;
                list.label(left + 4, y + 4, tr(name), made.active ? Theme.SOFT : Theme.DIM);
                y += ROW;
            }
            y += 4;
        }
        y += 6;
        if (!search.isEmpty() && !shown.isEmpty()) y += COUNT_H;
        contentH = y - TOP;
        list.viewport(new Bounds(left - 4, TOP, colW + 16, listBottom() - TOP), contentH);
        clear.visible = !search.isEmpty();
        searchField.setWidth(clear.visible ? searchW - Widgets.H - GAP : searchW);
        jump.active = !noRows;
        jump.setTooltip(noRows ? Tooltip.create(tr("search.none")) : null);
    }

    private void showSection() {
        String at = null;
        int top = list.scrolled(), bottom = top + listBottom() - TOP, most = 0;
        List<Map.Entry<String, Integer>> tops = List.copyOf(groupTops.entrySet());
        for (int i = 0; i < tops.size(); i++) {
            int end = i + 1 < tops.size() ? tops.get(i + 1).getValue() : contentH;
            int seen = Math.min(end, bottom) - Math.max(tops.get(i).getValue(), top);
            if (seen > most) {
                most = seen;
                at = tops.get(i).getKey();
            }
        }
        current = at;
        Component name = at == null ? tr("jump") : tr("group." + at);
        if (!name.equals(jump.getMessage())) jump.setMessage(name);
    }

    private final class Sections extends Popup {
        Sections() {
            super(tr("jump.title"), groupTops.keySet().stream().mapToInt(id -> Widgets.buttonWidth(tr("group." + id))).max().orElse(0) + 30, TITLE_H + 7 + groupTops.size() * (Widgets.H + GAP), () -> popup = null);
        }

        @Override
        protected Bounds place(int screenW, int screenH, int w, int h) {
            return new Bounds(Math.max(4, jump.getRight() - w), jump.getBottom() + 2, w, h);
        }

        @Override
        protected void build(Bounds in) {
            int y = in.y();
            String here = current;
            for (String id : groupTops.keySet()) {
                Component name = tr("group." + id);
                Button b = widgets.button(in.x(), y, in.w(), id.equals(here) ? name.copy().withColor(Theme.ACCENT) : name, () -> {
                    list.scrollTo(groupTops.get(id));
                    close();
                });
                if (id.equals(here)) widgets.setFocused(b);
                y += Widgets.H + GAP;
            }
        }

        @Override
        boolean keyPressed(KeyEvent e) {
            boolean back = e.key() == InputConstants.KEY_UP || e.key() == InputConstants.KEY_TAB && e.hasShiftDown();
            if (!back && e.key() != InputConstants.KEY_DOWN && e.key() != InputConstants.KEY_TAB) return super.keyPressed(e);
            List<AbstractWidget> choices = widgets.children();
            int at = choices.indexOf(widgets.getFocused());
            widgets.setFocused(choices.get(Math.floorMod(at + (back ? -1 : 1) + (at < 0 && back ? 1 : 0), choices.size())));
            return true;
        }
    }

    private void applied(boolean window) {
        stale = true;
        if (window && parent instanceof ChromaScreen screen) screen.reloadWindow();
    }

    private static boolean changed(String group, Map<String, Object> since) {
        return group.isEmpty() ? ModSettings.changedSince(since) : ModSettings.changedSince(group, since);
    }

    private static boolean shown(String name) {
        return !ModSettings.MEMORY.contains(name) && (!name.equals("hotbarAxiom") || AxiomBridge.isPresent());
    }

    private static Stream<String> changedFromDefault(String group) {
        Stream<String> names = group.isEmpty() ? ModSettings.GROUPS.values().stream().flatMap(List::stream) : ModSettings.GROUPS.get(group).stream();
        return names.filter(name -> shown(name) && !ModSettings.isDefault(name));
    }

    private static boolean atDefaults(String group) { return changedFromDefault(group).findAny().isEmpty(); }

    private void showReset(String group, Button reset) {
        reset.active = !atDefaults(group);
        String key = group.isEmpty() ? "reset_all" : "reset";
        reset.setTooltip(Tooltip.create(tr(reset.active ? key + ".tip" : key + ".none")));
    }

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

    private final class Confirm extends Popup {
        private static final int W = 220;
        private static final Component QUESTION = tr("reset_all.confirm");
        private final Component changed;

        Confirm() {
            List<Component> all = changedFromDefault("").map(SettingsScreen::tr).toList();
            int listed = all.size();
            Component names = changedText(all, listed);
            while (listed > 1 && lines(names) > 4) names = changedText(all, --listed);
            changed = names;
            super(tr("reset_all.title"), W, TITLE_H + 7 + (lines(QUESTION) + Math.min(4, lines(names))) * 9 + 8 + Widgets.H, () -> popup = null);
        }

        private static Component changedText(List<Component> all, int listed) {
            List<Component> names = new ArrayList<>(all.subList(0, listed));
            if (listed < all.size()) names.add(Component.translatable("settings.chromastudio.reset_all.more", all.size() - listed));
            return Component.translatable("settings.chromastudio.reset_all.changed", ComponentUtils.formatList(names, Component.literal(", ")));
        }

        private static int lines(Component text) { return Minecraft.getInstance().font.split(text, W - 10).size(); }

        @Override
        protected void build(Bounds in) {
            Component yes = tr("reset_all"), no = Component.translatable("gui.cancel");
            int noW = Widgets.buttonWidth(no), yesW = Widgets.buttonWidth(yes), y = in.bottom() - Widgets.H;
            widgets.button(in.right() - noW, y, no, this::close);
            widgets.button(in.right() - noW - GAP - yesW, y, yes.copy().withColor(Theme.BAD), this::confirm);
        }

        @Override
        protected void draw(GuiGraphicsExtractor g, Bounds in, int mouseX, int mouseY) {
            g.textWithWordWrap(font, QUESTION, in.x(), in.y(), in.w(), Theme.TEXT, false);
            g.enableScissor(in.x(), in.y(), in.right(), in.bottom() - Widgets.H - 2);
            g.textWithWordWrap(font, changed, in.x(), in.y() + lines(QUESTION) * 9 + 4, in.w(), Theme.SOFT, false);
            g.disableScissor();
        }

        @Override
        protected void confirm() {
            ModSettings.reset();
            applied(true);
            close();
        }
    }

    private static final class Band extends AbstractWidget {
        private final String setting;
        private final boolean stripe;
        private final int labelRight, controlX;
        private Component note;
        private boolean enabled = true;

        Band(int x, int y, int w, int h, String setting, boolean stripe, int labelRight, int controlX) {
            super(x, y, w, h, Component.empty());
            this.setting = setting;
            this.stripe = stripe;
            this.labelRight = labelRight;
            this.controlX = controlX;
            active = false;
        }

        @Override
        protected void extractWidgetRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta) {
            int x = getX(), y = getY();
            if (setting == null) {
                g.fill(x, y, x + width, y + height, Theme.BAR);
                g.fill(x, y + height - 1, x + width, y + height, Theme.BORDER);
                return;
            }
            if (isHovered() && enabled) g.fill(x, y, x + width, y + height, Theme.HOVER);
            else if (stripe) g.fill(x, y, x + width, y + height, Theme.veil(0x08));
            boolean changed = !setting.equals(MOD_KEYS) && !ModSettings.isDefault(setting);
            if (changed) g.fill(x, y + 2, x + 2, y + height - 2, Theme.HIGHLIGHT);
            List<Component> tags = !changed ? note == null ? List.of() : List.of(note)
                    : note == null ? List.of(tr("changed")) : List.of(tr("changed").copy().append("  ").append(note), note);
            for (Component tag : tags) {
                int tx = controlX - 6 - Widgets.tw(tag);
                if (tx <= labelRight + 6) continue;
                g.text(Minecraft.getInstance().font, tag, tx, y + 5, changed ? Theme.HIGHLIGHT : Theme.SOFT, false);
                return;
            }
        }

        @Override
        protected void updateWidgetNarration(NarrationElementOutput builder) { }
    }

    private static final class Swatch extends AbstractWidget {
        private final String setting;

        Swatch(int x, int y, String setting) {
            super(x, y, Widgets.H, Widgets.H, Component.empty());
            this.setting = setting;
            active = false;
        }

        @Override
        protected void extractWidgetRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta) {
            Widgets.swatch(g, getX(), getY(), width, height, Theme.base(ThemeChoice.CUSTOM, (String) ModSettings.get(setting)));
        }

        @Override
        protected void updateWidgetNarration(NarrationElementOutput builder) { }
    }

    private AbstractWidget control(String name, int x, int y, Component tip) {
        if (name.equals(MOD_KEYS)) {
            Button keys = list.button(x, y, CONTROL_W, tr("keys"), this::openKeys);
            keys.setTooltip(Tooltip.create(tip));
            return keys;
        }
        AbstractWidget made = control(name, x, y, tip, ModSettings.type(name));
        if (!(made instanceof CycleButton) && made.active)
            made.setTooltip(Tooltip.create(name.equals("customColour") && ModSettings.theme != ThemeChoice.CUSTOM ? tr(name + ".use.tip") : tip));
        if (made instanceof CycleButton<?> c && made.active && !toggles.contains(c)) cyclers.add(c);
        return made;
    }

    private static Component note(String name) {
        Class<?> type = name.equals(MOD_KEYS) ? String.class : ModSettings.type(name);
        if (type != int.class && type != double.class) return null;
        boolean whole = type == int.class;
        return Component.translatable("settings.chromastudio.range", Widgets.number(ModSettings.min(name), whole), Widgets.number(ModSettings.max(name), whole));
    }

    private AbstractWidget control(String name, int x, int y, Component tip, Class<?> type) {
        Object value = ModSettings.get(name);
        if (name.equals("dioramaPerspective"))
            return list.cycler(x, y, CONTROL_W, null, List.of(false, true), (Boolean) value,
                    v -> Component.translatable("option.chromastudio.projection." + (v ? "perspective" : "orthographic")), v -> ModSettings.set(name, v), tip);
        if (name.equals("hotbarAxiom") && AxiomBridge.hotbarsUnsupported().isPresent())
            return Widgets.disable(list.toggle(x, y, CONTROL_W, null, false, v -> ModSettings.set(name, v)), AxiomBridge.hotbarsUnsupported().orElseThrow());
        if (type == boolean.class) {
            CycleButton<Boolean> toggle = list.cycler(x, y, CONTROL_W, null, List.of(true, false), (Boolean) value,
                    v -> v ? CommonComponents.OPTION_ON.copy().withColor(Theme.GOOD) : CommonComponents.OPTION_OFF, v -> ModSettings.set(name, v), tip);
            toggles.add(toggle);
            return toggle;
        }
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
            if (!ids.contains(text)) ids.add(text);
            return list.cycler(x, y, CONTROL_W, null, ids, text, v -> Component.translatable("tab.chromastudio." + v), v -> ModSettings.set(name, v), tip);
        }
        if (name.equals("customColour")) {
            list.add(new Swatch(x, y, name));
            Runnable choose = () -> open(new ColourDialog(tr(name), Color.ofRgb(Theme.base(ThemeChoice.CUSTOM, ModSettings.customColour)), c -> {
                ModSettings.set(name, c.hex());
                stale = true;
            }, () -> popup = null));
            if (ModSettings.theme == ThemeChoice.CUSTOM)
                return list.button(x + Widgets.H + GAP, y, CONTROL_W - Widgets.H - GAP, Component.literal(text.toUpperCase(Locale.ROOT)), choose);
            return list.button(x + Widgets.H + GAP, y, CONTROL_W - Widgets.H - GAP, tr(name + ".use"), () -> {
                ModSettings.set("theme", ThemeChoice.CUSTOM);
                stale = true;
                choose.run();
            });
        }
        EditBox[] box = new EditBox[1];
        box[0] = list.field(x, y, CONTROL_W, text, v -> box[0].setTextColor(ModSettings.set(name, v) ? EditBox.DEFAULT_TEXT_COLOR : Theme.FIELD_BAD));
        if (name.endsWith("Folder")) box[0].setHint(tr("folder.hint"));
        return box[0];
    }

    private void openKeys() {
        KeyBindsScreen keys = new KeyBindsScreen(this, minecraft.options);
        minecraft.gui.setScreen(keys);
        String heading = Keys.CATEGORY.label().getString();
        for (GuiEventListener child : keys.children()) {
            if (!(child instanceof KeyBindsList binds)) continue;
            int y = 0;
            for (KeyBindsList.Entry e : binds.children()) {
                if (e instanceof KeyBindsList.CategoryEntry && e.children().stream()
                        .anyMatch(w -> w instanceof AbstractWidget name && name.getMessage().getString().equals(heading))) {
                    binds.setScrollAmount(y);
                    return;
                }
                y += e.getHeight();
            }
        }
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta) {
        super.extractBackground(g, mouseX, mouseY, delta);
        g.fill(0, 0, width, height, Theme.WINDOW);
        g.fill(0, 0, width, TOP - 1, Theme.BAR);
        g.fill(0, height - BOTTOM + 1, width, height, Theme.BAR);
        g.fill(0, TOP - 1, width, TOP, Theme.BORDER);
        g.fill(0, height - BOTTOM, width, height - BOTTOM + 1, Theme.BORDER);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta) {
        Theme.sync();
        if (stale) buildList();
        undos.forEach((group, undo) -> {
            if (changed(group, atOpen) != undo.active) showUndo(group, undo);
        });
        resets.forEach((group, reset) -> {
            if (atDefaults(group) == reset.active) showReset(group, reset);
        });
        showSection();
        int mx = popup == null ? mouseX : -1, my = popup == null ? mouseY : -1;
        super.extractRenderState(g, mx, my, delta);
        int left = left(), ax = jump.getRight() - 8, ay = jump.getY() + jump.getHeight() / 2 - 1;
        for (int i = 0; i < 3; i++) g.fill(ax - 2 + i, ay + i, ax + 3 - i, ay + i + 1, jump.active ? Theme.SOFT : Theme.GREYED);
        g.text(font, font.plainSubstrByWidth(title.getString(), searchField.getX() - 8 - left), left, 8, Theme.STRONG, false);
        int bottom = listBottom(), view = bottom - TOP;
        int barR = left + columnWidth() + 12, barL = barR - Widgets.BAR_W - 3;
        g.fill(barL, TOP, barR, bottom, Theme.veil(0x20));
        list.render(g, mx, my, delta);
        g.enableScissor(0, TOP, width, bottom);
        for (CycleButton<Boolean> t : toggles) {
            int lx = t.getX() + 5, ly = t.getY() + 5;
            g.fill(lx, ly, lx + 6, ly + 6, Theme.WELL);
            if (t.getValue()) g.fill(lx + 1, ly + 1, lx + 5, ly + 5, Theme.GOOD);
        }
        for (CycleButton<?> c : cyclers) {
            if (Widgets.tw(c.getMessage()) > c.getWidth() - 24) continue;
            int cy = c.getY() + c.getHeight() / 2, l = c.getX() + 6, r = c.getRight() - 7;
            for (int i = 0; i < 3; i++) {
                g.fill(l + i, cy - i, l + i + 1, cy + i + 1, Theme.SOFT);
                g.fill(r - i, cy - i, r - i + 1, cy + i + 1, Theme.SOFT);
            }
        }
        g.disableScissor();
        if (contentH > view) {
            int max = contentH - view, bar = Math.max(16, view * view / contentH), by = TOP + (int) ((long) (view - bar) * list.scrolled() / max);
            g.fill(barL, by, barR, by + bar, Theme.veil(0x90));
        }
        int clearRgb = Theme.WINDOW & 0xFFFFFF;
        if (list.scrolled() > 0 && !stops.contains(list.scrolled())) g.fillGradient(left - 4, TOP, barL, TOP + FADE, Theme.WINDOW, clearRgb);
        if (list.scrolled() < contentH - view) g.fillGradient(left - 4, bottom - FADE / 2, barL, bottom, clearRgb, Theme.WINDOW);
        Component hint = tr("hover_hint");
        int hintL = resets.get("").getRight() + 8, hintR = width - left - 8 - Widgets.buttonWidth(Component.translatable("gui.done")) - 14;
        if (!noRows && Widgets.tw(hint) <= hintR - hintL) Widgets.centred(g, hint, (hintL + hintR) / 2, height - 17, Theme.SOFT);
        int countY = TOP + contentH - COUNT_H - list.scrolled();
        if (!search.isEmpty() && !noRows && countY >= TOP && countY + 9 <= bottom)
            Widgets.centred(g, Component.translatable("settings.chromastudio.search.count", shownRows), left + columnWidth() / 2, countY, Theme.SOFT);
        if (noRows) {
            Widgets.centred(g, tr("search.none"), width / 2, TOP + 16, Theme.TEXT);
            Widgets.centred(g, tr("search.none.hint"), width / 2, TOP + 30, Theme.SOFT);
        }
        if (popup != null) popup.render(g, mouseX, mouseY, delta);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubled) {
        if (popup != null) return popup.mouseClicked(event, doubled);
        if (list.mouseClicked(event, doubled)) {
            setFocused(list.getFocused() instanceof EditBox ? list : null);
            return true;
        }
        boolean hit = super.mouseClicked(event, doubled);
        if (getFocused() instanceof Button) setFocused(null);
        return hit;
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        if (popup != null) return popup.mouseReleased(event);
        return list.mouseReleased(event) || super.mouseReleased(event);
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double dx, double dy) {
        if (popup != null) return popup.mouseDragged(event, dx, dy);
        return list.mouseDragged(event, dx, dy) || super.mouseDragged(event, dx, dy);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double h, double v) {
        if (popup != null) return popup.mouseScrolled(mx, my, h, v) || true;
        int before = list.scrolled();
        if (!list.mouseScrolled(mx, my, h, v)) {
            if (v == 0 || my < TOP || my >= listBottom()) return super.mouseScrolled(mx, my, h, v);
            list.scrollTo(before - (int) Math.round(v * ROW));
        }
        int after = list.scrolled();
        if (after > before) list.scrollTo(stops.stream().filter(stop -> stop >= after).findFirst().orElse(after));
        else if (after < before) list.scrollTo(stops.stream().filter(stop -> stop <= after).reduce((_, last) -> last).orElse(after));
        return true;
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (popup != null) return popup.keyPressed(event);
        if (event.isEscape()) {
            if (search.isEmpty()) onClose();
            else searchField.setValue("");
            return true;
        }
        boolean handled = list.keyPressed(event) || super.keyPressed(event);
        if (getFocused() == list && list.getFocused() instanceof AbstractWidget w) {
            if (w.getY() < TOP) list.scrollTo(list.scrolled() - (TOP - w.getY()) - 4);
            else if (w.getBottom() > listBottom()) list.scrollTo(list.scrolled() + w.getBottom() - listBottom() + 4);
        }
        return handled;
    }

    @Override
    public List<? extends GuiEventListener> children() {
        List<GuiEventListener> all = new ArrayList<>(super.children());
        all.add(list);
        return all;
    }

    @Override
    public boolean charTyped(CharacterEvent event) {
        if (popup != null) return popup.charTyped(event);
        return list.charTyped(event) || super.charTyped(event);
    }

    @Override
    public void removed() {
        if (popup != null) popup.close();
        ModSettings.save();
        super.removed();
    }

    @Override
    public void onClose() { minecraft.gui.setScreen(parent); }
}
