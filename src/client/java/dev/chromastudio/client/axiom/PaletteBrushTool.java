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
import dev.chromastudio.client.ModSettings;
import dev.chromastudio.client.PaletteStore;
import dev.chromastudio.client.action.PaletteBlocks;
import dev.chromastudio.client.axiom.AxiomBridge.Refused;
import dev.chromastudio.client.index.BlockIndex;
import dev.chromastudio.core.color.Color;
import dev.chromastudio.core.palette.BuildSearch;
import dev.chromastudio.core.palette.Palette;
import dev.chromastudio.core.palette.PaletteEntry;
import dev.chromastudio.core.palette.Recolour;
import dev.chromastudio.core.palette.WeightedPick;
import imgui.moulberry92.ImGui;
import imgui.moulberry92.flag.ImGuiColorEditFlags;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * An Axiom brush that paints with one of Chroma Studio's palettes. Scatter fills the brush's ball with the palette's
 * blocks, each as often as its weight says, as {@link ScatterBlocks} has them. Recolour changes each block the ball
 * touches as a recolour script into the palette would, through the same {@link RecolourPlan}, and so leaves the
 * ground, air and liquids alone. A stroke shows while the button is held and goes to Axiom as one change when it's let
 * go, so one undo takes it back.
 *
 * <p>Recolour can also take a whole build at once: used on a block, the tool finds the build joined to it
 * ({@link FoundBuild}) and shows it recoloured, and Apply sends it to Axiom as one change, as the gradient tool does.
 * Its default From is the build itself, whose darkest and lightest blocks set the range the palette is spread over;
 * against every block in the game, a grey house would use only the palette's few middle shades. Every change keeps to
 * the player's tool masks in Axiom ({@link AxiomMask}). The panel's choices are kept in {@link ModSettings}.
 */
final class PaletteBrushTool implements CustomTool {
    /**
     * The button that uses a tool, as Axiom numbers them: ImGui's order from 0, in which 1 is the right button. Axiom
     * maps it to its own "use tool" key, so a player who rebinds that key is still followed.
     */
    private static final int USE_TOOL = 1;
    /** Our name for the world's look while a whole-build preview shows. */
    private static final String PREVIEW = "chromastudio_brush";
    /** From choices that aren't palettes: the build the tool is used on, and every block but the ground. */
    private static final String FROM_BUILD = "build", FROM_ANY = "any";
    /** The most of a build's kinds the panel shows. */
    private static final int MAX_SHOWN = 32;

    private enum Mode { SCATTER, RECOLOUR }

    /** One block a preview changes: where it is, what was there when the preview was made, and what goes there. */
    private record Planned(int x, int y, int z, BlockState was, BlockState next) {}

    /** A palette block as a swatch: its colour, and what hovering it says. */
    private record Swatch(Color colour, String tip) {}

    private final BlockRegion stroke = AxiomTools.regions.createBlock();
    private final BooleanRegion outline = AxiomTools.regions.createBoolean();
    private final BooleanRegion one = AxiomTools.regions.createBoolean();
    private int outlineRadius = -1;

    // The panel's choices, as they were left last session.
    private final int[] radius = {ModSettings.brushRadius};
    private Mode mode = Mode.valueOf(ModSettings.brushMode);
    private boolean wholeBuild = ModSettings.brushWholeBuild;
    /** The palette, by {@link #key}; when no palette has it, the Palettes tab's or the first stands in. */
    private String paletteKey = ModSettings.brushPalette;
    /** {@link #FROM_BUILD}, {@link #FROM_ANY}, or a palette's {@link #key}. */
    private String fromKey = ModSettings.brushFrom;
    private Recolour.Mode match = Recolour.Mode.valueOf(ModSettings.brushMatch);

    /** Why the last stroke or preview couldn't go ahead, shown in the panel until the next one. */
    private @Nullable String refused;
    /** The last build found, to show what sets the shading, as swatches from darkest to lightest. */
    private @Nullable FoundBuild found;
    private List<Swatch> foundSwatches = List.of();

    // Set for the length of one stroke.
    private ToolPatherUnique pather;
    /** With From set to This build, the build's blocks, so a stroke that reaches another build leaves it alone. */
    private @Nullable Set<Long> strokeBuild;
    private WeightedPick<ScatterBlocks.Placed> scatter;
    private RecolourPlan plan;
    private AxiomMask.Test mask;
    private long seed;

