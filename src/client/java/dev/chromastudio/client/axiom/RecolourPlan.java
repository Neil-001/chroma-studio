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
import org.jspecify.annotations.Nullable;

/**
 * What each block becomes in one recolour into a palette, worked out once for the script and the palette brush alike,
 * so the two change a build the same way. {@link Recolour} picks each block's palette block; this class says which
 * blocks take part, how they look, and which shape the block they become takes.
 *
 * <p>Each block is decided as a whole, by the look of its first state in the index, as a script can only ask which
 * block stands somewhere. The block it becomes keeps the properties both share, such as a stair's facing. Without a
 * From palette the ground, grass, dirt, sand and gravel, is left alone, so a brush that reaches past a build doesn't
 * repaint the lawn.
 *
 * <p>A door changes as a whole, so a recoloured house never has a door of two woods: where the brush reaches one half,
 * the other half changes with it, even past the edge of the stroke, and where a tool mask or the build keeps the other
 * half out, neither changes ({@link #change}, {@link #apply(BlockGetter, int, int, int, Admit)}). The script does the
 * same, except that it can't see Axiom's tool masks, so with a mask that cuts a door in two it changes both halves.
 */
final class RecolourPlan {
    /** What one block becomes: the palette's block with its fixed properties, and the properties it copies from the block it replaces. */
    record Change(PaletteEntry to, BlockState state, List<String> copied) {}

    /**
     * Shapes that follow the full block their colour picks: a stair becomes the stair of a palette material, chosen
     * among the materials that have stairs, and is left alone when none has. Glass counts as a see-through full block.
     */
    private static final Set<Kind> SHAPED = Set.of(Kind.FULL, Kind.GLASS, Kind.SLAB, Kind.STAIRS, Kind.WALL, Kind.FENCE,
            Kind.FENCE_GATE, Kind.TRAPDOOR, Kind.DOOR);
    /**
     * Kinds that only become the palette's blocks of the same kind: a pane a pane, a carpet a carpet, a light a light.
     * Only lights that are full blocks take part, as glowstone and sea lanterns do, since torches and lanterns have
     * shapes of their own.
     */
    private static final Set<Kind> OWN_KIND = Set.of(Kind.PANE, Kind.CARPET, Kind.LIGHT);

    private final Map<Block, Change> changes;
    private final int unshaped;

    private RecolourPlan(Map<Block, Change> changes, int unshaped) {
        this.changes = changes;
        this.unshaped = unshaped;
    }

    /** {@link #of(Palette, Palette, Recolour.Mode, Face, TintContext)} with the face and biome the settings match by. */
    static RecolourPlan of(Palette to, @Nullable Palette from, Recolour.Mode mode) throws Refused {
        return of(to, from, mode, PaletteBlocks.queryFace(), PaletteBlocks.tint());
    }

