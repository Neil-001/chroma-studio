package dev.chromastudio.client.ui;

import com.mojang.blaze3d.platform.InputConstants;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.DoubleConsumer;
import java.util.function.Function;
import java.util.function.IntSupplier;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.components.events.AbstractContainerEventHandler;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.input.InputWithModifiers;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;

/**
 * A group of the game's own widgets with the mod's habits: buttons as wide as their text, option buttons that step
 * backwards on a right-click, and number fields with arrows that the mouse wheel steps. A tab or screen makes one,
 * adds controls through it, and hands it the drawing and the input. With {@link #viewport} the group scrolls.
 *
 * <p>The static helpers at the end draw the small things the tabs share.
 */
public final class Widgets extends AbstractContainerEventHandler {
    /** The height the mod's controls have. The game's own 20 is too tall for the window at its smallest. */
    public static final int H = 16;
    /** Horizontal space the arrows take from a number field: 8 px of arrows plus a 1 px gap. */
    private static final int ARROW_SLOT = 9;
    private static final int BAR_W = 3;

    private final List<AbstractWidget> children = new ArrayList<>();
    /** What a right-click does on buttons that step through options: step backwards. */
    private final Map<AbstractWidget, Runnable> reverse = new HashMap<>();
    private final List<Spinner> spinners = new ArrayList<>();
    /** Each widget's y before scrolling. */
    private final Map<AbstractWidget, Integer> baseY = new HashMap<>();
    private Bounds viewport;
    private int contentHeight, scroll;
    private boolean draggingBar;

    private static Font font() { return Minecraft.getInstance().font; }

    public static int tw(Component text) { return font().width(text); }
    public static int tw(String text) { return font().width(text); }
    /** The width a button needs for this label. */
    public static int buttonWidth(Component label) { return tw(label) + 10; }

    @Override
    public List<AbstractWidget> children() { return children; }

    /** Adds any widget to the group. The methods below call it themselves. */
    public <T extends AbstractWidget> T add(T widget) {
        children.add(widget);
        baseY.put(widget, widget.getY());
        if (viewport != null) widget.setY(widget.getY() - scroll);
        return widget;
    }

    /** Empties the group, ready to lay out again. The scroll position stays. */
    public void clear() {
        setFocused(null);
        children.clear();
        reverse.clear();
        spinners.clear();
        baseY.clear();
    }

    /** A button as wide as its label. */
    public Button button(int x, int y, Component label, Runnable action) { return button(x, y, buttonWidth(label), label, action); }

    public Button button(int x, int y, int w, Component label, Runnable action) {
        return add(Button.builder(label, _ -> action.run()).bounds(x, y, w, H).build());
    }

    /** A button that steps through options: click for the next one, right-click for the previous one. */
    public <T> CycleButton<T> cycler(int x, int y, int w, Component label, List<T> values, T initial,
                                     Function<T, Component> names, Consumer<T> onChange) {
        CycleButton.Builder<T> builder = CycleButton.builder(names, initial).withValues(values);
        if (label == null) builder.displayOnlyValue();
        CycleButton<T> b = builder.create(x, y, w, H, label == null ? Component.empty() : label, (_, v) -> onChange.accept(v));
        reverse.put(b, () -> {
            T prev = values.get((values.indexOf(b.getValue()) - 1 + values.size()) % values.size());
            b.setValue(prev);
            onChange.accept(prev);
        });
        return add(b);
    }

    public CycleButton<Boolean> toggle(int x, int y, int w, Component label, boolean initial, Consumer<Boolean> onChange) {
        return cycler(x, y, w, label, List.of(true, false), initial, CommonComponents::optionStatus, onChange);
    }

    private record Spinner(EditBox field, Arrow up, Arrow down, double[] value, double min, double max) {}

