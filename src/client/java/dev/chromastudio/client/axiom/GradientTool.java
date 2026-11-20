package dev.chromastudio.client.axiom;

import static dev.chromastudio.client.axiom.AxiomTools.tr;

import com.moulberry.axiomclientapi.CustomTool;
import com.moulberry.axiomclientapi.Effects;
import com.moulberry.axiomclientapi.IAxiomWorldRenderContext;
import com.moulberry.axiomclientapi.regions.BlockRegion;
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
import imgui.moulberry92.ImGui;
import imgui.moulberry92.flag.ImGuiColorEditFlags;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.EmptyBlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * An Axiom tool that lays one of Chroma Studio's gradients across a build. The player uses it on the block where the
 * gradient starts and then where it ends. The build is the blocks joined to the first one between those two ends, and
 * each of its blocks takes the step of where it sits along the line between them, neighbouring steps mixing across a
 * zone as wide as the player sets. Stairs, slabs, walls and the like take their step in their own shape, through
 * {@link GradientShapes}. The world shows the result before anything changes, with the blocks the gradient covers
 * outlined; using the tool again moves the nearer end, and Apply sends the whole change to Axiom as one entry in its
 * history.
 */
final class GradientTool implements CustomTool {
    /** The most blocks one gradient searches, so a build that runs on into the landscape can't stall the game. */
    private static final int CAP = 100_000;
    private static final int MAX_REACH = 64;
    /** Our name for the world's look while the preview shows. */
    private static final String PREVIEW = "chromastudio_gradient";
    /** The key for the gradient on the Gradient tab, which no saved gradient's name can be, as names are never blank. */
    private static final String ON_TAB = "";

    /** One block the preview changes: where it is, what was there when the preview was made, and what goes there. */
    private record Planned(int x, int y, int z, BlockState was, BlockState next) {}

    /** A gradient the panel offers: its key, the label it shows, and its blocks in order. */
    private record Choice(String key, String label, List<PaletteEntry> steps) {}

    private final BlockRegion change = AxiomTools.regions.createBlock();
    private final List<Planned> planned = new ArrayList<>();
    private final BooleanRegion touched = AxiomTools.regions.createBoolean();
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
    private BlockPos first, second;
    /**
     * The block the build is found from, and whose kind {@link #likeFirst} keeps to: the first block used, which stays
     * the same when the ends swap, so a swap only turns the gradient round. It moves with its end when that end moves.
     */
    private BlockPos seed;

