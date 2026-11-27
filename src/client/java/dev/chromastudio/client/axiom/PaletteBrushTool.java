package dev.chromastudio.client.axiom;

import static dev.chromastudio.client.axiom.AxiomTools.tr;

import com.moulberry.axiomclientapi.CustomTool;
import com.moulberry.axiomclientapi.Effects;
import com.moulberry.axiomclientapi.IAxiomWorldRenderContext;
import com.moulberry.axiomclientapi.pathers.BallShape;
import com.moulberry.axiomclientapi.regions.BooleanRegion;
import dev.chromastudio.api.ChromaStudioApi;
import dev.chromastudio.client.ModSettings;
import dev.chromastudio.client.PaletteStore;
import dev.chromastudio.client.action.PaletteBlocks;
import dev.chromastudio.client.axiom.AxiomBridge.Refused;
import dev.chromastudio.client.index.BlockIndex;
import dev.chromastudio.core.color.Color;
import dev.chromastudio.core.palette.BuildSearch;
import dev.chromastudio.core.palette.Palette;
import dev.chromastudio.core.palette.PaletteEntry;
import dev.chromastudio.core.palette.QueuedChanges;
import dev.chromastudio.core.palette.Recolour;
import dev.chromastudio.core.palette.WeightedPick;
import imgui.moulberry92.ImGui;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

final class PaletteBrushTool implements CustomTool, GuardedTool.PutAway {
    private static final String FROM_BUILD = "build", FROM_ANY = "any";
    private static final int MAX_SHOWN = 32;

    private enum Mode { SCATTER, RECOLOUR }

    private static final class Edits {
        private Palette palette;
        private List<PaletteEntry> copy = List.of();
        private int count;

        int of(Palette p) {
            if (p != palette || !p.entries.equals(copy)) {
                palette = p;
                copy = List.copyOf(p.entries);
                count++;
            }
            return count;
        }
    }

    private final BrushStroke stroke = new BrushStroke();
    private final BooleanRegion outline = AxiomTools.regions.createBoolean();
    private final BooleanRegion one = AxiomTools.regions.createBoolean();
    private final BooleanRegion hovered = AxiomTools.regions.createBoolean();
    private @Nullable ClientLevel hoveredIn;
    private long hoveredVisit;
    private int outlineRadius = -1;

    private final int[] radius = {ModSettings.brushRadius};
    private Mode mode = Mode.valueOf(ModSettings.brushMode);
    private boolean wholeBuild = ModSettings.brushWholeBuild;
    private String paletteKey = ModSettings.brushPalette;
    private String fromKey = ModSettings.brushFrom;
    private Recolour.Mode match = Recolour.Mode.valueOf(ModSettings.brushMatch);

    private @Nullable Component said;
    private boolean dropped;
    private final AxiomPanel.Layout layout = new AxiomPanel.Layout(true);
    private @Nullable Component refused;
    private @Nullable FoundBuild found;
    private List<AxiomPanel.Swatch> foundSwatches = List.of();

    private final BuildPreview preview = new BuildPreview("chromastudio_brush");
    private @Nullable BlockPos picked;
    private @Nullable ClientLevel pickedIn;
    private @Nullable Object previewFor;
    private final Edits toEdits = new Edits(), fromEdits = new Edits();
    private int kindsBefore, kindsAfter, masked, kept, receipt;
    private final Bound paletteBound = new Bound(), fromBound = new Bound();

    private List<Palette> listed = List.of();
    private List<String> listedNames = List.of(), labels = List.of(), fromLabels = List.of(), keys = List.of();
    private String listedLanguage = "";
    private @Nullable Palette swatched;
    private List<PaletteEntry> swatchedEntries = List.of();
    private int swatchedVersion = -1;
    private List<AxiomPanel.Swatch> swatches = List.of();
    private @Nullable ScatterBlocks scattered;
    private boolean anyInGame;

    PaletteBrushTool() {
        one.add(0, 0, 0);
    }

    @Override
    public String name() { return tr("axiom.chromastudio.brush"); }

