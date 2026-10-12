package dev.chromastudio.client.ui;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

/**
 * One tab of the window. {@link ChromaScreen} gives it the area under the title bar and forwards input to it while it
 * is the selected tab. Register one with {@link ChromaScreen#registerTab}.
 */
public abstract class Tab {
    public final String id;

    protected Tab(String id) { this.id = id; }

    /** Lays the tab out in {@code b}. Called when the window opens, after it is moved or resized, and when it enters or leaves fullscreen. */
    public abstract void init(Bounds b);

    /** Drawing is clipped to {@code b}; tooltips aren't. */
    public abstract void render(GuiGraphicsExtractor g, Bounds b, int mouseX, int mouseY, float delta);

    /** Draws the tab while the window is pinned: read-only, over the world, with every colour's alpha scaled by {@code opacity}. */
    public void renderPinned(GuiGraphicsExtractor g, Bounds b, float opacity) {}

    public boolean mouseClicked(MouseButtonEvent e, boolean doubled) { return false; }
    /** Also called for a release outside the tab's area when the tab took the click. The same goes for drags. */
    public boolean mouseReleased(MouseButtonEvent e) { return false; }
    public boolean mouseDragged(MouseButtonEvent e, double dx, double dy) { return false; }
    public boolean mouseScrolled(double x, double y, double h, double v) { return false; }
    public boolean keyPressed(KeyEvent e) { return false; }
    public boolean charTyped(CharacterEvent e) { return false; }

    /** Whether the player is typing into one of the tab's fields. While they are, the window's own keys (open, pin, fullscreen) are theirs to type. */
    public boolean typing() { return false; }

    public void tick() {}

    /** The window closed, other than by being pinned. When it only made way for the settings screen, {@link #init} follows. */
    public void closed() {}

    public Component title() { return Component.translatable("tab.chromastudio." + id); }
}
