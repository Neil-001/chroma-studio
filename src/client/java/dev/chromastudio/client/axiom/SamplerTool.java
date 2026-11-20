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
import imgui.moulberry92.flag.ImGuiColorEditFlags;
import imgui.moulberry92.type.ImString;
import java.lang.ref.WeakReference;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * An Axiom tool that turns a build into a Chroma palette. The player brushes over blocks, or uses the tool on a build
 * to take every block joined to it, and the panel counts what it found, most common first, to save as a palette
 * weighted by those counts. It only reads the world and never changes a block.
 *
 * <p>{@link SampleKind} says what each block counts as: a stair, slab, wall, fence, door or trapdoor as its
 * material, the ground and plants only when asked for, and air, liquids and chests never.
 */
final class SamplerTool implements CustomTool {
    /** As in {@link PaletteBrushTool}: Axiom's use-tool button in ImGui's order, in which 1 is the right button. */
    private static final int USE_TOOL = 1;
    private static final int MAX_RADIUS = 16;
    /** The most blocks one use of the tool on a build takes, as for the gradient tool. */
    private static final int CAP = 100_000;
    /** Past this many kinds the panel says a palette is getting hard to build with. Nothing is left out. */
    private static final int MANY = 24;
    /** The rows the list shows before it scrolls, so a long sample can't push the buttons off the screen. */
    private static final int ROWS = 6;

    private enum Mode { BRUSH, BUILD }

    private final BuildSample sample = new BuildSample();
    /** The sampled blocks, outlined in the world. Rebuilt from the sample when a block leaves it, as a region can't drop one. */
    private final BooleanRegion sampled = AxiomTools.regions.createBoolean();
    private boolean sampledStale;
    private final BooleanRegion ball = AxiomTools.regions.createBoolean();
    private int ballRadius = -1;
    private final BooleanRegion one = AxiomTools.regions.createBoolean();

    private Mode mode = Mode.BRUSH;
    private final int[] radius = {3};
    private boolean ground;
    /** The name to save under, filled with the default the first time the panel shows. */
    private ImString name;
    /** The palette the sample as it stands was saved as, so Open doesn't save it twice. Null once the sample changes. */
    private Palette savedAs;
    /** The world the sample's positions are in, as a position means nothing in another dimension or world. */
    private WeakReference<ClientLevel> sampledIn = new WeakReference<>(null);
    /** What the last use or button did, shown in the panel until the next. */
    private String said;

    /** The brush's path through the world while the button is held, and the world the stroke began in. */
    private ToolPatherUnique pather;
    private ClientLevel strokeIn;

    SamplerTool() {
        one.add(0, 0, 0);
    }

    @Override
    public String name() { return tr("axiom.chromastudio.sampler"); }

    /** Ends a stroke. The sample stays, as Axiom resets a tool when the player only switches to another for a moment. */
    @Override
    public void reset() {
        pather = null;
        strokeIn = null;
    }

    @Override
    public boolean callUseTool() {
        pather = null;
        if (!BlockIndex.get().ready()) {
            said = tr("axiom.chromastudio.sampler.not_ready");
            return false;
        }
        ClientLevel level = Minecraft.getInstance().level;
        if (level == null) return false;
        said = null;
        if (mode == Mode.BRUSH) {
            strokeIn = level;
            pather = AxiomTools.pathers.createUnique(radius[0], BallShape.SPHERE);
            return true;
        }
        BlockHitResult hit = AxiomTools.tools.raycastBlock();
        if (hit == null || hit.getType() != HitResult.Type.BLOCK) return false;
        BlockPos at = hit.getBlockPos();
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        // The search never spreads into the ground or plants, whatever the setting, or one use would take the landscape.
        BuildSearch.Found found = BuildSearch.from(at.getX(), at.getY(), at.getZ(), (x, y, z) -> SampleKind.member(level.getBlockState(pos.set(x, y, z))), CAP);
        // In a new world the sample starts over only at the first block that counts, so the old one counts as none.
        int before = sampledIn.get() == level ? sample.total() : 0;
        for (long p : found.blocks()) add(level, pos, BuildSearch.x(p), BuildSearch.y(p), BuildSearch.z(p));
        int added = sampledIn.get() == level ? sample.total() - before : 0;
        String result = found.blocks().length == 0 ? tr("axiom.chromastudio.sampler.not_build")
                : found.capped() ? tr("axiom.chromastudio.sampler.capped", CAP)
                : tr("axiom.chromastudio.sampler.added", added);
        // Keeps the word that the sample started over, when it did.
        said = said == null ? result : said + " " + result;
        return true;
    }