    // The whole-build preview.
    private final BlockRegion change = AxiomTools.regions.createBlock();
    private final BooleanRegion touched = AxiomTools.regions.createBoolean();
    private final List<Planned> planned = new ArrayList<>();
    /** The block the tool was used on, whose build is shown recoloured, or null, and the world it's in. */
    private @Nullable BlockPos picked;
    private @Nullable ClientLevel pickedIn;
    /** What the preview on show was made for, so it's made again only when something changes. */
    private @Nullable Object previewFor;
    private boolean overriding;
    /** When Axiom last drew this tool, as {@link System#nanoTime}. */
    private long drawn;
    /** How many kinds of block the previewed build has now, and will have once recoloured. */
    private int kindsBefore, kindsAfter;

    // The panel's view of the palettes, made again only when they change.
    private List<Palette> listed = List.of();
    private List<String> listedNames = List.of(), labels = List.of(), fromLabels = List.of(), keys = List.of();
    private String listedLanguage = "";
    private @Nullable Palette swatched;
    private List<PaletteEntry> swatchedEntries = List.of();
    private int swatchedVersion = -1;
    private List<Swatch> swatches = List.of();
    private @Nullable ScatterBlocks scattered;
    private boolean anyInGame;

    PaletteBrushTool() {
        one.add(0, 0, 0);
    }

    @Override
    public String name() { return tr("axiom.chromastudio.brush"); }

    @Override
    public void reset() {
        endStroke();
        picked = null;
        pickedIn = null;
        clearPreview();
    }

    private void endStroke() {
        stroke.clear();
        pather = null;
        scatter = null;
        plan = null;
        mask = null;
        strokeBuild = null;
    }

    @Override
    public boolean callUseTool() {
        endStroke();
        Palette to = current();
        if (to == null || !ChromaStudioApi.indexReady()) return false;
        ClientLevel level = Minecraft.getInstance().level;
        if (level == null) return false;
        refused = null;
        if (wholeBuild()) {
            BlockHitResult hit = AxiomTools.tools.raycastBlock();
            if (hit == null || hit.getType() != HitResult.Type.BLOCK) return false;
            BlockPos at = hit.getBlockPos().immutable();
            if (!FoundBuild.member(level, at, level.getBlockState(at))) {
                refused = tr("axiom.chromastudio.brush.not_build");
                return false;
            }
            picked = at;
            pickedIn = level;
            // The old preview goes at once, so Apply can't send it before the new one has been made.
            clearPreview();
            return true;
        }
        if (mode == Mode.SCATTER) {
            List<ScatterBlocks.Placed> blocks = ScatterBlocks.of(to).blocks();
            if (blocks.isEmpty()) return false;
            scatter = new WeightedPick<>(blocks, ScatterBlocks.Placed::weight);
            seed = System.nanoTime();
        } else {
            Palette from;
            if (fromKey.equals(FROM_BUILD)) {
                if (!find(level, AxiomTools.tools.raycastBlock())) return false;
                from = found.kinds();
                strokeBuild = new HashSet<>();
                for (long b : found.blocks()) strokeBuild.add(b);
            } else {
                from = from();
            }
            try {
                plan = RecolourPlan.of(to, from, match);
            } catch (Refused r) {
                refused = r.reason().getString();
                return false;
            }
            if (plan.size() == 0) {
                refused = tr("axiom.chromastudio.nothing_to_change");
                return false;
            }
        }
        mask = AxiomMask.now(level);
        pather = AxiomTools.pathers.createUnique(radius[0], BallShape.SPHERE);
        return true;
    }

    /** Finds the build at a block for the shading range, saying why not in the panel when there's none. */
    private boolean find(ClientLevel level, @Nullable BlockHitResult hit) {
        FoundBuild build = hit == null || hit.getType() != HitResult.Type.BLOCK ? null : FoundBuild.from(level, hit.getBlockPos());
        if (build == null) {
            refused = tr("axiom.chromastudio.brush.not_build");
            return false;
        }
        showFound(build);
        return true;
    }

    private void showFound(FoundBuild build) {
        found = build;
        List<Swatch> out = new ArrayList<>();
        for (PaletteEntry e : build.kinds().entries) {
            Color c = PaletteBlocks.colour(e);
            if (c != null) out.add(new Swatch(c, PaletteBlocks.name(e).getString()));
        }
        out.sort(Comparator.comparingDouble(s -> s.colour().oklab().l()));
        foundSwatches = out.subList(0, Math.min(out.size(), MAX_SHOWN));
    }

