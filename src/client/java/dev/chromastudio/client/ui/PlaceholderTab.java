package dev.chromastudio.client.ui;

import dev.chromastudio.client.ARGB;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;

/** Stands in for a tab that hasn't been written yet: it draws its title in the middle. */
final class PlaceholderTab extends Tab {
    PlaceholderTab(String id) { super(id); }

    @Override
    public void init(Bounds b) { }

    @Override
    public void render(GuiGraphics g, Bounds b, int mouseX, int mouseY, float delta) { renderPinned(g, b, 1); }

    @Override
    public void renderPinned(GuiGraphics g, Bounds b, float opacity) {
        var font = Minecraft.getInstance().font;
        Widgets.centred(g, title(), b.x() + b.w() / 2, b.y() + (b.h() - font.lineHeight) / 2, ARGB.multiplyAlpha(Theme.DIM, opacity));
    }
}
