package dev.chromastudio.client.ui.tabs;

import dev.chromastudio.client.input.InputWithModifiers;
import dev.chromastudio.client.ui.Theme;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.network.chat.Component;

/** A small square button with a drawn sign: an arrow, a cross or a plus. Its name is its tooltip. */
final class IconButton extends AbstractButton {
    enum Glyph { UP, DOWN, REMOVE, CLOSE, ADD }

    private final Glyph glyph;
    private final Runnable action;

    IconButton(int x, int y, int w, int h, Glyph glyph, Component name, Runnable action) {
        super(x, y, w, h, name);
        this.glyph = glyph;
        this.action = action;
        setTooltip(Tooltip.create(name));
    }

    @Override
    public void onPress() { action.run(); }

    @Override
    protected void renderWidget(GuiGraphics g, int mouseX, int mouseY, float delta) {
        int x = getX(), y = getY();
        boolean close = glyph == Glyph.CLOSE || glyph == Glyph.REMOVE;
        g.fill(x, y, x + width, y + height, !active ? Theme.CONTROL_OFF : isHovered() ? (close ? Theme.DANGER : Theme.CONTROL_HOVER) : Theme.CONTROL);
        int c = active ? Theme.TEXT : Theme.GREYED, cx = x + width / 2, cy = y + height / 2;
        switch (glyph) {
            case UP, DOWN -> {
                boolean up = glyph == Glyph.UP;
                for (int r = 0; r < 3; r++) {
                    int half = up ? r : 2 - r, yy = cy - 1 + r - (up ? 1 : 0);
                    g.fill(cx - half, yy, cx + half + 1, yy + 1, c);
                }
            }
            case ADD -> {
                g.fill(cx - 3, cy - 1, cx + 3, cy, c);
                g.fill(cx - 1, cy - 3, cx, cy + 2, c);
            }
            default -> {
                for (int i = -2; i <= 2; i++) {
                    g.fill(cx + i, cy + i, cx + i + 1, cy + i + 1, c);
                    g.fill(cx + i, cy - i, cx + i + 1, cy - i + 1, c);
                }
            }
        }
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput builder) { defaultButtonNarrationText(builder); }
}
