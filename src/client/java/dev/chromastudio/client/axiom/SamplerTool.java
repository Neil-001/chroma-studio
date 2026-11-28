package dev.chromastudio.client.axiom;

import static dev.chromastudio.client.axiom.AxiomTools.tr;

import com.moulberry.axiomclientapi.Effects;
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
import imgui.moulberry92.flag.ImGuiCol;
import imgui.moulberry92.flag.ImGuiStyleVar;
import imgui.moulberry92.flag.ImGuiTableColumnFlags;
import imgui.moulberry92.flag.ImGuiTableFlags;
import imgui.moulberry92.type.ImString;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

final class SamplerTool implements WorldRender.Tool {
    private static final int LEAST_ROWS = 3;
    private static final float OUT_ALPHA = 0.5f;

    private enum Mode { BRUSH, BUILD }

    private record Kind(String id, int count, boolean in) {}

    private BuildSample sample = new BuildSample();
    private @Nullable BuildSample cleared;
    private @Nullable Palette clearedSavedAs;
    private final BooleanRegion sampled = AxiomTools.regions.createBoolean();
    private boolean sampledStale;
    private final BooleanRegion ball = AxiomTools.regions.createBoolean();
    private int ballRadius = -1;
    private final BooleanRegion one = AxiomTools.regions.createBoolean();
    private final BooleanRegion focus = AxiomTools.regions.createBoolean();
    private final BooleanRegion excluded = AxiomTools.regions.createBoolean();
    private boolean excludedStale;
    private @Nullable String hovered;
    private @Nullable String focused;

    private Mode mode = Mode.BRUSH;
    private final int[] radius = {3};
    private boolean ground;
    private ImString name;
    private String defaultName = "";
    private @Nullable Palette savedAs;
    private WeakReference<ClientLevel> sampledIn = new WeakReference<>(null);
    private @Nullable Component said;
    private @Nullable String askedName;
    private final AxiomPanel.Layout layout = new AxiomPanel.Layout();
    private final List<String> shownOrder = new ArrayList<>();