    @Override
    public void render(IAxiomWorldRenderContext rc) {
        ClientLevel level = Minecraft.getInstance().level;
        if (pather != null && level != null) {
            // A stroke ends where the world does, as when the player changes dimension with the button held.
            if (AxiomTools.tools.isMouseDown(USE_TOOL) && level == strokeIn) {
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
        if (ballRadius != radius[0]) {
            ballRadius = radius[0];
            ball.clear();
            BallShape.SPHERE.fillRegion(ball, ballRadius);
        }
        ball.render(rc, corner, Effects.BLUE | Effects.OUTLINE);
    }

    /**
     * Starts the sample over when it was taken in another world or dimension, whose positions mean nothing here, and
     * says so. Called only at the first block in that world that counts, so a missed click there loses nothing.
     */
    private void enter(ClientLevel level) {
        if (sampledIn.get() == level) return;
        if (!sample.isEmpty()) {
            sample.clear();
            changed();
            said = tr("axiom.chromastudio.sampler.new_world");
        }
        // Weak, so the tool doesn't keep a world the player has left in memory.
        sampledIn = new WeakReference<>(level);
    }

    /** Samples one position as it stands now, and keeps the outline and the saved palette in step. */
    private void add(ClientLevel level, BlockPos.MutableBlockPos pos, int x, int y, int z) {
        String kind = SampleKind.of(BlockIndex.get(), level.getBlockState(pos.set(x, y, z)), ground);
        if (level != sampledIn.get()) {
            // Air brushed in a new world says nothing about the old sample, whose positions it would wrongly empty.
            if (kind == null) return;
            enter(level);
        }
        if (!sample.add(x, y, z, kind)) return;
        savedAs = null;
        if (sample.counts(x, y, z)) sampled.add(x, y, z);
        else sampledStale = true;
    }

    @Override
    public void displayImguiOptions() {
        if (name == null) name = new ImString(tr("axiom.chromastudio.sampler.default_name"), 64);
        boolean ready = BlockIndex.get().ready();
        if (!ready) wrapped(tr("axiom.chromastudio.sampler.not_ready"));

        if (ImGui.radioButton(tr("axiom.chromastudio.sampler.brush"), mode == Mode.BRUSH)) mode = Mode.BRUSH;
        tip(tr("axiom.chromastudio.sampler.brush.tip"));
        ImGui.sameLine();
        if (ImGui.radioButton(tr("axiom.chromastudio.sampler.build"), mode == Mode.BUILD)) mode = Mode.BUILD;
        tip(tr("axiom.chromastudio.sampler.build.tip"));
        ImGui.beginDisabled(mode != Mode.BRUSH);
        ImGui.sliderInt(tr("axiom.chromastudio.sampler.radius"), radius, 0, MAX_RADIUS);
        ImGui.endDisabled();
        if (mode != Mode.BRUSH) tip(tr("axiom.chromastudio.sampler.radius.build"));
        ImGui.beginDisabled(mode != Mode.BRUSH);
        if (ImGui.checkbox(tr("axiom.chromastudio.sampler.ground"), ground && mode == Mode.BRUSH)) ground = !ground;
        ImGui.endDisabled();
        tip(tr(mode == Mode.BRUSH ? "axiom.chromastudio.sampler.ground.tip" : "axiom.chromastudio.sampler.ground.build"));

        ImGui.separator();
        List<BuildSample.Row> rows = sample.rows();
        if (sample.isEmpty()) wrapped(tr("axiom.chromastudio.sampler.how"));
        else list(rows);
        removed();
        if (rows.size() > MANY) wrapped(tr("axiom.chromastudio.sampler.many", rows.size()));

        ImGui.separator();
        ImGui.inputText(tr("axiom.chromastudio.sampler.name"), name);
        boolean empty = rows.isEmpty(), saved = savedAs != null && PaletteStore.get().own().contains(savedAs);
        ImGui.beginDisabled(empty || saved);
        if (ImGui.button(tr("axiom.chromastudio.sampler.save"))) save();
        ImGui.endDisabled();
        tip(empty ? tr("axiom.chromastudio.sampler.save.empty")
                : saved ? tr("axiom.chromastudio.sampler.save.done", savedAs.name) : tr("axiom.chromastudio.sampler.save.tip", BuildSample.MAX_WEIGHT));
        ImGui.sameLine();
        ImGui.beginDisabled(empty);
        if (ImGui.button(tr("axiom.chromastudio.sampler.open")) && (saved || save())) {
            PaletteStore.get().select(savedAs);
            // The window opens at the next tick, after Axiom has finished drawing its panels this frame.
            ChromaStudioClient.openWindowSoon("palettes");
        }
        ImGui.endDisabled();
        tip(tr(empty ? "axiom.chromastudio.sampler.save.empty" : "axiom.chromastudio.sampler.open.tip"));
        if (said != null) wrapped(said);
    }

    /** The sample's kinds, most common first, each with its colour, name, count and share, and a button to leave it out. */
    private void list(List<BuildSample.Row> rows) {
        int total = sample.total();
        ImGui.alignTextToFramePadding();
        ImGui.textUnformatted(tr("axiom.chromastudio.sampler.total", total, rows.size()));
        ImGui.sameLine();
        if (ImGui.button(tr("axiom.chromastudio.sampler.clear"))) {
            sample.clear();
            changed();
            said = null;
        }
        tip(tr("axiom.chromastudio.sampler.clear.tip"));
        if (rows.isEmpty()) return;
        float height = Math.min(rows.size(), ROWS) * ImGui.getFrameHeightWithSpacing() + ImGui.getStyle().getWindowPaddingY() * 2;
        if (ImGui.beginChild("##sample", -1, height, true)) {
            for (int i = 0; i < rows.size(); i++) {
                BuildSample.Row r = rows.get(i);
                PaletteEntry e = new PaletteEntry(r.id(), "");
                String shown = shown(r.id());
                if (ImGui.smallButton(tr("axiom.chromastudio.sampler.remove") + "##remove" + i)) {
                    sample.remove(r.id());
                    changed();
                }
                tip(tr("axiom.chromastudio.sampler.remove.tip", shown));
                ImGui.sameLine();
                Color c = PaletteBlocks.colour(e);
                // A block whose colour can't be read still has its row, so it can be seen and removed.
                if (c != null) ImGui.colorButton("##swatch" + i, c.red() / 255f, c.green() / 255f, c.blue() / 255f, 1f, ImGuiColorEditFlags.NoTooltip);
                else ImGui.dummy(ImGui.getFrameHeight(), ImGui.getFrameHeight());
                ImGui.sameLine();
                ImGui.alignTextToFramePadding();
                ImGui.textUnformatted(shown);
                ImGui.sameLine();
                ImGui.beginDisabled();
                ImGui.textUnformatted(tr("axiom.chromastudio.sampler.count", r.count(), share(r.count(), total)));
                ImGui.endDisabled();
            }
        }
        ImGui.endChild();
    }

    /** The kinds left out, each a button that puts it back, as many to a line as fit. */
    private void removed() {
        List<String> removed = sample.removed();
        if (removed.isEmpty()) return;
        wrapped(tr("axiom.chromastudio.sampler.removed"));
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
            tip(tr("axiom.chromastudio.sampler.put_back.tip", shown));
        }
    }

    /**
     * A tooltip for the last item, shown as it is and wrapped: ImGui's own would read a % in a palette's or block's
     * name as a format, and these run to a few sentences.
     */
    private static void tip(String text) {
        if (!ImGui.beginItemTooltip()) return;
        ImGui.pushTextWrapPos(ImGui.getFontSize() * 30f);
        ImGui.textUnformatted(text);
        ImGui.popTextWrapPos();
        ImGui.endTooltip();
    }

    /** Text that wraps at the panel's edge, shown as it is: ImGui's own wrapped text would read a % in a name as a format. */
    private static void wrapped(String text) {
        ImGui.pushTextWrapPos(0f);
        ImGui.textUnformatted(text);
        ImGui.popTextWrapPos();
    }

    /** A share as a whole percentage, or "<1" for a few blocks among many, which rounding would show as none. */
    private static String share(int count, int total) {
        long percent = Math.round(count * 100.0 / total);
        return percent == 0 ? "<1" : Long.toString(percent);
    }

    private static String shown(String id) { return PaletteBlocks.name(new PaletteEntry(id, "")).getString(); }

    /** After a remove, put back or clear: the outline is drawn again and the sample needs saving again. */
    private void changed() {
        sampledStale = true;
        savedAs = null;
    }

    /** Saves the sample as a new palette of the player's own, never over another, and says how it went. */
    private boolean save() {
        PaletteStore store = PaletteStore.get();
        String asked = name.get().strip();
        Palette was = store.selected();
        Palette p = store.create(asked.isEmpty() ? tr("axiom.chromastudio.sampler.default_name") : asked, sample.entries());
        if (!store.saved(p)) {
            // The store keeps a palette it couldn't write, which a second try would add to under another name.
            store.delete(p);
            store.select(was);
            said = tr("axiom.chromastudio.sampler.save.failed");
            return false;
        }
        savedAs = p;
        said = asked.isEmpty() || p.name.equalsIgnoreCase(asked) ? tr("axiom.chromastudio.sampler.saved", p.name)
                : tr("axiom.chromastudio.sampler.saved.renamed", p.name, asked);
        return true;
    }
}