    @Override
    public void reset() {
        if (stroke.active()) dropped = true;
        stroke.cancel();
        picked = null;
        pickedIn = null;
        refused = null;
        clearPreview();
        forgetHover();
    }

    private void forgetHover() {
        hovered.clear();
        hoveredIn = null;
    }

    @Override
    public void putAway() {
        stroke.away();
        clearPreview();
        forgetHover();
    }

    private void clearPreview() {
        preview.clear();
        previewFor = null;
    }

    @Override
    public boolean callUseTool() {
        if (AxiomTools.off()) return false;
        stroke.cancel();
        Palette to = current();
        if (to == null || !ChromaStudioApi.indexReady()) return false;
        ClientLevel level = Minecraft.getInstance().level;
        if (level == null) return false;
        QueuedChanges<BlockState> queue = AxiomChanges.queue(level);
        said = null;
        refused = null;
        receipt = 0;
        stroke.forget();
        if (wholeBuild()) {
            BlockHitResult hit = AxiomTools.tools.raycastBlock();
            if (hit == null || hit.getType() != HitResult.Type.BLOCK) return false;
            BlockPos at = hit.getBlockPos().immutable();
            if (!FoundBuild.member(level, at, level.getBlockState(at))) {
                said = Component.translatable("axiom.chromastudio.not_build");
                return false;
            }
            picked = at;
            pickedIn = level;
            clearPreview();
            return true;
        }
        BlockHitResult hit = AxiomTools.tools.raycastBlock();
        BlockPos at = hit == null || hit.getType() != HitResult.Type.BLOCK ? null : hit.getBlockPos().immutable();
        if (mode == Mode.RECOLOUR && fromKey.equals(FROM_BUILD) && at == null) {
            said = Component.translatable("axiom.chromastudio.not_build");
            return false;
        } else if (mode == Mode.RECOLOUR && fromGone()) {
            said = Component.translatable("axiom.chromastudio.brush.from_gone");
            return false;
        }
        long seed = System.nanoTime();
        int size = AxiomPanel.radius(radius);
        BrushStroke.Setup setup = lvl -> painterNow(lvl, at, seed, size);
        try {
            return stroke.start(level, queue, size, setup);
        } catch (Refused r) {
            said = r.reason();
            return false;
        }
    }

    private static BrushStroke.Painter recolour(Level level, RecolourPlan plan, @Nullable Set<Long> inBuild) throws Refused {
        if (plan.size() == 0) throw new Refused(Component.translatable("axiom.chromastudio.nothing_to_change"));
        return (x, y, z, _, admit, put) -> plan.change(level, x, y, z,
                (ax, ay, az) -> admit.test(ax, ay, az) && (inBuild == null || inBuild.contains(BuildSearch.pack(ax, ay, az))), put);
    }

    private BrushStroke.@Nullable Painter painterNow(Level lvl, @Nullable BlockPos at, long seed, int size) throws Refused {
        Palette to = current();
        if (to == null || wholeBuild() || AxiomPanel.radius(radius) != size) return null;
        if (mode == Mode.SCATTER) {
            List<ScatterBlocks.Placed> blocks = ScatterBlocks.of(to).blocks();
            if (blocks.isEmpty()) return null;
            WeightedPick<ScatterBlocks.Placed> scatter = new WeightedPick<>(blocks, ScatterBlocks.Placed::weight);
            return (x, y, z, now, _, put) -> {
                if (ScatterBlocks.paintsOver(now)) put.put(x, y, z, scatter.at(seed, x, y, z).state());
            };
        }
        if (fromKey.equals(FROM_BUILD)) {
            FoundBuild build = at == null ? null : FoundBuild.from(lvl, at);
            if (build == null) throw new Refused(Component.translatable("axiom.chromastudio.not_build"));
            showFound(build);
            Set<Long> inBuild = new HashSet<>();
            for (long b : build.blocks()) inBuild.add(b);
            return recolour(lvl, RecolourPlan.of(to, build.kinds(), match), inBuild);
        }
        if (fromGone()) throw new Refused(Component.translatable("axiom.chromastudio.brush.from_gone"));
        return recolour(lvl, RecolourPlan.of(to, from(), match), null);
    }

