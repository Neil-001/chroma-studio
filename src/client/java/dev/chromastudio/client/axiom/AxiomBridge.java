package dev.chromastudio.client.axiom;

import dev.chromastudio.client.ChromaStudioClient;
import dev.chromastudio.client.ModSettings;
import dev.chromastudio.client.PaletteStore;
import dev.chromastudio.client.ui.Hud;
import dev.chromastudio.core.palette.AxiomScript;
import dev.chromastudio.core.palette.Palette;
import dev.chromastudio.core.palette.PaletteEntry;
import dev.chromastudio.core.palette.Recolour;
import java.util.List;
import java.util.Optional;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.BlockHitResult;
import org.jspecify.annotations.Nullable;

public final class AxiomBridge {
    private AxiomBridge() {}

    private static final String MOD_ID = "axiom";

    public static boolean isPresent() {
        return ChromaStudioClient.platform != null && ChromaStudioClient.platform.isFabric() && ChromaStudioClient.platform.isModLoaded(MOD_ID);
    }

    private static @Nullable Component broken;
    private static boolean version;
    private static boolean told;

    public static MutableComponent named(String key) { return AxiomNames.text(key); }

    public static Optional<Component> whyNot() {
        if (!isPresent()) {
            boolean fabric = ChromaStudioClient.platform == null || ChromaStudioClient.platform.isFabric();
            return Optional.of(Component.translatable(fabric ? "axiom.chromastudio.missing" : "axiom.chromastudio.missing.fabric_only"));
        }
        return Optional.ofNullable(broken);
    }

    private static boolean usable() { return whyNot().isEmpty(); }

    private static void live() {
        whyNot().ifPresent(why -> {
            throw new IllegalStateException(why.getString());
        });
    }

    public static void register() {
        if (!isPresent()) return;
        AxiomCommand.install();
        try {
            AxiomTools.register();
        } catch (Throwable t) {
            fail(t, true);
        }
    }

    static void forget() {
        broken = null;
        told = false;
    }

    static void fail(Throwable t) { fail(t, t instanceof LinkageError); }

    private static void fail(Throwable t, boolean unsupported) {
        if (broken != null) return;
        version = unsupported;
        if (unsupported) ChromaStudioClient.LOGGER.warn("Chroma Studio's tools don't work with this version of Axiom, so its Axiom features are off", t);
        else ChromaStudioClient.LOGGER.error("Chroma Studio's Axiom tools stopped after an error, so its Axiom features are off until the game restarts", t);
        broken = Component.translatable(unsupported ? "axiom.chromastudio.incompatible" : "axiom.chromastudio.stopped");
        try {
            AxiomTools.shutDown();
        } catch (Throwable _) {
        }
    }

    public static void leaveEditorForWindow() {
        if (usable()) AxiomEditor.leaveForWindow();
    }

    public static boolean editorOpen() { return usable() && AxiomEditor.active(); }

    public static void tick(Minecraft mc) {
        if (!isPresent()) return;
        if (broken != null) {
            if (!told && mc.player != null) {
                told = true;
                String notice = version ? "axiom.chromastudio.incompatible" : "axiom.chromastudio.stopped";
                Hud.readout(Component.translatable(notice + ".title"), Component.translatable(notice + ".how"), ModSettings.readoutSeconds);
            }
            return;
        }
        try {
            AxiomTools.tick();
            AxiomEditor.tick(mc);
        } catch (Throwable t) {
            fail(t);
        }
    }

    public static @Nullable BlockHitResult blockUnderMouse() { return editorOpen() ? AxiomBlock.underMouse() : null; }

    public record Taken(boolean taken, Component message) {}

    public static Optional<Component> activeBlockUnavailable() {
        Optional<Component> why = whyNot();
        return why.isPresent() ? why : AxiomBlock.unavailable();
    }

    public static Component activeBlockTip() { return AxiomBlock.tip(); }

    public static Taken takeActiveBlock() {
        Optional<Component> why = activeBlockUnavailable();
        return why.isPresent() ? new Taken(false, why.get()) : AxiomBlock.take();
    }

    public static Optional<Component> hotbarsUnsupported() {
        Optional<Component> why = whyNot();
        return why.isPresent() ? why : AxiomHotbars.unsupported();
    }

    public static Optional<Component> hotbarsUnavailable() {
        Optional<Component> why = whyNot();
        return why.isPresent() ? why : AxiomHotbars.unavailable();
    }

    public static Optional<Component> hotbarsUnreachable() {
        Optional<Component> why = whyNot();
        return why.isPresent() ? why : AxiomHotbars.unreachable();
    }

    public static void hotbarsFailed(Throwable t) { AxiomHotbars.failed(t); }

    public static int activeHotbar() throws Throwable {
        return AxiomHotbars.active();
    }

    public static ItemStack hotbarItem(int hotbar, int slot) throws Throwable {
        live();
        return AxiomHotbars.get(hotbar, slot);
    }

    public static void setHotbarItem(int hotbar, int slot, ItemStack stack) throws Throwable {
        live();
        AxiomHotbars.set(hotbar, slot, stack);
    }

    public static Component hotbarKey() { return AxiomHotbars.switchKey(); }

    public record Script(String text, Component scope, Component size, @Nullable Component warning, @Nullable Component nextStep, List<Component> details) {}

    public static final class Refused extends Exception {
        private static final long serialVersionUID = 1L;
        private final transient Component reason;

        Refused(Component reason) {
            super(reason.getString());
            this.reason = reason;
        }

        public Component reason() { return reason; }
    }

    public static Script recolourScript(Palette to, @Nullable Palette from, Recolour.Mode mode) throws Refused {
        return AxiomScripts.recolour(to, from, mode);
    }

    public static Script scatterScript(Palette p) throws Refused { return AxiomScripts.scatter(p); }

    public static Script gradientScript(String name, List<PaletteEntry> steps, AxiomScript.Axis axis, BlockPos at) throws Refused {
        return AxiomScripts.gradient(name, steps, axis, at);
    }

    public static String fromName(Palette p) {
        String name = PaletteStore.get().shownName(p);
        if (!p.preset) return name;
        for (Palette own : PaletteStore.get().own()) {
            if (own.name.strip().equalsIgnoreCase(name.strip())) return Component.translatable("axiom.chromastudio.from.preset", name).getString();
        }
        return name;
    }

    public static void copy(Script script) { AxiomScripts.copy(script); }
}