    /**
     * The plan for recolouring into {@code to} every block but the ground, or only the blocks of {@code from}, with
     * colours read from {@code face}, where {@link Face#ANY} averages all six. Refused, saying why, when the index
     * isn't ready or none of the palette's blocks are in this game.
     */
    static RecolourPlan of(Palette to, @Nullable Palette from, Recolour.Mode mode, Face face, TintContext tint) throws Refused {
        BlockIndex index = BlockIndex.get();
        if (!index.ready()) throw new Refused(Component.translatable("palettes.chromastudio.index_building"));

        // The palette's blocks by group: under FULL the full blocks whose shapes stand in for every shape, and each
        // kind that keeps to its own. A shaped entry, such as oak stairs, brings its material's full block along.
        Map<Kind, Map<BlockState, Target>> targets = new EnumMap<>(Kind.class);
        boolean any = false, anySolid = false, anyFull = false;
        for (PaletteEntry pe : to.entries) {
            BlockState state = PaletteBlocks.state(pe);
            Entry e = state == null ? null : PaletteBlocks.entry(state);
            if (e == null) continue;
            any = true;
            anySolid |= !seeThrough(e);
            Entry base = base(index, e);
            // Sand and concrete powder would fall out of a wall the first time a block next to them changed.
            if (base != null && base.block() instanceof Fallable) continue;
            if (base != null) {
                boolean listed = base == e;
                anyFull |= listed;
                Target t = targets.computeIfAbsent(Kind.FULL, _ -> new LinkedHashMap<>())
                        .computeIfAbsent(listed ? state : base.state(), s -> new Target(s, listed ? pe : PaletteBlocks.of(base)));
                t.listed |= listed;
                t.weight += pe.weight();
                if (!listed) t.shapes.putIfAbsent(e.kind(), pe);
            } else if (takesPart(e)) {
                targets.computeIfAbsent(e.kind(), _ -> new LinkedHashMap<>()).computeIfAbsent(state, s -> new Target(s, pe)).weight += pe.weight();
            }
        }
        if (!any) throw new Refused(Component.translatable("axiom.chromastudio.no_blocks"));
        // A palette that lists full blocks recolours full blocks only into those, and a block it lists only a shape
        // of, such as oak stairs beside stone bricks, lends just that shape.
        if (anyFull) for (Target t : targets.getOrDefault(Kind.FULL, Map.of()).values()) t.onlyShapes = !t.listed;

        Map<Kind, List<Source>> sources = new EnumMap<>(Kind.class);
        for (Source s : sources(index, from, mode, face, tint)) sources.computeIfAbsent(group(s.entry.kind()), _ -> new ArrayList<>()).add(s);

        Map<Block, Change> changes = new IdentityHashMap<>();
        int unshaped = 0;
        for (Map.Entry<Kind, List<Source>> g : sources.entrySet()) {
            List<Target> group = new ArrayList<>(targets.getOrDefault(g.getKey(), Map.of()).values());
            List<Recolour.Look> looks = new ArrayList<>();
            for (Target t : group) {
                Recolour.Look look = look(index, t.state, face, tint, t.weight);
                looks.add(look == null ? null : new Recolour.Look(look.colour(), look.seeThrough(), look.weight(), null, id(t.state.getBlock())));
            }
            // A palette block whose colour couldn't be read, as one an index rebuild has just dropped, sits out.
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
            // Each palette block in each shape, where its material has that shape.
            Map<Kind, List<@Nullable PaletteEntry>> shapes = new EnumMap<>(Kind.class);
            for (Source source : g.getValue()) {
                List<@Nullable PaletteEntry> shaped = shapes.computeIfAbsent(source.entry.kind(), k -> {
                    List<@Nullable PaletteEntry> out = new ArrayList<>();
                    for (Target t : group) out.add(shape(index, t, k));
                    return out;
                });
                // A solid block only turns see-through when the palette has nothing solid at all: a palette of glass
                // and sea lanterns leaves walls alone rather than glazing them.
                boolean solid = !source.look.seeThrough();
                int picked = recolour.pick(source.look, j -> shaped.get(j) != null && !(solid && solidElsewhere && looks.get(j).seeThrough()));
                if (picked < 0) {
                    unshaped++;
                    continue;
                }
                BlockState target = PaletteBlocks.state(shaped.get(picked));
                if (target == null || target == source.entry.block().defaultBlockState()) continue;
                // The row names the block as the game writes it, so the script reads it as the brush does.
                changes.put(source.entry.block(), new Change(PaletteBlocks.of(target), target, shared(source.entry.block(), target.getBlock())));
            }
        }
        return new RecolourPlan(changes, unshaped);
    }

    /** A palette block that a recolour may give: its state, the entry to place, and how it takes part. */
    private static final class Target {
        final BlockState state;
        final PaletteEntry entry;
        /** How much of a build it's meant to cover: its weight, with those of the shaped entries it stands for. */
        long weight;
        /** Whether the palette lists it, rather than only a shape of its material. */
        boolean listed;
        /** Whether it may only give the shapes of it the palette lists, as the palette has full blocks of its own. */
        boolean onlyShapes;
        /** The shapes of its material that the palette lists, which a block of that shape takes as listed. */
        final Map<Kind, PaletteEntry> shapes = new EnumMap<>(Kind.class);

        Target(BlockState state, PaletteEntry entry) {
            this.state = state;
            this.entry = entry;
        }
    }

    /** A block a recolour may change: its index entry, and how it looks in the state the build has it in. */
    private record Source(Entry entry, Recolour.Look look) {}

