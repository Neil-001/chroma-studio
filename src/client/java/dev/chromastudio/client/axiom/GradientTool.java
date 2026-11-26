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
import dev.chromastudio.core.palette.BuildSearch;
import dev.chromastudio.core.palette.GradientLine;
import dev.chromastudio.core.palette.PaletteEntry;
import dev.chromastudio.core.palette.QueuedChanges;
import dev.chromastudio.core.palette.StoredGradient;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.EmptyBlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

final class GradientTool implements CustomTool, GuardedTool.PutAway {
    private static final int MAX_REACH = 64;
    private static final String ON_TAB = "";
    private static final int STATUS_LINES = 5;

    private record Choice(String key, String label, List<PaletteEntry> steps) {}

    private final BuildPreview preview = new BuildPreview("chromastudio_gradient");
    private final AxiomPanel.Layout layout = new AxiomPanel.Layout(true);
    private final BooleanRegion ends = AxiomTools.regions.createBoolean();
    private final BooleanRegion one = AxiomTools.regions.createBoolean();

    private String picked;
    private final int[] blend = {75};
    private boolean likeFirst = true;
    private final int[] reach = {16};
    private @Nullable BlockPos first, second;
    private @Nullable BlockPos seed;
    private @Nullable ClientLevel endsIn;