    /**
     * A number field of total width w whose right edge holds up and down arrows, taken from the field rather than
     * added to it. The arrows and the mouse wheel step the value within the limits, and the arrows grey out at the
     * ends. A typed number is kept within the limits too, and anything that isn't a number changes nothing.
     */
    public EditBox spin(int x, int y, int w, double value, double step, double min, double max, boolean integer, DoubleConsumer onChange) {
        EditBox f = new EditBox(font(), x, y, w - ARROW_SLOT, H, Component.empty());
        f.setMaxLength(32);
        f.setValue(number(value, integer));
        double[] current = {value};
        f.setResponder(text -> {
            try {
                double d = Double.parseDouble(text.trim());
                if (!Double.isFinite(d)) return;
                current[0] = Math.max(min, Math.min(max, integer ? Math.rint(d) : d));
                onChange.accept(current[0]);
            } catch (NumberFormatException _) { }
        });
        add(f);
        int top = H / 2, ax = x + w - ARROW_SLOT + 1;
        Runnable up = () -> f.setValue(number(Math.min(max, current[0] + step), integer));
        Runnable down = () -> f.setValue(number(Math.max(min, current[0] - step), integer));
        spinners.add(new Spinner(f, add(new Arrow(ax, y, ARROW_SLOT - 1, top, true, up)), add(new Arrow(ax, y + top, ARROW_SLOT - 1, H - top, false, down)),
                current, min, max));
        return f;
    }

    /** A number as a field shows it: whole, or with up to three decimals and no trailing zeros. */
    public static String number(double v, boolean integer) {
        if (integer) return String.valueOf(Math.round(v));
        String t = String.format(Locale.ROOT, "%.3f", v);
        return t.replaceAll("0+$", "").replaceAll("\\.$", "");
    }

    public EditBox field(int x, int y, int w, String value, Consumer<String> onChange) {
        EditBox f = new EditBox(font(), x, y, w, H, Component.empty());
        f.setMaxLength(256);
        f.setValue(value);
        f.setResponder(onChange);
        return add(f);
    }

    /** A text field that shows a hint while it's empty. */
    public EditBox search(int x, int y, int w, Component hint, Consumer<String> onChange) {
        EditBox f = field(x, y, w, "", onChange);
        f.setHint(hint);
        return f;
    }

    /** A line of text that scrolls with the group. */
    public AbstractWidget label(int x, int y, Component text, int colour) { return add(new Label(x, y, text, colour)); }

    /** Greys a control out and says why in its tooltip. Controls that don't apply are greyed, never hidden. */
    public static <T extends AbstractWidget> T disable(T widget, Component why) {
        widget.active = false;
        if (widget instanceof EditBox f) f.setEditable(false);
        widget.setTooltip(Tooltip.create(why));
        return widget;
    }

    /**
     * Makes the group scroll inside {@code area}: widgets keep the positions they were added at, as if the area were
     * {@code contentHeight} tall, and the wheel or the slim bar at the right moves them.
     */
    public void viewport(Bounds area, int contentHeight) {
        viewport = area;
        this.contentHeight = contentHeight;
        scrollTo(scroll);
    }

    private int maxScroll() { return viewport == null ? 0 : Math.max(0, contentHeight - viewport.h()); }

    private void scrollTo(int to) {
        scroll = Math.max(0, Math.min(maxScroll(), to));
        for (AbstractWidget w : children) w.setY(baseY.get(w) - scroll);
    }

    /** Whether the player is typing into one of the group's fields. */
    public boolean typing() { return getFocused() instanceof EditBox f && f.isFocused() && f.active; }

    public void render(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta) {
        for (Spinner sp : spinners) {
            sp.up.active = sp.field.active && sp.value[0] < sp.max - 1e-9;
            sp.down.active = sp.field.active && sp.value[0] > sp.min + 1e-9;
        }
        if (viewport != null) g.enableScissor(viewport.x(), viewport.y(), viewport.right(), viewport.bottom());
        for (AbstractWidget w : children) w.extractRenderState(g, mouseX, mouseY, delta);
        if (viewport != null) {
            g.disableScissor();
            scrollbar(g, viewport, scroll, maxScroll());
        }
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubled) {
        if (viewport != null) {
            if (maxScroll() > 0 && event.button() == InputConstants.MOUSE_BUTTON_LEFT && overBar(viewport, event.x(), event.y())) {
                draggingBar = true;
                scrollTo(barScroll(viewport, maxScroll(), event.y()));
                return true;
            }
            if (!viewport.contains(event.x(), event.y())) {
                setFocused(null);
                return false;
            }
        }
        if (event.button() == InputConstants.MOUSE_BUTTON_RIGHT)
            for (var e : reverse.entrySet()) {
                AbstractWidget w = e.getKey();
                if (w.active && w.visible && w.isMouseOver(event.x(), event.y())) {
                    w.playDownSound(Minecraft.getInstance().getSoundManager());
                    e.getValue().run();   // may rebuild the group, so stop looking straight away
                    return true;
                }
            }
        boolean hit = super.mouseClicked(event, doubled);
        if (!hit) setFocused(null);   // a click on nothing ends typing
        return hit;
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        if (draggingBar) {
            draggingBar = false;
            return true;
        }
        return super.mouseReleased(event);
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double dx, double dy) {
        if (draggingBar) {
            scrollTo(barScroll(viewport, maxScroll(), event.y()));
            return true;
        }
        return super.mouseDragged(event, dx, dy);
    }