    private void showFound(FoundBuild build) {
        found = build;
        List<AxiomPanel.Swatch> out = AxiomPanel.Swatch.of(build.kinds().entries);
        out.sort(Comparator.comparingDouble(s -> s.colour().oklab().l()));
        foundSwatches = out.subList(0, Math.min(out.size(), MAX_SHOWN));
    }

    @Override
    public boolean callConfirm() { return !AxiomTools.off() && wholeBuild() && apply(); }

    @Override
    public boolean callDelete() {
        if (!wholeBuild() || picked == null) return false;
        cancel();
        return true;
    }

    void tick() {
        stroke.tick();
        if (preview.tick()) previewFor = null;
        current();
        from();
    }

    private boolean wholeBuild() { return mode == Mode.RECOLOUR && wholeBuild; }

    @Override
    public void render(IAxiomWorldRenderContext rc) {
        if (AxiomTools.off()) return;
        ClientLevel level = Minecraft.getInstance().level;
        if (level == null) return;
        if (wholeBuild()) {
            renderBuild(rc, level);
            return;
        }
        if (stroke.active()) {
            dropped = false;
            stroke.render(rc);
            return;
        }
        BlockHitResult hit = AxiomTools.tools.raycastBlock();
        if (hit == null || hit.getType() != HitResult.Type.BLOCK) return;
        int r = AxiomPanel.radius(radius);
        if (outlineRadius != r) {
            outlineRadius = r;
            outline.clear();
            BallShape.SPHERE.fillRegion(outline, outlineRadius);
        }
        outline.render(rc, Vec3.atLowerCornerOf(hit.getBlockPos()), Effects.OUTLINE);
    }

    private void renderBuild(IAxiomWorldRenderContext rc, ClientLevel level) {
        if (picked != null && pickedIn != level) reset();
        Palette to = current();
        Palette from = from();
        Object key = picked == null || to == null ? null : Arrays.asList(picked, to, toEdits.of(to), fromKey,
                from == null ? 0 : fromEdits.of(from), match, BlockIndex.get().version());
        if (refused == null && !Objects.equals(key, previewFor)) {
            makePreview(level);
            previewFor = key;
        }
        if (picked == null) {
            BlockHitResult hit = AxiomTools.tools.raycastBlock();
            if (hit == null || hit.getType() != HitResult.Type.BLOCK) {
                forgetHover();
                return;
            }
            BlockPos at = hit.getBlockPos();
            if (!FoundBuild.member(level, at, level.getBlockState(at))) {
                forgetHover();
                one.render(rc, Vec3.atLowerCornerOf(at), Effects.BLUE | Effects.OUTLINE);
                return;
            }
            if (hoveredIn != level || hoveredVisit != AxiomTools.visit() || !hovered.contains(at.getX(), at.getY(), at.getZ())) hover(level, at);
            hovered.render(rc, Vec3.ZERO, Effects.OUTLINE);
        } else {
            preview.render(rc);
        }
    }

    private void hover(ClientLevel level, BlockPos at) {
        hovered.clear();
        hoveredIn = level;
        hoveredVisit = AxiomTools.visit();
        for (long p : FoundBuild.blocks(level, at).blocks()) hovered.add(BuildSearch.x(p), BuildSearch.y(p), BuildSearch.z(p));
    }

    void makePreview(ClientLevel level) {
        preview.clear();
        if (picked == null || current() == null || !ChromaStudioApi.indexReady()) return;
        QueuedChanges<BlockState> queue = AxiomChanges.queue(level);
        said = null;
        if (fromGone()) {
            said = Component.translatable("axiom.chromastudio.brush.from_gone");
            return;
        }
        AxiomMask.Test mask = AxiomMask.now(level);
        WholeBuild made;
        try {
            made = wholeBuildNow(level, mask, queue);
        } catch (Refused r) {
            said = r.reason();
            return;
        }
        showFound(made.build());
        if (mask.failed()) {
            said = AxiomMask.unavailable().orElse(AxiomChanges.refusal(QueuedChanges.Refusal.MASK_FAILED));
            return;
        }
        masked = made.masked();
        kindsBefore = made.kindsBefore();
        kindsAfter = made.kindsAfter();
        kept = kept(level, made.build());
        boolean shown = preview.show(queue, mask, made.outlined(), again -> {
            QueuedChanges<BlockState> redone = new QueuedChanges<>(queue.origin());
            try {
                wholeBuildNow(level, again, redone);
            } catch (Refused _) {
                return redone;
            }
            return redone;
        });
        if (!shown) said = Component.translatable("axiom.chromastudio.preview.busy");
    }

