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
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

final class SavedGradients extends Dialog {
    private static final int ROW_H = 16, SWATCH_W = 48, CROSS = 11;

    private final Function<String, StoredGradient> current;
    private final Supplier<Component> unsaveable;
    private final Consumer<StoredGradient> load;
    private final String active;
    private String name;
    private EditBox field;
    private Button button;
    private List<StoredGradient> saved = List.of();
    private Widgets.ScrollList rows;

    SavedGradients(Function<String, StoredGradient> current, Supplier<Component> unsaveable, String name, Consumer<StoredGradient> load) {
        super(tr("gradient.chromastudio.store.title"), 300, 206);
        this.current = current;
        this.unsaveable = unsaveable;
        this.name = name;
        this.active = name;
        this.load = load;
    }

    private static Component tr(String key, Object... args) { return Component.translatable(key, args); }

    @Override
    protected void build(Bounds in) {
        saved = GradientStore.get().all();
        boolean first = field == null;
        Component save = tr("gradient.chromastudio.store.save");
        int saveW = Widgets.buttonWidth(save);
        field = widgets.search(in.x(), in.y(), in.w() - saveW - PanelTab.GAP, tr("palettes.chromastudio.name_hint"), s -> {
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
        button = widgets.button(in.right() - saveW, in.y(), save, this::confirm);
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
        button.setTooltip(blank ? Tooltip.create(tr("palettes.chromastudio.name_needed")) : null);
    }

    @Override
    protected boolean confirm() {
        if (unsaveable.get() != null) {
            refresh();
            return true;
        }
        if (name.isBlank()) {
            status(tr("palettes.chromastudio.name_needed"), Theme.FAIR);
            return true;
        }
        StoredGradient g = current.apply(name.strip());
        StoredGradient old = GradientStore.get().get(g.name());
        if (old == null) {
            store(g);
            return true;
        }
        tab.open(new PaletteDialogs.Confirm(tr("gradient.chromastudio.store.replace.title"), tr("gradient.chromastudio.store.replace.question", old.name()),
                tr("gradient.chromastudio.store.replace"), () -> store(g)));
        return true;
    }

    private void store(StoredGradient g) {
        boolean written = GradientStore.get().put(g);
        tab.closeDialog();
        if (written) tab.say(tr("gradient.chromastudio.store.done", g.name()), Theme.GOOD);
        else tab.say(tr("gradient.chromastudio.store.failed"), Theme.BAD);
    }

    @Override
    protected void draw(GuiGraphicsExtractor g, Bounds in, int mouseX, int mouseY) {
        Bounds list = rows.area;
        g.fill(list.x(), list.y(), list.right(), list.bottom(), Theme.WELL);
        if (status == null && !saved.isEmpty())
            g.text(PanelTab.font(), PanelTab.fit(tr("gradient.chromastudio.store.hint").getString(), in.w()), in.x(), in.bottom() - 9, Theme.DIM, false);
        rows.render(g, mouseX, mouseY, (g2, i, x, y, w, h, over) -> {
            if (saved.isEmpty()) {
                g2.text(PanelTab.font(), PanelTab.fit(tr("gradient.chromastudio.store.none").getString(), w - 8), x + 4, y + 4, Theme.DIM, false);
                return;
            }
            StoredGradient s = saved.get(i);
            boolean current = s.name().equalsIgnoreCase(active.strip());
            if (current) {
                g2.fill(x, y, x + w, y + h, Theme.SELECTED);
                g2.fill(x, y, x + 2, y + h, Theme.ACCENT);
            }
            if (over) g2.fill(x, y, x + w, y + h, Theme.veil(0x20));
            swatch(g2, s, x + 4, y + 3, SWATCH_W, h - 6);
            boolean onCross = over && overCross(x, w, mouseX);
            int cx = x + w - CROSS - 3, cy = y + (h - CROSS) / 2;
            if (over) {
                g2.fill(cx, cy, cx + CROSS, cy + CROSS, onCross ? Theme.DANGER : Theme.CONTROL);
                for (int k = 2; k < CROSS - 2; k++) {
                    g2.fill(cx + k, cy + k, cx + k + 1, cy + k + 1, Theme.TEXT);
                    g2.fill(cx + CROSS - 1 - k, cy + k, cx + CROSS - k, cy + k + 1, Theme.TEXT);
                }
            }
            String steps = tr("gradient.chromastudio.store.steps", s.steps()).getString();
            int stepsW = PanelTab.font().width(steps), textX = x + SWATCH_W + 9;
            g2.text(PanelTab.font(), steps, x + w - CROSS - 6 - stepsW, y + 4, Theme.DIM, false);
            String shown = PanelTab.fit(s.name(), x + w - CROSS - 10 - stepsW - textX);
            g2.text(PanelTab.font(), shown, textX, y + 4, Theme.TEXT, false);
            if (onCross) g2.setTooltipForNextFrame(PanelTab.font(), tr("gradient.chromastudio.store.delete"), mouseX, mouseY);
            else if (over && !shown.equals(s.name())) g2.setTooltipForNextFrame(PanelTab.font(), Component.literal(s.name()), mouseX, mouseY);
        });
    }

    private static void swatch(GuiGraphicsExtractor g, StoredGradient s, int x, int y, int w, int h) {
        int n = s.blocks().isEmpty() ? s.stops().size() : s.blocks().size();
        for (int k = 0; k < n; k++) {
            int rgb = s.blocks().isEmpty() ? s.stops().get(k).rgb() : s.blocks().get(k).ideal();
            g.fill(x + k * w / n, y, x + (k + 1) * w / n, y + h, 0xFF000000 | rgb);
        }
    }

    private static boolean overCross(int x, int w, double mx) { return mx >= x + w - CROSS - 3 && mx < x + w - 3; }

    @Override
    boolean mouseClicked(MouseButtonEvent e, boolean doubled) {
        if (rows != null && rows.area.contains(e.x(), e.y())) {
            if (rows.mouseClicked(e) || saved.isEmpty()) return true;
            int i = rows.rowAt(e.x(), e.y());
            if (i < 0 || i >= saved.size()) return true;
            StoredGradient s = saved.get(i);
            boolean right = e.button() == InputConstants.MOUSE_BUTTON_RIGHT;
            if (right || e.button() == InputConstants.MOUSE_BUTTON_LEFT && overCross(rows.area.x(), rowWidth(), e.x())) {
                tab.open(new PaletteDialogs.Confirm(tr("gradient.chromastudio.store.delete.title"), tr("gradient.chromastudio.store.delete.question", s.name()),
                        tr("gradient.chromastudio.store.delete"), () -> {
                            boolean deleted = GradientStore.get().delete(s.name());
                            SavedGradients again = new SavedGradients(current, unsaveable, name, load);
                            tab.open(again);
                            if (deleted) again.status(tr("gradient.chromastudio.store.deleted", s.name()), Theme.TEXT);
                            else again.status(tr("gradient.chromastudio.store.failed"), Theme.BAD);
                        }));
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
        return (rows != null && rows.mouseReleased()) | super.mouseReleased(e);
    }

    @Override
    boolean mouseScrolled(double x, double y, double h, double v) {
        return rows != null && rows.mouseScrolled(x, y, v) || super.mouseScrolled(x, y, h, v);
    }
}
