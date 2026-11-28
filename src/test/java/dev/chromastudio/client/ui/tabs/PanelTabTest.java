package dev.chromastudio.client.ui.tabs;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.mojang.blaze3d.platform.InputConstants;
import dev.chromastudio.client.input.MouseButtonEvent;
import dev.chromastudio.client.ui.Bounds;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.SharedConstants;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarrationElementOutput;
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

        tab.panel = true;
        tab.show();
        assertTrue(tab.dismiss());
        assertTrue(tab.panel && !tab.showing(), "the dialog went first");
        assertTrue(tab.dismiss());
        assertFalse(tab.panel);
        assertFalse(tab.dismiss());
    }

    private static final class Both extends PanelTab {
        final List<String> calls = new ArrayList<>();
        Both() { super("both"); }
        @Override protected void layout(Bounds b) {
            widgets.add(new AbstractWidget(b.x(), b.y(), b.w(), b.h(), Component.empty()) {
                @Override public boolean mouseClicked(double x, double y, int button) { return calls.add("widget click"); }
                @Override public boolean mouseReleased(double x, double y, int button) { return calls.add("widget release"); }
                @Override protected void renderWidget(GuiGraphics g, int mouseX, int mouseY, float delta) {}
                @Override protected void updateWidgetNarration(NarrationElementOutput out) {}
            });
        }
        @Override protected void draw(GuiGraphics g, Bounds b, int mouseX, int mouseY, float delta) {}
        @Override protected boolean clicked(MouseButtonEvent e, boolean doubled) { return calls.add("own click"); }
        @Override protected boolean released(MouseButtonEvent e) { return calls.add("own release"); }
    }

    @Test
    void theWidgetsSeeAClickTheTabTook() {
        Both tab = new Both();
        tab.init(new Bounds(0, 0, 400, 200));
        MouseButtonEvent e = new MouseButtonEvent(10, 10, InputConstants.MOUSE_BUTTON_LEFT, 0);
        assertTrue(tab.mouseClicked(e, false));
        assertTrue(tab.mouseReleased(e));
        assertEquals(List.of("own click", "widget click", "own release", "widget release"), tab.calls);
    }
}
