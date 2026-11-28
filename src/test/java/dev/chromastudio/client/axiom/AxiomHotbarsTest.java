package dev.chromastudio.client.axiom;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Optional;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import org.junit.jupiter.api.Test;

public class AxiomHotbarsTest {
    private static final String HERE = AxiomHotbarsTest.class.getName() + "$";
    private static final Component CHANGED = Component.literal("changed");

    public static final class Manager {
        public static int getActiveHotbarIndex() { return 3; }

        public static ItemStack getItemStack(int hotbar, int slot) { return null; }

        public static void setItemStack(int hotbar, int slot, ItemStack stack) {}
    }

    public static final class ChangedManager {
        public static int getActiveHotbarIndex() { return 3; }

        public static ItemStack getItemStack(int index) { return null; }

        public static void setItemStack(int index, ItemStack stack) {}
    }

    public static final class Permission {
        public static final Permission PLAYER_HOTBAR = new Permission();
    }

    public static final class Client {
        public static boolean hasPermission(Permission p) { return p == Permission.PLAYER_HOTBAR; }

        public static boolean isAxiomActive() { return true; }
    }

    private static AxiomInternals<AxiomHotbars.Handles> internals(String manager) {
        return new AxiomInternals<>("test hotbars", CHANGED, () -> AxiomHotbars.find(HERE + manager, HERE + "Client", HERE + "Permission"));
    }

    @Test
    void axiomsOwnSignaturesAreFoundAndWork() throws Throwable {
        AxiomInternals<AxiomHotbars.Handles> in = internals("Manager");
        assertEquals(Optional.empty(), in.reason());
        assertEquals(3, (int) AxiomHotbars.findIndex(HERE + "Manager").invoke());
        assertTrue((boolean) in.handles().allowed().invoke());
        assertTrue((boolean) in.handles().axiomActive().invoke());
    }

    @Test
    void aChangedSignatureTurnsTheHotbarsOffWithTheReason() {
        AxiomInternals<AxiomHotbars.Handles> in = internals("ChangedManager");
        assertEquals(Optional.of(CHANGED), in.reason());
        assertEquals(Optional.of(CHANGED), in.reason(), "it stays off");
        assertFalse(in.found());
        assertThrows(IllegalStateException.class, in::handles);
    }

    @Test
    void theHeldHotbarIsStillKnownWhenTheRestChanged() throws Throwable {
        assertEquals(3, (int) AxiomHotbars.findIndex(HERE + "ChangedManager").invoke());
    }

    @Test
    void aMissingClassTurnsTheHotbarsOff() {
        assertEquals(Optional.of(CHANGED), internals("NoSuchManager").reason());
    }

    @Test
    void aCallThatFailsTurnsTheHotbarsOffForTheSession() {
        AxiomInternals<AxiomHotbars.Handles> in = internals("Manager");
        assertEquals(Optional.empty(), in.reason());
        in.failed(new IllegalStateException("Axiom threw"));
        assertEquals(Optional.of(CHANGED), in.reason());
        assertTrue(in.found(), "what was written can still be put back");
        assertTrue((boolean) assertDoesNotThrow(() -> in.handles().allowed().invoke()));
    }

    @Test
    void theLookupRunsOnce() {
        int[] runs = {0};
        AxiomInternals<String> in = new AxiomInternals<>("test", CHANGED, () -> {
            runs[0]++;
            return "handles";
        });
        in.reason();
        in.reason();
        in.handles();
        assertEquals(1, runs[0]);
    }
}