    /**
     * Steps the number field under the cursor, a notch to an arrow click. In a group that scrolls, the wheel only
     * steps a field the player has clicked into, and scrolls the group otherwise.
     */
    @Override
    public boolean mouseScrolled(double mx, double my, double h, double v) {
        if (v == 0 || viewport != null && !viewport.contains(mx, my)) return false;
        for (Spinner sp : spinners) {
            EditBox f = sp.field;
            if (mx < f.getX() || mx >= sp.up.getRight() || my < f.getY() || my >= f.getBottom()) continue;
            if (viewport != null && !f.isFocused()) break;
            if (f.active) (v > 0 ? sp.up : sp.down).action.run();
            return true;
        }
        if (viewport == null) return super.mouseScrolled(mx, my, h, v);
        scrollTo(scroll - (int) Math.round(v * 20));
        return true;
    }

    /** A small up or down arrow. Drawn by hand so it stays crisp at 8 pixels, and greyed out when it can't go further. */
    private static final class Arrow extends AbstractButton {
        private final boolean up;
        private final Runnable action;

        Arrow(int x, int y, int w, int h, boolean up, Runnable action) {
            super(x, y, w, h, Component.translatable(up ? "widget.chromastudio.increase" : "widget.chromastudio.decrease"));
            this.up = up;
            this.action = action;
        }

        @Override public void onPress(InputWithModifiers input) { action.run(); }

        @Override
        protected void extractContents(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta) {
            int x = getX(), y = getY();
            g.fill(x, y, x + width, y + height, !active ? 0xFF22262C : isHovered() ? 0xFF55606E : 0xFF3A424D);
            int c = active ? 0xFFE4E9EF : 0xFF4E5560, cx = x + width / 2, cy = y + height / 2;
            for (int r = 0; r < 3; r++) {          // a 5-pixel-wide triangle
                int half = up ? r : 2 - r, yy = cy - 1 + r - (up ? 1 : 0);
                g.fill(cx - half, yy, cx + half + 1, yy + 1, c);
            }
        }

        @Override
        protected void updateWidgetNarration(NarrationElementOutput builder) { defaultButtonNarrationText(builder); }
    }

    /** Text as a widget, so it scrolls and clips with the rest. It never takes a click or the keyboard. */
    private static final class Label extends AbstractWidget {
        private final int colour;

        Label(int x, int y, Component text, int colour) {
            super(x, y, tw(text), 9, text);
            this.colour = colour;
            active = false;
        }

        @Override
        protected void extractWidgetRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta) {
            g.text(font(), getMessage(), getX(), getY(), colour, false);
        }