    private ToolPatherUnique pather;
    private ClientLevel strokeIn;
    private int counted;
    private boolean quiet;

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
            counted = 0;
            quiet = false;
            pather = AxiomTools.pathers.createUnique(AxiomPanel.radius(radius), BallShape.SPHERE);
            return true;
        }
        BlockHitResult hit = AxiomTools.tools.raycastBlock();
        if (hit == null || hit.getType() != HitResult.Type.BLOCK) return false;
        BlockPos at = hit.getBlockPos();
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        BuildSearch.Found found = BuildSearch.from(at.getX(), at.getY(), at.getZ(), (x, y, z) -> SampleKind.member(level.getBlockState(pos.set(x, y, z))), FoundBuild.CAP);
        counted = 0;
        for (long p : found.blocks()) add(level, pos, BuildSearch.x(p), BuildSearch.y(p), BuildSearch.z(p));
        report(found.blocks().length == 0 ? Component.translatable("axiom.chromastudio.not_build")
                : found.capped() ? Component.translatable("axiom.chromastudio.capped", FoundBuild.CAP).append(" ").append(Component.translatable("axiom.chromastudio.sampler.capped"))
                : added(counted));
        return true;
    }

    private static Component added(int added) {
        return Component.translatable(added == 1 ? "axiom.chromastudio.sampler.added.one" : "axiom.chromastudio.sampler.added", added);
    }

    private void report(Component result) { said = said == null ? result : said.copy().append(" ").append(result); }

    @Override
    public void render(WorldRender rc) {
        if (AxiomTools.off()) return;
        ClientLevel level = Minecraft.getInstance().level;
        if (pather != null && level != null) {
            if (AxiomTools.tools.isMouseDown(BrushStroke.USE_TOOL) && level == strokeIn) {
                BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
                pather.update((x, y, z) -> add(level, pos, x, y, z));
            } else {
                if (level == strokeIn && !quiet) report(added(counted));
                reset();
            }
        }
        if (sampledStale) {
            sampled.clear();
            sample.forEach(sampled::add);
            sampledStale = false;
        }
        if (excludedStale) {
            excluded.clear();
            sample.forEachLeftOut(excluded::add);
            excludedStale = false;
        }
        String showing = hovered;
        hovered = null;
        if (level != null && level == sampledIn.get()) {
            rc.draw(sampled, Vec3.ZERO, Effects.OUTLINE);
            rc.draw(excluded, Vec3.ZERO, Effects.RED);
            if (showing != null) {
                if (!showing.equals(focused)) {
                    focus.clear();
                    sample.forEachOf(showing, focus::add);
                    focused = showing;
                }
                rc.draw(focus, Vec3.ZERO, Effects.BLUE);
            }
        }
        if (pather != null) return;
        BlockHitResult hit = AxiomTools.tools.raycastBlock();
        if (hit == null || hit.getType() != HitResult.Type.BLOCK) return;
        Vec3 corner = Vec3.atLowerCornerOf(hit.getBlockPos());
        if (mode == Mode.BUILD) {
            rc.draw(one, corner, Effects.BLUE);
            return;
        }
        if (ballRadius != AxiomPanel.radius(radius)) {
            ballRadius = radius[0];
            ball.clear();
            BallShape.SPHERE.fillRegion(ball, ballRadius);
        }
        rc.draw(ball, corner, Effects.BLUE);
    }

    private void enter(ClientLevel level) {
        if (sampledIn.get() == level) return;
        cleared = null;
        shownOrder.clear();
        if (!sample.isEmpty()) {
            sample.clear();
            changed();
            quiet = false;
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
        if (Objects.equals(sample.kindAt(x, y, z), kind)) return;
        if (kind != null) cleared = null;
        boolean wasOut = sample.leftOut(x, y, z), wasCounted = sample.counts(x, y, z);
        boolean seen = sample.add(x, y, z, kind);
        if (!wasCounted && sample.counts(x, y, z)) counted++;
        if (wasOut || sample.leftOut(x, y, z)) {
            excludedStale = true;
            focused = null;
        }
        if (!seen) return;
        savedAs = null;
        focused = null;
        if (sample.counts(x, y, z)) sampled.add(x, y, z);
        else sampledStale = true;
    }

    @Override
    public void displayImguiOptions() {
        layout.flow(() -> {
            options();
            ImGui.separator();
            bottom();
        });
    }

    private void options() {
        boolean brush = mode == Mode.BRUSH;
        if (AxiomPanel.radio(tr("axiom.chromastudio.sampler.brush"), brush, true, "")) layout.later(() -> mode = Mode.BRUSH);
        AxiomPanel.besideChoice(tr("axiom.chromastudio.whole_build"));
        if (AxiomPanel.radio(tr("axiom.chromastudio.whole_build"), !brush, true, tr("axiom.chromastudio.sampler.build.tip"))) layout.later(() -> mode = Mode.BUILD);
        AxiomPanel.radius(radius, brush, brush ? "" : tr("axiom.chromastudio.radius.whole"));
        if (AxiomPanel.checkbox(tr("axiom.chromastudio.sampler.ground"), ground && brush, brush,
                tr(brush ? "axiom.chromastudio.sampler.ground.tip" : "axiom.chromastudio.sampler.ground.build"))) layout.later(() -> ground = !ground);
        AxiomPanel.note(tr(brush ? "axiom.chromastudio.sampler.how.brush" : "axiom.chromastudio.sampler.how.build"));
        AxiomPanel.tip(tr("axiom.chromastudio.sampler.how.tip"));
        if (sample.isEmpty()) {
            empty();
            return;
        }
        int total = sample.total(), kinds = sample.rows().size();
        ImGui.alignTextToFramePadding();
        AxiomPanel.text(total == 1 ? tr("axiom.chromastudio.sampler.total.one")
                : kinds == 1 ? tr("axiom.chromastudio.sampler.total.one_kind", total) : tr("axiom.chromastudio.sampler.total", total, kinds));
        AxiomPanel.besideButton(tr("axiom.chromastudio.sampler.clear"));
        if (AxiomPanel.button(tr("axiom.chromastudio.sampler.clear"), true, tr("axiom.chromastudio.sampler.clear.tip"))) {
            layout.later(() -> {
                cleared = sample;
                clearedSavedAs = savedAs;
                sample = new BuildSample();
                shownOrder.clear();
                changed();
            });
        }
        table(kinds(), total);
        if (!sample.removed().isEmpty()) AxiomPanel.note(tr("axiom.chromastudio.sampler.out.note"));
    }

    private void empty() {
        if (cleared == null) {
            AxiomPanel.note(tr("axiom.chromastudio.sampler.none"));
            return;
        }
        ImGui.alignTextToFramePadding();
        AxiomPanel.text(tr("axiom.chromastudio.sampler.cleared"));
        AxiomPanel.besideButton(tr("axiom.chromastudio.sampler.undo"));
        if (AxiomPanel.button(tr("axiom.chromastudio.sampler.undo"), true, tr("axiom.chromastudio.sampler.undo.tip"))) {
            layout.later(() -> {
                if (cleared == null) return;
                sample = cleared;
                cleared = null;
                shownOrder.clear();
                changed();
                savedAs = clearedSavedAs;
            });
        }
    }

    private List<Kind> kinds() {
        List<Kind> out = new ArrayList<>();
        for (BuildSample.Row r : sample.rows()) out.add(new Kind(r.id(), r.count(), true));
        for (String id : sample.removed()) out.add(new Kind(id, sample.count(id), false));
        out.sort(Comparator.comparingInt(Kind::count).reversed().thenComparing(Kind::id));
        if (pather == null) shownOrder.clear();
        for (Kind k : out) if (!shownOrder.contains(k.id())) shownOrder.add(k.id());
        if (pather != null) out.sort(Comparator.comparingInt(k -> {
            int i = shownOrder.indexOf(k.id());
            return i < 0 ? Integer.MAX_VALUE : i;
        }));
        return out;
    }

    private void bottom() {
        String byDefault = tr("axiom.chromastudio.sampler.default_name");
        if (name == null) name = new ImString(byDefault, 64);
        else if (name.get().equals(defaultName)) name.set(byDefault);
        defaultName = byDefault;
        boolean empty = sample.rows().isEmpty();
        Palette saved = savedAs != null && PaletteStore.get().own().contains(savedAs) ? savedAs : null;
        AxiomPanel.status(status(saved));
        String label = tr("axiom.chromastudio.sampler.name");
        float field = ImGui.getContentRegionAvailX() - ImGui.calcTextSizeX(label) - ImGui.getStyle().getItemInnerSpacingX();
        if (field >= ImGui.getFontSize() * 6) {
            ImGui.setNextItemWidth(field);
            ImGui.inputText(label, name);
        } else {
            AxiomPanel.text(label);
            ImGui.setNextItemWidth(-1);
            ImGui.inputText("##" + label, name);
        }
        if (AxiomPanel.button(tr(saved == null ? "axiom.chromastudio.sampler.save" : "axiom.chromastudio.sampler.saved.button"), !empty && saved == null, tr(empty ? "axiom.chromastudio.sampler.save.empty"
                : saved != null ? "axiom.chromastudio.sampler.save.done" : "axiom.chromastudio.sampler.save.tip"))) {
            layout.later(this::save);
        }
        AxiomPanel.besideButton(tr("axiom.chromastudio.sampler.open"));
        if (AxiomPanel.button(tr("axiom.chromastudio.sampler.open"), !empty, tr(empty ? "axiom.chromastudio.sampler.save.empty" : "axiom.chromastudio.sampler.open.tip"))) {
            layout.later(() -> {
                if (savedAs == null || !PaletteStore.get().own().contains(savedAs)) {
                    if (!save()) return;
                }
                PaletteStore.get().select(savedAs);
                ChromaStudioClient.openWindowSoon("palettes");
            });
        }
    }

    private AxiomPanel.@Nullable Status status(@Nullable Palette saved) {
        if (!BlockIndex.get().ready()) return AxiomPanel.Status.of(tr("axiom.chromastudio.not_ready"));
        if (said != null) return AxiomPanel.Status.of(said);
        if (saved == null) return null;
        AxiomPanel.Status receipt = AxiomPanel.Status.receipt(tr("axiom.chromastudio.sampler.saved", saved.name));
        return askedName == null ? receipt : receipt.withTip(tr("axiom.chromastudio.sampler.saved.renamed", askedName));
    }

    private void table(List<Kind> rows, int total) {
        float row = ImGui.getFrameHeightWithSpacing(), pad = ImGui.getStyle().getWindowPaddingY() * 2;
        float height = layout.grow(rows.size() * row + pad, Math.min(rows.size(), LEAST_ROWS) * row + pad);
        float countWidth = ImGui.calcTextSizeX(Integer.toString(rows.stream().mapToInt(Kind::count).max().orElse(0)));
        float shareWidth = ImGui.calcTextSizeX(tr("axiom.chromastudio.sampler.share", "100"));
        if (ImGui.beginTable("##sample", 5, ImGuiTableFlags.ScrollY | ImGuiTableFlags.SizingFixedFit | ImGuiTableFlags.BordersOuter, 0, height)) {
            try {
                ImGui.tableSetupColumn("##in", ImGuiTableColumnFlags.WidthFixed, (float) ImGui.getFontSize());
                ImGui.tableSetupColumn("##swatch", ImGuiTableColumnFlags.WidthFixed, (float) ImGui.getFontSize());
                ImGui.tableSetupColumn("##name", ImGuiTableColumnFlags.WidthStretch, 1f);
                ImGui.tableSetupColumn("##count", ImGuiTableColumnFlags.WidthFixed, countWidth);
                ImGui.tableSetupColumn("##share", ImGuiTableColumnFlags.WidthFixed, shareWidth);
                for (int i = 0; i < rows.size(); i++) row(rows.get(i), i, total);
            } finally {
                ImGui.endTable();
            }
        }
        layout.grown();
    }

    private void row(Kind r, int i, int total) {
        String shown = shown(r.id());
        String toggleTip = tr(r.in() ? "axiom.chromastudio.sampler.in.tip" : "axiom.chromastudio.sampler.out.tip");
        ImGui.tableNextRow();
        float padY = ImGui.getStyle().getFramePaddingY();
        ImGui.pushStyleVar(ImGuiStyleVar.FramePadding, ImGui.getStyle().getFramePaddingX(), 0);
        boolean over = false;
        try {
            ImGui.tableNextColumn();
            ImGui.setCursorPosY(ImGui.getCursorPosY() + padY);
            if (AxiomPanel.checkbox("##in" + i, r.in(), true, toggleTip)) toggle(r);
            over = ImGui.isItemHovered();
            ImGui.tableNextColumn();
            Color c = PaletteBlocks.colour(new PaletteEntry(r.id(), ""));
            if (c != null) {
                ImGui.setCursorPosY(ImGui.getCursorPosY() + padY);
                if (!r.in()) ImGui.pushStyleVar(ImGuiStyleVar.Alpha, OUT_ALPHA);
                try {
                    AxiomPanel.swatch("swatch" + i, new AxiomPanel.Swatch(c, shown));
                } finally {
                    if (!r.in()) ImGui.popStyleVar();
                }
                over |= ImGui.isItemHovered();
            }
        } finally {
            ImGui.popStyleVar();
        }
        ImGui.tableNextColumn();
        ImGui.alignTextToFramePadding();
        String fitted = AxiomPanel.fit(shown, ImGui.getContentRegionAvailX());
        if (!r.in()) ImGui.pushStyleColor(ImGuiCol.Text, ImGui.getColorU32(ImGuiCol.TextDisabled));
        if (ImGui.selectable(fitted + "##name" + i, false)) toggle(r);
        if (!r.in()) ImGui.popStyleColor();
        over |= ImGui.isItemHovered();
        AxiomPanel.tip(fitted.equals(shown) ? toggleTip : shown);
        ImGui.tableNextColumn();
        over |= right(Integer.toString(r.count()));
        ImGui.tableNextColumn();
        if (r.in()) over |= right(tr("axiom.chromastudio.sampler.share", share(r.count(), total)));
        if (over) hovered = r.id();
    }

    private void toggle(Kind r) {
        layout.later(() -> {
            if (r.in()) sample.remove(r.id());
            else sample.putBack(r.id());
            changed();
        });
    }

    private static boolean right(String text) {
        ImGui.alignTextToFramePadding();
        ImGui.setCursorPosX(ImGui.getCursorPosX() + Math.max(0, ImGui.getContentRegionAvailX() - ImGui.calcTextSizeX(text)));
        ImGui.pushStyleColor(ImGuiCol.Text, ImGui.getColorU32(ImGuiCol.TextDisabled));
        AxiomPanel.text(text);
        ImGui.popStyleColor();
        return ImGui.isItemHovered();
    }

    private static String share(int count, int total) {
        long percent = Math.round(count * 100.0 / total);
        return percent == 0 ? "<1" : Long.toString(percent);
    }

    private static String shown(String id) { return PaletteBlocks.name(new PaletteEntry(id, "")).getString(); }

    private void changed() {
        quiet = true;
        sampledStale = true;
        excludedStale = true;
        focused = null;
        said = null;
        savedAs = null;
    }

    private boolean save() {
        String asked = name.get().strip();
        Optional<Palette> made = PaletteStore.get().createSaved(asked.isEmpty() ? tr("axiom.chromastudio.sampler.default_name") : asked, sample.entries());
        if (made.isEmpty()) {
            said = Component.translatable("axiom.chromastudio.sampler.save.failed");
            return false;
        }
        savedAs = made.get();
        quiet = true;
        askedName = asked.isEmpty() || savedAs.name.equalsIgnoreCase(asked) ? null : asked;
        said = null;
        return true;
    }
}
