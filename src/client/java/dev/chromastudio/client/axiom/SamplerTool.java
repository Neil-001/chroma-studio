package dev.chromastudio.client.axiom;

import static dev.chromastudio.client.axiom.AxiomTools.tr;

import com.moulberry.axiomclientapi.CustomTool;
import com.moulberry.axiomclientapi.Effects;
import com.moulberry.axiomclientapi.IAxiomWorldRenderContext;
import com.moulberry.axiomclientapi.pathers.BallShape;
import com.moulberry.axiomclientapi.pathers.ToolPatherUnique;
import com.moulberry.axiomclientapi.regions.BooleanRegion;
import dev.chromastudio.client.ChromaStudioClient;
import dev.chromastudio.client.PaletteStore;
import dev.chromastudio.client.action.PaletteBlocks;
import dev.chromastudio.client.index.BlockIndex;
import dev.chromastudio.core.color.Color;
import dev.chromastudio.core.palette.BuildSample;
import dev.chromastudio.core.palette.BuildSearch;
import dev.chromastudio.core.palette.Palette;
import dev.chromastudio.core.palette.PaletteEntry;
import imgui.moulberry92.ImGui;
import imgui.moulberry92.type.ImString;
import java.lang.ref.WeakReference;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

final class SamplerTool implements CustomTool {
    private static final int MANY = 24;
    private static final int ROWS = 6;

    private enum Mode { BRUSH, BUILD }

    private final BuildSample sample = new BuildSample();
    private final BooleanRegion sampled = AxiomTools.regions.createBoolean();
    private boolean sampledStale;
    private final BooleanRegion ball = AxiomTools.regions.createBoolean();
    private int ballRadius = -1;
    private final BooleanRegion one = AxiomTools.regions.createBoolean();

    private Mode mode = Mode.BRUSH;
    private final int[] radius = {3};
    private boolean ground;
    private ImString name;
    private String defaultName = "";
    private Palette savedAs;
    private WeakReference<ClientLevel> sampledIn = new WeakReference<>(null);
    private @Nullable Component said;
    private @Nullable String askedName;
    private final AxiomPanel.Layout layout = new AxiomPanel.Layout();

    private ToolPatherUnique pather;
    private ClientLevel strokeIn;

    SamplerTool() {
        one.add(0, 0, 0);
    }

    @Override
    public String name() { return tr("axiom.chromastudio.sampler"); }

    @Override
    public void reset() {
        pather = null;
        strokeIn = null;
    }

    @Override
    public boolean callUseTool() {
        if (AxiomTools.off()) return false;
        pather = null;
        if (!BlockIndex.get().ready()) {
            said = Component.translatable("axiom.chromastudio.not_ready");
            return false;
        }
        ClientLevel level = Minecraft.getInstance().level;
        if (level == null) return false;
        said = null;
        if (mode == Mode.BRUSH) {
            strokeIn = level;
            pather = AxiomTools.pathers.createUnique(AxiomPanel.radius(radius), BallShape.SPHERE);
            return true;
        }
        BlockHitResult hit = AxiomTools.tools.raycastBlock();
        if (hit == null || hit.getType() != HitResult.Type.BLOCK) return false;
        BlockPos at = hit.getBlockPos();
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        BuildSearch.Found found = BuildSearch.from(at.getX(), at.getY(), at.getZ(), (x, y, z) -> SampleKind.member(level.getBlockState(pos.set(x, y, z))), FoundBuild.CAP);
        int before = sampledIn.get() == level ? sample.total() : 0;
        for (long p : found.blocks()) add(level, pos, BuildSearch.x(p), BuildSearch.y(p), BuildSearch.z(p));
        int added = sampledIn.get() == level ? sample.total() - before : 0;
        Component result = found.blocks().length == 0 ? Component.translatable("axiom.chromastudio.not_build")
                : found.capped() ? Component.translatable("axiom.chromastudio.capped", FoundBuild.CAP).append(" ").append(Component.translatable("axiom.chromastudio.sampler.capped"))
                : Component.translatable(added == 1 ? "axiom.chromastudio.sampler.added.one" : "axiom.chromastudio.sampler.added", added);
        said = said == null ? result : said.copy().append(" ").append(result);
        return true;
    }