    private WholeBuild wholeBuildNow(Level level, QueuedChanges.Mask mask, QueuedChanges<BlockState> into) throws Refused {
        Palette to = current();
        boolean fromBuild = fromKey.equals(FROM_BUILD);
        if (picked == null || to == null || !wholeBuild()) throw new Refused(Component.translatable("axiom.chromastudio.not_build"));
        if (fromGone()) throw new Refused(Component.translatable("axiom.chromastudio.brush.from_gone"));
        return wholeBuild(level, picked, to, fromBuild ? null : from(), fromBuild, match, mask, into);
    }

    private int kept(Level level, FoundBuild build) {
        Palette to = current();
        if (to == null) return 0;
        RecolourPlan plan;
        try {
            plan = RecolourPlan.of(to, fromKey.equals(FROM_BUILD) ? build.kinds() : from(), match);
        } catch (Refused _) {
            return 0;
        }
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        int n = 0;
        for (long p : build.blocks()) if (plan.keeps(level.getBlockState(pos.set(BuildSearch.x(p), BuildSearch.y(p), BuildSearch.z(p))))) n++;
        return n;
    }

    record WholeBuild(FoundBuild build, long[] outlined, int masked, int kindsBefore, int kindsAfter) {}

    static WholeBuild wholeBuild(Level level, BlockPos at, Palette to, @Nullable Palette from, boolean fromBuild, Recolour.Mode match,
                                 QueuedChanges.Mask mask, QueuedChanges<BlockState> into) throws Refused {
        FoundBuild build = FoundBuild.from(level, at);
        if (build == null) throw new Refused(Component.translatable("axiom.chromastudio.not_build"));
        RecolourPlan whole = RecolourPlan.of(to, fromBuild ? build.kinds() : from, match);
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        Set<Block> before = new HashSet<>(), after = new HashSet<>();
        LongArrayList outlined = new LongArrayList();
        int masked = 0;
        Set<Long> inBuild = new HashSet<>();
        for (long p : build.blocks()) inBuild.add(p);
        RecolourPlan.Admit admit = (x, y, z) -> mask.allows(x, y, z) && inBuild.contains(BuildSearch.pack(x, y, z));
        for (long p : build.blocks()) {
            int x = BuildSearch.x(p), y = BuildSearch.y(p), z = BuildSearch.z(p);
            BlockState now = level.getBlockState(pos.set(x, y, z));
            before.add(now.getBlock());
            boolean allowed = mask.allows(x, y, z);
            BlockState plain = whole.apply(now);
            boolean changes = plain != null && plain != now;
            if (changes && !allowed) {
                masked++;
                changes = false;
            }
            BlockState next = changes ? whole.apply(level, x, y, z, admit) : null;
            if (next == null) {
                after.add(now.getBlock());
                into.leave(x, y, z, now);
                continue;
            }
            after.add(next.getBlock());
            outlined.add(p);
            into.add(x, y, z, now, next);
        }
        return new WholeBuild(build, outlined.toLongArray(), masked, before.size(), after.size());
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
        List<Palette> all = palettes();
        Palette shown = current();
        boolean ready = ChromaStudioApi.indexReady();
        boolean recolour = mode == Mode.RECOLOUR;

        AxiomPanel.heading(tr("axiom.chromastudio.brush.palette"));
        int clicked = AxiomPanel.list(layout, "##palettes", labels, all.indexOf(shown));
        if (clicked >= 0) {
            String key = keys.get(clicked);
            Palette p = all.get(clicked);
            layout.later(() -> {
                paletteKey = key;
                paletteBound.choose(p, key);
                remember();
            });
        }
        if (shown != null) {
            readSwatches(shown);
            AxiomPanel.swatches("swatch", swatches);
        }

        AxiomPanel.heading(tr("axiom.chromastudio.heading.brush"));
        if (AxiomPanel.radio(tr("axiom.chromastudio.brush.scatter"), mode == Mode.SCATTER, ready,
                tr(ready ? "axiom.chromastudio.brush.scatter.tip" : "axiom.chromastudio.not_ready"))) layout.later(() -> setMode(Mode.SCATTER));
        AxiomPanel.besideChoice(tr("axiom.chromastudio.brush.recolour"));
        if (AxiomPanel.radio(tr("axiom.chromastudio.brush.recolour"), recolour, ready,
                tr(ready ? "axiom.chromastudio.brush.recolour.tip" : "axiom.chromastudio.not_ready"))) layout.later(() -> setMode(Mode.RECOLOUR));
        if (AxiomPanel.radius(radius, !wholeBuild(), wholeBuild() ? tr("axiom.chromastudio.radius.whole") : "")) remember();
        if (mode == Mode.SCATTER && anyInGame && scattered != null && !scattered.leftOut().isEmpty()) {
            String names = scattered.leftOut().stream().map(e -> PaletteBlocks.name(e).getString()).collect(Collectors.joining(", "));
            String line = tr("axiom.chromastudio.brush.left_out", names), fitted = AxiomPanel.fit(line, ImGui.getContentRegionAvailX());
            AxiomPanel.note(fitted);
            AxiomPanel.tip(fitted.equals(line) ? tr("axiom.chromastudio.brush.left_out.tip") : line + "\n" + tr("axiom.chromastudio.brush.left_out.tip"));
        }

        AxiomPanel.heading(tr("axiom.chromastudio.brush.recolour"));
        if (AxiomPanel.checkbox(tr("axiom.chromastudio.whole_build"), wholeBuild, recolour,
                tr(recolour ? "axiom.chromastudio.brush.whole.tip" : "axiom.chromastudio.from.na"))) {
            layout.later(() -> {
                wholeBuild = !wholeBuild;
                said = null;
                receipt = 0;
                stroke.forget();
                reset();
                remember();
            });
        }
        recolourOptions(all, recolour);
        if (recolour && fromKey.equals(FROM_BUILD) && found != null) {
            String label = wholeBuild() && picked != null && said == null && refused == null
                    ? tr("axiom.chromastudio.brush.materials.kinds", kindsBefore, kindsAfter) : tr("axiom.chromastudio.brush.materials");
            if (AxiomPanel.disclosure(label, "materials")) AxiomPanel.swatches("found", foundSwatches);
            AxiomPanel.tip(tr("axiom.chromastudio.brush.materials.tip"));
        }
    }