    @Override
    public boolean callConfirm() { return wholeBuild() && apply(); }

    @Override
    public boolean callDelete() {
        if (!wholeBuild() || picked == null) return false;
        reset();
        return true;
    }

    /**
     * Drops the whole-build preview once Axiom has stopped drawing this tool, as when the player leaves the editor or
     * picks another tool, neither of which Axiom tells a tool about. The world would otherwise go on showing blocks
     * that aren't there. The block it was used on is kept, so the preview comes back with the tool. Called every tick.
     */
    void tick() {
        if (overriding && System.nanoTime() - drawn > 250_000_000L) clearPreview();
    }

    private boolean wholeBuild() { return mode == Mode.RECOLOUR && wholeBuild; }

    @Override
    public void render(IAxiomWorldRenderContext rc) {
        drawn = System.nanoTime();
        ClientLevel level = Minecraft.getInstance().level;
        if (level == null) return;
        if (wholeBuild()) {
            renderBuild(rc, level);
            return;
        }
        if (overriding) clearPreview();
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
            endStroke();
            return;
        }
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        pather.update((x, y, z) -> {
            if (!mask.allows(x, y, z) || strokeBuild != null && !strokeBuild.contains(BuildSearch.pack(x, y, z))) return;
            BlockState now = level.getBlockState(pos.set(x, y, z));
            BlockState next = scatter != null ? scatter.at(seed, x, y, z).state() : plan.apply(now);
            if (next != null && next != now) stroke.addBlock(x, y, z, next);
        });
        // A slow pulse, as Axiom's own brushes show a stroke that hasn't been committed yet.
        float pulse = (float) Math.sin(rc.nanos() / 400_000_000.0);
        stroke.render(rc, Vec3.ZERO, 0.75f + pulse * 0.25f, 0.3f - pulse * 0.2f);
    }

    private void renderBuild(IAxiomWorldRenderContext rc, ClientLevel level) {
        // A preview belongs to the world it was made in, so a trip to another dimension or server doesn't carry it over.
        if (picked != null && pickedIn != level) reset();
        Palette to = current();
        Palette from = from();
        // The palettes' blocks by their hash, which changes with any edit, rather than a copy of them every frame.
        Object key = picked == null || to == null ? null : Arrays.asList(picked, to, to.entries.hashCode(), fromKey,
                from == null ? 0 : from.entries.hashCode(), match, BlockIndex.get().version());
        if (!Objects.equals(key, previewFor)) {
            preview(level, to, from);
            previewFor = key;
            // A big preview can take longer than the tick check waits, which mustn't take it for the editor closing.
            drawn = System.nanoTime();
        }
        if (picked == null) {
            BlockHitResult hit = AxiomTools.tools.raycastBlock();
            if (hit != null && hit.getType() == HitResult.Type.BLOCK) one.render(rc, Vec3.atLowerCornerOf(hit.getBlockPos()), Effects.BLUE | Effects.OUTLINE);
        } else {
            touched.render(rc, Vec3.ZERO, Effects.OUTLINE);
        }
    }

    /**
     * Works out what recolouring the build at {@link #picked} would do and shows it in the world in place of the
     * blocks there, with the blocks that change outlined.
     */
    private void preview(ClientLevel level, @Nullable Palette to, @Nullable Palette from) {
        clearPreview();
        if (picked == null || to == null || !ChromaStudioApi.indexReady()) return;
        refused = null;
        FoundBuild build = FoundBuild.from(level, picked);
        if (build == null) {
            refused = tr("axiom.chromastudio.brush.not_build");
            return;
        }
        showFound(build);
        RecolourPlan whole;
        try {
            whole = RecolourPlan.of(to, fromKey.equals(FROM_BUILD) ? build.kinds() : from, match);
        } catch (Refused r) {
            refused = r.reason().getString();
            return;
        }
        AxiomMask.Test allowed = AxiomMask.now(level);
        AxiomTools.tools.acquireChunkRenderOverrider(PREVIEW);
        overriding = true;
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        Set<Block> before = new HashSet<>(), after = new HashSet<>();
        for (long p : build.blocks()) {
            int x = BuildSearch.x(p), y = BuildSearch.y(p), z = BuildSearch.z(p);
            BlockState now = level.getBlockState(pos.set(x, y, z));
            BlockState next = whole.apply(now);
            before.add(now.getBlock());
            if (next == null || next == now || !allowed.allows(x, y, z)) {
                after.add(now.getBlock());
                continue;
            }
            after.add(next.getBlock());
            touched.add(x, y, z);
            planned.add(new Planned(x, y, z, now, next));
            change.addBlock(x, y, z, next);
            AxiomTools.tools.setBlockRenderOverride(x, y, z, next);
        }
        kindsBefore = before.size();
        kindsAfter = after.size();
    }

    private void clearPreview() {
        change.clear();
        planned.clear();
        touched.clear();
        previewFor = null;
        if (overriding) {
            AxiomTools.tools.releaseChunkRenderOverrider(PREVIEW);
            overriding = false;
        }
    }

    /**
     * Sends the whole-build change to Axiom as one entry in its history, and starts over. The preview is made again
     * first, against the world and the tool masks as they are now: when that changes anything, as when a block was
     * broken, the masks or selection changed, or the tool was just used on another build, the new preview stays on show
     * for the player to check and nothing is sent. False when nothing was sent.
     */
    private boolean apply() {
        ClientLevel level = Minecraft.getInstance().level;
        if (planned.isEmpty() || level == null || level != pickedIn) return false;
        List<Planned> shown = List.copyOf(planned);
        Object key = previewFor;
        preview(level, current(), from());
        previewFor = key;
        drawn = System.nanoTime();
        if (!planned.equals(shown)) {
            refused = tr("axiom.chromastudio.brush.changed");
            return false;
        }
        AxiomTools.tools.pushBlockRegionChange(change);
        reset();
        return true;
    }

    @Override
    public void displayImguiOptions() {
        List<Palette> all = palettes();
        Palette shown = current();
        boolean ready = ChromaStudioApi.indexReady();

        ImGui.text(tr("axiom.chromastudio.brush.palette"));
        if (ImGui.beginListBox("##palettes", -1, ImGui.getTextLineHeightWithSpacing() * 7)) {
            for (int i = 0; i < all.size(); i++) {
                // A label is also ImGui's id for the row, and a player's palette may share a preset's name.
                if (ImGui.selectable(labels.get(i) + "##" + i, all.get(i) == shown)) {
                    paletteKey = keys.get(i);
                    remember();
                }
            }
            ImGui.endListBox();
        }
        if (shown != null) {
            readSwatches(shown);
            swatches(swatches, "##swatch");
            if (!anyInGame) ImGui.textWrapped(tr("axiom.chromastudio.brush.empty"));
            else if (mode == Mode.SCATTER && scattered != null && !scattered.leftOut().isEmpty()) {
                ImGui.textWrapped(tr("axiom.chromastudio.brush.left_out",
                        scattered.leftOut().stream().map(e -> PaletteBlocks.name(e).getString()).collect(Collectors.joining(", "))));
            }
        }

        ImGui.separator();
        ImGui.beginDisabled(!ready);
        if (ImGui.radioButton(tr("axiom.chromastudio.brush.scatter"), mode == Mode.SCATTER)) setMode(Mode.SCATTER);
        ImGui.endDisabled();
        ImGui.setItemTooltip(tr(ready ? "axiom.chromastudio.brush.scatter.tip" : "axiom.chromastudio.brush.not_ready"));
        ImGui.sameLine();
        ImGui.beginDisabled(!ready);
        if (ImGui.radioButton(tr("axiom.chromastudio.brush.recolour"), mode == Mode.RECOLOUR)) setMode(Mode.RECOLOUR);
        ImGui.endDisabled();
        ImGui.setItemTooltip(tr(ready ? "axiom.chromastudio.brush.recolour.tip" : "axiom.chromastudio.brush.not_ready"));

        ImGui.beginDisabled(mode != Mode.RECOLOUR);
        if (ImGui.checkbox(tr("axiom.chromastudio.brush.whole"), wholeBuild)) {
            wholeBuild = !wholeBuild;
            refused = null;
            if (!wholeBuild) reset();
            remember();
        }
        ImGui.endDisabled();
        ImGui.setItemTooltip(tr(mode == Mode.RECOLOUR ? "axiom.chromastudio.brush.whole.tip" : "axiom.chromastudio.from.na"));

        ImGui.beginDisabled(wholeBuild());
        ImGui.sliderInt(tr("axiom.chromastudio.brush.radius"), radius, 0, ModSettings.BRUSH_MAX_RADIUS);
        if (ImGui.isItemDeactivatedAfterEdit()) remember();
        ImGui.endDisabled();
        if (wholeBuild()) ImGui.setItemTooltip(tr("axiom.chromastudio.brush.radius.whole"));

        recolourOptions(all);
        if (wholeBuild()) buildActions();
        else if (mode == Mode.RECOLOUR && refused != null) ImGui.textWrapped(refused);
        if (mode == Mode.RECOLOUR && fromKey.equals(FROM_BUILD) && found != null) {
            // What the build the tool found holds, darkest first, as that sets the shading.
            ImGui.textWrapped(tr("axiom.chromastudio.brush.found"));
            swatches(foundSwatches, "##found");
        }
        AxiomMask.unavailable().ifPresentOrElse(why -> ImGui.textWrapped(why.getString()), () -> {
            ImGui.textDisabled(tr("axiom.chromastudio.brush.masks"));
            wrappedTooltip(tr("axiom.chromastudio.brush.masks.tip"));
        });
    }

    private void setMode(Mode m) {
        if (mode == m) return;
        mode = m;
        refused = null;
        reset();
        remember();
    }

    /** Which blocks a recolour changes and how it matches them, greyed out for Scatter. */
    private void recolourOptions(List<Palette> all) {
        boolean recolour = mode == Mode.RECOLOUR;
        ImGui.beginDisabled(!recolour);
        Palette chosen = from();
        String preview = fromKey.equals(FROM_BUILD) ? tr("axiom.chromastudio.from.build") : chosen == null ? tr("axiom.chromastudio.from.any") : fromLabels.get(all.indexOf(chosen));
        if (ImGui.beginCombo(tr("axiom.chromastudio.from") + "##from", preview)) {
            if (ImGui.selectable(tr("axiom.chromastudio.from.build") + "##build", fromKey.equals(FROM_BUILD))) setFrom(FROM_BUILD);
            if (ImGui.selectable(tr("axiom.chromastudio.from.any") + "##any", fromKey.equals(FROM_ANY))) setFrom(FROM_ANY);
            for (int i = 0; i < all.size(); i++) {
                Palette p = all.get(i);
                if (ImGui.selectable(fromLabels.get(i) + "##from" + i, p == chosen)) setFrom(keys.get(i));
            }
            ImGui.endCombo();
        }
        ImGui.endDisabled();
        wrappedTooltip(tr(recolour ? "axiom.chromastudio.brush.from.tip" : "axiom.chromastudio.from.na"));
        for (Recolour.Mode m : Recolour.Mode.values()) {
            if (m.ordinal() > 0) ImGui.sameLine();
            ImGui.beginDisabled(!recolour);
            if (ImGui.radioButton(tr("axiom.chromastudio.match." + m.name().toLowerCase(Locale.ROOT)), match == m)) {
                match = m;
                remember();
            }
            ImGui.endDisabled();
            wrappedTooltip(tr(recolour ? "axiom.chromastudio.match.tip" : "axiom.chromastudio.from.na"));
        }
    }

    private void setFrom(String key) {
        fromKey = key;
        if (!key.equals(FROM_BUILD)) found = null;
        remember();
    }

    /** Apply and Cancel for a whole build, and what the preview shows. */
    private void buildActions() {
        ImGui.beginDisabled(change.isEmpty());
        if (ImGui.button(tr("axiom.chromastudio.gradient_tool.apply"))) apply();
        ImGui.endDisabled();
        ImGui.setItemTooltip(tr(change.isEmpty() ? "axiom.chromastudio.brush.apply.none" : "axiom.chromastudio.gradient_tool.apply.tip"));
        ImGui.sameLine();
        ImGui.beginDisabled(picked == null);
        if (ImGui.button(tr("axiom.chromastudio.gradient_tool.cancel"))) reset();
        ImGui.endDisabled();
        ImGui.setItemTooltip(tr(picked == null ? "axiom.chromastudio.gradient_tool.cancel.none" : "axiom.chromastudio.brush.cancel.tip"));
        if (refused != null) ImGui.textWrapped(refused);
        if (picked == null) {
            if (refused == null) ImGui.textWrapped(tr("axiom.chromastudio.brush.pick"));
        } else if (found != null) {
            ImGui.textWrapped(tr("axiom.chromastudio.brush.count", change.count(), found.blocks().length));
            ImGui.textWrapped(tr("axiom.chromastudio.brush.kinds", kindsBefore, kindsAfter));
            if (found.capped()) ImGui.textWrapped(tr("axiom.chromastudio.brush.capped", FoundBuild.CAP));
        }
    }

    /** A tooltip for the last item that wraps its text, as these run to a few sentences and would leave the screen. */
    private static void wrappedTooltip(String text) {
        if (!ImGui.beginItemTooltip()) return;
        ImGui.pushTextWrapPos(ImGui.getFontSize() * 30f);
        ImGui.textUnformatted(text);
        ImGui.popTextWrapPos();
        ImGui.endTooltip();
    }

    /** Swatches in rows, each named when hovered. */
    private static void swatches(List<Swatch> shown, String id) {
        // As many to a row as the panel is wide, so a narrow panel wraps them rather than cutting the last off.
        float size = ImGui.getFrameHeight(), gap = ImGui.getStyle().getItemSpacingX();
        int perRow = Math.max(1, (int) ((ImGui.getContentRegionAvailX() + gap) / (size + gap)));
        for (int i = 0; i < shown.size(); i++) {
            Swatch s = shown.get(i);
            Color c = s.colour();
            if (i % perRow != 0) ImGui.sameLine();
            ImGui.colorButton(id + i, c.red() / 255f, c.green() / 255f, c.blue() / 255f, 1f, ImGuiColorEditFlags.NoTooltip);
            ImGui.setItemTooltip(s.tip());
        }
    }

    /**
     * The palettes, and the names the panel shows for them, read again only when one is added, removed or renamed, or
     * the language changes.
     */
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

    /** Reads a palette's swatches and what Scatter does with it, again only when its blocks or the index change. */
    private void readSwatches(Palette p) {
        int version = BlockIndex.get().ready() ? BlockIndex.get().version() : -1;
        if (p == swatched && p.entries.equals(swatchedEntries) && version == swatchedVersion) return;
        swatched = p;
        swatchedEntries = List.copyOf(p.entries);
        swatchedVersion = version;
        List<Swatch> out = new ArrayList<>();
        anyInGame = false;
        for (PaletteEntry e : p.entries) {
            anyInGame |= PaletteBlocks.state(e) != null;
            Color c = PaletteBlocks.colour(e);
            if (c == null) continue;
            String name = PaletteBlocks.name(e).getString();
            out.add(new Swatch(c, e.weight() > 1 ? tr("axiom.chromastudio.brush.weight", name, e.weight()) : name));
        }
        swatches = out;
        scattered = version < 0 ? null : ScatterBlocks.of(p);
    }

    private @Nullable Palette current() {
        List<Palette> all = palettes();
        // The panel's choice, while it's still in the list: the player may have deleted it in Chroma Studio's window.
        for (int i = 0; i < all.size(); i++) if (keys.get(i).equals(paletteKey)) return all.get(i);
        Palette selected = PaletteStore.get().selected();
        return selected != null ? selected : all.isEmpty() ? null : all.getFirst();
    }

    /** The From palette, while it's still in the list, or null for the build or every block but the ground. */
    private @Nullable Palette from() {
        List<Palette> all = palettes();
        for (int i = 0; i < all.size(); i++) if (keys.get(i).equals(fromKey)) return all.get(i);
        return null;
    }

    /**
     * How the panel's choices name each palette in the settings file: a preset by its id, the player's own by its
     * name, and a second palette of the player's with the same name by its name and how many came before it.
     */
    private static List<String> keys(List<Palette> all) {
        List<String> out = new ArrayList<>();
        Map<String, Integer> seen = new HashMap<>();
        for (Palette p : all) {
            String preset = PaletteStore.get().presetId(p);
            if (preset != null) {
                out.add("preset:" + preset);
                continue;
            }
            int before = seen.merge(p.name, 1, Integer::sum) - 1;
            out.add("own:" + p.name + (before == 0 ? "" : "#" + before));
        }
        return out;
    }

    /** Keeps the panel's choices for next session. */
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
