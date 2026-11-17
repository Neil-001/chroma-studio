package dev.chromastudio.client.action;

import dev.chromastudio.client.ChromaStudioClient;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.Optional;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

/**
 * Reads and writes Axiom's extra hotbars, when Axiom is there. These are Axiom's internal classes, not an API, and
 * Axiom is closed source and Fabric-only, so nothing is compiled against it: the few methods used are looked up by
 * name the first time they're needed, and if any is missing, or a call throws, the hotbars count as unavailable and
 * nothing reaches the caller.
 *
 * <p>Axiom numbers its hotbars from 0 and keeps nine slots in each. The active one is the player's real hotbar, so
 * its slots are written with the creative inventory packet, which Axiom sends only in creative mode; the others are
 * kept on the client and saved to Axiom's {@code hotbars} file about ten seconds after a change. Switching hotbars
 * also works only in creative mode, so this is offered only there.
 */
public final class AxiomHotbars {
    private AxiomHotbars() {}

    private static final String MOD_ID = "axiom";

    /** {@code HotbarManager}'s methods, looked up once: active hotbar, get and set a slot, and the permission check. */
    private record Handles(MethodHandle active, MethodHandle get, MethodHandle set, MethodHandle allowed) {}

    private static Handles handles;
    private static boolean looked;

    public static boolean isLoaded() {
        return ChromaStudioClient.platform != null && ChromaStudioClient.platform.isModLoaded(MOD_ID);
    }

    /** Why a fill can't use Axiom's hotbars now, or nothing when it can. */
    public static Optional<Component> unavailable() {
        if (!isLoaded()) return Optional.of(Component.translatable("hotbar.chromastudio.axiom.missing"));
        Handles h = handles();
        if (h == null) return Optional.of(Component.translatable("hotbar.chromastudio.axiom.changed"));
        if (Minecraft.getInstance().player == null) return Optional.of(Component.translatable("palettes.chromastudio.no_world"));
        if (!Minecraft.getInstance().player.hasInfiniteMaterials()) return Optional.of(Component.translatable("hotbar.chromastudio.axiom.creative"));
        try {
            if (!(boolean) h.allowed.invoke()) return Optional.of(Component.translatable("hotbar.chromastudio.axiom.denied"));
        } catch (Throwable t) {
            ChromaStudioClient.LOGGER.warn("Couldn't ask Axiom whether its hotbars are allowed", t);
            return Optional.of(Component.translatable("hotbar.chromastudio.axiom.changed"));
        }
        return Optional.empty();
    }

    /** The hotbar the player holds now. Throws when Axiom's method fails; callers catch it. */
    static int active() throws Throwable { return (int) handles.active.invoke(); }

    /** A copy of what one slot of a hotbar holds, the active one included. */
    static ItemStack get(int hotbar, int slot) throws Throwable { return (ItemStack) handles.get.invoke(hotbar, slot); }

    /** Sets one slot of a hotbar, to nothing for an empty stack. The active hotbar's slots are the real hotbar's. */
    static void set(int hotbar, int slot, ItemStack stack) throws Throwable { handles.set.invoke(hotbar, slot, stack.copy()); }

    private static synchronized Handles handles() {
        if (looked) return handles;
        looked = true;
        try {
            MethodHandles.Lookup lookup = MethodHandles.publicLookup();
            Class<?> manager = Class.forName("com.moulberry.axiom.HotbarManager");
            MethodHandle active = lookup.findStatic(manager, "getActiveHotbarIndex", MethodType.methodType(int.class));
            MethodHandle get = lookup.findStatic(manager, "getItemStack", MethodType.methodType(ItemStack.class, int.class, int.class));
            MethodHandle set = lookup.findStatic(manager, "setItemStack", MethodType.methodType(void.class, int.class, int.class, ItemStack.class));
            Class<?> permission = Class.forName("com.moulberry.axiom.restrictions.AxiomPermission");
            Object hotbar = lookup.findStaticGetter(permission, "PLAYER_HOTBAR", permission).invoke();
            MethodHandle allowed = MethodHandles.insertArguments(lookup.findStatic(Class.forName("com.moulberry.axiom.AxiomClient"), "hasPermission",
                    MethodType.methodType(boolean.class, permission)), 0, hotbar);
            handles = new Handles(active, get, set, allowed);
        } catch (Throwable t) {
            ChromaStudioClient.LOGGER.warn("Axiom is loaded, but not as this version of Chroma Studio knows it, so its hotbars aren't used", t);
        }
        return handles;
    }
}