    private void bottom() {
        AxiomPanel.Status status = status();
        AxiomPanel.status(status);
        if (!wholeBuild()) return;
        AxiomPanel.applyCancel(!preview.isEmpty(), status != null && preview.isEmpty() && !status.receipt() ? status.text() : tr("axiom.chromastudio.brush.apply.none"),
                picked != null, this::apply, () -> layout.later(this::cancel), null);
    }

    private AxiomPanel.@Nullable Status status() {
        Optional<AxiomPanel.Status> masks = AxiomPanel.masks();
        if (masks.isPresent()) return masks.get();
        if (!ChromaStudioApi.indexReady()) return AxiomPanel.Status.of(tr("axiom.chromastudio.not_ready"));
        Palette to = current();
        if (to == null) return AxiomPanel.Status.of(tr("axiom.chromastudio.brush.choose"));
        readSwatches(to);
        if (!anyInGame) return AxiomPanel.Status.of(tr(to.entries.isEmpty() ? "axiom.chromastudio.brush.no_entries" : "axiom.chromastudio.brush.empty"));
        if (mode == Mode.SCATTER && scattered != null && scattered.blocks().isEmpty()) return AxiomPanel.Status.of(tr("axiom.chromastudio.brush.scatter_none"));
        if (mode == Mode.RECOLOUR && fromGone()) return AxiomPanel.Status.of(tr("axiom.chromastudio.brush.from_gone"));
        Component why = wholeBuild() ? refused : stroke.refused();
        if (why == null && dropped && !wholeBuild()) why = AxiomChanges.refusal(QueuedChanges.Refusal.EDITOR);
        if (why != null) return AxiomPanel.refusal(why, wholeBuild() ? "axiom.chromastudio.again.build" : "axiom.chromastudio.again.brush");
        if (said != null) return AxiomPanel.Status.of(said);
        int sent = wholeBuild() ? receipt : stroke.sent();
        if (sent > 0 && (!wholeBuild() || picked == null)) return AxiomPanel.Status.receipt(AxiomTools.count("axiom.chromastudio.changed", sent, sent));
        if (!wholeBuild()) return null;
        if (picked == null) return AxiomPanel.Status.of(tr("axiom.chromastudio.brush.pick"));
        if (found == null) return null;
        int total = found.blocks().length;
        String count = AxiomTools.count("axiom.chromastudio.count", total, preview.count(), total);
        AxiomPanel.Status s = AxiomPanel.Status.of(count);
        if (kept > 0) s = s.withTip(AxiomTools.count("axiom.chromastudio.brush.kept", kept, kept));
        s = s.withTip(tr("axiom.chromastudio.brush.kinds", kindsBefore, kindsAfter));
        if (masked > 0) s = s.withTip(AxiomTools.count("axiom.chromastudio.masked", masked, masked));
        if (found.capped()) s = s.withTip(tr("axiom.chromastudio.capped", FoundBuild.CAP));
        return s;
    }

