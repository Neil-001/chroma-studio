package dev.chromastudio.client.ui.tabs;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.mojang.blaze3d.platform.InputConstants;
import java.lang.reflect.Field;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.input.MouseButtonInfo;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class GradientTabTest {
    @BeforeAll
    static void loadGame() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    private static Field field(String name) throws NoSuchFieldException {
        Field f = GradientTab.class.getDeclaredField(name);
        f.setAccessible(true);
        return f;
    }

    @Test
    void closingTheWindowDropsAStopPressWhoseReleaseNeverCame() throws Exception {
        GradientTab tab = new GradientTab();
        field("pressedStop").setInt(tab, 1);
        field("draggingStop").setBoolean(tab, true);
        tab.closed();
        assertEquals(-1, field("pressedStop").getInt(tab));
        assertFalse(field("draggingStop").getBoolean(tab));
    }

    @Test
    void anotherButtonPressedDuringAStopPressCancelsIt() throws Exception {
        GradientTab tab = new GradientTab();
        field("pressedStop").setInt(tab, 1);
        field("draggingStop").setBoolean(tab, true);
        assertTrue(tab.clicked(new MouseButtonEvent(0, 0, new MouseButtonInfo(InputConstants.MOUSE_BUTTON_RIGHT, 0)), false));
        assertEquals(-1, field("pressedStop").getInt(tab));
        assertFalse(field("draggingStop").getBoolean(tab));
    }
}