        @Override
        protected void updateWidgetNarration(NarrationElementOutput builder) { }
    }

    private static boolean overBar(Bounds area, double mx, double my) {
        return mx >= area.right() - BAR_W - 2 && mx < area.right() && my >= area.y() && my < area.bottom();
    }

    private static int barLength(Bounds area, int max) { return Math.max(10, (int) ((long) area.h() * area.h() / (max + area.h()))); }

    /** The scroll position that puts the middle of the bar at the cursor. */
    private static int barScroll(Bounds area, int max, double my) {
        int bar = barLength(area, max), track = area.h() - bar;
        return track <= 0 ? 0 : (int) Math.round((my - area.y() - bar / 2.0) * max / track);
    }

    private static void scrollbar(GuiGraphicsExtractor g, Bounds area, int scroll, int max) {
        if (max <= 0) return;
        int bar = barLength(area, max), by = area.y() + (int) ((long) (area.h() - bar) * scroll / max);
        g.fill(area.right() - BAR_W, by, area.right() - 1, by + bar, 0x80FFFFFF);
    }

    /**
     * A list of rows of one height that scrolls, with a slim bar at the right. It only knows how many rows there are;
     * the caller draws each one and asks {@link #rowAt} which one a click landed on.
     */
    public static final class ScrollList {
        /** Draws row {@code index} in the given rectangle. */
        public interface Row {
            void render(GuiGraphicsExtractor g, int index, int x, int y, int w, int h, boolean hovered);
        }

        public Bounds area;
        private final int rowHeight;
        private final IntSupplier count;
        private int scroll;
        private boolean draggingBar;

        public ScrollList(Bounds area, int rowHeight, IntSupplier count) {
            this.area = area;
            this.rowHeight = rowHeight;
            this.count = count;
        }

        private int max() { return Math.max(0, count.getAsInt() * rowHeight - area.h()); }

        public void render(GuiGraphicsExtractor g, int mouseX, int mouseY, Row row) {
            scroll = Math.min(scroll, max());
            int w = area.w() - (max() > 0 ? BAR_W + 2 : 0);
            int hovered = g.containsPointInScissor(mouseX, mouseY) ? rowAt(mouseX, mouseY) : -1;
            g.enableScissor(area.x(), area.y(), area.right(), area.bottom());
            for (int i = scroll / rowHeight; i < count.getAsInt(); i++) {
                int y = area.y() + i * rowHeight - scroll;
                if (y >= area.bottom()) break;
                row.render(g, i, area.x(), y, w, rowHeight, i == hovered);
            }
            g.disableScissor();
            scrollbar(g, area, scroll, max());
        }

        /** The row under the cursor, or -1. The scrollbar isn't part of any row. */
        public int rowAt(double mx, double my) {
            if (!area.contains(mx, my) || max() > 0 && overBar(area, mx, my)) return -1;
            int i = (int) ((my - area.y() + scroll) / rowHeight);
            return i < count.getAsInt() ? i : -1;
        }

        /** Scrolls just far enough to show a row. */
        public void reveal(int index) {
            int top = index * rowHeight;
            scroll = Math.max(0, Math.min(max(), Math.max(Math.min(scroll, top), top + rowHeight - area.h())));
        }

        public boolean mouseScrolled(double mx, double my, double v) {
            if (!area.contains(mx, my)) return false;
            scroll = Math.max(0, Math.min(max(), scroll - (int) Math.round(v * rowHeight)));
            return true;
        }

        /** Takes a click on the scrollbar. The caller handles clicks on rows through {@link #rowAt}. */
        public boolean mouseClicked(MouseButtonEvent event) {
            if (max() <= 0 || event.button() != InputConstants.MOUSE_BUTTON_LEFT || !overBar(area, event.x(), event.y())) return false;
            draggingBar = true;
            scroll = Math.max(0, Math.min(max(), barScroll(area, max(), event.y())));
            return true;
        }

        public boolean mouseDragged(MouseButtonEvent event) {
            if (!draggingBar) return false;
            scroll = Math.max(0, Math.min(max(), barScroll(area, max(), event.y())));
            return true;
        }

        public boolean mouseReleased() {
            boolean was = draggingBar;
            draggingBar = false;
            return was;
        }
    }

    /** A square of one colour, packed 0xRRGGBB, with a dark rim so it shows on any background. */
    public static void swatch(GuiGraphicsExtractor g, int x, int y, int w, int h, int rgb) {
        g.fill(x, y, x + w, y + h, 0xFF000000);
        g.fill(x + 1, y + 1, x + w - 1, y + h - 1, 0xFF000000 | rgb);
    }

    /** A block as its item, 16 pixels square. A block with no item draws nothing. */
    public static void blockIcon(GuiGraphicsExtractor g, Block block, int x, int y) { blockIcon(g, new ItemStack(block), x, y); }

    public static void blockIcon(GuiGraphicsExtractor g, ItemStack stack, int x, int y) {
        if (!stack.isEmpty()) g.fakeItem(stack, x, y);
    }
}
