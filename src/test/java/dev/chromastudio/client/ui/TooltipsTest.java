package dev.chromastudio.client.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;
import net.minecraft.SharedConstants;
import net.minecraft.network.chat.Component;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.level.block.Blocks;
import org.junit.jupiter.api.Test;

class TooltipsTest {
    @Test
    void aBlockWhoseLinesFailIsWarnedAboutOnce() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
        Supplier<List<Component>> broken = () -> {
            throw new IllegalStateException("broken");
        };
        List<Component> lines = new ArrayList<>(List.of(Component.literal("Granite")));
        assertTrue(Tooltips.add(Blocks.GRANITE, broken, lines));
        assertFalse(Tooltips.add(Blocks.GRANITE, broken, lines));
        assertTrue(Tooltips.add(Blocks.DIORITE, broken, lines));
        assertEquals(List.of(Component.literal("Granite")), lines);

        assertFalse(Tooltips.add(Blocks.GRANITE, () -> List.of(Component.literal("#8A6050")), lines));
        assertEquals(2, lines.size());
    }
}
