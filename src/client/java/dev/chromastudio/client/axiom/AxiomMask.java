package dev.chromastudio.client.axiom;

import dev.chromastudio.core.palette.BuildSearch;
import dev.chromastudio.core.palette.QueuedChanges;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import org.jspecify.annotations.Nullable;

final class AxiomMask {
    private AxiomMask() {}

    interface Test extends QueuedChanges.Mask {
        Map<Long, Boolean> answers();
    }

    private record Fixed(boolean allows, boolean failed) implements Test {
        @Override
        public boolean allows(int x, int y, int z) { return allows; }

        @Override
        public Map<Long, Boolean> answers() { return Map.of(); }
    }

    static boolean differ(Test a, Test b) {
        Map<Long, Boolean> theirs = b.answers();
        for (Map.Entry<Long, Boolean> e : a.answers().entrySet()) {
            Boolean other = theirs.get(e.getKey());
            if (other != null && !other.equals(e.getValue())) return true;
        }
        return false;
    }

    static boolean aloneChanged(QueuedChanges<BlockState> plan, Test before, Test now, QueuedChanges.Plan<BlockState> again) {
        Map<Long, Boolean> was = before.answers(), fresh = now.answers();
        boolean[] unknown = {false};
        QueuedChanges<BlockState> redone = again.make(new QueuedChanges.Mask() {
            @Override
            public boolean allows(int x, int y, int z) {
                long p = BuildSearch.pack(x, y, z);
                Boolean a = was.containsKey(p) ? was.get(p) : fresh.get(p);
                if (a == null) unknown[0] = true;
                return a != null && a;
            }

            @Override
            public boolean failed() { return false; }
        });
        return !unknown[0] && plan.sameAs(redone);
    }

    private static final Test FAILED = new Fixed(false, true);

    private record Handles(MethodHandle destMask, MethodHandle context, MethodHandle setPosition, MethodHandle test, MethodHandle luaError) {}

    private static final String MANAGER = "com.moulberry.axiom.mask.MaskManager";

    static final AxiomInternals<Handles> INTERNALS = new AxiomInternals<>("Axiom's tool masks",
            Component.translatable("axiom.chromastudio.mask.unknown"), () -> {
                MethodHandles.Lookup lookup = MethodHandles.publicLookup();
                Class<?> manager = Class.forName(MANAGER);
                Class<?> element = Class.forName("com.moulberry.axiom.mask.MaskElement");
                Class<?> context = Class.forName("com.moulberry.axiom.mask.MaskContext");
                return new Handles(lookup.findStatic(manager, "getDestMask", MethodType.methodType(element)),
                        lookup.findConstructor(context, MethodType.methodType(void.class, Level.class)),
                        lookup.findVirtual(context, "setPosition", MethodType.methodType(void.class, int.class, int.class, int.class)),
                        lookup.findVirtual(element, "test", MethodType.methodType(boolean.class, context)),
                        lookup.findStaticGetter(Class.forName("com.moulberry.axiom.editor.windows.global_mask.ToolMaskWindow"), "luaExecutionError", String.class));
            });

    private record Script(MethodHandle inUse, MethodHandle script, MethodHandle lastCompile) {}

    static final AxiomInternals<Script> SCRIPT = new AxiomInternals<>("Axiom's mask scripts", Component.empty(), () -> {
        MethodHandles.Lookup lookup = MethodHandles.publicLookup();
        Class<?> manager = Class.forName(MANAGER);
        Class<?> window = Class.forName("com.moulberry.axiom.editor.windows.global_mask.ToolMaskWindow");
        MethodHandles.Lookup inWindow = MethodHandles.privateLookupIn(window, MethodHandles.lookup());
        return new Script(lookup.findStaticGetter(manager, "useLuaScript", boolean.class),
                lookup.findStaticGetter(manager, "luaScript", String.class),
                inWindow.findStaticGetter(window, "lastLuaCompile", long.class));
    });

    private static boolean scriptInvalid() {
        if (SCRIPT.reason().isPresent() || windowClosed().orElse(false)) return false;
        Script s = SCRIPT.handles();
        try {
            return (boolean) s.inUse.invoke() && s.script.invoke() == null && (long) s.lastCompile.invoke() > 0;
        } catch (Throwable t) {
            SCRIPT.failed(t);
            return false;
        }
    }

