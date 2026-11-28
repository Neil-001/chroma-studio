package dev.chromastudio.client.ui.tabs;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.mojang.blaze3d.platform.InputConstants;
import dev.chromastudio.client.input.MouseButtonEvent;
import dev.chromastudio.client.ui.Bounds;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
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
    void onlyALeftReleaseEndsAStopPress() throws Exception {
        GradientTab tab = new GradientTab();
        field("pressedStop").setInt(tab, 1);
        field("draggingStop").setBoolean(tab, true);
        assertFalse(tab.released(new MouseButtonEvent(0, 0, InputConstants.MOUSE_BUTTON_RIGHT, 0)));
        assertEquals(1, field("pressedStop").getInt(tab));
    }

    @Test
    void everyStepCellFitsTheStripWithoutOverlapping() throws Exception {
        Method grid = GradientTab.class.getDeclaredMethod("grid", Bounds.class, int.class, boolean.class);
        grid.setAccessible(true);
        GradientTab tab = new GradientTab();
        for (Bounds area : new Bounds[] {new Bounds(3, 57, 421, 151), new Bounds(3, 57, 421, 60), new Bounds(10, 40, 900, 400)}) {
            for (boolean compact : new boolean[] {false, true}) {
                for (int count = 2; count <= 32; count++) {
                    Object g = grid.invoke(tab, area, count, compact);
                    Method cellX = g.getClass().getDeclaredMethod("cellX", int.class), cellY = g.getClass().getDeclaredMethod("cellY", int.class), cellH = g.getClass().getDeclaredMethod("cellH");
                    cellX.setAccessible(true);
                    cellY.setAccessible(true);
                    cellH.setAccessible(true);
                    Field cellW = g.getClass().getDeclaredField("cellW");
                    cellW.setAccessible(true);
                    int w = cellW.getInt(g), h = (int) cellH.invoke(g);
                    for (int i = 0; i < count; i++) {
                        int x = (int) cellX.invoke(g, i), y = (int) cellY.invoke(g, i);
                        String where = area + " " + compact + " " + count + " step " + i;
                        assertTrue(x >= area.x() && x + w <= area.right(), where);
                        if (!compact && area.h() >= 151) assertTrue(y >= area.y() && y + h <= area.bottom(), where);
                        if (i > 0 && (int) cellY.invoke(g, i - 1) == y) assertEquals((int) cellX.invoke(g, i - 1) + w, x, where);
                    }
                }
            }
        }
    }
}
