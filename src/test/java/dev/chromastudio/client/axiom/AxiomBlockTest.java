package dev.chromastudio.client.axiom;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;

import com.moulberry.axiom.editor.EditorUI;
import java.util.Optional;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.block.Blocks;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class AxiomBlockTest {
    private FakeAxiom axiom;

    @BeforeEach
    void install() {
        axiom = FakeAxiom.install();
        axiom.client();
    }

    @AfterEach
    void uninstall() { FakeAxiom.uninstall(); }

    @Test
    void aFailedReadForgetsTheRememberedBlockAndSaysSo() {
        axiom.tools.activeBlock = Blocks.GRANITE.defaultBlockState();
        AxiomEditor.tick(Minecraft.getInstance());
        assertEquals(Blocks.GRANITE.defaultBlockState(), AxiomEditor.lastActiveBlock());

        axiom.tools.failActiveBlock = new IllegalStateException("getActiveBlock");
        AxiomEditor.tick(Minecraft.getInstance());
        AxiomEditor.tick(Minecraft.getInstance());
        assertNull(AxiomEditor.lastActiveBlock());
        EditorUI.active = false;
        Component failed = Component.translatable("axiom.chromastudio.failed");
        assertEquals(Optional.of(failed), AxiomBlock.unavailable());
        AxiomBridge.Taken taken = AxiomBlock.take();
        assertFalse(taken.taken());
        assertEquals(failed, taken.message());
        assertEquals(Optional.empty(), AxiomBridge.whyNot());

        axiom.tools.failActiveBlock = null;
        EditorUI.active = true;
        AxiomEditor.tick(Minecraft.getInstance());
        EditorUI.active = false;
        assertEquals(Blocks.GRANITE.defaultBlockState(), AxiomEditor.lastActiveBlock());
        assertEquals(Optional.empty(), AxiomBlock.unavailable());
    }
}
