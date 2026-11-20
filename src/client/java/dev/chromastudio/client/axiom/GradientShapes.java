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
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.Fallable;
import net.minecraft.world.level.block.WeatheringCopper;
import net.minecraft.world.level.block.state.BlockState;
import org.jspecify.annotations.Nullable;

/**
 * What a stair, slab, wall or other shaped block of a build becomes when the gradient tool gives it a step: the step's
 * block in the same shape, facing and half, as a recolour gives it, so a tower's trim follows the gradient rather than
 * keeping a stripe of its old material. {@link RecolourPlan} makes the change, so the two tools shape blocks alike.
 *
 * <p>When the step's material has no block of that shape, as concrete has no stairs, the shape is taken from the block
 * of that shape closest in colour to the step, so the trim still fades with the gradient. Doors are left alone, as
 * their two halves could fall in different steps, and so are shapes no block has, such as a pane.
 */
final class GradientShapes {
    /** The shapes that follow the gradient. */
    private static final Set<Kind> SHAPES = Set.of(Kind.SLAB, Kind.STAIRS, Kind.WALL, Kind.FENCE, Kind.FENCE_GATE, Kind.TRAPDOOR);

    private final BlockIndex index = BlockIndex.get();
    private final Face face;
    private final TintContext tint;
    /** Each step block's change for each shaped block, empty where it's left alone. */
    private final Map<List<Block>, Optional<RecolourPlan>> plans = new HashMap<>();
    /** The blocks of each shape that a step may borrow it from, with how they look. */
    private final Map<Kind, Candidates> candidates = new EnumMap<>(Kind.class);

    /** With the face and biome the settings match by. */
    GradientShapes() { this(PaletteBlocks.queryFace(), PaletteBlocks.tint()); }

    GradientShapes(Face face, TintContext tint) {
        this.face = face;
        this.tint = tint;
    }

    /**
     * The block that a shaped block becomes in a step whose full block is {@code step}, with its facing, half and the
     * like kept, or null to leave it as it is.
     */
    @Nullable BlockState apply(BlockState step, BlockState here) {
        Entry e = index.entryOf(here);
        if (e == null || !SHAPES.contains(e.kind()) || here.getBlock() instanceof EntityBlock) return null;
        Optional<RecolourPlan> plan = plans.computeIfAbsent(List.of(step.getBlock(), here.getBlock()), _ -> plan(step, here, e.kind()));
        return plan.map(p -> p.apply(here)).orElse(null);
    }

    /** The change for one step and one shaped block: into the step's own material where it has the shape, or else the closest-coloured block that has it. */
    private Optional<RecolourPlan> plan(BlockState step, BlockState here, Kind kind) {
        Palette from = palette(here);
        Entry stepEntry = index.entryOf(step);
        if (stepEntry == null) return Optional.empty();
        for (Entry m : index.materialSet(stepEntry)) {
            if (m.kind() != kind) continue;
            // The block already has its step's material in this shape.
            if (m.block() == here.getBlock()) return Optional.empty();
            return recolour(palette(step), from);
        }
        BlockState borrowed = borrowed(kind, step);
        return borrowed == null || borrowed.getBlock() == here.getBlock() ? Optional.empty() : recolour(palette(borrowed), from);
    }

    /** A recolour into a palette of one block, which gives each block of {@code from} that block's shape of it. */
    private Optional<RecolourPlan> recolour(Palette to, Palette from) {
        try {
            RecolourPlan plan = RecolourPlan.of(to, from, Recolour.Mode.CLOSEST, face, tint);
            return plan.size() == 0 ? Optional.empty() : Optional.of(plan);
        } catch (Refused _) {
            return Optional.empty();
        }
    }

    private static Palette palette(BlockState state) {
        Palette p = new Palette();
        p.entries.add(PaletteBlocks.of(state.getBlock().defaultBlockState()));
        return p;
    }

    /**
     * The blocks of a shape a step may borrow it from: not ones with a block entity, not ones that fall, and not copper
     * that hasn't been waxed, which would weather to another colour after it was placed.
     */
    private Candidates candidates(Kind kind) {
        List<BlockState> states = new ArrayList<>();
        List<Recolour.Look> looks = new ArrayList<>();
        for (Entry e : index.entries()) {
            Block b = e.block();
            if (e.kind() != kind || b instanceof EntityBlock || b instanceof Fallable || b instanceof WeatheringCopper) continue;
            Optional<Color> colour = index.colourOf(b.defaultBlockState(), face, tint);
            if (colour.isEmpty()) continue;
            states.add(b.defaultBlockState());
            looks.add(new Recolour.Look(colour.get(), false));
        }
        return new Candidates(states, looks.isEmpty() ? null : new Recolour(looks, List.of(), Recolour.Mode.CLOSEST));
    }

    /** The blocks of one shape a step may borrow it from, and the recolour that picks the one closest in colour. */
    private record Candidates(List<BlockState> states, @Nullable Recolour closest) {}

    /** The candidate closest in colour to a step's block, or null when there are none or the step's colour can't be read. */
    private @Nullable BlockState borrowed(Kind kind, BlockState step) {
        Candidates c = candidates.computeIfAbsent(kind, this::candidates);
        Optional<Color> colour = index.colourOf(step, face, tint);
        if (c.closest() == null || colour.isEmpty()) return null;
        int i = c.closest().pick(new Recolour.Look(colour.get(), false), _ -> true);
        return i < 0 ? null : c.states().get(i);
    }
}
