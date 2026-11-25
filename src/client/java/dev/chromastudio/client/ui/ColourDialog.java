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
import net.minecraft.client.gui.components.Button;
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
    private Button done;
    private boolean filling;

    ColourDialog(Component title, Color start, Consumer<Color> onChange, Runnable onClose) {
        super(title, 260, 144, onClose);
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
            bad[i] = false;
        }
        compare = new Bounds(cx, in.y() + 3 * ROW + 2, in.right() - cx, Widgets.H);
        Component done = Component.translatable("gui.done"), cancel = Component.translatable("gui.cancel");
        int y = in.bottom() - Widgets.H, doneW = Widgets.buttonWidth(done) + 14, cancelW = Widgets.buttonWidth(cancel) + 6;
        this.done = widgets.button(in.right() - doneW, y, doneW, done, this::close);
        widgets.button(in.right() - doneW - 2 - cancelW, y, cancelW, cancel, this::cancel);
        widgets.setFocused(fields[0]);
        showBad();
    }

    private int firstBad() {
        for (int i = 0; i < bad.length; i++) if (bad[i]) return i;
        return -1;
    }

    private void showBad() {
        boolean ok = firstBad() < 0;
        done.active = ok;
        done.setTooltip(ok ? null : Tooltip.create(tr("fix")));
    }

    private void typed(int i, String text, Predicate<String> take) {
        if (filling || fields[i] == null) return;
        bad[i] = !take.test(text);
        fields[i].setTextColor(bad[i] ? Theme.FIELD_BAD : EditBox.DEFAULT_TEXT_COLOR);
        showBad();
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
        showBad();
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
        int hint = firstBad();
        for (int i = 0; i < fields.length && hint < 0; i++) if (fields[i].isFocused()) hint = i;
        for (int i = 0; i < fields.length && hint < 0; i++) if (fields[i].isMouseOver(mouseX, mouseY)) hint = i;
        if (hint >= 0)
            g.textWithWordWrap(font, tr(NAMES[hint] + ".tip"), labelX, compare.bottom() + 4, in.right() - labelX, bad[hint] ? Theme.FIELD_BAD : Theme.FIXED_SOFT, false);
        int half = compare.w() / 2;
        swatch(g, font, compare.x(), half, start.rgb(), tr("old"));
        swatch(g, font, compare.x() + half, compare.w() - half, picker.model.colour().rgb(), tr("new"));
    }

    private void swatch(GuiGraphicsExtractor g, Font font, int x, int w, int rgb, Component label) {
        Widgets.swatch(g, x, compare.y(), w, compare.h(), rgb);
        int ink = Color.ofRgb(rgb).oklab().l() < Theme.LIGHT_FROM ? 0xFFFFFFFF : 0xFF000000;
        g.text(font, label, x + (w - font.width(label)) / 2, compare.y() + 4, ink, false);
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
    protected void confirm() {
        if (firstBad() < 0) close();
        else picked(-1);
    }

    @Override
    void close() {
        picker.close();
        super.close();
    }
}