    private boolean reversed;
    private BuildSearch.@Nullable Found found;
    private @Nullable GradientLine line;
    private @Nullable Object previewFor;
    private int covered, leftAlone, masked;
    private @Nullable Component refused;
    private @Nullable Search searched;
    private @Nullable Object searchedFor;
    private int receipt;

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
        reversed = false;
        refused = null;
        searched = null;
        searchedFor = null;
        clearPreview();
    }

    @Override
    public void putAway() {
        preview.clear();
        previewFor = null;
        line = null;
        covered = leftAlone = masked = 0;
    }

    private void clearPreview() {
        preview.clear();
        ends.clear();
        found = null;
        line = null;
        previewFor = null;
        covered = leftAlone = masked = 0;
    }

    @Override
    public boolean callUseTool() {
        if (AxiomTools.off()) return false;
        BlockHitResult hit = AxiomTools.tools.raycastBlock();
        ClientLevel level = Minecraft.getInstance().level;
        if (hit == null || hit.getType() != HitResult.Type.BLOCK || level == null) return false;
        if (endsIn != level) reset();
        endsIn = level;
        refused = null;
        receipt = 0;
        searched = null;
        clearPreview();
        BlockPos at = hit.getBlockPos().immutable();
        if (first == null) {
            first = at;
            seed = at;
        } else if (second == null) {
            if (!at.equals(first)) second = at;
        } else if (!at.equals(first) && !at.equals(second)) {
            boolean nearFirst = at.distSqr(first) <= at.distSqr(second);
            if ((nearFirst ? first : second).equals(seed)) seed = at;
            if (nearFirst) first = at;
            else second = at;
        }
        return true;
    }

    @Override
    public boolean callConfirm() { return !AxiomTools.off() && apply(); }

    @Override
    public boolean callDelete() {
        if (first == null) return false;
        cancel();
        return true;
    }

    void tick() {
        if (preview.tick()) previewFor = null;
    }

    @Override
    public void render(IAxiomWorldRenderContext rc) {
        if (AxiomTools.off()) return;
        ClientLevel level = Minecraft.getInstance().level;
        if (level == null) return;
        if (first != null && endsIn != level) reset();
        Choice choice = current(choices());
        Object key = Arrays.asList(choice == null ? null : choice.steps(), first, second, seed, reversed, likeFirst,
                AxiomPanel.held(reach, 1, MAX_REACH), AxiomPanel.held(blend, 0, 100), BlockIndex.get().version());
        if (refused == null && !key.equals(previewFor)) {
            preview(level);
            previewFor = key;
        }
        if (second == null) {
            BlockHitResult hit = AxiomTools.tools.raycastBlock();
            if (hit != null && hit.getType() == HitResult.Type.BLOCK) one.render(rc, Vec3.atLowerCornerOf(hit.getBlockPos()), Effects.BLUE | Effects.OUTLINE);
        } else {
            preview.render(rc);
        }
        if (first != null) ends.render(rc, Vec3.ZERO, Effects.BLUE | Effects.OUTLINE);
    }

    record Search(BuildSearch.Found found, GradientLine line) {}

    static Search search(BlockGetter level, BlockPos first, BlockPos second, BlockPos seed, boolean likeFirst, int reach) {
        GradientLine l = GradientLine.between(first.getX(), first.getY(), first.getZ(), second.getX(), second.getY(), second.getZ());
        Set<Block> like = like(level.getBlockState(seed));
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        BuildSearch.Found found = BuildSearch.from(likeFirst ? starts(level, seed, seed.equals(first) ? second : first) : new long[] {BuildSearch.pack(seed.getX(), seed.getY(), seed.getZ())},
                (x, y, z) -> {
                    BlockState s = level.getBlockState(pos.set(x, y, z));
                    if (s.isAir() || s.getBlock() instanceof LiquidBlock) return false;
                    return (!likeFirst || like.contains(s.getBlock())) && l.reaches(x, y, z, reach);
                }, FoundBuild.CAP);
        return new Search(found, l);
    }

    private static long[] starts(BlockGetter level, BlockPos from, BlockPos to) {
        int dx = to.getX() - from.getX(), dy = to.getY() - from.getY(), dz = to.getZ() - from.getZ();
        int n = Math.max(Math.abs(dx), Math.max(Math.abs(dy), Math.abs(dz)));
        boolean onGround = RecolourPlan.ground(level.getBlockState(from));
        LongArrayList out = new LongArrayList();
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int i = 0; i <= Math.min(n, FoundBuild.CAP); i++) {
            pos.set(from.getX() + (int) Math.round((double) dx * i / n), from.getY() + (int) Math.round((double) dy * i / n),
                    from.getZ() + (int) Math.round((double) dz * i / n));
            BlockState s = level.getBlockState(pos);
            if (s.isAir() || s.getBlock() instanceof LiquidBlock || !onGround && RecolourPlan.ground(s)) break;
            out.add(BuildSearch.pack(pos.getX(), pos.getY(), pos.getZ()));
        }
        return out.toLongArray();
    }

    record Laid(long[] outlined, int covered, int leftAlone, int masked) {}

    static Laid lay(BlockGetter level, Search s, List<BlockState> steps, boolean reversed, int blend, QueuedChanges.Mask mask,
                    QueuedChanges<BlockState> into) {
        GradientShapes shapes = new GradientShapes();
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        LongArrayList outlined = new LongArrayList();
        int covered = 0, leftAlone = 0, masked = 0;
        for (long p : s.found().blocks()) {
            int x = BuildSearch.x(p), y = BuildSearch.y(p), z = BuildSearch.z(p);
            BlockState now = level.getBlockState(pos.set(x, y, z));
            boolean allowed = mask.allows(x, y, z);
            int at = s.line().step(x, y, z, steps.size(), blend / 100.0);
            BlockState step = steps.get(reversed ? steps.size() - 1 - at : at);
            BlockState next = shapes.shaped(now) ? shapes.apply(step, now) : paintable(level, pos, now) ? step : null;
            if (next != null && !RecolourPlan.keepsWater(now, next)) next = null;
            if (next == null) {
                leftAlone++;
                into.leave(x, y, z, now);
                continue;
            }
            if (!allowed) {
                masked++;
                into.leave(x, y, z, now);
                continue;
            }
            covered++;
            outlined.add(p);
            if (next != now) into.add(x, y, z, now, next);
            else into.leave(x, y, z, now);
        }
        return new Laid(outlined.toLongArray(), covered, leftAlone, masked);
    }

    void preview(ClientLevel level) {
        clearPreview();
        if (first != null) ends.add(first.getX(), first.getY(), first.getZ());
        if (second != null) ends.add(second.getX(), second.getY(), second.getZ());
        QueuedChanges<BlockState> queue = AxiomChanges.queue(level);
        AxiomMask.Test mask = AxiomMask.now(level);
        Made made = layNow(level, mask, queue, true);
        if (made == null) return;
        found = made.search().found();
        line = made.search().line();
        if (mask.failed()) {
            refused = AxiomMask.unavailable().orElse(AxiomChanges.refusal(QueuedChanges.Refusal.MASK_FAILED));
            return;
        }
        covered = made.laid().covered();
        leftAlone = made.laid().leftAlone();
        masked = made.laid().masked();
        boolean shown = preview.show(queue, mask, made.laid().outlined(), again -> {
            QueuedChanges<BlockState> redone = new QueuedChanges<>(queue.origin());
            layNow(level, again, redone, false);
            return redone;
        });
        if (!shown) refused = Component.translatable("axiom.chromastudio.preview.busy");
    }

    private record Made(Search search, Laid laid) {}

    private @Nullable Made layNow(ClientLevel level, QueuedChanges.Mask mask, QueuedChanges<BlockState> into, boolean forPreview) {
        Choice choice = current(choices());
        List<BlockState> steps = choice == null ? List.of() : states(choice);
        if (steps.size() < 2 || first == null || second == null || seed == null || !BlockIndex.get().ready()) return null;
        int r = AxiomPanel.held(reach, 1, MAX_REACH), mix = AxiomPanel.held(blend, 0, 100);
        Search s;
        if (forPreview) {
            Object searchKey = Arrays.asList(level, first, second, seed, likeFirst, r, BlockIndex.get().version());
            if (searched == null || !searchKey.equals(searchedFor)) {
                searched = search(level, first, second, seed, likeFirst, r);
                searchedFor = searchKey;
            }
            s = searched;
        } else {
            s = search(level, first, second, seed, likeFirst, r);
        }
        return new Made(s, lay(level, s, steps, reversed, mix, mask, into));
    }

    static boolean paintable(BlockGetter level, BlockPos pos, BlockState s) {
        return GradientShapes.paintable(level, pos, s);
    }

    private boolean apply() {
        if (preview.isEmpty()) return false;
        int count = preview.count();
        Optional<Component> why = preview.apply();
        if (why.isPresent()) {
            refused = why.get();
            return false;
        }
        reset();
        receipt = count;
        return true;
    }

    @Override
    public void displayImguiOptions() { layout.draw(this::options, this::bottom); }

    private void options() {
        List<Choice> all = choices();
        Choice shown = current(all);
        AxiomPanel.steps(List.of(tr("axiom.chromastudio.gradient_tool.step.start"), tr("axiom.chromastudio.gradient_tool.step.end"),
                tr("axiom.chromastudio.gradient_tool.step.apply")), first == null ? 0 : second == null ? 1 : 2);
        AxiomPanel.heading(tr("axiom.chromastudio.gradient_tool.gradient"));
        if (all.isEmpty()) {
            AxiomPanel.note(tr("axiom.chromastudio.gradient_tool.none"));
        } else {
            boolean ready = BlockIndex.get().ready();
            List<String> labels = all.stream().map(c -> ready && states(c).size() < 2 ? tr("axiom.chromastudio.gradient_tool.unusable_mark", c.label()) : c.label()).toList();
            int clicked = AxiomPanel.list(layout, "##gradients", labels, all.indexOf(shown));
            if (clicked >= 0) {
                String key = all.get(clicked).key();
                layout.later(() -> picked = key);
            }
            if (shown != null) {
                List<AxiomPanel.Swatch> painted = AxiomPanel.Swatch.of(shown.steps());
                AxiomPanel.stripe("step", reversed ? painted.reversed() : painted, tr("axiom.chromastudio.gradient_tool.start_label"), tr("axiom.chromastudio.gradient_tool.end_label"));
            }
        }
        int usable = shown == null ? 0 : states(shown).size();
        if (usable >= 2 && usable < shown.steps().size()) {
            int left = shown.steps().size() - usable;
            AxiomPanel.note(AxiomTools.count("axiom.chromastudio.gradient_tool.steps_left", left, left));
            AxiomPanel.tip(tr("axiom.chromastudio.gradient_tool.steps_left.tip"));
        }
        if (AxiomPanel.button(tr("axiom.chromastudio.gradient_tool.open_tab"), true, tr("axiom.chromastudio.gradient_tool.open_tab.tip"))) {
            ChromaStudioClient.openWindowSoon("gradient");
        }

        AxiomPanel.heading(tr("axiom.chromastudio.heading.settings"));
        AxiomPanel.sliderAbove(tr("axiom.chromastudio.gradient_tool.blend"), blend, 0, 100, "%d%%", tr("axiom.chromastudio.gradient_tool.blend.tip"));
        int r = AxiomPanel.held(reach, 1, MAX_REACH);
        String reachShown = AxiomTools.count("axiom.chromastudio.gradient_tool.reach.value", r, "%d");
        AxiomPanel.sliderAbove(tr("axiom.chromastudio.gradient_tool.reach"), reach, 1, MAX_REACH, reachShown, tr("axiom.chromastudio.gradient_tool.reach.tip"));
        if (AxiomPanel.checkbox(tr("axiom.chromastudio.gradient_tool.like_first"), likeFirst, true, tr("axiom.chromastudio.gradient_tool.like_first.tip"))) {
            layout.later(() -> likeFirst = !likeFirst);
        }
        ClientLevel level = Minecraft.getInstance().level;
        if (likeFirst && seed != null && level != null && level == endsIn) {
            BlockState start = level.getBlockState(seed);
            AxiomPanel.soft(tr("axiom.chromastudio.gradient_tool.like", start.getBlock().getName()));
            if (!start.isCollisionShapeFullBlock(level, seed)) AxiomPanel.tip(tr("axiom.chromastudio.gradient_tool.like.tip"));
        } else if (likeFirst) {
            AxiomPanel.soft(tr("axiom.chromastudio.gradient_tool.like.any"));
        }
    }

    private void bottom() {
        float top = AxiomPanel.lineTop();
        AxiomPanel.Status problem = problem();
        AxiomPanel.Status status = problem != null ? problem : progress();
        AxiomPanel.markedStatus(status, problem != null);
        boolean applied = status != null && status.receipt();
        if (applied) {
            AxiomPanel.wrapped(tr("axiom.chromastudio.gradient_tool.start"));
            AxiomPanel.soft(tr("axiom.chromastudio.gradient_tool.undo"));
        } else {
            String next = next();
            if (!next.isEmpty()) AxiomPanel.soft(next);
        }
        AxiomPanel.holdLines(top, STATUS_LINES);
        boolean ready = BlockIndex.get().ready();
        String applyWhy = status != null && preview.isEmpty() && !applied ? status.text()
                : tr(applied ? "axiom.chromastudio.gradient_tool.start"
                : second != null && !ready ? "axiom.chromastudio.not_ready" : "axiom.chromastudio.gradient_tool.apply.none");
        AxiomPanel.applyCancel(!preview.isEmpty(), applyWhy, first != null, this::apply, () -> layout.later(this::cancel), () -> {
            AxiomPanel.besideButton(tr("axiom.chromastudio.gradient_tool.swap"));
            boolean canSwap = laid();
            String swapWhy = second == null ? tr("axiom.chromastudio.gradient_tool.swap.none") : canSwap || status == null ? "" : status.text();
            if (AxiomPanel.button(tr("axiom.chromastudio.gradient_tool.swap"), canSwap, swapWhy)) layout.later(() -> reversed = !reversed);
        }, true);
    }

    private AxiomPanel.@Nullable Status problem() {
        Optional<AxiomPanel.Status> masks = AxiomPanel.masks();
        if (masks.isPresent()) return masks.get();
        if (refused != null) return AxiomPanel.refusal(refused, "axiom.chromastudio.again.gradient");
        Choice shown = current(choices());
        if (shown == null) return AxiomPanel.Status.of(tr("axiom.chromastudio.gradient_tool.need"));
        if (BlockIndex.get().ready() && states(shown).size() < 2) {
            return AxiomPanel.Status.of(tr("axiom.chromastudio.gradient_tool.unusable")).withTip(tr("axiom.chromastudio.gradient_tool.unusable.tip"));
        }
        return null;
    }

    private AxiomPanel.@Nullable Status progress() {
        if (receipt > 0 && first == null) return AxiomPanel.Status.receipt(AxiomTools.count("axiom.chromastudio.changed", receipt, receipt));
        if (first == null) return AxiomPanel.Status.of(tr("axiom.chromastudio.gradient_tool.start"));
        if (second == null) return AxiomPanel.Status.of(tr("axiom.chromastudio.gradient_tool.end"));
        if (!BlockIndex.get().ready()) return AxiomPanel.Status.of(tr("axiom.chromastudio.not_ready"));
        if (line == null) return null;
        AxiomPanel.Status s = AxiomPanel.Status.of(AxiomTools.count("axiom.chromastudio.count", covered, preview.count(), covered));
        if (line.straightened()) s = s.withTip(tr("axiom.chromastudio.gradient_tool.straightened"));
        else if (line.slanted()) s = s.withTip(tr("axiom.chromastudio.gradient_tool.diagonal"));
        if (leftAlone > 0) s = s.withTip(AxiomTools.count("axiom.chromastudio.gradient_tool.left_alone", leftAlone, leftAlone));
        if (masked > 0) s = s.withTip(AxiomTools.count("axiom.chromastudio.masked", masked, masked));
        if (found != null && found.capped()) s = s.withTip(tr("axiom.chromastudio.capped", FoundBuild.CAP) + " " + tr("axiom.chromastudio.gradient_tool.capped"));
        return s;
    }

    private boolean laid() { return second != null && line != null && refused == null; }

    private String next() {
        if (!laid() || first == null) return "";
        Choice shown = current(choices());
        int steps = shown == null ? 0 : states(shown).size();
        if (steps < 2) return "";
        double length = Math.sqrt(first.distSqr(second)) + 1;
        int matching = covered - preview.count();
        String already = matching > 0 ? AxiomTools.count("axiom.chromastudio.gradient_tool.already", matching, matching) + "\n" : "";
        return already + tr("axiom.chromastudio.gradient_tool.length", Math.round(length), String.format("%.1f", length / steps)) + "\n"
                + tr("axiom.chromastudio.gradient_tool.adjust");
    }

    private void cancel() {
        receipt = 0;
        reset();
    }

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
        for (Choice c : all) if (c.key().equals(picked)) return c;
        return all.isEmpty() ? null : all.getFirst();
    }

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

    private static List<BlockState> states(Choice c) {
        List<BlockState> out = new ArrayList<>();
        for (PaletteEntry e : c.steps()) {
            BlockState s = PaletteBlocks.state(e);
            if (s != null && paintable(EmptyBlockGetter.INSTANCE, BlockPos.ZERO, s)) out.add(s);
        }
        return out;
    }
}