    /** The block a block becomes, with the properties it copies taken from it, or null to leave it as it is. */
    @Nullable BlockState apply(BlockState here) {
        Change c = changes.get(here.getBlock());
        if (c == null) return null;
        BlockState out = c.state();
        for (String name : c.copied()) out = copy(here, out, name);
        return out;
    }

    /** Whether a position may change, as the tool masks and the build say. */
    @FunctionalInterface
    interface Admit {
        boolean test(int x, int y, int z);
    }

    /** Takes a block the recolour puts down. */
    @FunctionalInterface
    interface Put {
        void put(int x, int y, int z, BlockState state);
    }

    /**
     * What the block at a position becomes, or null to leave it: null too where {@code admit} keeps the position out,
     * or keeps out the other half of a door there, so a door is never left in two materials. For a whole build, which
     * holds both halves of a door and so asks this of each.
     */
    @Nullable BlockState apply(BlockGetter level, int x, int y, int z, Admit admit) {
        if (!admit.test(x, y, z)) return null;
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos(x, y, z);
        BlockState here = level.getBlockState(pos);
        BlockState out = apply(here);
        if (out == null || out == here) return null;
        int dy = otherHalf(level, pos, here);
        return dy == 0 || admit.test(x, y + dy, z) ? out : null;
    }

    /**
     * Puts what the block at a position becomes, as {@link #apply(BlockGetter, int, int, int, Admit)} says, and with
     * a door's half its other half too, wherever the brush reaches. For a brush's stroke, whose edge can cut a door
     * in two. Puts nothing when the block stays as it is.
     */
    void change(BlockGetter level, int x, int y, int z, Admit admit, Put put) {
        // Found before the first put, in case that put reaches the level this reads.
        BlockPos pos = new BlockPos(x, y, z);
        int dy = otherHalf(level, pos, level.getBlockState(pos));
        BlockState out = apply(level, x, y, z, admit);
        if (out == null) return;
        put.put(x, y, z, out);
        if (dy == 0) return;
        BlockState other = level.getBlockState(pos.above(dy)), otherOut = apply(other);
        if (otherOut != null && otherOut != other) put.put(x, y + dy, z, otherOut);
    }

    /**
     * Which way the other half of a two-high block, such as a door, lies from {@code here}: 1 up, -1 down, or 0 when
     * it isn't one, or its other half is missing, as the script also checks.
     */
    static int otherHalf(BlockGetter level, BlockPos pos, BlockState here) {
        if (!here.hasProperty(BlockStateProperties.DOUBLE_BLOCK_HALF)) return 0;
        DoubleBlockHalf half = here.getValue(BlockStateProperties.DOUBLE_BLOCK_HALF);
        int dy = half == DoubleBlockHalf.LOWER ? 1 : -1;
        BlockState other = level.getBlockState(pos.above(dy));
        return other.is(here.getBlock()) && other.getValue(BlockStateProperties.DOUBLE_BLOCK_HALF) != half ? dy : 0;
    }

    /** The plan as rows for a script, in the order of the blocks' ids. */
    List<AxiomScript.Remap> rows() {
        List<AxiomScript.Remap> rows = new ArrayList<>();
        changes.forEach((block, c) -> rows.add(new AxiomScript.Remap(PaletteBlocks.of(block.defaultBlockState()).blockId(), c.to(), c.copied(),
                block.defaultBlockState().hasProperty(BlockStateProperties.DOUBLE_BLOCK_HALF))));
        rows.sort(Comparator.comparing(AxiomScript.Remap::from));
        return rows;
    }

    /** How many blocks change. */
    int size() { return changes.size(); }

    /** How many blocks are left alone because no palette block has their shape or, for glass, can be seen through. */
    int unshaped() { return unshaped; }

    /** The group a block is matched in: shapes with their full blocks, and every other kind with its own. */
    private static Kind group(Kind kind) { return SHAPED.contains(kind) ? Kind.FULL : kind; }

