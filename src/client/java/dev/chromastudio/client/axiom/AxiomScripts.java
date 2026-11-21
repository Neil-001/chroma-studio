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
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;
import org.jspecify.annotations.Nullable;

final class AxiomScripts {
    private AxiomScripts() {}

    static Script recolour(Palette to, @Nullable Palette from, Recolour.Mode mode) throws Refused {
        RecolourPlan plan = RecolourPlan.of(to, from, mode);
        if (plan.size() == 0) {
            MutableComponent why = Component.translatable("axiom.chromastudio.nothing_to_change");
            throw new Refused(plan.kept() == 0 ? why : why.append(" ").append(kept(plan)));
        }
        String title = from == null ? to.name : from.name + " to " + to.name;
        Script made = made(AxiomScript.recolour(title, plan.rows()), Component.translatable("axiom.chromastudio.recolour.done", plan.size(), plan.unshaped()),
                from == null ? "axiom.chromastudio.long.from" : "axiom.chromastudio.long.brush",
                from == null ? "axiom.chromastudio.too_long.from" : "axiom.chromastudio.too_long");
        return plan.kept() == 0 ? made : new Script(made.text(), made.summary().copy().append(" ").append(kept(plan)));
    }

    private static Component kept(RecolourPlan plan) {
        return Component.translatable(plan.kept() == 1 ? "axiom.chromastudio.recolour.kept.one" : "axiom.chromastudio.recolour.kept", plan.kept());
    }

    static Script scatter(Palette p) throws Refused {
        if (p.entries.isEmpty()) throw new Refused(Component.translatable("palettes.chromastudio.need_entries"));
        ScatterBlocks blocks = ScatterBlocks.of(p);
        Component leftOut = Component.translatable("axiom.chromastudio.brush.left_out",
                blocks.leftOut().stream().map(e -> PaletteBlocks.name(e).getString()).collect(Collectors.joining(", ")));
        if (blocks.blocks().isEmpty()) throw new Refused(blocks.leftOut().isEmpty() ? Component.translatable("axiom.chromastudio.no_blocks") : leftOut);
        Palette placed = new Palette(p.name);
        for (ScatterBlocks.Placed b : blocks.blocks()) placed.entries.add(PaletteBlocks.of(b.state()).withWeight(b.weight()));
        MutableComponent summary = Component.translatable("axiom.chromastudio.scatter.done", placed.entries.size());
        if (!blocks.leftOut().isEmpty()) summary.append(" ").append(leftOut);
        AxiomScript.Kept kept = kept(s -> !ScatterBlocks.paintsOver(s), s -> {
            String half = property(s, "half");
            return "upper".equals(half) || "lower".equals(half) || "true".equals(property(s, "waterlogged"));
        });
        return made(AxiomScript.scatter(placed, kept), summary, "axiom.chromastudio.long.brush", "axiom.chromastudio.too_long");
    }

    static Script gradient(String name, List<PaletteEntry> steps, AxiomScript.Axis axis) throws Refused {
        Minecraft mc = Minecraft.getInstance();
        return gradient(name, steps, axis, mc.player == null ? BlockPos.ZERO : mc.player.blockPosition());
    }

    static Script gradient(String name, List<PaletteEntry> steps, AxiomScript.Axis axis, BlockPos at) throws Refused {
        if (steps.size() < 2) throw new Refused(Component.translatable("axiom.chromastudio.gradient.too_short"));
        List<PaletteEntry> full = new ArrayList<>();
        for (PaletteEntry e : steps) {
            BlockState s = PaletteBlocks.state(e);
            if (s != null && GradientShapes.paintable(EmptyBlockGetter.INSTANCE, BlockPos.ZERO, s)) full.add(e);
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

    static AxiomScript.Kept gradientKept() {
        return kept(s -> !s.isAir() && !(FoundBuild.member(EmptyBlockGetter.INSTANCE, BlockPos.ZERO, s)
                && GradientShapes.paintable(EmptyBlockGetter.INSTANCE, BlockPos.ZERO, s)), s -> {
            return s.getBlock().defaultBlockState().getCollisionShape(EmptyBlockGetter.INSTANCE, BlockPos.ZERO).isEmpty()
                    || AxiomScript.KEPT_BY_PROPERTY.stream().anyMatch(p -> property(s, p) != null);
        });
    }

    private static AxiomScript.Kept kept(Predicate<BlockState> refuses, Predicate<BlockState> caught) {
        Set<Block> named = new LinkedHashSet<>();
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

    private static String name(Block b) {
        Identifier id = BuiltInRegistries.BLOCK.getKey(b);
        return id.getNamespace().equals("minecraft") ? id.getPath() : id.toString();
    }

    private static @Nullable String property(BlockState s, String name) {
        Property<?> p = s.getBlock().getStateDefinition().getProperty(name);
        return p == null ? null : value(s, p);
    }

    private static <T extends Comparable<T>> String value(BlockState s, Property<T> p) { return p.getName(s.getValue(p)); }

    static void copy(Script script) { Clipboard.copy(script.text()); }

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
