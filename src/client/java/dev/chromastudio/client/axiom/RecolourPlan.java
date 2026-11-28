package dev.chromastudio.client.axiom;

import dev.chromastudio.client.action.PaletteBlocks;
import dev.chromastudio.client.axiom.AxiomBridge.Refused;
import dev.chromastudio.client.index.BlockIndex;
import dev.chromastudio.client.index.Entry;
import dev.chromastudio.client.index.Kind;
import dev.chromastudio.client.index.TintContext;
import dev.chromastudio.core.color.Color;
import dev.chromastudio.core.color.Face;
import dev.chromastudio.core.palette.AxiomScript;
import dev.chromastudio.core.palette.Palette;
import dev.chromastudio.core.palette.PaletteEntry;
import dev.chromastudio.core.palette.Recolour;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.Fallable;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.block.state.properties.Property;
import org.jetbrains.annotations.Nullable;

final class RecolourPlan {
    record Change(PaletteEntry to, BlockState state, List<String> copied) {}

    private static final Set<Kind> SHAPED = Set.of(Kind.FULL, Kind.GLASS, Kind.SLAB, Kind.STAIRS, Kind.WALL, Kind.FENCE,
            Kind.FENCE_GATE, Kind.TRAPDOOR, Kind.DOOR);
    private static final Set<Kind> OWN_KIND = Set.of(Kind.PANE, Kind.CARPET, Kind.LIGHT);

    private final Map<Block, Change> changes;
    private final int unshaped;
    private final Set<Block> kept;

    private RecolourPlan(Map<Block, Change> changes, int unshaped, Set<Block> kept) {
        this.changes = changes;
        this.unshaped = unshaped;
        this.kept = kept;
    }

    static RecolourPlan of(Palette to, @Nullable Palette from, Recolour.Mode mode) throws Refused {
        return of(to, from, mode, PaletteBlocks.queryFace(), PaletteBlocks.tint());
    }

