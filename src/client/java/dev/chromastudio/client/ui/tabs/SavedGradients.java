package dev.chromastudio.client.ui.tabs;

import com.mojang.blaze3d.platform.InputConstants;
import dev.chromastudio.client.GradientStore;
import dev.chromastudio.client.ui.Bounds;
import dev.chromastudio.client.ui.Theme;
import dev.chromastudio.client.ui.Widgets;
import dev.chromastudio.core.palette.StoredGradient;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import org.jspecify.annotations.Nullable;

final class SavedGradients extends Dialog {
    private static final int ROW_H = 16, SWATCH_W = 48, NAME_X = SWATCH_W + 9, CROSS = 11;
    private static final Component NEEDED = Component.translatable("palettes.chromastudio.name_needed");

    private final Function<String, StoredGradient> current;
    private final Supplier<Component> unsaveable;
    private final Supplier<List<Integer>> preview;
    private final Consumer<StoredGradient> load, stored;
    private final String loaded;
    private String name;
    private EditBox field;
    private Button button;
    private List<StoredGradient> saved = List.of();
    private Widgets.ScrollList rows;
    private String hovered, target;

    SavedGradients(Function<String, StoredGradient> current, Supplier<Component> unsaveable, Supplier<List<Integer>> preview, String loaded,
            String suggested, Consumer<StoredGradient> load, Consumer<StoredGradient> stored) {
        super(tr("gradient.chromastudio.store.title"), 340, 206);
        this.current = current;
        this.unsaveable = unsaveable;
        this.preview = preview;
        this.loaded = loaded;
        this.name = loaded != null ? loaded : suggested;
        this.load = load;
        this.stored = stored;
    }

    private static Component tr(String key, Object... args) { return Component.translatable(key, args); }

    @Override
    protected void build(Bounds in) {
        saved = GradientStore.get().all();
        boolean first = field == null;
        Component save = tr("gradient.chromastudio.store.save");
        int saveW = Math.max(Widgets.buttonWidth(save), Widgets.buttonWidth(tr("gradient.chromastudio.store.overwrite")));
        int fieldX = in.x() + NAME_X;
        field = widgets.search(fieldX, in.y(), in.right() - saveW - PanelTab.GAP - fieldX, tr("palettes.chromastudio.name_hint"), s -> {
            if (!s.equals(name)) status = null;
            name = s;
            nameChanged();
        });
        field.setMaxLength(Integer.MAX_VALUE);
        field.setValue(name);
        Component why = unsaveable.get();
        if (first && why == null) {
            field.setFocused(true);
            widgets.setFocused(field);
        }
        button = widgets.button(in.right() - saveW, in.y(), saveW, save, this::confirm);
        if (why != null) {
            Widgets.disable(button, why);
            Widgets.disable(field, why);
        } else nameChanged();
        int top = in.y() + PanelTab.ROW + 2;
        Bounds list = new Bounds(in.x(), top, in.w(), Math.max(ROW_H, (in.bottom() - STATUS_H - top) / ROW_H * ROW_H));
        if (rows == null) rows = new Widgets.ScrollList(list, ROW_H, () -> Math.max(1, saved.size()));
        else rows.area = list;
    }

    private void nameChanged() {
        if (button == null || unsaveable.get() != null) return;
        boolean blank = name.isBlank();
        button.active = !blank;
        button.setTooltip(blank ? Tooltip.create(NEEDED) : null);
        StoredGradient same = blank ? null : GradientStore.get().get(name);
        target = same == null ? null : same.name();
        button.setMessage(tr(target != null ? "gradient.chromastudio.store.overwrite" : "gradient.chromastudio.store.save"));
        if (blank) status(NEEDED, Theme.FAIR);
        else if (NEEDED.equals(status)) status = null;
    }

