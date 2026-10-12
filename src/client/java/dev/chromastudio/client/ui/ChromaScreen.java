package dev.chromastudio.client.ui;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** The mod's window. For now it only shows its title; Escape closes it. */
public final class ChromaScreen extends Screen {
    public ChromaScreen() {
        super(Component.translatable("screen.chromastudio.title"));
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor ctx, int mx, int my, float delta) {
        super.extractRenderState(ctx, mx, my, delta);
        ctx.text(font, title, (width - font.width(title)) / 2, (height - font.lineHeight) / 2, 0xFFFFFFFF);
    }
}
