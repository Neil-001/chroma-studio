package dev.chromastudio.client.ui.tabs;

import dev.chromastudio.client.ui.Theme;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.input.InputWithModifiers;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

final class ToolButton extends AbstractButton {
    static final int ICON = 16;

    private final ItemStack icon;
    private final boolean labelled, lit;
    private final Runnable action;

    ToolButton(int x, int y, int w, ItemStack icon, Component label, boolean labelled, boolean lit, Runnable action) {
        super(x, y, w, ICON, label);
        this.icon = icon;
        this.labelled = labelled;
        this.lit = lit;
        this.action = action;
    }

    static int labelledWidth(Component label) { return ICON + 2 + PanelTab.font().width(label) + 4; }

    @Override
    public void onPress(InputWithModifiers input) { action.run(); }

    @Override
    protected void extractContents(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta) {
        int x = getX(), y = getY();
        g.fill(x, y, x + width, y + height, !active ? Theme.TOOL_OFF : isHovered() ? Theme.CONTROL_HOVER : lit ? Theme.TOOL_LIT : Theme.TOOL);
        if (lit) g.fill(x, y + height - 1, x + width, y + height, Theme.ACCENT);
        g.fakeItem(icon, x, y);
        if (!active) g.fill(x, y, x + ICON, y + height, Theme.TOOL_OFF & 0xB0FFFFFF);
        if (labelled) g.text(PanelTab.font(), getMessage(), x + ICON + 2, y + 4, active ? Theme.TEXT : Theme.DIM, false);
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput builder) { defaultButtonNarrationText(builder); }
}
