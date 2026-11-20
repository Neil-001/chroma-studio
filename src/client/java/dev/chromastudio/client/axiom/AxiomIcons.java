package dev.chromastudio.client.axiom;

import com.moulberry.axiomclientapi.CustomTool;
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

/**
 * Gives each of Chroma Studio's tools its own icon in Axiom's tool grid. Axiom's API draws every tool added through
 * it with the same pencil, so five Chroma tools in a row couldn't be told apart. Axiom keeps its tools in one list,
 * and draws each with the character its {@code Tool.iconChar} names in Axiom's icon font; this swaps the entry Axiom
 * made for a tool with one that answers its own character and passes every other call to Axiom's entry unchanged.
 * It's internal to Axiom, so it goes through {@link AxiomInternals}, and when anything doesn't match, the tool keeps
 * the pencil, as it would without this.
 *
 * <p>Axiom 6.1.5 finds its tools by their place in that list, or by their class for its own tools, and keys only their
 * key bindings by the entry itself, which it gives none of the tools added through its API. So swapping the entry
 * loses nothing. In case another version keys an entry of ours, the swap is left undone when it would lose a lookup,
 * and undone when anything goes wrong after it: a pencil is better than a tool Axiom can't find.
 *
 * <p>The characters are ones Axiom's own tools don't use in this version, picked for what they show: a scribble and
 * pencil, a pipette, two strands twisting from one end to the other, arrows up and down for lighter and darker, and a
 * hand that takes blocks. Another version of Axiom could draw something else for them; that would show a wrong icon,
 * never break a tool.
 */
final class AxiomIcons {
    private AxiomIcons() {}

    static final char PALETTE_BRUSH = '\ue924', EYEDROPPER = '\ue927', GRADIENT = '\ue907', SHADE_BRUSH = '\ue90e', SAMPLER = '\ue923';

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

    /**
     * Gives {@code added}, just registered through Axiom's API, the icon {@code icon}. Leaves it with the pencil when
     * Axiom's list isn't as this version of Chroma Studio knows it.
     */
    static void give(GuardedTool added, char icon) {
        if (INTERNALS.reason().isPresent()) return;
        Handles h = INTERNALS.handles();
        try {
            @SuppressWarnings("unchecked")
            List<Object> tools = (List<Object>) h.tools.invoke();
            // Axiom's entry for the tool is the last one with its name: its own tools never share our names.
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

    /**
     * Axiom's own entry for a tool, answering {@code icon} for its icon. Equal only to itself, as Axiom keys maps by it.
     * It also tells the tool when Axiom picks another, which Axiom says only to its own tools' entries, through
     * {@code toolDeselected}: with "Clear tool when swapping" off it calls nothing else.
     */
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
                // What Axiom's entry throws, such as the API's refusal to write settings, reaches Axiom as it would have.
                throw e.getCause();
            }
        }
    }
}
