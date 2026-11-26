package dev.chromastudio.client.ui.tabs;

import dev.chromastudio.client.ui.Bounds;
import dev.chromastudio.client.ui.Theme;
import dev.chromastudio.client.ui.Widgets;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;

abstract class Dialog {
    static final int TITLE_H = 13, STATUS_LINES = 2;
    static final int STATUS_H = STATUS_LINES * 9 + 2;

    protected final Widgets widgets = new Widgets();
    private final Component title;
    private final int wantW, wantH;
    protected PanelTab tab;
    protected Bounds box;
    protected Component status;
    protected int statusColour = Theme.TEXT;

    protected Dialog(Component title, int width, int height) {
        this.title = title;
        wantW = width;
        wantH = height;
    }

    final void open(PanelTab tab, Bounds content) {
        this.tab = tab;
        layout(content);
    }

    final void layout(Bounds content) {
        int w = Math.min(wantW, content.w() - 8), h = Math.min(wantH, content.h() - 8);
        box = new Bounds(content.x() + (content.w() - w) / 2, content.y() + (content.h() - h) / 2, w, h);
        widgets.clear();
        widgets.add(new IconButton(box.right() - 13, box.y() + 2, 10, 10, IconButton.Glyph.CLOSE, Component.translatable("window.chromastudio.close"), this::cancel));
        build(new Bounds(box.x() + 5, box.y() + TITLE_H + 3, box.w() - 10, box.h() - TITLE_H - 7));
    }

    protected final void refresh() { layout(tab.bounds); }

    protected abstract void build(Bounds in);

    protected void draw(GuiGraphicsExtractor g, Bounds in, int mouseX, int mouseY) {}

    protected final void close() { tab.closeDialog(); }

    void cancel() { close(); }

    protected int statusLines() { return STATUS_LINES; }

    protected boolean statusFromTop() { return false; }

    protected Component statusTip() { return status; }

    protected final void status(Component text, int colour) {
        status = text;
        statusColour = colour;
    }

    final void render(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta) {
        g.fill(box.x(), box.y(), box.right(), box.bottom(), Theme.PANEL);
        g.fill(box.x(), box.y(), box.right(), box.y() + TITLE_H, Theme.BAR);
        g.outline(box.x(), box.y(), box.w(), box.h(), Theme.BORDER);
        g.text(PanelTab.font(), PanelTab.fit(title.getString(), box.w() - 22), box.x() + 5, box.y() + 3, Theme.TEXT, false);
        Bounds in = new Bounds(box.x() + 5, box.y() + TITLE_H + 3, box.w() - 10, box.h() - TITLE_H - 7);
        draw(g, in, mouseX, mouseY);
        widgets.render(g, mouseX, mouseY, delta);
        if (status != null) {
            List<FormattedCharSequence> lines = PanelTab.font().split(status, in.w());
            int shown = Math.min(statusLines(), lines.size()), top = in.bottom() - (statusFromTop() ? statusLines() : shown) * 9;
            for (int i = 0; i < shown; i++) g.text(PanelTab.font(), lines.get(i), in.x(), top + i * 9, statusColour, false);
            if ((lines.size() > shown || statusTip() != status) && mouseY >= top && mouseY < in.bottom() && mouseX >= in.x() && mouseX < in.right())
                g.setTooltipForNextFrame(PanelTab.font(), Tooltip.splitTooltip(Minecraft.getInstance(), statusTip()), mouseX, mouseY);
        }
    }

    boolean mouseClicked(MouseButtonEvent e, boolean doubled) {
        if (!box.contains(e.x(), e.y())) {
            cancel();
            return true;
        }
        widgets.mouseClicked(e, doubled);
        return true;
    }

    boolean mouseScrolled(double x, double y, double h, double v) { return widgets.mouseScrolled(x, y, h, v); }

    boolean mouseDragged(MouseButtonEvent e, double dx, double dy) { return widgets.mouseDragged(e, dx, dy); }

    boolean mouseReleased(MouseButtonEvent e) { return widgets.mouseReleased(e); }

    boolean keyPressed(KeyEvent e) {
        if (widgets.keyPressed(e)) return true;
        if (e.isConfirmation()) return confirm();
        return false;
    }

    protected boolean confirm() { return false; }

    void tick() {}
}
