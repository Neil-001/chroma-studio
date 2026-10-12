package dev.chromastudio.client.ui.tabs;

import dev.chromastudio.client.ui.Widgets;
import java.util.function.BooleanSupplier;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
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
    protected void extractContents(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta) {
        boolean lit = on.getAsBoolean();
        int x = getX(), y = getY();
        g.fill(x, y, x + width, y + height, !active ? 0xFF1B1F25 : lit ? (isHovered() ? 0xFF3F78B0 : 0xFF2F5D8A) : isHovered() ? 0xFF3A424D : 0xFF262C34);
        g.outline(x, y, width, height, lit && active ? 0xFF8EC2F2 : 0xFF3A424D);
        g.centeredText(Minecraft.getInstance().font, getMessage(), x + width / 2, y + (height - 8) / 2, !active ? 0xFF5A626E : lit ? 0xFFFFFFFF : 0xFFC8CED6);
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput builder) { defaultButtonNarrationText(builder); }
}