    /** What the preview on show was made for, so it's made again only when something changes. */
    private Object previewFor;
    private boolean overriding;
    /**
     * What the preview found: the blocks the gradient covers, the blocks it leaves as they are, as doors and barrels,
     * and whether the cap cut the build short.
     */
    private int covered, leftAlone;
    private boolean capped;
    /** When Axiom last drew this tool, as {@link System#nanoTime}. */
    private long drawn;

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
        clearPreview();
    }

    @Override
    public boolean callUseTool() {
        BlockHitResult hit = AxiomTools.tools.raycastBlock();
        if (hit == null || hit.getType() != HitResult.Type.BLOCK) return false;
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

    /**
     * Drops the preview once Axiom has stopped drawing this tool, as when the player leaves the editor or picks another
     * tool, neither of which Axiom tells a tool about. The world would otherwise go on showing blocks that aren't there.
     * The ends are kept, so the preview comes back with the tool. Called every client tick.
     */
    void tick() {
        if (overriding && System.nanoTime() - drawn > 250_000_000L) clearPreview();
    }

    @Override
    public void render(IAxiomWorldRenderContext rc) {
        drawn = System.nanoTime();
        ClientLevel level = Minecraft.getInstance().level;
        if (level == null) return;
        Choice choice = current(choices());
        Object key = choice == null ? null : Arrays.asList(choice.steps(), first, second, blend[0], likeFirst, reach[0]);
        if (!Objects.equals(key, previewFor)) {
            preview(level, choice);
            previewFor = key;
            // A big preview can take longer than the tick check waits, which mustn't take it for the editor closing.
            drawn = System.nanoTime();
        }
        if (second == null) {
            BlockHitResult hit = AxiomTools.tools.raycastBlock();
            if (hit != null && hit.getType() == HitResult.Type.BLOCK) one.render(rc, Vec3.atLowerCornerOf(hit.getBlockPos()), Effects.BLUE | Effects.OUTLINE);
        } else {
            touched.render(rc, Vec3.ZERO, Effects.OUTLINE);
        }
        if (first != null) ends.render(rc, Vec3.ZERO, Effects.BLUE | Effects.OUTLINE);
    }

    /**
     * Works out what the gradient would do and shows it in the world in place of the blocks there. Only blocks of the
     * build change: never air or liquid, nor a block that holds items or other data, such as a barrel, which the
     * change would empty. A block that isn't a full cube keeps its shape, or is left alone when it has none a step can
     * take.
     */
    private void preview(ClientLevel level, Choice choice) {
        clearPreview();
        if (first != null) ends.add(first.getX(), first.getY(), first.getZ());
        if (second != null) ends.add(second.getX(), second.getY(), second.getZ());
        List<BlockState> steps = choice == null ? List.of() : states(choice);
        if (first == null || second == null || steps.size() < 2) return;

        GradientLine line = new GradientLine(first.getX(), first.getY(), first.getZ(), second.getX(), second.getY(), second.getZ());
        Set<Block> like = like(level.getBlockState(seed));
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        BuildSearch.Found found = BuildSearch.from(seed.getX(), seed.getY(), seed.getZ(), (x, y, z) -> {
            BlockState s = level.getBlockState(pos.set(x, y, z));
            if (s.isAir() || s.getBlock() instanceof LiquidBlock) return false;
            return (!likeFirst || like.contains(s.getBlock())) && line.reaches(x, y, z, reach[0]);
        }, CAP);
        capped = found.capped();

        AxiomTools.tools.acquireChunkRenderOverrider(PREVIEW);
        overriding = true;
        GradientShapes shapes = new GradientShapes();
        for (long p : found.blocks()) {
            int x = BuildSearch.x(p), y = BuildSearch.y(p), z = BuildSearch.z(p);
            BlockState now = level.getBlockState(pos.set(x, y, z));
            BlockState step = steps.get(line.step(x, y, z, steps.size(), blend[0] / 100.0));
            BlockState next = paintable(level, pos, now) ? step : shapes.apply(step, now);
            if (next == null) {
                leftAlone++;
                continue;
            }
            covered++;
            touched.add(x, y, z);
            if (next == now) continue;
            planned.add(new Planned(x, y, z, now, next));
            change.addBlock(x, y, z, next);
            AxiomTools.tools.setBlockRenderOverride(x, y, z, next);
        }
    }

    /** Whether the gradient may change a block, or put one in, as a whole: a full cube with no block entity. */
    private static boolean paintable(BlockGetter level, BlockPos pos, BlockState s) {
        return !(s.getBlock() instanceof EntityBlock) && s.isCollisionShapeFullBlock(level, pos);
    }

    private void clearPreview() {
        change.clear();
        planned.clear();
        touched.clear();
        ends.clear();
        covered = 0;
        leftAlone = 0;
        capped = false;
        previewFor = null;
        if (overriding) {
            AxiomTools.tools.releaseChunkRenderOverrider(PREVIEW);
            overriding = false;
        }
    }

    /**
     * Sends the change to Axiom as one entry in its history, and starts over. A block that has changed since the
     * preview was made, as when another player broke it or put a chest there, is left as it now is, so nothing fills
     * a gap or replaces a block the preview didn't show. False when there's nothing to change.
     */
    private boolean apply() {
        ClientLevel level = Minecraft.getInstance().level;
        if (planned.isEmpty() || level == null) return false;
        change.clear();
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (Planned p : planned) if (level.getBlockState(pos.set(p.x(), p.y(), p.z())) == p.was()) change.addBlock(p.x(), p.y(), p.z(), p.next());
        boolean any = !change.isEmpty();
        if (any) AxiomTools.tools.pushBlockRegionChange(change);
        reset();
        return any;
    }

    @Override
    public void displayImguiOptions() {
        List<Choice> all = choices();
        Choice shown = current(all);

        ImGui.text(tr("axiom.chromastudio.gradient_tool.gradient"));
        if (all.isEmpty()) {
            ImGui.textWrapped(tr("axiom.chromastudio.gradient_tool.none"));
            // The window opens at the next tick, after Axiom has finished drawing its panels this frame.
            if (ImGui.button(tr("key.chromastudio.open"))) ChromaStudioClient.openWindowSoon("gradient");
        } else {
            // As tall as the list, up to six rows, so a short list leaves room for the controls below it.
            float rows = Math.min(all.size(), 6) * ImGui.getTextLineHeightWithSpacing() + ImGui.getStyle().getFramePaddingY() * 2;
            if (ImGui.beginListBox("##gradients", -1, rows)) {
                for (int i = 0; i < all.size(); i++) {
                    Choice c = all.get(i);
                    // Choices are made afresh each frame, so the one on show is found by its key.
                    if (ImGui.selectable(c.label() + "##" + i, shown != null && c.key().equals(shown.key()))) picked = c.key();
                }
                ImGui.endListBox();
            }
            if (shown != null) {
                swatches(shown.steps());
                int usable = states(shown).size();
                if (usable < 2) ImGui.textWrapped(tr("axiom.chromastudio.gradient_tool.missing"));
                else if (usable < shown.steps().size()) ImGui.textWrapped(tr("axiom.chromastudio.gradient_tool.skipped", shown.steps().size() - usable));
            }
        }

        ImGui.separator();
        ImGui.sliderInt(tr("axiom.chromastudio.gradient_tool.blend"), blend, 0, 100, "%d%%");
        // ImGui reads a tooltip as a printf format, so the percent signs the tip shows are doubled for it.
        ImGui.setItemTooltip(tr("axiom.chromastudio.gradient_tool.blend.tip").replace("%", "%%"));
        if (ImGui.checkbox(tr("axiom.chromastudio.gradient_tool.like_first"), likeFirst)) likeFirst = !likeFirst;
        ImGui.setItemTooltip(tr("axiom.chromastudio.gradient_tool.like_first.tip"));
        ImGui.sliderInt(tr("axiom.chromastudio.gradient_tool.reach"), reach, 1, MAX_REACH);
        ImGui.setItemTooltip(tr("axiom.chromastudio.gradient_tool.reach.tip"));

        ImGui.separator();
        ImGui.beginDisabled(change.isEmpty());
        if (ImGui.button(tr("axiom.chromastudio.gradient_tool.apply"))) apply();
        ImGui.endDisabled();
        ImGui.setItemTooltip(tr(change.isEmpty() ? "axiom.chromastudio.gradient_tool.apply.none" : "axiom.chromastudio.gradient_tool.apply.tip"));
        ImGui.sameLine();
        ImGui.beginDisabled(second == null);
        if (ImGui.button(tr("axiom.chromastudio.gradient_tool.swap"))) {
            BlockPos was = first;
            first = second;
            second = was;
        }
        ImGui.endDisabled();
        ImGui.setItemTooltip(tr(second == null ? "axiom.chromastudio.gradient_tool.swap.none" : "axiom.chromastudio.gradient_tool.swap.tip"));
        ImGui.sameLine();
        ImGui.beginDisabled(first == null);
        if (ImGui.button(tr("axiom.chromastudio.gradient_tool.cancel"))) reset();
        ImGui.endDisabled();
        ImGui.setItemTooltip(tr(first == null ? "axiom.chromastudio.gradient_tool.cancel.none" : "axiom.chromastudio.gradient_tool.cancel.tip"));
        if (first == null) ImGui.textWrapped(tr("axiom.chromastudio.gradient_tool.start"));
        else if (second == null) ImGui.textWrapped(tr("axiom.chromastudio.gradient_tool.end"));
        else {
            int steps = shown == null ? 0 : states(shown).size();
            double length = Math.sqrt(first.distSqr(second)) + 1;
            if (steps >= 2) ImGui.textWrapped(tr("axiom.chromastudio.gradient_tool.length", Math.round(length), String.format("%.1f", length / steps)));
            ImGui.textWrapped(tr("axiom.chromastudio.gradient_tool.count", covered, change.count()));
            if (leftAlone > 0) ImGui.textWrapped(tr("axiom.chromastudio.gradient_tool.left_alone", leftAlone));
            if (capped) ImGui.textWrapped(tr("axiom.chromastudio.gradient_tool.capped", CAP));
            ImGui.textWrapped(tr("axiom.chromastudio.gradient_tool.adjust"));
        }
    }

    /** The gradient's blocks as a row of their colours, each named when hovered. */
    private static void swatches(List<PaletteEntry> steps) {
        // As many to a row as the panel is wide, so a narrow panel wraps them rather than cutting the last off.
        float size = ImGui.getFrameHeight(), gap = ImGui.getStyle().getItemSpacingX();
        int perRow = Math.max(1, (int) ((ImGui.getContentRegionAvailX() + gap) / (size + gap)));
        int column = 0;
        for (int i = 0; i < steps.size(); i++) {
            PaletteEntry e = steps.get(i);
            Color c = PaletteBlocks.colour(e);
            if (c == null) continue;
            if (column % perRow != 0) ImGui.sameLine();
            ImGui.colorButton("##step" + i, c.red() / 255f, c.green() / 255f, c.blue() / 255f, 1f, ImGuiColorEditFlags.NoTooltip);
            ImGui.setItemTooltip(PaletteBlocks.name(e).getString());
            column++;
        }
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

    private Choice current(List<Choice> all) {
        // The panel's choice, while it's still in the list: the player may have deleted it in Chroma Studio's window.
        for (Choice c : all) if (c.key().equals(picked)) return c;
        return all.isEmpty() ? null : all.getFirst();
    }

    /**
     * The blocks that count as like the first block: those of its material, such as stone bricks with their stairs,
     * slabs and walls, so a build's trim takes the gradient along with its walls.
     */
    private static Set<Block> like(BlockState start) {
        Set<Block> out = new HashSet<>();
        out.add(start.getBlock());
        BlockIndex index = BlockIndex.get();
        Entry e = index.entryOf(start);
        if (e != null) for (Entry m : index.materialSet(e)) out.add(m.block());
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
