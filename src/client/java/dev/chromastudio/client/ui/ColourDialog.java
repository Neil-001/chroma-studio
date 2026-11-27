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
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

/**
 * Picks a colour as the Match tab's target is picked: a square with a hue bar, and hex, RGB and HSL fields that all
 * follow each other. Every change goes to {@code onChange} straight away, so what the colour is for shows it at once.
 */
final class ColourDialog extends Popup {
    private static final int LABEL_W = 26, ROW = Widgets.H + 2;

    private final ColourPicker picker;
    private final Consumer<Color> onChange;
    private EditBox hex, rgb, hsl;
    /** Set while the dialog writes the fields itself, so it doesn't read them back: a rounded colour taken again would drift. */
    private boolean filling;

    ColourDialog(Component title, Color start, Consumer<Color> onChange, Runnable onClose) {
        super(title, 236, 100, onClose);
        this.onChange = onChange;
        picker = new ColourPicker(start, _ -> picked(null));
    }

    @Override
    protected void build(Bounds in) {
        TargetModel model = picker.model;
        int side = Math.min(in.h(), in.w() / 2 - 14);
        picker.layout(new Bounds(in.x(), in.y(), side + 10, side));
        int fx = in.x() + side + 16 + LABEL_W, fw = in.right() - fx, y = in.y();
        hex = widgets.field(fx, y, fw, model.hex(), text -> typed(hex, text, model::typeHex));
        hex.setMaxLength(7);
        hex.setTooltip(Tooltip.create(Component.translatable("match.chromastudio.hex.tip")));
        y += ROW;
        rgb = widgets.field(fx, y, fw, model.rgbText(), text -> typed(rgb, text, model::typeRgb));
        rgb.setMaxLength(13);
        rgb.setTooltip(Tooltip.create(Component.translatable("match.chromastudio.rgb.tip")));
        y += ROW;
        hsl = widgets.field(fx, y, fw, model.hslText(), text -> typed(hsl, text, model::typeHsl));
        hsl.setMaxLength(13);
        hsl.setTooltip(Tooltip.create(Component.translatable("match.chromastudio.hsl.tip")));
        Component done = Component.translatable("gui.done");
        int doneW = Widgets.buttonWidth(done) + 14;
        widgets.button(in.right() - doneW, in.bottom() - Widgets.H, doneW, done, this::close);
    }


    /** A field's text changed. Text that isn't a colour shows red and changes nothing. */
    private void typed(EditBox field, String text, Predicate<String> take) {
        if (filling || field == null) return;
        boolean valid = take.test(text);
        field.setTextColor(valid ? EditBox.DEFAULT_TEXT_COLOR : Theme.FIELD_BAD);
        if (valid) picked(field);
    }

    /** The colour changed. The fields follow, but for the one being typed in. */
    private void picked(EditBox except) {
        TargetModel model = picker.model;
        filling = true;
        if (hex != except) hex.setValue(model.hex());
        if (rgb != except) rgb.setValue(model.rgbText());
        if (hsl != except) hsl.setValue(model.hslText());
        for (EditBox f : List.of(hex, rgb, hsl)) if (f != except) f.setTextColor(EditBox.DEFAULT_TEXT_COLOR);
        filling = false;
        onChange.accept(model.colour());
    }

    @Override
    protected void draw(GuiGraphicsExtractor g, Bounds in, int mouseX, int mouseY) {
        Font font = Minecraft.getInstance().font;
        picker.render(g, font, 1);
        // Drawn here rather than as controls, because the colour they're drawn in changes as the player picks.
        String[] names = {"hex", "rgb", "hsl"};
        for (int i = 0; i < names.length; i++)
            g.text(font, Component.translatable("settings.chromastudio.colour." + names[i]), hex.getX() - LABEL_W, in.y() + i * ROW + 4, Theme.SOFT, false);
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
    protected void confirm() { close(); }

    @Override
    void close() {
        picker.close();
        super.close();
    }
}