    @Override
    protected boolean confirm() {
        if (unsaveable.get() != null) {
            refresh();
            return true;
        }
        if (name.isBlank()) {
            status(NEEDED, Theme.FAIR);
            return true;
        }
        String n = name.strip();
        StoredGradient old = GradientStore.get().get(n);
        if (old == null) {
            store(current.apply(n));
            return true;
        }
        StoredGradient now = current.apply(n);
        tab.open(new PaletteDialogs.Confirm(tr("gradient.chromastudio.store.replace.title"), tr("gradient.chromastudio.store.replace.question"), old.name(),
                List.of(row(tr("gradient.chromastudio.store.replace.saved"), colours(old), old.steps()), row(tr("gradient.chromastudio.store.replace.current"), colours(now), now.steps())),
                tr("gradient.chromastudio.store.replace"), () -> store(now), () -> tab.open(this)));
        return true;
    }

    private void store(StoredGradient g) {
        boolean written = GradientStore.get().put(g);
        tab.closeDialog();
        if (written) {
            stored.accept(g);
            tab.say(tr("gradient.chromastudio.store.done", g.name()), Theme.GOOD);
        } else tab.say(tr("gradient.chromastudio.store.failed"), Theme.BAD);
    }

    @Override
    protected void draw(GuiGraphicsExtractor g, Bounds in, int mouseX, int mouseY) {
        Bounds list = rows.area;
        g.fill(list.x(), list.y(), list.right(), list.bottom(), Theme.WELL);
        List<Integer> colours = preview.get();
        if (!colours.isEmpty()) {
            int px = in.x() + 4, py = in.y() + 3;
            swatch(g, colours, px, py, SWATCH_W, PanelTab.ROW - 6);
            if (mouseX >= px && mouseX < px + SWATCH_W && mouseY >= py && mouseY < py + PanelTab.ROW - 6)
                g.setTooltipForNextFrame(PanelTab.font(), tr("gradient.chromastudio.store.preview", colours.size()), mouseX, mouseY);
        }
        hovered = null;
        rows.render(g, mouseX, mouseY, (g2, i, x, y, w, h, over) -> {
            if (saved.isEmpty()) {
                g2.text(PanelTab.font(), PanelTab.fit(tr("gradient.chromastudio.store.none").getString(), w - 8), x + 4, y + 4, Theme.DIM, false);
                return;
            }
            StoredGradient s = saved.get(i);
            if (s.name().equals(target)) {
                g2.fill(x, y, x + w, y + h, Theme.SELECTED);
                g2.fill(x, y, x + 2, y + h, Theme.ACCENT);
            }
            if (over) g2.fill(x, y, x + w, y + h, Theme.veil(0x30));
            swatch(g2, colours(s), x + 4, y + 3, SWATCH_W, h - 6);
            boolean onCross = over && overCross(x, w, mouseX);
            int cx = x + w - CROSS - 3, cy = y + (h - CROSS) / 2;
            if (over) {
                if (onCross) g2.fill(cx, cy, cx + CROSS, cy + CROSS, Theme.DANGER);
                int ink = onCross ? Theme.TEXT : Theme.BAD;
                for (int k = 2; k < CROSS - 2; k++) {
                    g2.fill(cx + k, cy + k, cx + k + 1, cy + k + 1, ink);
                    g2.fill(cx + CROSS - 1 - k, cy + k, cx + CROSS - k, cy + k + 1, ink);
                }
            }
            String steps = tr("gradient.chromastudio.store.steps", s.steps()).getString();
            int stepsW = PanelTab.font().width(steps), textX = x + NAME_X;
            g2.text(PanelTab.font(), steps, x + w - CROSS - 6 - stepsW, y + 4, Theme.DIM, false);
            String shown = PanelTab.fit(s.name(), x + w - CROSS - 10 - stepsW - textX);
            g2.text(PanelTab.font(), shown, textX, y + 4, Theme.TEXT, false);
            if (onCross) g2.setTooltipForNextFrame(PanelTab.font(), tr("gradient.chromastudio.store.delete"), mouseX, mouseY);
            else if (over && !shown.equals(s.name())) hovered = s.name();
        });
        boolean footer = status == null && !saved.isEmpty();
        if (hovered != null && (!footer || PanelTab.font().width(hovered) > in.w())) {
            g.setTooltipForNextFrame(PanelTab.font(), Tooltip.splitTooltip(Minecraft.getInstance(), Component.literal(hovered)), mouseX, mouseY);
            hovered = null;
        }
        if (!footer) return;
        g.text(PanelTab.font(), PanelTab.fit(tr("gradient.chromastudio.store.hint").getString(), in.w()), in.x(), in.bottom() - 9, Theme.DIM, false);
        if (hovered != null) g.text(PanelTab.font(), hovered, in.x(), in.bottom() - 18, Theme.TEXT, false);
        else if (target != null) {
            boolean editing = loaded != null && target.equalsIgnoreCase(loaded.strip());
            Component note = tr(editing ? "gradient.chromastudio.store.editing" : "gradient.chromastudio.store.replaces", target);
            g.text(PanelTab.font(), PanelTab.fit(note.getString(), in.w()), in.x(), in.bottom() - 18, editing ? Theme.TEXT : Theme.FAIR, false);
        }
    }

