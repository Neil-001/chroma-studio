package dev.chromastudio.client.axiom;

import static dev.chromastudio.client.axiom.AxiomTools.tr;

import com.moulberry.axiomclientapi.CustomTool;
import com.moulberry.axiomclientapi.Effects;
import com.moulberry.axiomclientapi.IAxiomWorldRenderContext;
import com.moulberry.axiomclientapi.pathers.BallShape;
import com.moulberry.axiomclientapi.pathers.ToolPatherUnique;
import com.moulberry.axiomclientapi.regions.BlockRegion;
import com.moulberry.axiomclientapi.regions.BooleanRegion;
import dev.chromastudio.api.ChromaStudioApi;
import dev.chromastudio.client.PaletteStore;
import dev.chromastudio.client.action.PaletteBlocks;
import dev.chromastudio.client.axiom.AxiomBridge.Refused;
import dev.chromastudio.core.color.Color;
import dev.chromastudio.core.palette.Palette;
import dev.chromastudio.core.palette.PaletteEntry;
import dev.chromastudio.core.palette.Recolour;
import dev.chromastudio.core.palette.WeightedPick;
import imgui.moulberry92.ImGui;
import imgui.moulberry92.flag.ImGuiColorEditFlags;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * An Axiom brush that paints with one of Chroma Studio's palettes. Scatter fills the brush's ball with the palette's
 * blocks, each as often as its weight says. Recolour changes each block the ball touches as a recolour script into
 * the palette would, through the same {@link RecolourPlan}, and so leaves the ground, air and liquids alone. The
 * stroke shows while the button is held and goes to Axiom as one change when it's let go, so one undo takes it back.
 */
final class PaletteBrushTool implements CustomTool {
    /**
     * The button that uses a tool, as Axiom numbers them: ImGui's order from 0, in which 1 is the right button. Axiom
     * maps it to its own "use tool" key, so a player who rebinds that key is still followed.
     */
    private static final int USE_TOOL = 1;
    private static final int MAX_RADIUS = 16;

    private enum Mode { SCATTER, RECOLOUR }

    private final BlockRegion stroke = AxiomTools.regions.createBlock();
    private final BooleanRegion outline = AxiomTools.regions.createBoolean();
    private final int[] radius = {4};
    private int outlineRadius = -1;
    private Mode mode = Mode.SCATTER;
    /** The palette picked in the panel. Null until one is, and then the first in the list stands in. */
    private Palette palette;
    /** The palette whose blocks a recolour changes, or null for every block but the ground. */
    private Palette from;
    private Recolour.Mode match = Recolour.Mode.SHADING;
    /** Why the last recolour stroke couldn't start, shown in the panel until the next one. */
    private String refused;

    // Set for the length of one stroke.
    private ToolPatherUnique pather;
    private WeightedPick<Weighted> scatter;
    private RecolourPlan plan;
    private long seed;

    @Override
    public String name() { return tr("axiom.chromastudio.brush"); }

    @Override
    public void reset() {
        stroke.clear();
        pather = null;
        scatter = null;
        plan = null;
    }

    @Override
    public boolean callUseTool() {
        reset();
        if (blocks(current()).isEmpty() || mode == Mode.RECOLOUR && !ChromaStudioApi.indexReady()) return false;
        if (mode == Mode.SCATTER) {
            scatter = new WeightedPick<>(usable(current()).stream().map(e -> new Weighted(PaletteBlocks.state(e), e.weight())).toList(), Weighted::weight);
            seed = System.nanoTime();
        } else {
            refused = null;
            try {
                plan = RecolourPlan.of(current(), from(), match);
            } catch (Refused r) {
                refused = r.reason().getString();
                return false;
            }
            if (plan.size() == 0) {
                refused = tr("axiom.chromastudio.nothing_to_change");
                return false;
            }
        }
        pather = AxiomTools.pathers.createUnique(radius[0], BallShape.SPHERE);
        return true;
    }

    @Override
    public void render(IAxiomWorldRenderContext rc) {
        if (pather == null) {
            BlockHitResult hit = AxiomTools.tools.raycastBlock();
            if (hit == null || hit.getType() != HitResult.Type.BLOCK) return;
            if (outlineRadius != radius[0]) {
                outlineRadius = radius[0];
                outline.clear();
                BallShape.SPHERE.fillRegion(outline, outlineRadius);
            }
            outline.render(rc, Vec3.atLowerCornerOf(hit.getBlockPos()), Effects.BLUE | Effects.OUTLINE);
            return;
        }
        if (!AxiomTools.tools.isMouseDown(USE_TOOL)) {
            if (!stroke.isEmpty()) AxiomTools.tools.pushBlockRegionChange(stroke);
            reset();
            return;
        }
        ClientLevel level = Minecraft.getInstance().level;
        if (level == null) return;
        pather.update((x, y, z) -> {
            BlockState now = level.getBlockState(new BlockPos(x, y, z));
            BlockState next = scatter != null ? scatter.at(seed, x, y, z).state() : plan.apply(now);
            if (next != null && next != now) stroke.addBlock(x, y, z, next);
        });
        // A slow pulse, as Axiom's own brushes show a stroke that hasn't been committed yet.
        float pulse = (float) Math.sin(rc.nanos() / 400_000_000.0);
        stroke.render(rc, Vec3.ZERO, 0.75f + pulse * 0.25f, 0.3f - pulse * 0.2f);
    }

