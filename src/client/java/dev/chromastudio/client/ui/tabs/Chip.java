package dev.chromastudio.client.ui.tabs;

import dev.chromastudio.client.ui.Theme;
import dev.chromastudio.client.ui.Widgets;
import java.util.function.BooleanSupplier;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.input.InputWithModifiers;
import net.minecraft.network.chat.Component;

/** A compact toggle: its label alone, lit while it is on. The filter row has too little room for "Label: ON". */
final class Chip extends AbstractButton {
    private final BooleanSupplier on;
    private final Runnable press;

    Chip(int x, int y, Component label, BooleanSupplier on, Runnable press) {
        super(x, y, width(label), Widgets.H, label);
        this.on = on;
        this.press = press;
    }

    static int width(Component label) { return Widgets.tw(label) + 8; }

    @Override
    public void onPress(InputWithModifiers input) { press.run(); }

    @Override
    protected void renderContents(GuiGraphics g, int mouseX, int mouseY, float delta) {
        boolean lit = on.getAsBoolean();
        int x = getX(), y = getY();
        g.fill(x, y, x + width, y + height, !active ? Theme.CHIP_OFF : lit ? (isHovered() ? Theme.LIT_HOVER : Theme.LIT) : isHovered() ? Theme.CONTROL : Theme.CHIP);
        g.renderOutline(x, y, width, height, lit && active ? Theme.HIGHLIGHT : Theme.BORDER);
        Widgets.centred(g, getMessage(), x + width / 2, y + (height - 8) / 2, !active ? Theme.CHIP_GREYED : lit ? Theme.ON_LIT : Theme.SOFT);
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput builder) { defaultButtonNarrationText(builder); }
}
