package dev.chromastudio.client.axiom;

import static dev.chromastudio.client.axiom.AxiomTools.tr;

import com.moulberry.axiomclientapi.CustomTool;
import com.moulberry.axiomclientapi.Effects;
import com.moulberry.axiomclientapi.IAxiomWorldRenderContext;
import com.moulberry.axiomclientapi.regions.BooleanRegion;
import dev.chromastudio.client.ChromaStudioClient;
import dev.chromastudio.client.GradientStore;
import dev.chromastudio.client.action.PaletteBlocks;
import dev.chromastudio.client.index.BlockIndex;
import dev.chromastudio.client.index.Entry;
import dev.chromastudio.client.ui.tabs.GradientTab;
import dev.chromastudio.core.color.Color;
import dev.chromastudio.core.palette.BuildSearch;
import dev.chromastudio.core.palette.GradientLine;
import dev.chromastudio.core.palette.PaletteEntry;
import dev.chromastudio.core.palette.StoredGradient;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.EmptyBlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * An Axiom tool that lays one of Chroma Studio's gradients across a build. The player uses it on the block where the
 * gradient starts and then where it ends. The build is the blocks joined to the first one between those two ends, and
 * each of its blocks takes the step of where it sits along the line between them, neighbouring steps mixing across a
 * zone as wide as the player sets. Ends clicked nearly in line, as the top and bottom of a tower in different
 * columns, are straightened onto that line ({@link GradientLine#between}), so the steps lie level with the build.
 * Stairs, slabs, walls and the like take their step in their own shape, through {@link GradientShapes}. The world
 * shows the result before anything changes, through {@link BuildPreview}; using the tool again moves the nearer end,
 * and Apply sends the whole change to Axiom as one entry in its history. Every change keeps to the player's tool
 * masks in Axiom ({@link AxiomMask}).
 */
final class GradientTool implements CustomTool {
    private static final int MAX_REACH = 64;
    /** The key for the gradient on the Gradient tab, which no saved gradient's name can be, as names are never blank. */
    private static final String ON_TAB = "";

    /** A gradient the panel offers: its key, the label it shows, and its blocks in order. */
    private record Choice(String key, String label, List<PaletteEntry> steps) {}

    private final BuildPreview preview = new BuildPreview("chromastudio_gradient");
    private final BooleanRegion ends = AxiomTools.regions.createBoolean();
    private final BooleanRegion one = AxiomTools.regions.createBoolean();

    /** The key of the gradient picked in the panel, or null until one is, and then the first in the list stands in. */
    private String picked;
    /**
     * How wide the zone where neighbouring steps mix is, as a percentage of a step. Three quarters fades steps into
     * each other on a tower a few blocks a step as on a long wall, while each step still shows pure along its middle.
     */
    private final int[] blend = {75};
    private boolean likeFirst = true;
    private final int[] reach = {16};
    private @Nullable BlockPos first, second;
    /**
     * The block the build is found from, and whose kind {@link #likeFirst} keeps to: the first block used, which stays
     * the same when the ends swap, so a swap only turns the gradient round. It moves with its end when that end moves.
     */
    private @Nullable BlockPos seed;
    /** The world the ends are in. */
    private @Nullable ClientLevel endsIn;

    /** The build between the ends and the line through them, found again only when what it's found from changes. */
    private BuildSearch.@Nullable Found found;
    private @Nullable GradientLine line;
    private @Nullable Object foundFor;
    /** What the preview on show was painted for, so it's painted again only when something changes. */
    private @Nullable Object paintedFor;
    /** What the preview found: the blocks it covers, those it leaves as they are, and those the tool masks keep out. */
    private int covered, leftAlone, masked;
    /** Why the last Apply didn't go, until the next use or Apply. */
    private @Nullable Component refused;

    GradientTool() {
        one.add(0, 0, 0);
    }

    @Override
    public String name() { return tr("axiom.chromastudio.gradient_tool"); }

    @Override
    public void reset() {
        first = null;
        second = null;
        seed = null;
        endsIn = null;
        refused = null;
        clearPreview();
    }

    private void clearPreview() {
        preview.clear();
        ends.clear();
        found = null;
        line = null;
        foundFor = null;
        paintedFor = null;
        covered = leftAlone = masked = 0;
    }

    @Override
    public boolean callUseTool() {
        BlockHitResult hit = AxiomTools.tools.raycastBlock();
        ClientLevel level = Minecraft.getInstance().level;
        if (hit == null || hit.getType() != HitResult.Type.BLOCK || level == null) return false;
        if (endsIn != level) reset();
        endsIn = level;
        refused = null;
        BlockPos at = hit.getBlockPos().immutable();
        if (first == null) {
            first = at;
            seed = at;
        } else if (second == null) {
            if (!at.equals(first)) second = at;
        } else if (!at.equals(first) && !at.equals(second)) {
            // Moves whichever end is nearer, so the player can adjust either without starting over.
            boolean nearFirst = at.distSqr(first) <= at.distSqr(second);
            if ((nearFirst ? first : second).equals(seed)) seed = at;
            if (nearFirst) first = at;
            else second = at;
        }
        return true;
    }

    @Override
    public boolean callConfirm() { return apply(); }

    @Override
    public boolean callDelete() {
        if (first == null) return false;
        reset();
        return true;
    }

    /** Called every client tick. The ends are kept when the preview is dropped, so it comes back with the tool. */
    void tick() {
        if (preview.tick()) foundFor = paintedFor = null;
    }

    @Override
    public void render(IAxiomWorldRenderContext rc) {
        ClientLevel level = Minecraft.getInstance().level;
        if (level == null) return;
        // The ends belong to the world they were set in, so a trip to another dimension or server doesn't carry them over.
        if (first != null && endsIn != level) reset();
        Choice choice = current(choices());
        Object search = second == null || !BlockIndex.get().ready() ? null
                : Arrays.asList(first, second, seed, likeFirst, reach[0], BlockIndex.get().version());
        if (!Objects.equals(search, foundFor)) {
            find(level);
            foundFor = search;
            paintedFor = null;
        }
        Object paint = Arrays.asList(foundFor, choice == null ? null : choice.steps(), blend[0]);
        if (!paint.equals(paintedFor)) {
            paint(level, choice);
            paintedFor = paint;
        }
        if (second == null) {
            BlockHitResult hit = AxiomTools.tools.raycastBlock();
            if (hit != null && hit.getType() == HitResult.Type.BLOCK) one.render(rc, Vec3.atLowerCornerOf(hit.getBlockPos()), Effects.BLUE | Effects.OUTLINE);
        } else {
            preview.render(rc);
        }
        if (first != null) ends.render(rc, Vec3.ZERO, Effects.BLUE | Effects.OUTLINE);
    }

    /**
     * Finds the build between the ends: only blocks of the build change, never air or liquid, and with
     * {@link #likeFirst} only those of the first block's material.
     */
    private void find(ClientLevel level) {
        found = null;
        line = null;
        if (first == null || second == null || seed == null || !BlockIndex.get().ready()) return;
        GradientLine l = GradientLine.between(first.getX(), first.getY(), first.getZ(), second.getX(), second.getY(), second.getZ());
        Set<Block> like = like(level.getBlockState(seed));
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        found = BuildSearch.from(seed.getX(), seed.getY(), seed.getZ(), (x, y, z) -> {
            BlockState s = level.getBlockState(pos.set(x, y, z));
            if (s.isAir() || s.getBlock() instanceof LiquidBlock) return false;
            return (!likeFirst || like.contains(s.getBlock())) && l.reaches(x, y, z, reach[0]);
        }, FoundBuild.CAP);
        line = l;
    }

    /**
     * Works out what the gradient would do to the build found and shows it in the world in place of the blocks
     * there. A block that holds items or other data, such as a barrel, which the change would empty, stays as it is.
     * A block that isn't a full cube keeps its shape, or stays as it is when it has none a step can take.
     */
    private void paint(ClientLevel level, @Nullable Choice choice) {
        preview.clear();
        ends.clear();
        covered = leftAlone = masked = 0;
        if (first != null) ends.add(first.getX(), first.getY(), first.getZ());
        if (second != null) ends.add(second.getX(), second.getY(), second.getZ());
        List<BlockState> steps = choice == null ? List.of() : states(choice);
        if (found == null || line == null || steps.size() < 2) return;
        preview.begin(level);
        GradientShapes shapes = new GradientShapes();
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (long p : found.blocks()) {
            int x = BuildSearch.x(p), y = BuildSearch.y(p), z = BuildSearch.z(p);
            BlockState now = level.getBlockState(pos.set(x, y, z));
            BlockState step = steps.get(line.step(x, y, z, steps.size(), blend[0] / 100.0));
            BlockState next = shapes.shaped(now) ? shapes.apply(step, now) : paintable(level, pos, now) ? step : null;
            if (next == null) {
                leftAlone++;
                continue;
            }
            if (!preview.allows(x, y, z)) {
                masked++;
                continue;
            }
            covered++;
            preview.outline(x, y, z);
            if (next != now) preview.change(x, y, z, now, next);
        }
        // A preview made while the masks failed could show blocks they were keeping out.
        if (preview.maskFailed()) preview.clear();
        preview.drawnNow();
    }

    /** Whether the gradient may change a block, or put one in, as a whole: a full cube with no block entity. */
    private static boolean paintable(BlockGetter level, BlockPos pos, BlockState s) {
        return !(s.getBlock() instanceof EntityBlock) && s.isCollisionShapeFullBlock(level, pos);
    }

    /**
     * Sends the change to Axiom as one entry in its history, and starts over. When anything has changed since the
     * preview was made, nothing is sent: the preview is made again from the world as it is now, for the player to
     * check, and the panel says why. False when nothing was sent.
     */
    private boolean apply() {
        if (preview.isEmpty()) return false;
        Optional<Component> why = preview.apply();
        if (why.isPresent()) {
            refused = why.get();
            foundFor = paintedFor = null;
            return false;
        }
        reset();
        return true;
    }

    @Override
    public void displayImguiOptions() { AxiomPanel.draw(this::panel); }

    private void panel() {
        List<Choice> all = choices();
        Choice shown = current(all);
        if (all.isEmpty()) {
            AxiomPanel.text(tr("axiom.chromastudio.gradient_tool.gradient"));
            AxiomPanel.wrapped(tr("axiom.chromastudio.gradient_tool.none"));
            // The window opens at the next tick, after Axiom has finished drawing its panels this frame.
            if (AxiomPanel.button(tr("key.chromastudio.open"), true, tr("axiom.chromastudio.open.tip"))) ChromaStudioClient.openWindowSoon("gradient");
        } else {
            int clicked = AxiomPanel.list(tr("axiom.chromastudio.gradient_tool.gradient"), "##gradients", all.stream().map(Choice::label).toList(), all.indexOf(shown));
            if (clicked >= 0) picked = all.get(clicked).key();
            if (shown != null) {
                AxiomPanel.swatches("step", swatches(shown.steps()));
                int usable = states(shown).size();
                if (usable < 2) AxiomPanel.wrapped(tr("axiom.chromastudio.gradient_tool.missing"));
                else if (usable < shown.steps().size()) AxiomPanel.note(tr("axiom.chromastudio.gradient_tool.skipped", shown.steps().size() - usable));
            }
        }

        AxiomPanel.slider(tr("axiom.chromastudio.gradient_tool.blend"), blend, 0, 100, "%d%%", true, tr("axiom.chromastudio.gradient_tool.blend.tip"));
        AxiomPanel.slider(tr("axiom.chromastudio.gradient_tool.reach"), reach, 1, MAX_REACH, "%d", true, tr("axiom.chromastudio.gradient_tool.reach.tip"));
        if (AxiomPanel.checkbox(tr("axiom.chromastudio.gradient_tool.like_first"), likeFirst, true, tr("axiom.chromastudio.gradient_tool.like_first.tip"))) likeFirst = !likeFirst;
        AxiomPanel.masks();

        AxiomPanel.separator();
        boolean ready = BlockIndex.get().ready();
        AxiomPanel.applyCancel(!preview.isEmpty(), tr(second != null && !ready ? "axiom.chromastudio.not_ready" : "axiom.chromastudio.gradient_tool.apply.none"),
                first != null, this::apply, this::reset, () -> {
                    if (AxiomPanel.button(tr("axiom.chromastudio.gradient_tool.swap"), second != null,
                            tr(second == null ? "axiom.chromastudio.gradient_tool.swap.none" : "axiom.chromastudio.gradient_tool.swap.tip"))) {
                        BlockPos was = first;
                        first = second;
                        second = was;
                    }
                });
        if (refused != null) AxiomPanel.wrapped(refused);
        if (first == null) AxiomPanel.wrapped(tr("axiom.chromastudio.gradient_tool.start"));
        else if (second == null) AxiomPanel.wrapped(tr("axiom.chromastudio.gradient_tool.end"));
        else if (!ready) AxiomPanel.wrapped(tr("axiom.chromastudio.not_ready"));
        else if (line != null && shown != null) {
            int steps = states(shown).size();
            double length = Math.sqrt(first.distSqr(second)) + 1;
            if (steps >= 2) AxiomPanel.wrapped(tr("axiom.chromastudio.gradient_tool.length", Math.round(length), String.format("%.1f", length / steps)));
            if (line.straightened()) AxiomPanel.note(tr("axiom.chromastudio.gradient_tool.straightened"));
            else if (line.slanted()) AxiomPanel.note(tr("axiom.chromastudio.gradient_tool.diagonal"));
            AxiomPanel.wrapped(tr("axiom.chromastudio.count", preview.count(), covered));
            if (masked > 0) AxiomPanel.wrapped(tr("axiom.chromastudio.masked", masked));
            if (leftAlone > 0) AxiomPanel.note(tr("axiom.chromastudio.gradient_tool.left_alone", leftAlone));
            if (found != null && found.capped()) AxiomPanel.wrapped(tr("axiom.chromastudio.capped", FoundBuild.CAP) + " " + tr("axiom.chromastudio.gradient_tool.capped"));
            AxiomPanel.note(tr("axiom.chromastudio.gradient_tool.adjust"));
        }
    }

    /** A gradient's blocks as swatches, each named when hovered. */
    private static List<AxiomPanel.Swatch> swatches(List<PaletteEntry> steps) {
        List<AxiomPanel.Swatch> out = new ArrayList<>();
        for (PaletteEntry e : steps) {
            Color c = PaletteBlocks.colour(e);
            if (c != null) out.add(new AxiomPanel.Swatch(c, PaletteBlocks.name(e).getString()));
        }
        return out;
    }

    /** The gradient on the Gradient tab, when it has one, and then the saved gradients by name. */
    private static List<Choice> choices() {
        List<Choice> out = new ArrayList<>();
        List<PaletteEntry> onTab = GradientTab.stepsOnShow();
        if (!onTab.isEmpty()) out.add(new Choice(ON_TAB, tr("axiom.chromastudio.gradient_tool.on_tab"), onTab));
        for (StoredGradient g : GradientStore.get().all()) {
            out.add(new Choice(g.name(), g.name(), g.blocks().stream().map(StoredGradient.Step::block).toList()));
        }
        return out;
    }

    private @Nullable Choice current(List<Choice> all) {
        // The panel's choice, while it's still in the list: the player may have deleted it in Chroma Studio's window.
        for (Choice c : all) if (c.key().equals(picked)) return c;
        return all.isEmpty() ? null : all.getFirst();
    }

    /**
     * The blocks that count as like the first block: those of its material, such as stone bricks with their stairs,
     * slabs and walls, so a build's trim takes the gradient along with its walls, and a glass's own pane.
     */
    private static Set<Block> like(BlockState start) {
        Set<Block> out = new HashSet<>();
        out.add(start.getBlock());
        BlockIndex index = BlockIndex.get();
        Entry e = index.entryOf(start);
        if (e != null) for (Entry m : index.materialSet(e)) out.add(m.block());
        Entry pair = e == null ? null : GradientShapes.glassPair(e);
        if (pair != null) out.add(pair.block());
        return out;
    }

    /**
     * The gradient's blocks that this game has and that are full cubes, in order. A gradient chosen among every kind of
     * block may hold a slab or a stair, which would change the build's shape where it went, so it's left out.
     */
    private static List<BlockState> states(Choice c) {
        List<BlockState> out = new ArrayList<>();
        for (PaletteEntry e : c.steps()) {
            BlockState s = PaletteBlocks.state(e);
            if (s != null && paintable(EmptyBlockGetter.INSTANCE, BlockPos.ZERO, s)) out.add(s);
        }
        return out;
    }
}