    /**
     * The blocks a recolour may change, one a block: those of {@code from}, looked at in the state it lists them in
     * and weighted as it weights them, with the shapes of each full block it lists, or every block the index holds,
     * each once. Each knows its family, so variants share a shade and different materials don't, and which block it
     * is, so a block the palette has is known. Blocks with a block entity, such as chests and signs, technical
     * blocks, the ground without a From palette, kinds a palette can't recolour, such as plants, and blocks whose
     * colour couldn't be read are left out.
     *
     * <p>To keep the shading, a shape, such as a stair or a door, counts as its material's full block, as a palette
     * sampled from a build counts it: it takes that block's shade and changes with it, so the build itself and a
     * palette sampled from it as From give the same blocks.
     */
    private static List<Source> sources(BlockIndex index, @Nullable Palette from, Recolour.Mode mode, Face face, TintContext tint) {
        // Each block with how much of the build it is: every block in the game counts once, and a From palette's
        // blocks by their weights, which for a palette made from a build are how many of each it has. A full block
        // it lists brings its shapes along, which add nothing, as its weight already counts them.
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
        // To keep the shading, a From palette's shape counts toward its full block, as a palette sampled from the
        // build counts it, so the build and its sample find the same main material. A shape whose full block isn't
        // there keeps its own. Every block in the game counts once as it is, so none of them is a main material.
        if (mode == Recolour.Mode.SHADING && from != null) {
            Map<Block, BlockState> full = new HashMap<>();
            for (BlockState state : weights.keySet()) full.putIfAbsent(state.getBlock(), state);
            for (Map.Entry<BlockState, Integer> w : weights.entrySet()) {
                Entry e = index.entryOf(w.getKey());
                Entry base = e == null || e.kind() == Kind.FULL || e.kind() == Kind.GLASS ? null : base(index, e);
                BlockState into = base == null ? null : full.get(base.block());
                if (into == null || w.getValue() == 0) continue;
                weights.merge(into, w.getValue(), Integer::sum);
                w.setValue(0);
            }
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

    /** A block's id, as the palette names it. */
    private static String id(Block block) { return PaletteBlocks.of(block.defaultBlockState()).blockId(); }

    /** Whether a block of a kind that keeps to its own takes part in a recolour. */
    private static boolean takesPart(Entry e) { return OWN_KIND.contains(e.kind()) && (e.kind() != Kind.LIGHT || e.fullCube()); }

    /** Grass, dirt, sand and gravel, which a recolour of every block leaves alone. */
    static boolean ground(BlockState state) {
        return state.is(BlockTags.SUBSTRATE_OVERWORLD) || state.is(BlockTags.SAND) || state.is(Blocks.GRAVEL);
    }

    /** How a block looks to {@link Recolour}, or null when the index can't say. */
    private static Recolour.@Nullable Look look(BlockIndex index, BlockState state, Face face, TintContext tint, long weight) {
        Entry e = index.entryOf(state);
        Optional<Color> colour = index.colourOf(state, face, tint);
        return e == null || colour.isEmpty() ? null : new Recolour.Look(colour.get(), seeThrough(e), (int) Math.min(Integer.MAX_VALUE, weight));
    }

    /** Whether a full block lets light and sight through, as glass and ice do. */
    private static boolean seeThrough(Entry e) { return e.kind() == Kind.GLASS || e.kind() == Kind.FULL && !e.opaque(); }

    /**
     * The full block a palette block lends its shapes from: itself when it's a full block or glass, or else its
     * material's first full block, so a palette of stone brick stairs recolours walls into stone bricks. Null for a
     * block with no full block, such as a flower.
     */
    private static @Nullable Entry base(BlockIndex index, Entry e) {
        if (e.kind() == Kind.FULL || e.kind() == Kind.GLASS) return e;
        if (!SHAPED.contains(e.kind())) return null;
        for (Entry m : index.materialSet(e)) if (m.kind() == Kind.FULL) return m;
        return null;
    }

    /**
     * A palette block in the shape of a block it replaces: itself for a full block, glass or a kind that keeps to its
     * own, the palette's own entry of that shape where it lists one, else its material's block of that shape. Null
     * when there is none, or when the palette lists the block only for its shapes.
     */
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

    /**
     * The properties a recolour carries from one block to the other: those both have by the same name, where the new
     * block takes every value the old one can have, such as a stair's facing, half and shape.
     */
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

    /** {@code to} with the value {@code from} has for a property, by its name, as the script's withBlockProperty sets it. */
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
