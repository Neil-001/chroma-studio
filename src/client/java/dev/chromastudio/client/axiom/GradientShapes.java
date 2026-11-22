package dev.chromastudio.client.axiom;

import dev.chromastudio.client.action.PaletteBlocks;
import dev.chromastudio.client.axiom.AxiomBridge.Refused;
import dev.chromastudio.client.index.BlockIndex;
import dev.chromastudio.client.index.Entry;
import dev.chromastudio.client.index.Kind;
import dev.chromastudio.client.index.TintContext;
import dev.chromastudio.core.color.Color;
import dev.chromastudio.core.color.Face;
import dev.chromastudio.core.palette.Palette;
import dev.chromastudio.core.palette.Recolour;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.UnaryOperator;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.Fallable;
import net.minecraft.world.level.block.StainedGlassPaneBlock;
import net.minecraft.world.level.block.WeatheringCopper;
import net.minecraft.world.level.block.state.BlockState;
import org.jspecify.annotations.Nullable;

final class GradientShapes {
    private static final Set<Kind> SHAPES = Set.of(Kind.SLAB, Kind.STAIRS, Kind.WALL, Kind.FENCE, Kind.FENCE_GATE, Kind.TRAPDOOR, Kind.PANE);

    private final BlockIndex index = BlockIndex.get();
    private final Face face;
    private final TintContext tint;
    private final Map<List<Object>, UnaryOperator<BlockState>> changes = new HashMap<>();
    private final Map<Kind, Candidates> candidates = new EnumMap<>(Kind.class);

    GradientShapes() { this(PaletteBlocks.queryFace(), PaletteBlocks.tint()); }

    GradientShapes(Face face, TintContext tint) {
        this.face = face;
        this.tint = tint;
    }

    static boolean paintable(BlockGetter level, BlockPos pos, BlockState s) {
        return !(s.getBlock() instanceof EntityBlock) && s.isCollisionShapeFullBlock(level, pos);
    }

    boolean shaped(BlockState here) {
        Entry e = index.entryOf(here);
        return e != null && SHAPES.contains(e.kind());
    }

    @Nullable BlockState apply(BlockState step, BlockState here) {
        Entry e = index.entryOf(here);
        if (e == null || !SHAPES.contains(e.kind()) || here.getBlock() instanceof EntityBlock) return null;
        return changes.computeIfAbsent(List.of(step, here.getBlock()), _ -> change(step, here, e)).apply(here);
    }

    private UnaryOperator<BlockState> change(BlockState step, BlockState here, Entry hereEntry) {
        Kind kind = hereEntry.kind();
        Entry stepEntry = index.entryOf(step);
        if (stepEntry == null) return _ -> null;
        if (kind == Kind.PANE && (stepEntry.kind() != Kind.GLASS || !glassPane(hereEntry))) return _ -> null;
        Optional<Entry> own = kind == Kind.PANE ? Optional.ofNullable(glassPair(stepEntry))
                : index.materialSet(stepEntry).stream().filter(m -> m.kind() == kind).findFirst();
        BlockState to = own.isEmpty() ? borrowed(kind, step) : kind == Kind.PANE ? own.get().block().defaultBlockState() : step;
        if (to == null) return _ -> null;
        if (own.map(Entry::block).orElse(to.getBlock()) == here.getBlock()) return UnaryOperator.identity();
        try {
            RecolourPlan plan = RecolourPlan.of(palette(to), palette(here), Recolour.Mode.CLOSEST, face, tint);
            return plan::apply;
        } catch (Refused _) {
            return _ -> null;
        }
    }

    static boolean glassPane(Entry e) {
        return e.kind() == Kind.PANE && (e.block() instanceof StainedGlassPaneBlock || e.id().endsWith("glass_pane"));
    }

    static @Nullable Entry glassPair(Entry e) {
        String id = e.id();
        String want = e.kind() == Kind.GLASS ? id + "_pane" : glassPane(e) && id.endsWith("_pane") ? id.substring(0, id.length() - "_pane".length()) : null;
        if (want == null) return null;
        for (Entry m : BlockIndex.get().entries()) if (m.id().equals(want)) return m;
        return null;
    }

    private static Palette palette(BlockState state) {
        Palette p = new Palette();
        p.entries.add(PaletteBlocks.of(state.getBlock().defaultBlockState()));
        return p;
    }

    private Candidates candidates(Kind kind) {
        List<BlockState> states = new ArrayList<>();
        List<Recolour.Look> looks = new ArrayList<>();
        for (Entry e : index.entries()) {
            Block b = e.block();
            if (e.kind() != kind || b instanceof EntityBlock || b instanceof Fallable || b instanceof WeatheringCopper) continue;
            if (kind == Kind.PANE && !glassPane(e)) continue;
            Optional<Color> colour = index.colourOf(b.defaultBlockState(), face, tint);
            if (colour.isEmpty()) continue;
            states.add(b.defaultBlockState());
            looks.add(new Recolour.Look(colour.get(), false));
        }
        return new Candidates(states, looks.isEmpty() ? null : new Recolour(looks, List.of(), Recolour.Mode.CLOSEST));
    }

    private record Candidates(List<BlockState> states, @Nullable Recolour closest) {}

    private @Nullable BlockState borrowed(Kind kind, BlockState step) {
        Candidates c = candidates.computeIfAbsent(kind, this::candidates);
        Optional<Color> colour = index.colourOf(step, face, tint);
        if (c.closest() == null || colour.isEmpty()) return null;
        int i = c.closest().pick(new Recolour.Look(colour.get(), false), _ -> true);
        return i < 0 ? null : c.states().get(i);
    }
}
