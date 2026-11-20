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
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.EmptyBlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;
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
            MutableComponent why = Component.translatable("axiom.chromastudio.nothing_to_change");
            throw new Refused(plan.kept() == 0 ? why : why.append(" ").append(kept(plan)));
        }
        String title = from == null ? to.name : from.name + " to " + to.name;
        Script made = made(AxiomScript.recolour(title, plan.rows()), Component.translatable("axiom.chromastudio.recolour.done", plan.size(), plan.unshaped()),
                from == null ? "axiom.chromastudio.long.from" : "axiom.chromastudio.long.brush",
                from == null ? "axiom.chromastudio.too_long.from" : "axiom.chromastudio.too_long");
        // A build mostly of the palette's own blocks hardly changes, which would look like the script failing. It goes
        // last, after the note on servers' limit, which the dialog is sized to show.
        return plan.kept() == 0 ? made : new Script(made.text(), made.summary().copy().append(" ").append(kept(plan)));
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
        // As the brush's Scatter does, it leaves a block that holds items, or one half of a door or a tall plant; the
        // script tells the halves by their property.
        AxiomScript.Kept kept = kept(s -> !ScatterBlocks.paintsOver(s), s -> {
            String half = property(s, "half");
            return "upper".equals(half) || "lower".equals(half);
        });
        return made(AxiomScript.scatter(placed, kept), summary, "axiom.chromastudio.long.brush", "axiom.chromastudio.too_long");
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
        int skipped = steps.size() - full.size();
        if (skipped > 0) {
            summary.append(" ").append(Component.translatable(skipped == 1 ? "axiom.chromastudio.gradient_tool.skipped.one" : "axiom.chromastudio.gradient_tool.skipped", skipped));
        }
        return made(AxiomScript.gradient(name, full, axis, start, full.size() * 2, gradientKept()), summary, "axiom.chromastudio.long", "axiom.chromastudio.too_long");
    }

    /**
     * What a gradient script leaves alone: as the gradient tool does, each block but a full cube with nothing inside
     * that a build is made of, as the tool would reshape it, empty it or not count it part of the build; but for air,
     * which the script fills when its Paint air box is ticked. Most it tells by having no collision or a property
     * such as {@code facing}; the rest, such as the ground, a jukebox or a brewing stand, it names.
     */
    static AxiomScript.Kept gradientKept() {
        return kept(s -> !s.isAir() && !(FoundBuild.member(EmptyBlockGetter.INSTANCE, BlockPos.ZERO, s)
                && GradientTool.paintable(EmptyBlockGetter.INSTANCE, BlockPos.ZERO, s)), s -> {
            // Axiom's isSolid asks about the block's default state, whatever state stands there.
            return s.getBlock().defaultBlockState().getCollisionShape(EmptyBlockGetter.INSTANCE, BlockPos.ZERO).isEmpty()
                    || AxiomScript.KEPT_BY_PROPERTY.stream().anyMatch(p -> property(s, p) != null);
        });
    }

    /**
     * What a script leaves alone: each block with a state that its tool {@code refuses} but the script's own checks,
     * {@code caught}, would change. Blocks are left whole, as a script can only ask which block stands somewhere. Where
     * a property belongs only to blocks the tool leaves in every state, such as a turtle egg's {@code hatch}, the
     * script asks for it in place of the blocks' names, when that's shorter, as servers limit how long a script may be.
     */
    private static AxiomScript.Kept kept(Predicate<BlockState> refuses, Predicate<BlockState> caught) {
        Set<Block> named = new LinkedHashSet<>();
        // Each property, by name, and the blocks that have it; and the properties of a block the tool may change.
        Map<String, List<Block>> holders = new TreeMap<>();
        Set<String> changed = new HashSet<>();
        for (Block b : BuiltInRegistries.BLOCK) {
            List<BlockState> states = b.getStateDefinition().getPossibleStates();
            if (states.stream().anyMatch(s -> refuses.test(s) && !caught.test(s))) named.add(b);
            boolean left = states.stream().allMatch(refuses);
            for (Property<?> p : b.getStateDefinition().getProperties()) {
                holders.computeIfAbsent(p.getName(), _ -> new ArrayList<>()).add(b);
                if (!left) changed.add(p.getName());
            }
        }
        // The property that saves the most letters, each time, of those only blocks the tool leaves alone have.
        List<String> properties = new ArrayList<>();
        while (true) {
            String best = null;
            int most = 0;
            for (Map.Entry<String, List<Block>> h : holders.entrySet()) {
                if (changed.contains(h.getKey())) continue;
                int saved = h.getValue().stream().filter(named::contains).mapToInt(b -> name(b).length()).sum() - h.getKey().length();
                if (saved > most) {
                    best = h.getKey();
                    most = saved;
                }
            }
            if (best == null) break;
            properties.add(best);
            holders.get(best).forEach(named::remove);
        }
        return new AxiomScript.Kept(properties, named.stream().map(b -> BuiltInRegistries.BLOCK.getKey(b).toString()).toList());
    }

    /** A block's id as a script names it: its path alone for one of the game's own. */
    private static String name(Block b) {
        Identifier id = BuiltInRegistries.BLOCK.getKey(b);
        return id.getNamespace().equals("minecraft") ? id.getPath() : id.toString();
    }

    /** A property's value by name, as Axiom's {@code getBlockProperty} gives it, or null when the block hasn't it. */
    private static @Nullable String property(BlockState s, String name) {
        Property<?> p = s.getBlock().getStateDefinition().getProperty(name);
        return p == null ? null : value(s, p);
    }

    private static <T extends Comparable<T>> String value(BlockState s, Property<T> p) { return p.getName(s.getValue(p)); }

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
