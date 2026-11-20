package dev.chromastudio.client.axiom;

import dev.chromastudio.client.action.Clipboard;
import dev.chromastudio.client.axiom.AxiomBridge.Refused;
import dev.chromastudio.client.axiom.AxiomBridge.Script;
import dev.chromastudio.core.export.ClipboardSize;
import dev.chromastudio.core.palette.AxiomScript;
import dev.chromastudio.core.palette.Palette;
import dev.chromastudio.core.palette.PaletteEntry;
import dev.chromastudio.core.palette.Recolour;
import java.util.List;
import java.util.Locale;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import org.jspecify.annotations.Nullable;

/**
 * Makes Lua scripts for Axiom's Script Brush from palettes and gradients, and copies them, through the Palettes and
 * Gradient tabs and {@code /chroma axiom}. The text comes from {@link AxiomScript}; this class picks the blocks.
 */
final class AxiomScripts {
    private AxiomScripts() {}

    /**
     * A recolour into a palette of every block but the ground, or only those of {@code from}, as {@link RecolourPlan}
     * works it out, so the palette brush would change the same blocks the same way. Refused, with the reason, when
     * nothing would change.
     */
    static Script recolour(Palette to, @Nullable Palette from, Recolour.Mode mode) throws Refused {
        RecolourPlan plan = RecolourPlan.of(to, from, mode);
        if (plan.size() == 0) throw new Refused(Component.translatable("axiom.chromastudio.nothing_to_change"));
        String title = from == null ? to.name : from.name + " to " + to.name;
        return made(AxiomScript.recolour(title, plan.rows()), Component.translatable("axiom.chromastudio.recolour.done", plan.size(), plan.unshaped()),
                from == null ? "axiom.chromastudio.long.from" : "axiom.chromastudio.long.brush",
                from == null ? "axiom.chromastudio.too_long.from" : "axiom.chromastudio.too_long");
    }

    /** A brush that paints the palette's blocks at random by weight. */
    static Script scatter(Palette p) throws Refused {
        if (p.entries.isEmpty()) throw new Refused(Component.translatable("palettes.chromastudio.need_entries"));
        return made(AxiomScript.scatter(p), Component.translatable("axiom.chromastudio.scatter.done", p.entries.size()), "axiom.chromastudio.long.brush", "axiom.chromastudio.too_long");
    }

    /**
     * A brush that paints a gradient's steps along an axis, starting where the player stands and running two blocks
     * a step, which the brush's own controls then change.
     */
    static Script gradient(String name, List<PaletteEntry> steps, AxiomScript.Axis axis) throws Refused {
        if (steps.size() < 2) throw new Refused(Component.translatable("axiom.chromastudio.gradient.too_short"));
        Minecraft mc = Minecraft.getInstance();
        BlockPos at = mc.player == null ? BlockPos.ZERO : mc.player.blockPosition();
        int start = switch (axis) {
            case X -> at.getX();
            case Y -> at.getY();
            case Z -> at.getZ();
        };
        return made(AxiomScript.gradient(name, steps, axis, start, steps.size() * 2),
                Component.translatable("axiom.chromastudio.gradient.done", steps.size(), axis.name().toLowerCase(Locale.ROOT)), "axiom.chromastudio.long",
                "axiom.chromastudio.too_long");
    }

    /** Puts a script on the clipboard. */
    static void copy(Script script) { Clipboard.copy(script.text()); }

    /**
     * A script with its summary and its size, and a note when a server could refuse a script this long from a
     * player without import rights, {@code tooLong}, which says what the player can do instead. Refused when it's too
     * long to copy, saying what to do in {@code tooBig}, as what helps depends on the script.
     */
    private static Script made(String script, Component summary, String tooLong, String tooBig) throws Refused {
        int bytes = ClipboardSize.bytes(script), counted = AxiomScript.counted(script);
        if (bytes > ClipboardSize.MAX_BYTES) {
            throw new Refused(Component.translatable(tooBig, ClipboardSize.kilobytes(bytes), ClipboardSize.kilobytes(ClipboardSize.MAX_BYTES)));
        }
        MutableComponent out = Component.empty().append(summary).append(" ")
                .append(Component.translatable("axiom.chromastudio.size", ClipboardSize.kilobytes(bytes), counted));
        if (counted > AxiomScript.SERVER_LIMIT) {
            out.append(" ").append(Component.translatable(tooLong, AxiomScript.SERVER_LIMIT));
        }
        return new Script(script, out);
    }
}