    private static List<Integer> colours(StoredGradient s) {
        return s.blocks().isEmpty() ? s.stops().stream().map(StoredGradient.Stop::rgb).toList() : s.blocks().stream().map(StoredGradient.Step::ideal).toList();
    }

    static void swatch(GuiGraphicsExtractor g, List<Integer> colours, int x, int y, int w, int h) {
        int n = colours.size();
        for (int k = 0; k < n; k++) g.fill(x + k * w / n, y, x + (k + 1) * w / n, y + h, 0xFF000000 | colours.get(k));
    }

    private static PaletteDialogs.Confirm.Row row(@Nullable Component label, List<Integer> colours, int steps) {
        return new PaletteDialogs.Confirm.Row(label, colours, tr("gradient.chromastudio.store.steps", steps));
    }

    private static boolean overCross(int x, int w, double mx) { return mx >= x + w - CROSS - 6 && mx < x + w; }

    @Override
    boolean mouseClicked(MouseButtonEvent e, boolean doubled) {
        if (rows != null && rows.area.contains(e.x(), e.y())) {
            if (rows.mouseClicked(e) || saved.isEmpty()) return true;
            int i = rows.rowAt(e.x(), e.y());
            if (i < 0 || i >= saved.size()) return true;
            StoredGradient s = saved.get(i);
            boolean right = e.button() == InputConstants.MOUSE_BUTTON_RIGHT;
            if (right || e.button() == InputConstants.MOUSE_BUTTON_LEFT && overCross(rows.area.x(), rowWidth(), e.x())) {
                tab.open(new PaletteDialogs.Confirm(tr("gradient.chromastudio.store.delete.title"), tr("gradient.chromastudio.store.delete.question"), s.name(),
                        List.of(row(null, colours(s), s.steps())), tr("gradient.chromastudio.store.delete"), () -> {
                            boolean deleted = GradientStore.get().delete(s.name());
                            SavedGradients again = new SavedGradients(current, unsaveable, preview, loaded, name, load, stored);
                            again.name = name;
                            tab.open(again);
                            if (deleted) again.status(tr("gradient.chromastudio.store.deleted", s.name()), Theme.TEXT);
                            else again.status(tr("gradient.chromastudio.store.failed"), Theme.BAD);
                        }, () -> tab.open(this)));
            } else if (e.button() == InputConstants.MOUSE_BUTTON_LEFT) {
                tab.closeDialog();
                load.accept(s);
            }
            return true;
        }
        return super.mouseClicked(e, doubled);
    }

    private int rowWidth() { return rows.area.w() - (saved.size() * ROW_H > rows.area.h() ? Widgets.BAR_W + 2 : 0); }

    @Override
    boolean mouseDragged(MouseButtonEvent e, double dx, double dy) {
        return rows != null && rows.mouseDragged(e) || super.mouseDragged(e, dx, dy);
    }

    @Override
    boolean mouseReleased(MouseButtonEvent e) {
        boolean took = rows != null && rows.mouseReleased();
        boolean base = super.mouseReleased(e);
        return took || base;
    }

    @Override
    boolean mouseScrolled(double x, double y, double h, double v) {
        return rows != null && rows.mouseScrolled(x, y, v) || super.mouseScrolled(x, y, h, v);
    }
}
