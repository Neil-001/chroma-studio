package dev.chromastudio.client.ui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.util.ARGB;

/** Stands in for a tab that hasn't been written yet: it draws its title in the middle. */
final class PlaceholderTab extends Tab {
    PlaceholderTab(String id) { super(id); }

    @Override
    public void init(Bounds b) { }

    @Override
    public void render(GuiGraphicsExtractor g, Bounds b, int mouseX, int mouseY, float delta) { renderPinned(g, b, 1); }

    @Override
    public void renderPinned(GuiGraphicsExtractor g, Bounds b, float opacity) {
        var font = Minecraft.getInstance().font;
        g.centeredText(font, title(), b.x() + b.w() / 2, b.y() + (b.h() - font.lineHeight) / 2, ARGB.multiplyAlpha(0xFF9AA5B3, opacity));
    }
}