    @Override
    public void render(IAxiomWorldRenderContext rc) {
        if (AxiomTools.off()) return;
        ClientLevel level = Minecraft.getInstance().level;
        if (pather != null && level != null) {
            if (AxiomTools.tools.isMouseDown(BrushStroke.USE_TOOL) && level == strokeIn) {
                BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
                pather.update((x, y, z) -> add(level, pos, x, y, z));
            } else {
                reset();
            }
        }
        if (sampledStale) {
            sampled.clear();
            sample.forEach(sampled::add);
            sampledStale = false;
        }
        if (level != null && level == sampledIn.get()) sampled.render(rc, Vec3.ZERO, Effects.OUTLINE);
        if (pather != null) return;
        BlockHitResult hit = AxiomTools.tools.raycastBlock();
        if (hit == null || hit.getType() != HitResult.Type.BLOCK) return;
        Vec3 corner = Vec3.atLowerCornerOf(hit.getBlockPos());
        if (mode == Mode.BUILD) {
            one.render(rc, corner, Effects.BLUE | Effects.OUTLINE);
            return;
        }
        if (ballRadius != AxiomPanel.radius(radius)) {
            ballRadius = radius[0];
            ball.clear();
            BallShape.SPHERE.fillRegion(ball, ballRadius);
        }
        ball.render(rc, corner, Effects.BLUE | Effects.OUTLINE);
    }

    private void enter(ClientLevel level) {
        if (sampledIn.get() == level) return;
        if (!sample.isEmpty()) {
            sample.clear();
            changed();
            said = Component.translatable("axiom.chromastudio.sampler.new_world");
        }
        sampledIn = new WeakReference<>(level);
    }

    private void add(ClientLevel level, BlockPos.MutableBlockPos pos, int x, int y, int z) {
        String kind = SampleKind.of(BlockIndex.get(), level.getBlockState(pos.set(x, y, z)), ground);
        if (level != sampledIn.get()) {
            if (kind == null) return;
            enter(level);
        }
        if (!sample.add(x, y, z, kind)) return;
        savedAs = null;
        if (sample.counts(x, y, z)) sampled.add(x, y, z);
        else sampledStale = true;
    }

    @Override
    public void displayImguiOptions() { layout.draw(this::options, this::bottom); }

    private void options() {
        boolean brush = mode == Mode.BRUSH;
        if (AxiomPanel.radio(tr("axiom.chromastudio.sampler.brush"), brush, true, tr("axiom.chromastudio.sampler.brush.tip"))) mode = Mode.BRUSH;
        AxiomPanel.besideChoice(tr("axiom.chromastudio.whole_build"));
        if (AxiomPanel.radio(tr("axiom.chromastudio.whole_build"), !brush, true, tr("axiom.chromastudio.sampler.build.tip"))) mode = Mode.BUILD;
        AxiomPanel.radius(radius, brush, tr(brush ? "axiom.chromastudio.radius.tip" : "axiom.chromastudio.radius.whole"));
        if (AxiomPanel.checkbox(tr("axiom.chromastudio.sampler.ground"), ground && brush, brush,
                tr(brush ? "axiom.chromastudio.sampler.ground.tip" : "axiom.chromastudio.sampler.ground.build"))) ground = !ground;
        List<BuildSample.Row> rows = sample.rows();
        if (sample.isEmpty()) AxiomPanel.note(tr("axiom.chromastudio.sampler.how"));
        else list(rows);
        removed();
        if (rows.size() > MANY) AxiomPanel.note(tr("axiom.chromastudio.sampler.many", rows.size()));
    }

    private void bottom() {
        String byDefault = tr("axiom.chromastudio.sampler.default_name");
        if (name == null) name = new ImString(byDefault, 64);
        else if (name.get().equals(defaultName)) name.set(byDefault);
        defaultName = byDefault;
        List<BuildSample.Row> rows = sample.rows();
        boolean empty = rows.isEmpty(), saved = savedAs != null && PaletteStore.get().own().contains(savedAs);
        if (said != null) AxiomPanel.wrapped(said);
        if (saved) AxiomPanel.wrapped(askedName == null ? tr("axiom.chromastudio.sampler.saved", savedAs.name)
                : tr("axiom.chromastudio.sampler.saved.renamed", savedAs.name, askedName));
        if (!BlockIndex.get().ready()) AxiomPanel.wrapped(tr("axiom.chromastudio.not_ready"));
        if (!sample.isEmpty()) {
            int total = sample.total(), kinds = rows.size();
            ImGui.alignTextToFramePadding();
            AxiomPanel.text(total == 1 ? tr("axiom.chromastudio.sampler.total.one")
                    : kinds == 1 ? tr("axiom.chromastudio.sampler.total.one_kind", total) : tr("axiom.chromastudio.sampler.total", total, kinds));
            AxiomPanel.besideButton(tr("axiom.chromastudio.sampler.clear"));
            if (AxiomPanel.button(tr("axiom.chromastudio.sampler.clear"), true, tr("axiom.chromastudio.sampler.clear.tip"))) {
                sample.clear();
                changed();
                said = null;
            }
        }
        ImGui.inputText(tr("axiom.chromastudio.sampler.name"), name);
        if (AxiomPanel.button(tr("axiom.chromastudio.sampler.save"), !empty && !saved, empty ? tr("axiom.chromastudio.sampler.save.empty")
                : saved ? tr("axiom.chromastudio.sampler.save.done", savedAs.name) : tr("axiom.chromastudio.sampler.save.tip", BuildSample.MAX_WEIGHT))) save();
        AxiomPanel.besideButton(tr("axiom.chromastudio.sampler.open"));
        if (AxiomPanel.button(tr("axiom.chromastudio.sampler.open"), !empty, tr(empty ? "axiom.chromastudio.sampler.save.empty" : "axiom.chromastudio.sampler.open.tip"))
                && (saved || save())) {
            PaletteStore.get().select(savedAs);
            ChromaStudioClient.openWindowSoon("palettes");
        }
    }

