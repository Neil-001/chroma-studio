package dev.chromastudio.client.ui.tabs;

import static dev.chromastudio.client.ui.Theme.TEXT;

import dev.chromastudio.client.ui.Bounds;
import dev.chromastudio.client.ui.Tab;
import dev.chromastudio.client.ui.Theme;
import dev.chromastudio.client.ui.Widgets;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Util;

abstract class PanelTab extends Tab {
    static final int PAD = 3, GAP = 2, ROW = Widgets.H + GAP;

    protected final Widgets widgets = new Widgets();
    protected Bounds bounds;
    private Dialog dialog;
    private Component note;
    private int noteColour;
    private long noteUntil;

    protected PanelTab(String id) { super(id); }

    static Font font() { return Minecraft.getInstance().font; }

    static String fit(String text, int width) {
        Font font = font();
        if (font.width(text) <= width) return text;
        String dots = "...";
        return font.plainSubstrByWidth(text, Math.max(0, width - font.width(dots))) + dots;
    }

    @Override
    public void init(Bounds b) {
        bounds = b;
        rebuild();
    }

    protected final void rebuild() {
        if (bounds == null) return;
        widgets.clear();
        layout(bounds);
        if (dialog != null) dialog.layout(bounds);
    }

    protected abstract void layout(Bounds b);

    protected abstract void draw(GuiGraphicsExtractor g, Bounds b, int mouseX, int mouseY, float delta);

    protected final void open(Dialog d) {
        dialog = d;
        widgets.setFocused(null);
        d.open(this, bounds);
    }

    final void closeDialog() {
        dialog = null;
        rebuild();
    }

    protected final boolean dialogOpen() { return dialog != null; }

    final void say(Component text, int colour) {
        note = text;
        noteColour = colour;
        noteUntil = Util.getMillis() + 5000;
    }

    final void say(Component text) { say(text, TEXT); }

    protected final Component note() { return note != null && Util.getMillis() < noteUntil ? note : null; }

    protected final int noteColour() { return noteColour; }

    @Override
    public final void render(GuiGraphicsExtractor g, Bounds b, int mouseX, int mouseY, float delta) {
        int mx = dialog == null ? mouseX : -1, my = dialog == null ? mouseY : -1;
        draw(g, b, mx, my, delta);
        widgets.render(g, mx, my, delta);
        drawOver(g, b, mx, my, delta);
        if (dialog != null) {
            g.fill(b.x(), b.y(), b.right(), b.bottom(), Theme.SCRIM);
            dialog.render(g, mouseX, mouseY, delta);
        }
    }

    protected void drawOver(GuiGraphicsExtractor g, Bounds b, int mouseX, int mouseY, float delta) {}

    protected boolean clicked(MouseButtonEvent e, boolean doubled) { return false; }
    protected boolean released(MouseButtonEvent e) { return false; }
    protected boolean dragged(MouseButtonEvent e, double dx, double dy) { return false; }
    protected boolean scrolled(double x, double y, double h, double v) { return false; }
    protected boolean pressed(KeyEvent e) { return false; }
    protected boolean typed(CharacterEvent e) { return false; }
    protected boolean typingElsewhere() { return false; }

    @Override
    public final boolean mouseClicked(MouseButtonEvent e, boolean doubled) {
        if (dialog != null) return dialog.mouseClicked(e, doubled);
        boolean own = clicked(e, doubled);
        return widgets.mouseClicked(e, doubled) | own;
    }

    @Override
    public final boolean mouseReleased(MouseButtonEvent e) {
        if (dialog != null) return dialog.mouseReleased(e);
        return released(e) | widgets.mouseReleased(e);
    }

    @Override
    public final boolean mouseDragged(MouseButtonEvent e, double dx, double dy) {
        if (dialog != null) return dialog.mouseDragged(e, dx, dy);
        return dragged(e, dx, dy) || widgets.mouseDragged(e, dx, dy);
    }

    @Override
    public final boolean mouseScrolled(double x, double y, double h, double v) {
        if (dialog != null) return dialog.mouseScrolled(x, y, h, v) || true;
        return scrolled(x, y, h, v) || widgets.mouseScrolled(x, y, h, v);
    }

    @Override
    public final boolean keyPressed(KeyEvent e) {
        if (dialog != null) return dialog.keyPressed(e);
        return pressed(e) || widgets.keyPressed(e);
    }

    @Override
    public final boolean dismiss() {
        if (dialog == null) return dismissPanel();
        dialog.cancel();
        return true;
    }

    protected boolean dismissPanel() { return false; }

    @Override
    public final boolean charTyped(CharacterEvent e) {
        if (dialog != null) return dialog.widgets.charTyped(e);
        return typed(e) || widgets.charTyped(e);
    }

    @Override
    public final boolean typing() { return dialog != null ? dialog.widgets.typing() : widgets.typing() || typingElsewhere(); }

    @Override
    public void tick() {
        if (dialog != null) dialog.tick();
    }
}
