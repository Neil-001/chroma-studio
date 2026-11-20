package dev.chromastudio.client.axiom;

import dev.chromastudio.client.action.Clipboard;
import dev.chromastudio.client.action.PaletteBlocks;
import dev.chromastudio.client.axiom.AxiomBridge.Refused;
import dev.chromastudio.client.axiom.AxiomBridge.Script;
import dev.chromastudio.core.export.ClipboardSize;
import dev.chromastudio.core.palette.AxiomScript;
import dev.chromastudio.core.palette.Palette;
import dev.chromastudio.core.palette.PaletteEntry;
import dev.chromastudio.core.palette.Recolour;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.level.EmptyBlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.state.BlockState;
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
        if (plan.size() == 0) {
            // When it's because the palette already has the blocks, the player is told so.
            throw new Refused(plan.kept() == 0 ? Component.translatable("axiom.chromastudio.nothing_to_change") : kept(plan));
        }
        String title = from == null ? to.name : from.name + " to " + to.name;
        MutableComponent summary = Component.translatable("axiom.chromastudio.recolour.done", plan.size(), plan.unshaped());
        // A build mostly of the palette's own blocks hardly changes, which would look like the script failing.
        if (plan.kept() > 0) summary.append(" ").append(kept(plan));
        return made(AxiomScript.recolour(title, plan.rows()), summary,
                from == null ? "axiom.chromastudio.long.from" : "axiom.chromastudio.long.brush",
                from == null ? "axiom.chromastudio.too_long.from" : "axiom.chromastudio.too_long");
    }

    /** How many kinds of block a recolour leaves as they are because the palette already has them. */
    private static Component kept(RecolourPlan plan) {
        return Component.translatable(plan.kept() == 1 ? "axiom.chromastudio.recolour.kept.one" : "axiom.chromastudio.recolour.kept", plan.kept());
    }

    /** A brush that paints the palette's blocks at random by weight. */
    static Script scatter(Palette p) throws Refused {
        if (p.entries.isEmpty()) throw new Refused(Component.translatable("palettes.chromastudio.need_entries"));
        // The blocks the palette brush's Scatter places, so stairs go in as their full block, as the tool has them.
        ScatterBlocks blocks = ScatterBlocks.of(p);
        Component leftOut = Component.translatable("axiom.chromastudio.brush.left_out",
                blocks.leftOut().stream().map(e -> PaletteBlocks.name(e).getString()).collect(Collectors.joining(", ")));
        if (blocks.blocks().isEmpty()) throw new Refused(blocks.leftOut().isEmpty() ? Component.translatable("axiom.chromastudio.no_blocks") : leftOut);
        Palette placed = new Palette(p.name);
        for (ScatterBlocks.Placed b : blocks.blocks()) placed.entries.add(PaletteBlocks.of(b.state()).withWeight(b.weight()));
        MutableComponent summary = Component.translatable("axiom.chromastudio.scatter.done", placed.entries.size());
        if (!blocks.leftOut().isEmpty()) summary.append(" ").append(leftOut);
        return made(AxiomScript.scatter(placed), summary, "axiom.chromastudio.long.brush", "axiom.chromastudio.too_long");
    }

    /**
     * A brush that paints a gradient's steps along an axis, starting where the player stands and running two blocks
     * a step, which the brush's own controls then change.
     */
    static Script gradient(String name, List<PaletteEntry> steps, AxiomScript.Axis axis) throws Refused {
        Minecraft mc = Minecraft.getInstance();
        return gradient(name, steps, axis, mc.player == null ? BlockPos.ZERO : mc.player.blockPosition());
    }

    /**
     * {@link #gradient(String, List, AxiomScript.Axis)} from {@code at}. As the gradient tool does, it paints only the
     * steps that are full blocks with nothing inside, which this game has, and says how many it left out: a chain
     * or a slab would leave holes in a wall, and a chest would come empty.
     */
    static Script gradient(String name, List<PaletteEntry> steps, AxiomScript.Axis axis, BlockPos at) throws Refused {
        if (steps.size() < 2) throw new Refused(Component.translatable("axiom.chromastudio.gradient.too_short"));
        List<PaletteEntry> full = new ArrayList<>();
        for (PaletteEntry e : steps) {
            BlockState s = PaletteBlocks.state(e);
            if (s != null && !(s.getBlock() instanceof EntityBlock) && s.isCollisionShapeFullBlock(EmptyBlockGetter.INSTANCE, BlockPos.ZERO)) full.add(e);
        }
        if (full.size() < 2) throw new Refused(Component.translatable("axiom.chromastudio.gradient_tool.missing"));
        int start = switch (axis) {
            case X -> at.getX();
            case Y -> at.getY();
            case Z -> at.getZ();
        };
        MutableComponent summary = Component.translatable("axiom.chromastudio.gradient.done", full.size(), axis.name().toLowerCase(Locale.ROOT));
        if (full.size() < steps.size()) summary.append(" ").append(Component.translatable("axiom.chromastudio.gradient_tool.skipped", steps.size() - full.size()));
        return made(AxiomScript.gradient(name, full, axis, start, full.size() * 2, kept()), summary, "axiom.chromastudio.long", "axiom.chromastudio.too_long");
    }

    /**
     * The full blocks that hold items or data, which the gradient tool leaves alone, and which have none of the
     * properties by which the script leaves a block alone, such as a jukebox or a spawner, so the script names them.
     */
    static List<String> kept() {
        List<String> out = new ArrayList<>();
        for (Block b : BuiltInRegistries.BLOCK) {
            BlockState s = b.defaultBlockState();
            // The ground, such as suspicious sand, the script leaves alone already.
            if (!(b instanceof EntityBlock) || RecolourPlan.ground(s) || !s.isCollisionShapeFullBlock(EmptyBlockGetter.INSTANCE, BlockPos.ZERO)) continue;
            if (s.getProperties().stream().noneMatch(p -> AxiomScript.KEPT_BY_PROPERTY.contains(p.getName()))) out.add(BuiltInRegistries.BLOCK.getKey(b).toString());
        }
        return out;
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
