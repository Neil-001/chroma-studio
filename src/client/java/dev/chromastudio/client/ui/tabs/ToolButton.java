package dev.chromastudio.client.ui.tabs;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.input.InputWithModifiers;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

/** A toolbar button: an item as its icon, alone when the toolbar is narrow and with its label when there's room. */
final class ToolButton extends AbstractButton {
    static final int ICON = 16;

    private final ItemStack icon;
    private final boolean labelled, lit;
    private final Runnable action;

    /** {@code lit} marks the button of the panel or mode that's showing now. */
    ToolButton(int x, int y, int w, ItemStack icon, Component label, boolean labelled, boolean lit, Runnable action) {
        super(x, y, w, ICON, label);
        this.icon = icon;
        this.labelled = labelled;
        this.lit = lit;
        this.action = action;
    }

    /** The width a button needs with its label showing. */
    static int labelledWidth(Component label) { return ICON + 2 + PanelTab.font().width(label) + 4; }

    @Override
    public void onPress(InputWithModifiers input) { action.run(); }

    @Override
    protected void extractContents(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta) {
        int x = getX(), y = getY();
        g.fill(x, y, x + width, y + height, !active ? 0xFF1A1D22 : isHovered() ? 0xFF55606E : lit ? 0xFF3F5670 : 0xFF2A3038);
        if (lit) g.fill(x, y + height - 1, x + width, y + height, 0xFFFFB84D);
        g.fakeItem(icon, x, y);
        // The game has no greyed-out item drawing, so a veil over the icon stands in for one.
        if (!active) g.fill(x, y, x + ICON, y + height, 0xB01A1D22);
        if (labelled) g.text(PanelTab.font(), getMessage(), x + ICON + 2, y + 4, active ? PanelTab.TEXT : PanelTab.GREYED, false);
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput builder) { defaultButtonNarrationText(builder); }
}
