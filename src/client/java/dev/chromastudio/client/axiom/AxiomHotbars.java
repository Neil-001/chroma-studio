package dev.chromastudio.client.axiom;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.Optional;
import java.util.function.Function;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

final class AxiomHotbars {
    private AxiomHotbars() {}

    record Handles(MethodHandle get, MethodHandle set, MethodHandle allowed, MethodHandle axiomActive) {}

    private static final String MANAGER = "com.moulberry.axiom.HotbarManager";

    private static final AxiomInternals<Handles> INTERNALS = new AxiomInternals<>("Axiom's hotbars",
            Component.translatable("hotbar.chromastudio.axiom.changed"),
            () -> find(MANAGER, "com.moulberry.axiom.AxiomClient", "com.moulberry.axiom.restrictions.AxiomPermission"));

    private static final AxiomInternals<MethodHandle> INDEX = new AxiomInternals<>("Axiom's hotbar number",
            Component.translatable("hotbar.chromastudio.axiom.changed"), () -> findIndex(MANAGER));

    private static final String CONTEXT_KEY = "axiom.keybind.context";

    static MethodHandle findIndex(String manager) throws Throwable {
        return MethodHandles.publicLookup().findStatic(Class.forName(manager), "getActiveHotbarIndex", MethodType.methodType(int.class));
    }

    static Handles find(String manager, String client, String permission) throws Throwable {
        MethodHandles.Lookup lookup = MethodHandles.publicLookup();
        Class<?> m = Class.forName(manager), c = Class.forName(client), p = Class.forName(permission);
        MethodHandle get = lookup.findStatic(m, "getItemStack", MethodType.methodType(ItemStack.class, int.class, int.class));
        MethodHandle set = lookup.findStatic(m, "setItemStack", MethodType.methodType(void.class, int.class, int.class, ItemStack.class));
        Object hotbar = lookup.findStaticGetter(p, "PLAYER_HOTBAR", p).invoke();
        MethodHandle allowed = MethodHandles.insertArguments(lookup.findStatic(c, "hasPermission", MethodType.methodType(boolean.class, p)), 0, hotbar);
        MethodHandle axiomActive = lookup.findStatic(c, "isAxiomActive", MethodType.methodType(boolean.class));
        return new Handles(get, set, allowed, axiomActive);
    }

    static Optional<Component> unsupported() { return INTERNALS.reason().or(INDEX::reason); }

    static void failed(Throwable t) { INTERNALS.failed(t); }

    static Optional<Component> unavailable() { return unsupported().or(AxiomHotbars::situation); }

    static Optional<Component> unreachable() { return INTERNALS.found() && INDEX.found() ? situation() : unsupported(); }

    private static Optional<Component> situation() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return Optional.of(Component.translatable("palettes.chromastudio.no_world"));
        if (!mc.player.hasInfiniteMaterials()) return Optional.of(Component.translatable("hotbar.chromastudio.axiom.creative"));
        try {
            if (!(boolean) call(Handles::axiomActive)) return Optional.of(Component.translatable("hotbar.chromastudio.axiom.inactive"));
            if (!(boolean) call(Handles::allowed)) return Optional.of(Component.translatable("hotbar.chromastudio.axiom.denied"));
        } catch (Throwable ignored) {
            return INTERNALS.reason();
        }
        return Optional.empty();
    }

    static int active() throws Throwable {
        try {
            return (int) INDEX.handles().invoke();
        } catch (Throwable t) {
            INDEX.failed(t);
            throw t;
        }
    }

    static ItemStack get(int hotbar, int slot) throws Throwable { return (ItemStack) call(Handles::get, hotbar, slot); }

    static void set(int hotbar, int slot, ItemStack stack) throws Throwable { call(Handles::set, hotbar, slot, stack.copy()); }

    private static Object call(Function<Handles, MethodHandle> which, Object... args) throws Throwable {
        try {
            return which.apply(INTERNALS.handles()).invokeWithArguments(args);
        } catch (Throwable t) {
            INTERNALS.failed(t);
            throw t;
        }
    }

    static Component switchKey() {
        for (KeyMapping key : Minecraft.getInstance().options.keyMappings)
            if (key.getName().equals(CONTEXT_KEY)) return key.getTranslatedKeyMessage();
        return Component.translatable("key.keyboard.left.alt");
    }
}
