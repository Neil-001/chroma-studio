package dev.chromastudio.client.ui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

abstract class Popup {
    static final int TITLE_H = 13;

    protected final Widgets widgets = new Widgets();
    private final Component title;
    private final int wantW, wantH;
    private final Runnable onClose;
    protected Bounds box;

    protected Popup(Component title, int width, int height, Runnable onClose) {
        this.title = title;
        wantW = width;
        wantH = height;
        this.onClose = onClose;
    }

    final void layout(int screenW, int screenH) {
        int w = Math.min(wantW, screenW - 8), h = Math.min(wantH, screenH - 8);
        box = place(screenW, screenH, w, h);
        widgets.clear();
        build(inside());
    }

    protected Bounds place(int screenW, int screenH, int w, int h) { return new Bounds((screenW - w) / 2, (screenH - h) / 2, w, h); }

    private Bounds inside() { return new Bounds(box.x() + 5, box.y() + TITLE_H + 3, box.w() - 10, box.h() - TITLE_H - 7); }

    protected abstract void build(Bounds in);

    protected boolean fixedColours() { return false; }

    protected void draw(GuiGraphicsExtractor g, Bounds in, int mouseX, int mouseY) {}

    void close() { onClose.run(); }

    final void render(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta) {
        g.fill(0, 0, g.guiWidth(), g.guiHeight(), Theme.SCRIM);
        boolean fixed = fixedColours();
        g.fill(box.x(), box.y(), box.right(), box.bottom(), fixed ? Theme.FIXED_PANEL : Theme.PANEL);
        g.fill(box.x(), box.y(), box.right(), box.y() + TITLE_H, fixed ? Theme.FIXED_BAR : Theme.BAR);
        g.outline(box.x(), box.y(), box.w(), box.h(), fixed ? Theme.FIXED_BORDER : Theme.BORDER);
        g.text(Minecraft.getInstance().font, title, box.x() + 5, box.y() + 3, fixed ? Theme.FIXED_TEXT : Theme.TEXT, false);
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

    protected void confirm() {}
}
