package dev.chromastudio.client.ui.tabs;

import com.mojang.blaze3d.platform.InputConstants;
import dev.chromastudio.client.GradientStore;
import dev.chromastudio.client.input.MouseButtonEvent;
import dev.chromastudio.client.ui.Bounds;
import dev.chromastudio.client.ui.DeferredTooltips;
import dev.chromastudio.client.ui.Theme;
import dev.chromastudio.client.ui.Widgets;
import dev.chromastudio.core.palette.StoredGradient;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.network.chat.Component;

/**
 * The Gradient tab's saved gradients: a name and "Save gradient" over the list of those saved, each shown as its
 * steps' colours and its name. A click on one loads it into the tab, and its cross deletes it after asking.
 */
final class SavedGradients extends Dialog {
    private static final int ROW_H = 16, SWATCH_W = 48, CROSS = 9;

    /** The gradient on show under a name, or null with why it can't be saved. */
    private final Function<String, StoredGradient> current;
    private final Supplier<Component> unsaveable;
    private final Consumer<StoredGradient> load;
    private String name;
    private EditBox field;
    private List<StoredGradient> saved = List.of();
    private Widgets.ScrollList rows;

    /**
     * @param current    the gradient on show, saved under a name
     * @param unsaveable why the gradient on show can't be saved now, or null when it can. It is asked again on saving, as a new answer may be on its way
     * @param name       the name the field starts with
     * @param load       shows a saved gradient in the tab
     */
    SavedGradients(Function<String, StoredGradient> current, Supplier<Component> unsaveable, String name, Consumer<StoredGradient> load) {
        super(tr("gradient.chromastudio.store.title"), 260, 170);
        this.current = current;
        this.unsaveable = unsaveable;
        this.name = name;
        this.load = load;
    }

    private static Component tr(String key, Object... args) { return Component.translatable(key, args); }

    @Override
    protected void build(Bounds in) {
        saved = GradientStore.get().all();
        boolean first = field == null;
        Component save = tr("gradient.chromastudio.store.save");
        int saveW = Widgets.buttonWidth(save);
        field = widgets.search(in.x(), in.y(), in.w() - saveW - PanelTab.GAP, tr("palettes.chromastudio.name_hint"), s -> name = s);
        field.setMaxLength(Integer.MAX_VALUE);
        field.setValue(name);
        Component why = unsaveable.get();
        if (first && why == null) {
            field.setFocused(true);
            widgets.setFocused(field);
        }
        Button button = widgets.button(in.right() - saveW, in.y(), save, this::confirm);
        if (why != null) {
            Widgets.disable(button, why);
            Widgets.disable(field, why);
        } else button.setTooltip(Tooltip.create(tr("gradient.chromastudio.store.save.tip")));
        Bounds list = new Bounds(in.x(), in.y() + PanelTab.ROW + 2, in.w(), in.bottom() - STATUS_H - (in.y() + PanelTab.ROW + 2));
        if (rows == null) rows = new Widgets.ScrollList(list, ROW_H, () -> Math.max(1, saved.size()));
        else rows.area = list;
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
        // Saving over another gradient can't be undone, so it asks first.
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
    protected void draw(GuiGraphics g, Bounds in, int mouseX, int mouseY) {
        Bounds list = rows.area;
        g.fill(list.x(), list.y(), list.right(), list.bottom(), Theme.WELL);
        rows.render(g, mouseX, mouseY, (g2, i, x, y, w, h, over) -> {
            if (saved.isEmpty()) {
                g2.drawString(PanelTab.font(), PanelTab.fit(tr("gradient.chromastudio.store.none").getString(), w - 8), x + 4, y + 4, Theme.DIM, false);
                return;
            }
            StoredGradient s = saved.get(i);
            if (over) g2.fill(x, y, x + w, y + h, Theme.veil(0x20));
            swatch(g2, s, x + 2, y + 3, SWATCH_W, h - 6);
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
            int stepsW = PanelTab.font().width(steps), textX = x + SWATCH_W + 6;
            g2.drawString(PanelTab.font(), steps, x + w - CROSS - 6 - stepsW, y + 4, Theme.DIM, false);
            g2.drawString(PanelTab.font(), PanelTab.fit(s.name(), x + w - CROSS - 10 - stepsW - textX), textX, y + 4, Theme.TEXT, false);
            if (onCross) DeferredTooltips.text(PanelTab.font(), tr("gradient.chromastudio.store.delete"), mouseX, mouseY);
            else if (over) DeferredTooltips.components(PanelTab.font(), List.of(Component.literal(s.name()),
                    tr("gradient.chromastudio.store.load").copy().withColor(Theme.TIP_DIM)), mouseX, mouseY);
        });
    }

    /** The steps' colours side by side, or the stops' where there are no steps. */
    private static void swatch(GuiGraphics g, StoredGradient s, int x, int y, int w, int h) {
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
                            // Back to the list, which the question took the place of.
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

    /** A row's width, which the scrollbar narrows when there is one. */
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
