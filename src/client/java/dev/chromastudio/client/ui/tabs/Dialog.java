package dev.chromastudio.client.ui.tabs;

import dev.chromastudio.client.ui.Bounds;
import dev.chromastudio.client.ui.Widgets;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;

/** A small panel of controls over a tab, with a title and a close button. A click outside it closes it. */
abstract class Dialog {
    static final int TITLE_H = 13, STATUS_LINES = 2;
    /** The room the status lines take at the bottom of a dialog. */
    static final int STATUS_H = STATUS_LINES * 9 + 2;

    protected final Widgets widgets = new Widgets();
    private final Component title;
    private final int wantW, wantH;
    protected PanelTab tab;
    protected Bounds box;
    /** A line under the controls saying what happened, and its colour. */
    protected Component status;
    protected int statusColour = PanelTab.TEXT;

    protected Dialog(Component title, int width, int height) {
        this.title = title;
        wantW = width;
        wantH = height;
    }

    final void open(PanelTab tab, Bounds content) {
        this.tab = tab;
        layout(content);
    }

    /** Centres the dialog in the tab, no bigger than the tab has room for, and lays its controls out. */
    final void layout(Bounds content) {
        int w = Math.min(wantW, content.w() - 8), h = Math.min(wantH, content.h() - 8);
        box = new Bounds(content.x() + (content.w() - w) / 2, content.y() + (content.h() - h) / 2, w, h);
        widgets.clear();
        widgets.add(new IconButton(box.right() - 13, box.y() + 2, 10, 10, IconButton.Glyph.CLOSE, Component.translatable("window.chromastudio.close"), this::close));
        build(new Bounds(box.x() + 5, box.y() + TITLE_H + 3, box.w() - 10, box.h() - TITLE_H - 7));
    }

    /** Lays the controls out again, keeping the dialog open: after a choice that greys other controls out. */
    protected final void refresh() { layout(tab.bounds); }

    /** Adds the dialog's controls to {@link #widgets}, inside {@code in}. */
    protected abstract void build(Bounds in);

    /** Draws what isn't a control. */
    protected void draw(GuiGraphicsExtractor g, Bounds in, int mouseX, int mouseY) {}

    protected final void close() { tab.closeDialog(); }

    protected final void status(Component text, int colour) {
        status = text;
        statusColour = colour;
    }

    final void render(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta) {
        g.fill(box.x(), box.y(), box.right(), box.bottom(), PanelTab.PANEL);
        g.fill(box.x(), box.y(), box.right(), box.y() + TITLE_H, 0xFF1B2027);
        g.outline(box.x(), box.y(), box.w(), box.h(), PanelTab.BORDER);
        g.text(PanelTab.font(), PanelTab.fit(title.getString(), box.w() - 22), box.x() + 5, box.y() + 3, PanelTab.TEXT, false);
        Bounds in = new Bounds(box.x() + 5, box.y() + TITLE_H + 3, box.w() - 10, box.h() - TITLE_H - 7);
        draw(g, in, mouseX, mouseY);
        widgets.render(g, mouseX, mouseY, delta);
        if (status != null) {
            // Two lines at the bottom, the second cut short if there's more. Pointing at them shows the whole of it.
            List<FormattedCharSequence> lines = PanelTab.font().split(status, in.w());
            int shown = Math.min(STATUS_LINES, lines.size()), top = in.bottom() - shown * 9;
            for (int i = 0; i < shown; i++) g.text(PanelTab.font(), lines.get(i), in.x(), top + i * 9, statusColour, false);
            if (lines.size() > shown && mouseY >= top && mouseY < in.bottom() && mouseX >= in.x() && mouseX < in.right())
                g.setTooltipForNextFrame(PanelTab.font(), Tooltip.splitTooltip(Minecraft.getInstance(), status), mouseX, mouseY);
        }
    }

    boolean mouseClicked(MouseButtonEvent e, boolean doubled) {
        if (!box.contains(e.x(), e.y())) {
            close();
            return true;
        }
        widgets.mouseClicked(e, doubled);
        return true;
    }

    boolean keyPressed(KeyEvent e) {
        if (widgets.keyPressed(e)) return true;
        if (e.isConfirmation()) return confirm();
        return false;
    }

    /** What Enter does. Returns whether it did anything. */
    protected boolean confirm() { return false; }

    void tick() {}
}
