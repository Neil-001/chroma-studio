package dev.chromastudio.client.ui;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

public abstract class Tab {
    public final String id;

    protected Tab(String id) { this.id = id; }

    public abstract void init(Bounds b);

    public abstract void render(GuiGraphicsExtractor g, Bounds b, int mouseX, int mouseY, float delta);

    public void renderPinned(GuiGraphicsExtractor g, Bounds b, float opacity) {}

    public boolean mouseClicked(MouseButtonEvent e, boolean doubled) { return false; }
    public boolean mouseReleased(MouseButtonEvent e) { return false; }
    public boolean mouseDragged(MouseButtonEvent e, double dx, double dy) { return false; }
    public boolean mouseScrolled(double x, double y, double h, double v) { return false; }
    public boolean keyPressed(KeyEvent e) { return false; }
    public boolean charTyped(CharacterEvent e) { return false; }

    public boolean dismiss() { return false; }

    public boolean typing() { return false; }

    public void tick() {}

    public void closed() {}

    public Component title() { return Component.translatable("tab.chromastudio." + id); }
}