    private void list(List<BuildSample.Row> rows) {
        int total = sample.total();
        if (rows.isEmpty()) return;
        float height = Math.min(rows.size(), ROWS) * ImGui.getFrameHeightWithSpacing() + ImGui.getStyle().getWindowPaddingY() * 2;
        try {
            if (ImGui.beginChild("##sample", -1, height, true)) rows(rows, total);
        } finally {
            ImGui.endChild();
        }
    }

    private void rows(List<BuildSample.Row> rows, int total) {
        for (int i = 0; i < rows.size(); i++) {
            BuildSample.Row r = rows.get(i);
            PaletteEntry e = new PaletteEntry(r.id(), "");
            String shown = shown(r.id());
            if (ImGui.smallButton(tr("axiom.chromastudio.sampler.remove") + "##remove" + i)) {
                sample.remove(r.id());
                changed();
            }
            AxiomPanel.tip(tr("axiom.chromastudio.sampler.remove.tip", shown));
            AxiomPanel.sameLine();
            Color c = PaletteBlocks.colour(e);
            if (c != null) AxiomPanel.swatch("swatch" + i, new AxiomPanel.Swatch(c, shown));
            else ImGui.dummy(ImGui.getFrameHeight(), ImGui.getFrameHeight());
            AxiomPanel.sameLine();
            ImGui.alignTextToFramePadding();
            AxiomPanel.text(shown);
            AxiomPanel.sameLine();
            ImGui.beginDisabled();
            AxiomPanel.text(tr("axiom.chromastudio.sampler.count", r.count(), share(r.count(), total)));
            ImGui.endDisabled();
        }
    }

    private void removed() {
        List<String> removed = sample.removed();
        if (removed.isEmpty()) return;
        AxiomPanel.note(tr("axiom.chromastudio.sampler.removed"));
        float line = ImGui.getContentRegionAvailX(), left = line, gap = ImGui.getStyle().getItemSpacingX();
        for (int i = 0; i < removed.size(); i++) {
            String id = removed.get(i), shown = shown(id);
            float width = ImGui.calcTextSizeX(shown) + ImGui.getStyle().getFramePaddingX() * 2;
            if (i > 0 && width + gap <= left) {
                ImGui.sameLine();
                left -= width + gap;
            } else {
                left = line - width;
            }
            if (ImGui.smallButton(shown + "##back" + i)) {
                sample.putBack(id);
                changed();
            }
            AxiomPanel.tip(tr("axiom.chromastudio.sampler.put_back.tip", shown));
        }
    }

    private static String share(int count, int total) {
        long percent = Math.round(count * 100.0 / total);
        return percent == 0 ? "<1" : Long.toString(percent);
    }

    private static String shown(String id) { return PaletteBlocks.name(new PaletteEntry(id, "")).getString(); }

    private void changed() {
        sampledStale = true;
        savedAs = null;
    }

    private boolean save() {
        PaletteStore store = PaletteStore.get();
        String asked = name.get().strip();
        Palette was = store.selected();
        Palette p = store.create(asked.isEmpty() ? tr("axiom.chromastudio.sampler.default_name") : asked, sample.entries());
        if (!store.saved(p)) {
            // The store keeps a palette it couldn't write, which a second try would add to under another name.
            store.delete(p);
            store.select(was);
            said = Component.translatable("axiom.chromastudio.sampler.save.failed");
            return false;
        }
        savedAs = p;
        askedName = asked.isEmpty() || p.name.equalsIgnoreCase(asked) ? null : asked;
        said = null;
        return true;
    }
}
