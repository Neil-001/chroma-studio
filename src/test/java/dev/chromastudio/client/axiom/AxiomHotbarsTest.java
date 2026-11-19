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

/**
 * How Chroma Studio tells an Axiom whose hotbars it knows from one whose internals changed, without Axiom: the
 * lookup is pointed at stand-ins with Axiom 6.1's signatures, and at one with a changed signature.
 */
public class AxiomHotbarsTest {
    private static final String HERE = AxiomHotbarsTest.class.getName() + "$";
    private static final Component CHANGED = Component.literal("changed");

    /** {@code HotbarManager} as Axiom 6.1 has it. */
    public static final class Manager {
        public static int getActiveHotbarIndex() { return 3; }

        public static ItemStack getItemStack(int hotbar, int slot) { return null; }

        public static void setItemStack(int hotbar, int slot, ItemStack stack) {}
    }

    /** As a later Axiom might have it: the same names, but a slot set by one number. */
    public static final class ChangedManager {
        public static int getActiveHotbarIndex() { return 3; }

        public static ItemStack getItemStack(int index) { return null; }

        public static void setItemStack(int index, ItemStack stack) {}
    }

    /** {@code AxiomPermission}, with the one constant used. */
    public static final class Permission {
        public static final Permission PLAYER_HOTBAR = new Permission();
    }

    /** {@code AxiomClient}'s two methods. */
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
        assertEquals(3, (int) in.handles().active().invoke());
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
        assertEquals(3, (int) assertDoesNotThrow(() -> in.handles().active().invoke()));
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
