package dev.chromastudio.client.ui;

import java.util.List;
import java.util.function.Consumer;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;

/**
 * A tooltip asked for while a screen draws, drawn once the screen has finished. 1.21.1's {@link GuiGraphics} draws a
 * tooltip where it is asked for, so a scissor in force would clip it; newer versions keep one back for the end of
 * the frame themselves, and this does the same. The last one asked for in a frame is the one drawn.
 */
public final class DeferredTooltips {
    private DeferredTooltips() {}

    private static Consumer<GuiGraphics> pending;

    public static void text(Font font, List<? extends FormattedCharSequence> lines, int x, int y) { pending = g -> g.renderTooltip(font, lines, x, y); }

    public static void text(Font font, Component text, int x, int y) { pending = g -> g.renderTooltip(font, text, x, y); }

    public static void components(Font font, List<Component> lines, int x, int y) { pending = g -> g.renderComponentTooltip(font, lines, x, y); }

    /** Draws what was asked for, if anything. Call it once the screen has drawn everything else and closed every scissor. */
    public static void draw(GuiGraphics g) {
        Consumer<GuiGraphics> tooltip = pending;
        pending = null;
        if (tooltip != null) tooltip.accept(g);
    }
}