    static RecolourPlan of(Palette to, @Nullable Palette from, Recolour.Mode mode, Face face, TintContext tint) throws Refused {
        BlockIndex index = BlockIndex.get();
        if (!index.ready()) throw new Refused(Component.translatable("palettes.chromastudio.index_building"));

        Map<Kind, Map<BlockState, Target>> targets = new EnumMap<>(Kind.class);
        boolean any = false, anySolid = false, anyFull = false;
        for (PaletteEntry pe : to.entries) {
            BlockState state = PaletteBlocks.state(pe);
            Entry e = state == null ? null : PaletteBlocks.entry(state);
            if (e == null) continue;
            any = true;
            anySolid |= !seeThrough(e);
            Entry base = base(index, e);
            if (base != null && base.block() instanceof Fallable) continue;
            if (base != null) {
                boolean listed = base == e;
                anyFull |= listed;
                Target t = targets.computeIfAbsent(Kind.FULL, ignored -> new LinkedHashMap<>())
                        .computeIfAbsent(listed ? state : base.state(), s -> new Target(s, listed ? pe : PaletteBlocks.of(base)));
                t.listed |= listed;
                t.weight += pe.weight();
                if (!listed) t.shapes.putIfAbsent(e.kind(), pe);
            } else if (takesPart(e)) {
                targets.computeIfAbsent(e.kind(), ignored -> new LinkedHashMap<>()).computeIfAbsent(state, s -> new Target(s, pe)).weight += pe.weight();
            }
        }
        if (!any) throw new Refused(Component.translatable("axiom.chromastudio.no_blocks"));
        if (anyFull) for (Target t : targets.getOrDefault(Kind.FULL, Map.of()).values()) t.onlyShapes = !t.listed;

        Map<Kind, List<Source>> sources = new EnumMap<>(Kind.class);
        for (Source s : sources(index, from, mode, face, tint)) sources.computeIfAbsent(group(s.entry.kind()), ignored -> new ArrayList<>()).add(s);

        Map<Block, Change> changes = new IdentityHashMap<>();
        Set<Block> kept = Collections.newSetFromMap(new IdentityHashMap<>());
        int unshaped = 0;
        for (Map.Entry<Kind, List<Source>> g : sources.entrySet()) {
            List<Target> group = new ArrayList<>(targets.getOrDefault(g.getKey(), Map.of()).values());
            List<Recolour.Look> looks = new ArrayList<>();
            for (Target t : group) {
                Recolour.Look look = look(index, t.state, face, tint, t.weight);
                looks.add(look == null ? null : new Recolour.Look(look.colour(), look.seeThrough(), look.weight(), null, id(t.state.getBlock())));
            }
            for (int j = group.size() - 1; j >= 0; j--) {
                if (looks.get(j) != null) continue;
                group.remove(j);
                looks.remove(j);
            }
            if (group.isEmpty()) {
                unshaped += g.getValue().size();
                continue;
            }
            Recolour recolour = new Recolour(looks, g.getValue().stream().map(Source::look).toList(), mode);
            final boolean solidElsewhere = anySolid;
            Map<Kind, List<@Nullable PaletteEntry>> shapes = new EnumMap<>(Kind.class);
            for (Source source : g.getValue()) {
                List<@Nullable PaletteEntry> shaped = shapes.computeIfAbsent(source.entry.kind(), k -> {
                    List<@Nullable PaletteEntry> out = new ArrayList<>();
                    for (Target t : group) out.add(shape(index, t, k));
                    return out;
                });
                boolean solid = !source.look.seeThrough();
                int picked = recolour.pick(source.look, j -> shaped.get(j) != null && !(solid && solidElsewhere && looks.get(j).seeThrough()));
                if (picked < 0) {
                    unshaped++;
                    continue;
                }
                BlockState target = PaletteBlocks.state(shaped.get(picked));
                if (target == null) continue;
                if (target.getBlock() == source.entry.block()) {
                    kept.add(source.entry.block());
                    continue;
                }
                changes.put(source.entry.block(), new Change(PaletteBlocks.of(target), target, shared(source.entry.block(), target.getBlock())));
            }
        }
        return new RecolourPlan(changes, unshaped, kept);
    }

    private static final class Target {
        final BlockState state;
        final PaletteEntry entry;
        long weight;
        boolean listed;
        boolean onlyShapes;
        final Map<Kind, PaletteEntry> shapes = new EnumMap<>(Kind.class);

        Target(BlockState state, PaletteEntry entry) {
            this.state = state;
            this.entry = entry;
        }
    }

    private record Source(Entry entry, Recolour.Look look) {}

    @Nullable BlockState apply(BlockState here) {
        Change c = changes.get(here.getBlock());
        if (c == null) return null;
        BlockState out = c.state();
        for (String name : c.copied()) out = copy(here, out, name);
        return keepsWater(here, out) ? out : null;
    }

    static boolean keepsWater(BlockState here, BlockState out) { return !waterlogged(here) || waterlogged(out); }

    private static boolean waterlogged(BlockState s) {
        return s.hasProperty(BlockStateProperties.WATERLOGGED) && s.getValue(BlockStateProperties.WATERLOGGED);
    }

    @FunctionalInterface
    interface Admit {
        boolean test(int x, int y, int z);
    }

    @FunctionalInterface
    interface Put {
        void put(int x, int y, int z, BlockState state);
    }

    @Nullable BlockState apply(BlockGetter level, int x, int y, int z, Admit admit) {
        if (!admit.test(x, y, z)) return null;
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos(x, y, z);
        BlockState here = level.getBlockState(pos);
        BlockState out = apply(here);
        if (out == null || out == here) return null;
        int dy = otherHalf(level, pos, here);
        return dy == 0 || admit.test(x, y + dy, z) ? out : null;
    }

