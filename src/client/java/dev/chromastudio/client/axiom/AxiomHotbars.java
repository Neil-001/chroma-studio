package dev.chromastudio.client.axiom;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.Optional;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

/**
 * Reads and writes Axiom's extra hotbars. Only {@link AxiomBridge} calls this, once Axiom is known to be loaded.
 * The hotbars are Axiom's internal {@code HotbarManager}, not its API, so the methods are found by exact signature
 * through {@link AxiomInternals}; if any is missing, or a call throws, the hotbars are off for the session with a reason
 * the player can act on.
 *
 * <p>As Axiom 6.1 has them: hotbars are numbered from 0 with no upper limit, nine slots each, shown nine to a page in
 * the menu its context key opens (Left Alt by default), where scrolling steps through them across pages. The active
 * one is the player's real hotbar: reading it reads the inventory, and writing it sends the creative inventory packet,
 * but only in creative mode, and outside it the write returns having done nothing. So callers write the active hotbar
 * themselves and only the others through here. The others live on the client, kept per world or server unless the
 * player chose global hotbars, and are saved about ten seconds after a change, but only while Axiom is active.
 */
final class AxiomHotbars {
    private AxiomHotbars() {}

    /** {@code HotbarManager}'s methods, and two of {@code AxiomClient}'s with the permission bound in. */
    record Handles(MethodHandle active, MethodHandle get, MethodHandle set, MethodHandle allowed, MethodHandle axiomActive) {}

    private static final AxiomInternals<Handles> INTERNALS = new AxiomInternals<>("Axiom's hotbars",
            Component.translatable("hotbar.chromastudio.axiom.changed"),
            () -> find("com.moulberry.axiom.HotbarManager", "com.moulberry.axiom.AxiomClient", "com.moulberry.axiom.restrictions.AxiomPermission"));

    /** The context key's name in Axiom 6.1, a game key mapping like any other, so no Axiom class is needed to read it. */
    private static final String CONTEXT_KEY = "axiom.keybind.context";

    /** Looks the methods up in the classes named, so a test can point it at classes of its own. */
    static Handles find(String manager, String client, String permission) throws Throwable {
        MethodHandles.Lookup lookup = MethodHandles.publicLookup();
        Class<?> m = Class.forName(manager), c = Class.forName(client), p = Class.forName(permission);
        MethodHandle active = lookup.findStatic(m, "getActiveHotbarIndex", MethodType.methodType(int.class));
        MethodHandle get = lookup.findStatic(m, "getItemStack", MethodType.methodType(ItemStack.class, int.class, int.class));
        MethodHandle set = lookup.findStatic(m, "setItemStack", MethodType.methodType(void.class, int.class, int.class, ItemStack.class));
        Object hotbar = lookup.findStaticGetter(p, "PLAYER_HOTBAR", p).invoke();
        MethodHandle allowed = MethodHandles.insertArguments(lookup.findStatic(c, "hasPermission", MethodType.methodType(boolean.class, p)), 0, hotbar);
        MethodHandle axiomActive = lookup.findStatic(c, "isAxiomActive", MethodType.methodType(boolean.class));
        return new Handles(active, get, set, allowed, axiomActive);
    }

    /** Why the hotbars can't be used on any world: this Axiom isn't one Chroma Studio knows. Nothing when they can be. */
    static Optional<Component> unsupported() { return INTERNALS.reason(); }

    /** Why a fill can't use the hotbars now, or nothing when it can. */
    static Optional<Component> unavailable() {
        Optional<Component> why = INTERNALS.reason();
        if (why.isPresent()) return why;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return Optional.of(Component.translatable("palettes.chromastudio.no_world"));
        if (!mc.player.hasInfiniteMaterials()) return Optional.of(Component.translatable("hotbar.chromastudio.axiom.creative"));
        try {
            // Axiom is off on a server that doesn't support it, and then saves no hotbar and opens no menu to reach one.
            if (!(boolean) INTERNALS.handles().axiomActive().invoke()) return Optional.of(Component.translatable("hotbar.chromastudio.axiom.inactive"));
            if (!(boolean) INTERNALS.handles().allowed().invoke()) return Optional.of(Component.translatable("hotbar.chromastudio.axiom.denied"));
        } catch (Throwable t) {
            INTERNALS.failed(t);
            return INTERNALS.reason();
        }
        return Optional.empty();
    }

    /** The hotbar the player holds now. Throws when Axiom's method fails, after turning the hotbars off. */
    static int active() throws Throwable {
        try {
            return (int) INTERNALS.handles().active().invoke();
        } catch (Throwable t) {
            INTERNALS.failed(t);
            throw t;
        }
    }

    /** A copy of what one slot of a hotbar holds, the active one included. Throws as {@link #active} does. */
    static ItemStack get(int hotbar, int slot) throws Throwable {
        try {
            return (ItemStack) INTERNALS.handles().get().invoke(hotbar, slot);
        } catch (Throwable t) {
            INTERNALS.failed(t);
            throw t;
        }
    }

    /**
     * Sets one slot of a hotbar that isn't the active one, to nothing for an empty stack. Throws as {@link #active}
     * does. The active one is the player's own hotbar, which Axiom writes only in creative mode and otherwise leaves
     * as it was without a word, so callers write it themselves.
     */
    static void set(int hotbar, int slot, ItemStack stack) throws Throwable {
        try {
            INTERNALS.handles().set().invoke(hotbar, slot, stack.copy());
        } catch (Throwable t) {
            INTERNALS.failed(t);
            throw t;
        }
    }

    /** The key that opens Axiom's menu of hotbars, as the player has it bound, or Left Alt, its default. */
    static Component switchKey() {
        for (KeyMapping key : Minecraft.getInstance().options.keyMappings)
            if (key.getName().equals(CONTEXT_KEY)) return key.getTranslatedKeyMessage();
        return Component.translatable("key.keyboard.left.alt");
    }
}