    static Optional<Boolean> peek(Level level, int x, int y, int z) {
        if (INTERNALS.reason().isPresent()) return Optional.empty();
        Handles h = INTERNALS.handles();
        try {
            Object context = h.context.invoke(level);
            h.setPosition.invoke(context, x, y, z);
            return Optional.of((boolean) h.test.invoke(h.destMask.invoke(), context));
        } catch (VirtualMachineError e) {
            throw e;
        } catch (Throwable _) {
            return Optional.empty();
        }
    }

    static boolean scripted() {
        if (SCRIPT.reason().isPresent()) return true;
        try {
            return (boolean) SCRIPT.handles().inUse.invoke();
        } catch (Throwable t) {
            SCRIPT.failed(t);
            return true;
        }
    }

    private record Window(Object toolMasks, MethodHandle isOpen) {}

    static final AxiomInternals<Window> WINDOW = new AxiomInternals<>("Axiom's Tool Masks window", Component.empty(), () -> {
        MethodHandles.Lookup lookup = MethodHandles.publicLookup();
        Class<?> type = Class.forName("com.moulberry.axiom.editor.EditorWindowType");
        return new Window(lookup.findStaticGetter(type, "TOOL_MASKS", type).invoke(),
                lookup.findVirtual(type, "isOpen", MethodType.methodType(boolean.class)));
    });

    static Optional<Boolean> windowClosed() {
        if (WINDOW.reason().isPresent()) return Optional.empty();
        Window w = WINDOW.handles();
        try {
            return Optional.of(!(boolean) w.isOpen.invoke(w.toolMasks));
        } catch (Throwable t) {
            WINDOW.failed(t);
            return Optional.empty();
        }
    }

    static Optional<Component> unavailable() {
        if (INTERNALS.reason().isEmpty()) {
            String error = luaError();
            if (error != null) return Optional.of(Component.translatable("axiom.chromastudio.mask.lua"));
            return scriptInvalid() ? Optional.of(Component.translatable("axiom.chromastudio.mask.lua_invalid")) : Optional.empty();
        }
        return Optional.of(Component.translatable(INTERNALS.found() ? "axiom.chromastudio.mask.failed" : "axiom.chromastudio.mask.unknown"));
    }

    static Optional<String> detail() {
        String error = luaError();
        if (error != null) return Optional.of(error);
        return scriptInvalid() ? Optional.of(Component.translatable("axiom.chromastudio.mask.lua_invalid.tip").getString()) : Optional.empty();
    }

    private static @Nullable String luaError() {
        if (INTERNALS.reason().isPresent()) return null;
        try {
            return (String) INTERNALS.handles().luaError.invoke();
        } catch (Throwable t) {
            INTERNALS.failed(t);
            return null;
        }
    }

    static Test now(Level level) {
        if (INTERNALS.reason().isPresent()) return FAILED;
        Handles h = INTERNALS.handles();
        Object mask, context;
        try {
            mask = h.destMask.invoke();
            context = h.context.invoke(level);
        } catch (Throwable t) {
            INTERNALS.failed(t);
            return FAILED;
        }
        return new Test() {
            private boolean threw;
            private boolean errored;
            private final Map<Long, Boolean> asked = new HashMap<>();

            @Override
            public boolean allows(int x, int y, int z) {
                if (threw) return false;
                return asked.computeIfAbsent(BuildSearch.pack(x, y, z), _ -> ask(x, y, z));
            }

            private boolean ask(int x, int y, int z) {
                try {
                    h.setPosition.invoke(context, x, y, z);
                    Object before = h.luaError.invoke();
                    boolean allows = (boolean) h.test.invoke(mask, context);
                    Object after = h.luaError.invoke();
                    if (after != null && after != before) errored = true;
                    return allows;
                } catch (Throwable t) {
                    INTERNALS.failed(t);
                    threw = true;
                    return false;
                }
            }

            @Override
            public boolean failed() { return threw || errored || INTERNALS.reason().isPresent() || luaError() != null || scriptInvalid(); }

            @Override
            public Map<Long, Boolean> answers() { return Collections.unmodifiableMap(asked); }
        };
    }
}