    void change(BlockGetter level, int x, int y, int z, Admit admit, Put put) {
        BlockPos pos = new BlockPos(x, y, z);
        int dy = otherHalf(level, pos, level.getBlockState(pos));
        BlockState out = apply(level, x, y, z, admit);
        if (out == null) return;
        put.put(x, y, z, out);
        if (dy == 0) return;
        BlockState other = level.getBlockState(pos.above(dy)), otherOut = apply(other);
        if (otherOut != null && otherOut != other) put.put(x, y + dy, z, otherOut);
    }

    static int otherHalf(BlockGetter level, BlockPos pos, BlockState here) {
        if (!here.hasProperty(BlockStateProperties.DOUBLE_BLOCK_HALF)) return 0;
        DoubleBlockHalf half = here.getValue(BlockStateProperties.DOUBLE_BLOCK_HALF);
        int dy = half == DoubleBlockHalf.LOWER ? 1 : -1;
        BlockState other = level.getBlockState(pos.above(dy));
        return other.is(here.getBlock()) && other.getValue(BlockStateProperties.DOUBLE_BLOCK_HALF) != half ? dy : 0;
    }

    List<AxiomScript.Remap> rows() {
        List<AxiomScript.Remap> rows = new ArrayList<>();
        changes.forEach((block, c) -> rows.add(new AxiomScript.Remap(PaletteBlocks.of(block.defaultBlockState()).blockId(), c.to(), c.copied(),
                block.defaultBlockState().hasProperty(BlockStateProperties.DOUBLE_BLOCK_HALF))));
        rows.sort(Comparator.comparing(AxiomScript.Remap::from));
        return rows;
    }

    int size() { return changes.size(); }

    int unshaped() { return unshaped; }

    boolean keeps(BlockState here) { return kept.contains(here.getBlock()); }

    int kept() { return kept.size(); }

    private static Kind group(Kind kind) { return SHAPED.contains(kind) ? Kind.FULL : kind; }

    private static List<Source> sources(BlockIndex index, @Nullable Palette from, Recolour.Mode mode, Face face, TintContext tint) {
        Map<BlockState, Integer> weights = new LinkedHashMap<>();
        if (from == null) for (Entry e : index.entries()) weights.putIfAbsent(e.block().defaultBlockState(), 1);
        else {
            for (PaletteEntry pe : from.entries) {
                BlockState state = PaletteBlocks.state(pe);
                if (state != null) weights.putIfAbsent(state, pe.weight());
            }
            for (BlockState state : List.copyOf(weights.keySet())) {
                Entry e = index.entryOf(state);
                if (e == null || e.kind() != Kind.FULL) continue;
                for (Entry m : index.materialSet(e)) if (m.kind() != Kind.FULL && SHAPED.contains(m.kind())) weights.putIfAbsent(m.block().defaultBlockState(), 0);
            }
        }
        if (mode == Recolour.Mode.SHADING && from != null) {
            Map<Block, BlockState> full = new HashMap<>();
            for (BlockState state : weights.keySet()) full.putIfAbsent(state.getBlock(), state);
            Map<BlockState, Integer> folded = new LinkedHashMap<>();
            for (Map.Entry<BlockState, Integer> w : weights.entrySet()) {
                Entry e = index.entryOf(w.getKey());
                Entry base = e == null || e.kind() == Kind.FULL || e.kind() == Kind.GLASS ? null : base(index, e);
                if (base == null) {
                    folded.merge(w.getKey(), w.getValue(), Integer::sum);
                    continue;
                }
                folded.merge(full.getOrDefault(base.block(), base.state()), w.getValue(), Integer::sum);
                folded.putIfAbsent(w.getKey(), 0);
            }
            weights = folded;
        }
        List<Source> out = new ArrayList<>();
        Set<Block> seen = new HashSet<>();
        for (Map.Entry<BlockState, Integer> w : weights.entrySet()) {
            BlockState state = w.getKey();
            Block block = state.getBlock();
            if (!seen.add(block)) continue;
            Entry e = index.entryOf(state);
            if (e == null || block instanceof EntityBlock || e.technical() || from == null && ground(state)) continue;
            if (!SHAPED.contains(e.kind()) && !takesPart(e)) continue;
            Entry as = mode == Recolour.Mode.SHADING && e.kind() != Kind.FULL && e.kind() != Kind.GLASS ? base(index, e) : null;
            if (as == null) as = e;
            Recolour.Look look = look(index, as != e || from == null ? as.state() : state, face, tint, w.getValue());
            if (look != null) out.add(new Source(e, new Recolour.Look(look.colour(), look.seeThrough(), look.weight(), index.family(as), id(as.block()))));
        }
        return out;
    }

