package dev.chromastudio.client.ui;

import dev.chromastudio.client.input.CharacterEvent;
import dev.chromastudio.client.input.KeyEvent;
import dev.chromastudio.client.input.MouseButtonEvent;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

/**
 * A small box of controls in the middle of a screen, with a title, over everything else the screen shows. While one
 * is open the screen gives it all the input; a click outside it or Escape closes it.
 */
abstract class Popup {
    static final int TITLE_H = 13;

    protected final Widgets widgets = new Widgets();
    private final Component title;
    private final int wantW, wantH;
    private final Runnable onClose;
    protected Bounds box;

    /** {@code onClose} runs when the popup closes, however it does: the screen forgets it there. */
    protected Popup(Component title, int width, int height, Runnable onClose) {
        this.title = title;
        wantW = width;
        wantH = height;
        this.onClose = onClose;
    }

    /** Centres the box on a screen of this size, no bigger than there's room for, and lays its controls out. */
    final void layout(int screenW, int screenH) {
        int w = Math.min(wantW, screenW - 8), h = Math.min(wantH, screenH - 8);
        box = new Bounds((screenW - w) / 2, (screenH - h) / 2, w, h);
        widgets.clear();
        build(inside());
    }

    private Bounds inside() { return new Bounds(box.x() + 5, box.y() + TITLE_H + 3, box.w() - 10, box.h() - TITLE_H - 7); }

    /** Adds the popup's controls to {@link #widgets}, inside {@code in}. */
    protected abstract void build(Bounds in);

    /** Draws what isn't a control. */
    protected void draw(GuiGraphics g, Bounds in, int mouseX, int mouseY) {}

    void close() { onClose.run(); }

    final void render(GuiGraphics g, int mouseX, int mouseY, float delta) {
        g.fill(0, 0, g.guiWidth(), g.guiHeight(), Theme.SCRIM);
        g.fill(box.x(), box.y(), box.right(), box.bottom(), Theme.PANEL);
        g.fill(box.x(), box.y(), box.right(), box.y() + TITLE_H, Theme.BAR);
        g.renderOutline(box.x(), box.y(), box.w(), box.h(), Theme.BORDER);
        g.drawString(Minecraft.getInstance().font, title, box.x() + 5, box.y() + 3, Theme.TEXT, false);
        draw(g, inside(), mouseX, mouseY);
        widgets.render(g, mouseX, mouseY, delta);
    }

    boolean mouseClicked(MouseButtonEvent e, boolean doubled) {
        if (!box.contains(e.x(), e.y())) close();
        else widgets.mouseClicked(e, doubled);
        return true;
    }

    boolean mouseReleased(MouseButtonEvent e) { return widgets.mouseReleased(e); }
    boolean mouseDragged(MouseButtonEvent e, double dx, double dy) { return widgets.mouseDragged(e, dx, dy); }
    boolean mouseScrolled(double x, double y, double h, double v) { return widgets.mouseScrolled(x, y, h, v); }
    boolean charTyped(CharacterEvent e) { return widgets.charTyped(e); }

    boolean keyPressed(KeyEvent e) {
        if (e.isEscape()) close();
        else if (!widgets.keyPressed(e) && e.isConfirmation()) confirm();
        return true;
    }

    /** What Enter does when no control takes it. */
    protected void confirm() {}
}