    @Override
    public void displayImguiOptions() {
        List<Palette> all = PaletteStore.get().all();
        Palette shown = current();

        ImGui.text(tr("axiom.chromastudio.brush.palette"));
        if (ImGui.beginListBox("##palettes", -1, ImGui.getTextLineHeightWithSpacing() * 7)) {
            for (int i = 0; i < all.size(); i++) {
                Palette p = all.get(i);
                // A label is also ImGui's id for the row, and a player's palette may share a preset's name.
                if (ImGui.selectable(PaletteStore.get().shownName(p) + "##" + i, p == shown)) palette = p;
            }
            ImGui.endListBox();
        }
        if (shown != null) swatches(shown);

        ImGui.separator();
        ImGui.sliderInt(tr("axiom.chromastudio.brush.radius"), radius, 0, MAX_RADIUS);

        if (ImGui.radioButton(tr("axiom.chromastudio.brush.scatter"), mode == Mode.SCATTER)) mode = Mode.SCATTER;
        ImGui.setItemTooltip(tr("axiom.chromastudio.brush.scatter.tip"));
        ImGui.sameLine();
        boolean ready = ChromaStudioApi.indexReady();
        ImGui.beginDisabled(!ready);
        if (ImGui.radioButton(tr("axiom.chromastudio.brush.recolour"), mode == Mode.RECOLOUR)) mode = Mode.RECOLOUR;
        ImGui.endDisabled();
        ImGui.setItemTooltip(tr(ready ? "axiom.chromastudio.brush.recolour.tip" : "axiom.chromastudio.brush.not_ready"));
        if (mode == Mode.RECOLOUR) recolourOptions(all);
        if (shown != null && blocks(shown).isEmpty()) ImGui.textWrapped(tr("axiom.chromastudio.brush.empty"));
    }

    /** Which blocks a recolour changes and how it matches them, as the Axiom script dialog offers. */
    private void recolourOptions(List<Palette> all) {
        Palette chosen = from();
        String any = tr("axiom.chromastudio.from.any");
        if (ImGui.beginCombo(tr("axiom.chromastudio.from") + "##from", chosen == null ? any : AxiomBridge.fromName(chosen))) {
            if (ImGui.selectable(any + "##any", chosen == null)) from = null;
            for (int i = 0; i < all.size(); i++) {
                Palette p = all.get(i);
                if (ImGui.selectable(AxiomBridge.fromName(p) + "##from" + i, p == chosen)) from = p;
            }
            ImGui.endCombo();
        }
        wrappedTooltip(tr("axiom.chromastudio.from.tip"));
        for (Recolour.Mode m : Recolour.Mode.values()) {
            if (m.ordinal() > 0) ImGui.sameLine();
            if (ImGui.radioButton(tr("axiom.chromastudio.match." + m.name().toLowerCase(Locale.ROOT)), match == m)) match = m;
            wrappedTooltip(tr("axiom.chromastudio.match.tip"));
        }
        if (refused != null) ImGui.textWrapped(refused);
    }

    /** A tooltip for the last item that wraps its text, as these run to a few sentences and would leave the screen. */
    private static void wrappedTooltip(String text) {
        if (!ImGui.beginItemTooltip()) return;
        ImGui.pushTextWrapPos(ImGui.getFontSize() * 30f);
        ImGui.textUnformatted(text);
        ImGui.popTextWrapPos();
        ImGui.endTooltip();
    }

    /** The From palette, while it's still in the list, or null for every block but the ground. */
    private Palette from() {
        for (Palette p : PaletteStore.get().all()) if (p == from) return p;
        return null;
    }

    /** The palette's blocks as a row of their colours, each named when hovered. */
    private static void swatches(Palette p) {
        // As many to a row as the panel is wide, so a narrow panel wraps them rather than cutting the last off.
        float size = ImGui.getFrameHeight(), gap = ImGui.getStyle().getItemSpacingX();
        int perRow = Math.max(1, (int) ((ImGui.getContentRegionAvailX() + gap) / (size + gap)));
        int column = 0;
        for (int i = 0; i < p.entries.size(); i++) {
            PaletteEntry e = p.entries.get(i);
            Color c = PaletteBlocks.colour(e);
            if (c == null) continue;
            if (column % perRow != 0) ImGui.sameLine();
            ImGui.colorButton("##swatch" + i, c.red() / 255f, c.green() / 255f, c.blue() / 255f, 1f, ImGuiColorEditFlags.NoTooltip);
            ImGui.setItemTooltip(PaletteBlocks.name(e).getString() + (e.weight() > 1 ? " ×" + e.weight() : ""));
            column++;
        }
    }

    private Palette current() {
        List<Palette> all = PaletteStore.get().all();
        // The panel's choice, while it's still in the list: the player may have deleted it in Chroma Studio's window.
        for (Palette p : all) if (p == palette) return p;
        Palette selected = PaletteStore.get().selected();
        return selected != null ? selected : all.isEmpty() ? null : all.getFirst();
    }

    /** The entries whose block this game has. */
    private static List<PaletteEntry> usable(Palette p) {
        List<PaletteEntry> out = new ArrayList<>();
        if (p != null) for (PaletteEntry e : p.entries) if (PaletteBlocks.state(e) != null) out.add(e);
        return out;
    }

    private static List<BlockState> blocks(Palette p) { return usable(p).stream().map(PaletteBlocks::state).toList(); }

    /** An entry's block, read once a stroke, as entries are parsed from text. */
    private record Weighted(BlockState state, int weight) {}
}
