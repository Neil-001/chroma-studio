package dev.chromastudio.client.ui;

import dev.chromastudio.client.ui.tabs.ColourPicker;
import dev.chromastudio.client.ui.tabs.TargetModel;
import dev.chromastudio.core.color.Color;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Predicate;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

final class ColourDialog extends Popup {
    private static final int LABEL_W = 26, ROW = Widgets.H + 2, HUE_W = 12, GAP = 8;
    private static final String[] NAMES = {"hex", "rgb", "hsl"};
    private static final int[] LENGTHS = {7, 13, 13};

    private final ColourPicker picker;
    private final Color start;
    private final Consumer<Color> onChange;
    private final EditBox[] fields = new EditBox[3];
    private final boolean[] bad = new boolean[3];
    private Bounds compare;
    private boolean filling;

    ColourDialog(Component title, Color start, Consumer<Color> onChange, Runnable onClose) {
        super(title, 260, 124, onClose);
        this.start = start;
        this.onChange = onChange;
        picker = new ColourPicker(start, _ -> picked(-1));
    }

    private static Component tr(String key) { return Component.translatable("settings.chromastudio.colour." + key); }

    @Override
    protected boolean fixedColours() { return true; }

    @Override
    protected void build(Bounds in) {
        TargetModel model = picker.model;
        int side = Math.min(in.h(), in.w() / 2 - HUE_W - 2 - GAP);
        picker.layout(new Bounds(in.x(), in.y(), side + 2 + HUE_W, side), HUE_W);
        int cx = in.x() + side + 2 + HUE_W + GAP, fx = cx + LABEL_W, fw = in.right() - fx;
        String[] values = {model.hex(), model.rgbText(), model.hslText()};
        List<Predicate<String>> takes = List.of(model::typeHex, model::typeRgb, model::typeHsl);
        for (int i = 0; i < fields.length; i++) {
            int at = i;
            fields[i] = widgets.field(fx, in.y() + i * ROW, fw, values[i], text -> typed(at, text, takes.get(at)));
            fields[i].setMaxLength(LENGTHS[i]);
            fields[i].setTooltip(Tooltip.create(tr(NAMES[i] + ".tip")));
            bad[i] = false;
        }
        Component done = Component.translatable("gui.done"), cancel = Component.translatable("gui.cancel");
        int y = in.bottom() - Widgets.H, doneW = Widgets.buttonWidth(done) + 14, cancelW = Widgets.buttonWidth(cancel) + 6;
        widgets.button(in.right() - doneW, y, doneW, done, this::close);
        int cancelX = in.right() - doneW - 2 - cancelW;
        widgets.button(cancelX, y, cancelW, cancel, this::cancel);
        compare = new Bounds(cx, y, Math.max(Widgets.H, cancelX - 4 - cx), Widgets.H);
    }

    private void typed(int i, String text, Predicate<String> take) {
        if (filling || fields[i] == null) return;
        bad[i] = !take.test(text);
        fields[i].setTextColor(bad[i] ? Theme.FIELD_BAD : EditBox.DEFAULT_TEXT_COLOR);
        if (!bad[i]) picked(i);
    }

    private void picked(int except) {
        TargetModel model = picker.model;
        String[] values = {model.hex(), model.rgbText(), model.hslText()};
        filling = true;
        for (int i = 0; i < fields.length; i++) {
            if (i == except) continue;
            fields[i].setValue(values[i]);
            fields[i].setTextColor(EditBox.DEFAULT_TEXT_COLOR);
            bad[i] = false;
        }
        filling = false;
        onChange.accept(model.colour());
    }

    private void cancel() {
        picker.model.setColour(start);
        onChange.accept(start);
        close();
    }

    @Override
    protected void draw(GuiGraphicsExtractor g, Bounds in, int mouseX, int mouseY) {
        Font font = Minecraft.getInstance().font;
        picker.render(g, font, 1);
        int labelX = fields[0].getX() - LABEL_W;
        for (int i = 0; i < NAMES.length; i++) {
            EditBox f = fields[i];
            g.text(font, tr(NAMES[i]), labelX, f.getY() + 4, Theme.FIXED_SOFT, false);
            if (bad[i]) g.outline(f.getX() - 1, f.getY() - 1, f.getWidth() + 2, f.getHeight() + 2, Theme.FIELD_BAD);
        }
        for (int i = 0; i < NAMES.length; i++) {
            if (!bad[i]) continue;
            g.textWithWordWrap(font, tr(NAMES[i] + ".tip"), labelX, fields[2].getY() + ROW + 2, in.right() - labelX, Theme.FIELD_BAD, false);
            break;
        }
        int half = compare.w() / 2;
        g.fill(compare.x(), compare.y(), compare.right(), compare.bottom(), 0xFF000000);
        g.fill(compare.x() + 1, compare.y() + 1, compare.x() + half, compare.bottom() - 1, 0xFF000000 | start.rgb());
        g.fill(compare.x() + half, compare.y() + 1, compare.right() - 1, compare.bottom() - 1, 0xFF000000 | picker.model.colour().rgb());
        if (compare.contains(mouseX, mouseY)) g.setTooltipForNextFrame(font, tr("compare.tip"), mouseX, mouseY);
    }

    @Override
    boolean mouseClicked(MouseButtonEvent e, boolean doubled) {
        if (box.contains(e.x(), e.y()) && picker.mouseClicked(e.x(), e.y())) {
            widgets.setFocused(null);
            return true;
        }
        return super.mouseClicked(e, doubled);
    }

    @Override
    boolean mouseDragged(MouseButtonEvent e, double dx, double dy) { return picker.mouseDragged(e.x(), e.y()) || super.mouseDragged(e, dx, dy); }

    @Override
    boolean mouseReleased(MouseButtonEvent e) { return picker.mouseReleased() || super.mouseReleased(e); }

    @Override
    boolean keyPressed(KeyEvent e) {
        if (!e.isEscape()) return super.keyPressed(e);
        cancel();
        return true;
    }

    @Override
    protected void confirm() { close(); }

    @Override
    void close() {
        picker.close();
        super.close();
    }
}
