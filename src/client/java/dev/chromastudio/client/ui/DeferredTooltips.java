package dev.chromastudio.client.ui;

import java.util.List;
import java.util.function.Consumer;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipPositioner;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;

public final class DeferredTooltips {
    private DeferredTooltips() {}

    private static Consumer<GuiGraphics> pending;

    public static void text(Font font, List<? extends FormattedCharSequence> lines, int x, int y) { pending = g -> g.renderTooltip(font, lines, x, y); }

    public static void text(Font font, Component text, int x, int y) { pending = g -> g.renderTooltip(font, text, x, y); }

    public static void text(Font font, List<FormattedCharSequence> lines, ClientTooltipPositioner place, int x, int y) {
        pending = g -> g.renderTooltip(font, lines, place, x, y);
    }

    public static void components(Font font, List<Component> lines, int x, int y) { pending = g -> g.renderComponentTooltip(font, lines, x, y); }

    public static void draw(GuiGraphics g) {
        Consumer<GuiGraphics> tooltip = pending;
        pending = null;
        if (tooltip != null) tooltip.accept(g);
    }
}
