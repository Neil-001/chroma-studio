package dev.chromastudio.client.ui.tabs;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.chromastudio.client.ui.Bounds;
import net.minecraft.SharedConstants;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.server.Bootstrap;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class PanelTabTest {
    @BeforeAll
    static void loadGame() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    private static final class Plain extends PanelTab {
        Plain() { super("plain"); }
        @Override protected void layout(Bounds b) {}
        @Override protected void draw(GuiGraphics g, Bounds b, int mouseX, int mouseY, float delta) {}
        void show() { open(new Dialog(Component.literal("A dialog"), 120, 60) { @Override protected void build(Bounds in) {} }); }
        boolean showing() { return dialogOpen(); }
        boolean panel;
        @Override protected boolean dismissPanel() {
            boolean was = panel;
            panel = false;
            return was;
        }
    }

    /** The window asks the tab first when Escape is pressed, and closes only when the tab had nothing open. */
    @Test
    void escapeClosesAnOpenDialogBeforeTheWindow() {
        Plain tab = new Plain();
        tab.init(new Bounds(0, 0, 400, 200));
        assertFalse(tab.dismiss(), "nothing is open, so the window closes");
        tab.show();
        assertTrue(tab.showing());
        assertTrue(tab.dismiss(), "the dialog took the key");
        assertFalse(tab.showing());
        assertFalse(tab.dismiss());

        // A panel that took the tab's place goes after the dialog over it, and before the window.
        tab.panel = true;
        tab.show();
        assertTrue(tab.dismiss());
        assertTrue(tab.panel && !tab.showing(), "the dialog went first");
        assertTrue(tab.dismiss());
        assertFalse(tab.panel);
        assertFalse(tab.dismiss());
    }
}