    private static String id(Block block) { return PaletteBlocks.of(block.defaultBlockState()).blockId(); }

    private static boolean takesPart(Entry e) { return OWN_KIND.contains(e.kind()) && (e.kind() != Kind.LIGHT || e.fullCube()); }

    static boolean ground(BlockState state) {
        return state.is(BlockTags.DIRT) || state.is(BlockTags.SAND) || state.is(Blocks.GRAVEL) || state.is(Blocks.BEDROCK);
    }

    private static Recolour.@Nullable Look look(BlockIndex index, BlockState state, Face face, TintContext tint, long weight) {
        Entry e = index.entryOf(state);
        Optional<Color> colour = index.colourOf(state, face, tint);
        return e == null || colour.isEmpty() ? null : new Recolour.Look(colour.get(), seeThrough(e), (int) Math.min(Integer.MAX_VALUE, weight));
    }

    private static boolean seeThrough(Entry e) { return e.kind() == Kind.GLASS || e.kind() == Kind.FULL && !e.opaque(); }

    private static @Nullable Entry base(BlockIndex index, Entry e) {
        if (e.kind() == Kind.FULL || e.kind() == Kind.GLASS) return e;
        if (!SHAPED.contains(e.kind())) return null;
        for (Entry m : index.materialSet(e)) if (m.kind() == Kind.FULL) return m;
        return null;
    }

    private static @Nullable PaletteEntry shape(BlockIndex index, Target target, Kind kind) {
        if (OWN_KIND.contains(kind)) return target.entry;
        if (kind == Kind.FULL || kind == Kind.GLASS) return target.onlyShapes ? null : target.entry;
        PaletteEntry listed = target.shapes.get(kind);
        if (listed != null) return listed;
        if (target.onlyShapes) return null;
        Entry e = index.entryOf(target.state);
        if (e == null) return null;
        for (Entry m : index.materialSet(e)) if (m.kind() == kind) return PaletteBlocks.of(m);
        return null;
    }

    private static List<String> shared(Block from, Block to) {
        List<String> out = new ArrayList<>();
        for (Property<?> p : from.getStateDefinition().getProperties()) {
            Property<?> q = to.getStateDefinition().getProperty(p.getName());
            if (q != null && names(q).containsAll(names(p))) out.add(p.getName());
        }
        return out;
    }

    private static <T extends Comparable<T>> Set<String> names(Property<T> p) {
        Set<String> out = new LinkedHashSet<>();
        for (T value : p.getPossibleValues()) out.add(p.getName(value));
        return out;
    }

    private static BlockState copy(BlockState from, BlockState to, String name) {
        Property<?> p = from.getBlock().getStateDefinition().getProperty(name);
        Property<?> q = to.getBlock().getStateDefinition().getProperty(name);
        return p == null || q == null ? to : set(to, q, valueName(from, p));
    }

    private static <T extends Comparable<T>> String valueName(BlockState state, Property<T> p) { return p.getName(state.getValue(p)); }

    private static <T extends Comparable<T>> BlockState set(BlockState state, Property<T> p, String value) {
        return p.getValue(value).map(v -> state.setValue(p, v)).orElse(state);
    }
}
