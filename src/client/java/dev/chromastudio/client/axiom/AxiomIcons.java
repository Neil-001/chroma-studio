package dev.chromastudio.client.axiom;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.List;
import java.util.Map;
import net.minecraft.network.chat.Component;

final class AxiomIcons {
    private AxiomIcons() {}

    static final char PALETTE_BRUSH = '\ue924', EYEDROPPER = '\ue927', GRADIENT = '\ue907', SHADE_BRUSH = '\ue926', SAMPLER = '\ue923';

    private record Handles(Class<?> tool, MethodHandle tools, MethodHandle keybinds, Method iconChar, Method name) {}

    static final AxiomInternals<Handles> INTERNALS = new AxiomInternals<>("Axiom's tool icons", Component.empty(), () -> {
        Class<?> tool = Class.forName("com.moulberry.axiom.tools.Tool");
        Class<?> manager = Class.forName("com.moulberry.axiom.tools.ToolManager");
        MethodHandle tools = MethodHandles.publicLookup().findStatic(manager, "getTools", MethodType.methodType(List.class));
        MethodHandle keybinds = MethodHandles.publicLookup().findStaticGetter(manager, "keybindMap", Map.class);
        Method icon = tool.getMethod("iconChar");
        if (icon.getReturnType() != char.class) throw new NoSuchMethodException("Tool.iconChar doesn't return a char");
        return new Handles(tool, tools, keybinds, icon, tool.getMethod("name"));
    });

    static void give(GuardedTool added, char icon) {
        if (INTERNALS.reason().isPresent()) return;
        Handles h = INTERNALS.handles();
        try {
            @SuppressWarnings("unchecked")
            List<Object> tools = (List<Object>) h.tools.invoke();
            String name = added.name();
            for (int i = tools.size() - 1; i >= 0; i--) {
                Object entry = tools.get(i);
                if (!h.tool.isInstance(entry) || !name.equals(h.name.invoke(entry))) continue;
                if (((Map<?, ?>) h.keybinds.invoke()).containsKey(entry)) throw new IllegalStateException("Axiom keys the entry for " + name);
                Object swapped = Proxy.newProxyInstance(h.tool.getClassLoader(), new Class<?>[] {h.tool}, new WithIcon(entry, icon, h.iconChar, added));
                tools.set(i, swapped);
                try {
                    if (tools.get(i) != swapped || !name.equals(h.name.invoke(swapped))) throw new IllegalStateException("Axiom's tool list didn't take the entry for " + name);
                } catch (Throwable t) {
                    tools.set(i, entry);
                    throw t;
                }
                return;
            }
            throw new IllegalStateException("Axiom's tool list has no entry for " + name);
        } catch (Throwable t) {
            INTERNALS.failed(t);
        }
    }

    private record WithIcon(Object entry, char icon, Method iconChar, GuardedTool tool) implements InvocationHandler {
        @Override
        public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
            if (method.equals(iconChar)) return icon;
            if (method.getName().equals("toolDeselected") && method.getParameterCount() == 0) {
                Object out = pass(method, args);
                tool.deselected();
                return out;
            }
            switch (method.getName()) {
                case "equals" -> {
                    if (method.getParameterCount() == 1) return proxy == args[0];
                }
                case "hashCode" -> {
                    if (method.getParameterCount() == 0) return System.identityHashCode(proxy);
                }
                default -> {}
            }
            return pass(method, args);
        }

        private Object pass(Method method, Object[] args) throws Throwable {
            try {
                return method.invoke(entry, args);
            } catch (InvocationTargetException e) {
                throw e.getCause();
            }
        }
    }
}