    private void cancel() {
        receipt = 0;
        reset();
    }

    private void setMode(Mode m) {
        if (mode == m) return;
        mode = m;
        said = null;
        receipt = 0;
        stroke.forget();
        reset();
        remember();
    }

    private void recolourOptions(List<Palette> all, boolean recolour) {
        ImGui.beginDisabled(!recolour);
        Palette chosen = from();
        String shown = fromKey.equals(FROM_BUILD) ? tr("axiom.chromastudio.from.build") : fromKey.equals(FROM_ANY) ? tr("axiom.chromastudio.from.any")
                : chosen == null ? tr("axiom.chromastudio.brush.from_choose") : fromLabels.get(all.indexOf(chosen));
        String fitted = AxiomPanel.fit(shown, AxiomPanel.comboWidth());
        if (ImGui.beginCombo(tr("axiom.chromastudio.from") + "##from", fitted)) {
            if (ImGui.selectable(tr("axiom.chromastudio.from.build") + "##build", fromKey.equals(FROM_BUILD))) layout.later(() -> setFrom(FROM_BUILD));
            if (ImGui.selectable(tr("axiom.chromastudio.from.any") + "##any", fromKey.equals(FROM_ANY))) layout.later(() -> setFrom(FROM_ANY));
            for (int i = 0; i < all.size(); i++) {
                Palette p = all.get(i);
                String key = keys.get(i);
                if (ImGui.selectable(fromLabels.get(i) + "##from" + i, p == chosen)) layout.later(() -> {
                    fromBound.choose(p, key);
                    setFrom(key);
                });
            }
            ImGui.endCombo();
        }
        ImGui.endDisabled();
        String tip = tr(recolour ? "axiom.chromastudio.brush.from.tip" : "axiom.chromastudio.from.na");
        AxiomPanel.tip(fitted.equals(shown) ? tip : shown + "\n" + tip);
        for (Recolour.Mode m : Recolour.Mode.values()) {
            String label = tr("axiom.chromastudio.match." + m.name().toLowerCase(Locale.ROOT));
            if (m.ordinal() > 0) AxiomPanel.besideChoice(label);
            if (AxiomPanel.radio(label, match == m, recolour,
                    tr(recolour ? "axiom.chromastudio.match.tip" : "axiom.chromastudio.from.na"))) {
                layout.later(() -> {
                    match = m;
                    remember();
                });
            }
        }
    }

    private void setFrom(String key) {
        fromKey = key;
        if (!key.equals(FROM_BUILD)) found = null;
        remember();
    }

    private List<Palette> palettes() {
        List<Palette> all = PaletteStore.get().all();
        List<String> names = all.stream().map(p -> p.name).toList();
        String language = Minecraft.getInstance().getLanguageManager().getSelected();
        if (!same(all, listed) || !names.equals(listedNames) || !language.equals(listedLanguage)) {
            listed = all;
            listedNames = names;
            listedLanguage = language;
            labels = all.stream().map(PaletteStore.get()::shownName).toList();
            keys = keys(all);
            fromLabels = all.stream().map(AxiomBridge::fromName).toList();
            swatched = null;
        }
        return listed;
    }

    private static boolean same(List<Palette> a, List<Palette> b) {
        if (a.size() != b.size()) return false;
        for (int i = 0; i < a.size(); i++) if (a.get(i) != b.get(i)) return false;
        return true;
    }

    private void readSwatches(Palette p) {
        int version = BlockIndex.get().ready() ? BlockIndex.get().version() : -1;
        if (p == swatched && p.entries.equals(swatchedEntries) && version == swatchedVersion) return;
        swatched = p;
        swatchedEntries = List.copyOf(p.entries);
        swatchedVersion = version;
        List<AxiomPanel.Swatch> out = new ArrayList<>();
        anyInGame = false;
        for (PaletteEntry e : p.entries) {
            anyInGame |= PaletteBlocks.state(e) != null;
            Color c = PaletteBlocks.colour(e);
            if (c == null) continue;
            String name = PaletteBlocks.name(e).getString();
            out.add(new AxiomPanel.Swatch(c, e.weight() > 1 ? tr("axiom.chromastudio.brush.weight", name, e.weight()) : name));
        }
        swatches = out;
        scattered = version < 0 ? null : ScatterBlocks.of(p);
    }

    private @Nullable Palette current() {
        List<Palette> all = palettes();
        Palette bound = paletteBound.find(paletteKey, all, keys);
        if (bound != null) {
            if (!paletteBound.key().equals(paletteKey)) {
                paletteKey = paletteBound.key();
                remember();
            }
            return bound;
        }
        Palette selected = PaletteStore.get().selected();
        return selected != null ? selected : all.isEmpty() ? null : all.getFirst();
    }

    private boolean fromGone() { return !fromKey.equals(FROM_BUILD) && !fromKey.equals(FROM_ANY) && from() == null; }

    private @Nullable Palette from() {
        Palette bound = fromBound.find(fromKey, palettes(), keys);
        if (bound != null && !fromBound.key().equals(fromKey)) {
            fromKey = fromBound.key();
            remember();
        }
        return bound;
    }

    private static final class Bound {
        private @Nullable String key;
        private @Nullable Palette palette;

        @Nullable Palette find(String wanted, List<Palette> all, List<String> keys) {
            if (!wanted.equals(key)) {
                key = wanted;
                int i = keys.indexOf(wanted);
                palette = i < 0 ? null : all.get(i);
                return palette;
            }
            for (int i = 0; i < all.size(); i++) {
                if (all.get(i) == palette) {
                    key = keys.get(i);
                    return palette;
                }
            }
            return null;
        }

        void choose(Palette p, String chosen) {
            key = chosen;
            palette = p;
        }

        String key() { return Objects.requireNonNull(key); }
    }

    private static List<String> keys(List<Palette> all) {
        List<String> out = new ArrayList<>();
        for (Palette p : all) {
            String preset = PaletteStore.get().presetId(p), file = PaletteStore.get().fileName(p);
            out.add(preset != null ? "preset:" + preset : file != null ? "file:" + file + ":" + p.name : "own:" + p.name);
        }
        return out;
    }

    private void remember() {
        ModSettings.brushPalette = paletteKey;
        ModSettings.brushMode = mode.name();
        ModSettings.brushWholeBuild = wholeBuild;
        ModSettings.brushFrom = fromKey;
        ModSettings.brushMatch = match.name();
        ModSettings.brushRadius = radius[0];
        ModSettings.save();
    }
}
