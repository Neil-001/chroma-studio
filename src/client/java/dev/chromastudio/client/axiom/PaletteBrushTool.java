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
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * An Axiom brush that paints with one of Chroma Studio's palettes. Scatter fills the brush's ball with the palette's
 * blocks, each as often as its weight says, as {@link ScatterBlocks} has them. Recolour changes each block the ball
 * touches as a recolour script into the palette would, through the same {@link RecolourPlan}, and so leaves the
 * ground, air and liquids alone. A stroke ({@link BrushStroke}) shows while the button is held and goes to Axiom as one
 * change when it's let go, so one undo takes it back.
 *
 * <p>Recolour can also take a whole build at once: used on a block, the tool finds the build joined to it
 * ({@link FoundBuild}) and shows it recoloured ({@link BuildPreview}), and Apply sends it to Axiom as one change, as
 * the gradient tool does. Its default From is the build itself, whose darkest and lightest blocks set the range the
 * palette is spread over; against every block in the game, a grey house would use only the palette's few middle
 * shades. Every change keeps to the player's tool masks in Axiom ({@link AxiomMask}). The panel's choices are kept in
 * {@link ModSettings}.
 */
final class PaletteBrushTool implements CustomTool {
    /** From choices that aren't palettes: the build the tool is used on, and every block but the ground. */
    private static final String FROM_BUILD = "build", FROM_ANY = "any";
    /** The most of a build's kinds the panel shows. */
    private static final int MAX_SHOWN = 32;

    private enum Mode { SCATTER, RECOLOUR }

    /**
     * Counts the edits to a palette's blocks: a copy is kept and compared each frame, which allocates nothing, and is
     * made again only when the blocks differ, as after an edit in Chroma Studio's window.
     */
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
    private BrushStroke.Painter painter;
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

    /** Why the last use or preview couldn't go ahead, shown in the panel until the next. */
    private @Nullable String said;
    /** Why the last Apply didn't go, until the next use or Apply. */
    private @Nullable Component refused;
    /** The last build found, to show what sets the shading, as swatches from darkest to lightest. */
    private @Nullable FoundBuild found;
    private List<AxiomPanel.Swatch> foundSwatches = List.of();

    // The whole-build preview.
    private final BuildPreview preview = new BuildPreview("chromastudio_brush");
    /** The block the tool was used on, whose build is shown recoloured, or null, and the world it's in. */
    private @Nullable BlockPos picked;
    private @Nullable ClientLevel pickedIn;
    /** What the preview on show was made for, so it's made again only when something changes. */
    private @Nullable Object previewFor;
    /** Count edits to the palettes the preview is made from, so it's made again when one is. */
    private final Edits toEdits = new Edits(), fromEdits = new Edits();
    /** How many kinds of block the previewed build has now, and will have once recoloured, and how many blocks the masks keep out. */
    private int kindsBefore, kindsAfter, masked;

    // The panel's view of the palettes, made again only when they change.
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
        stroke.cancel();
        picked = null;
        pickedIn = null;
        refused = null;
        clearPreview();
    }

    private void clearPreview() {
        preview.clear();
        previewFor = null;
    }

    @Override
    public boolean callUseTool() {
        stroke.cancel();
        Palette to = current();
        if (to == null || !ChromaStudioApi.indexReady()) return false;
        ClientLevel level = Minecraft.getInstance().level;
        if (level == null) return false;
        QueuedChanges<BlockState> queue = AxiomChanges.queue(level);
        said = null;
        refused = null;
        stroke.forget();
        if (wholeBuild()) {
            BlockHitResult hit = AxiomTools.tools.raycastBlock();
            if (hit == null || hit.getType() != HitResult.Type.BLOCK) return false;
            BlockPos at = hit.getBlockPos().immutable();
            if (!FoundBuild.member(level, at, level.getBlockState(at))) {
                said = tr("axiom.chromastudio.not_build");
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
            WeightedPick<ScatterBlocks.Placed> scatter = new WeightedPick<>(blocks, ScatterBlocks.Placed::weight);
            long seed = System.nanoTime();
            // Painting over one half of a door or a tall plant would leave the other half on its own.
            painter = (x, y, z, now, _, put) -> {
                // Painting over one half of a door or a tall plant would leave the other half on its own.
                if (!now.hasProperty(BlockStateProperties.DOUBLE_BLOCK_HALF)) put.put(x, y, z, scatter.at(seed, x, y, z).state());
            };
        } else {
            Palette from;
            Set<Long> build = null;
            if (fromKey.equals(FROM_BUILD)) {
                if (!find(level, AxiomTools.tools.raycastBlock())) return false;
                from = found.kinds();
                // With From set to This build, a stroke that reaches another build leaves it alone.
                build = new HashSet<>();
                for (long b : found.blocks()) build.add(b);
            } else {
                from = from();
                if (fromGone()) {
                    said = tr("axiom.chromastudio.brush.from_gone");
                    return false;
                }
            }
            RecolourPlan plan;
            try {
                plan = RecolourPlan.of(to, from, match);
            } catch (Refused r) {
                said = r.reason().getString();
                return false;
            }
            if (plan.size() == 0) {
                said = tr("axiom.chromastudio.nothing_to_change");
                return false;
            }
            Set<Long> inBuild = build;
            // A door changes as a whole, its other half too, unless the masks or the build keep that half out.
            painter = (x, y, z, _, admit, put) -> plan.change(level, x, y, z,
                    (ax, ay, az) -> admit.test(ax, ay, az) && (inBuild == null || inBuild.contains(BuildSearch.pack(ax, ay, az))), put);
        }
        stroke.start(level, queue, AxiomPanel.radius(radius));
        return true;
    }

    /** Finds the build at a block for the shading range, saying why not in the panel when there's none. */
    private boolean find(ClientLevel level, @Nullable BlockHitResult hit) {
        FoundBuild build = hit == null || hit.getType() != HitResult.Type.BLOCK ? null : FoundBuild.from(level, hit.getBlockPos());
        if (build == null) {
            said = tr("axiom.chromastudio.not_build");
            return false;
        }
        showFound(build);
        return true;
    }

    private void showFound(FoundBuild build) {
        found = build;
        List<AxiomPanel.Swatch> out = AxiomPanel.Swatch.of(build.kinds().entries);
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

    /** Called every client tick. The block the tool was used on is kept, so the preview comes back with the tool. */
    void tick(boolean closed) {
        stroke.tick(closed);
        if (preview.tick(closed)) previewFor = null;
    }

    private boolean wholeBuild() { return mode == Mode.RECOLOUR && wholeBuild; }

    @Override
    public void render(IAxiomWorldRenderContext rc) {
        ClientLevel level = Minecraft.getInstance().level;
        if (level == null) return;
        if (wholeBuild()) {
            renderBuild(rc, level);
            return;
        }
        if (stroke.active()) {
            stroke.render(rc, painter);
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
        outline.render(rc, Vec3.atLowerCornerOf(hit.getBlockPos()), Effects.BLUE | Effects.OUTLINE);
    }

    private void renderBuild(IAxiomWorldRenderContext rc, ClientLevel level) {
        // A preview belongs to the world it was made in, so a trip to another dimension or server doesn't carry it over.
        if (picked != null && pickedIn != level) reset();
        Palette to = current();
        Palette from = from();
        Object key = picked == null || to == null ? null : Arrays.asList(picked, to, toEdits.of(to), fromKey,
                from == null ? 0 : fromEdits.of(from), match, BlockIndex.get().version());
        if (!Objects.equals(key, previewFor)) {
            makePreview(level, to, from);
            previewFor = key;
        }
        if (picked == null) {
            BlockHitResult hit = AxiomTools.tools.raycastBlock();
            if (hit != null && hit.getType() == HitResult.Type.BLOCK) one.render(rc, Vec3.atLowerCornerOf(hit.getBlockPos()), Effects.BLUE | Effects.OUTLINE);
        } else {
            preview.render(rc);
        }
    }

    /**
     * Works out what recolouring the build at {@link #picked} would do and shows it in the world in place of the
     * blocks there, with the blocks that change outlined.
     */
    private void makePreview(ClientLevel level, @Nullable Palette to, @Nullable Palette from) {
        preview.clear();
        if (picked == null || to == null || !ChromaStudioApi.indexReady()) return;
        QueuedChanges<BlockState> queue = AxiomChanges.queue(level);
        said = null;
        FoundBuild build = FoundBuild.from(level, picked);
        if (build == null) {
            said = tr("axiom.chromastudio.not_build");
            return;
        }
        showFound(build);
        if (fromGone()) {
            said = tr("axiom.chromastudio.brush.from_gone");
            return;
        }
        RecolourPlan whole;
        try {
            whole = RecolourPlan.of(to, fromKey.equals(FROM_BUILD) ? build.kinds() : from, match);
        } catch (Refused r) {
            said = r.reason().getString();
            return;
        }
        preview.begin(level, queue);
        masked = 0;
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        Set<Block> before = new HashSet<>(), after = new HashSet<>();
        // A door changes only when both its halves may: a capped search can hold one half without the other.
        Set<Long> inBuild = new HashSet<>();
        for (long p : build.blocks()) inBuild.add(p);
        RecolourPlan.Admit admit = (x, y, z) -> preview.allows(x, y, z) && inBuild.contains(BuildSearch.pack(x, y, z));
        for (long p : build.blocks()) {
            int x = BuildSearch.x(p), y = BuildSearch.y(p), z = BuildSearch.z(p);
            BlockState now = level.getBlockState(pos.set(x, y, z));
            before.add(now.getBlock());
            BlockState plain = whole.apply(now);
            boolean changes = plain != null && plain != now;
            if (changes && !preview.allows(x, y, z)) {
                masked++;
                changes = false;
            }
            BlockState next = changes ? whole.apply(level, x, y, z, admit) : null;
            if (next == null) {
                after.add(now.getBlock());
                continue;
            }
            after.add(next.getBlock());
            preview.outline(x, y, z);
            preview.change(x, y, z, now, next);
        }
        kindsBefore = before.size();
        kindsAfter = after.size();
        preview.made();
    }

    /**
     * Sends the whole-build change to Axiom as one entry in its history, and starts over. When anything has changed
     * since the preview was made, nothing is sent: the preview is made again from the world as it is now, for the
     * player to check, and the panel says why. False when nothing was sent.
     */
    private boolean apply() {
        if (preview.isEmpty()) return false;
        Optional<Component> why = preview.apply();
        if (why.isPresent()) {
            refused = why.get();
            previewFor = null;
            return false;
        }
        reset();
        return true;
    }

    @Override
    public void displayImguiOptions() { AxiomPanel.draw(this::panel); }

    private void panel() {
        List<Palette> all = palettes();
        Palette shown = current();
        boolean ready = ChromaStudioApi.indexReady();
        boolean recolour = mode == Mode.RECOLOUR;

        int clicked = AxiomPanel.list(tr("axiom.chromastudio.brush.palette"), "##palettes", labels, all.indexOf(shown));
        if (clicked >= 0) {
            paletteKey = keys.get(clicked);
            remember();
        }
        if (shown != null) {
            readSwatches(shown);
            AxiomPanel.swatches("swatch", swatches);
            if (!anyInGame) AxiomPanel.wrapped(tr("axiom.chromastudio.brush.empty"));
            else if (mode == Mode.SCATTER && scattered != null && !scattered.leftOut().isEmpty()) {
                AxiomPanel.note(tr("axiom.chromastudio.brush.left_out",
                        scattered.leftOut().stream().map(e -> PaletteBlocks.name(e).getString()).collect(Collectors.joining(", "))));
                AxiomPanel.tip(tr("axiom.chromastudio.brush.left_out.tip"));
            }
        }

        if (AxiomPanel.radio(tr("axiom.chromastudio.brush.scatter"), mode == Mode.SCATTER, ready,
                tr(ready ? "axiom.chromastudio.brush.scatter.tip" : "axiom.chromastudio.not_ready"))) setMode(Mode.SCATTER);
        AxiomPanel.besideChoice(tr("axiom.chromastudio.brush.recolour"));
        if (AxiomPanel.radio(tr("axiom.chromastudio.brush.recolour"), recolour, ready,
                tr(ready ? "axiom.chromastudio.brush.recolour.tip" : "axiom.chromastudio.not_ready"))) setMode(Mode.RECOLOUR);
        if (AxiomPanel.checkbox(tr("axiom.chromastudio.whole_build"), wholeBuild, recolour,
                tr(recolour ? "axiom.chromastudio.brush.whole.tip" : "axiom.chromastudio.from.na"))) {
            wholeBuild = !wholeBuild;
            said = null;
            reset();
            remember();
        }
        if (AxiomPanel.radius(radius, !wholeBuild(), tr(wholeBuild() ? "axiom.chromastudio.radius.whole" : "axiom.chromastudio.radius.tip"))) remember();
        recolourOptions(all, recolour);
        AxiomPanel.masks();

        AxiomPanel.separator();
        if (wholeBuild()) {
            AxiomPanel.applyCancel(!preview.isEmpty(), tr("axiom.chromastudio.brush.apply.none"), picked != null, this::apply, this::reset, null);
        }
        Component why = wholeBuild() ? refused : stroke.refused();
        if (why != null) AxiomPanel.wrapped(why);
        if (recolour && fromGone()) AxiomPanel.wrapped(tr("axiom.chromastudio.brush.from_gone"));
        else if (said != null) AxiomPanel.wrapped(said);
        if (!wholeBuild()) {
            AxiomPanel.note(tr("axiom.chromastudio.stroke.how"));
        } else if (picked == null) {
            if (said == null) AxiomPanel.wrapped(tr("axiom.chromastudio.brush.pick"));
        } else if (found != null && said == null) {
            AxiomPanel.wrapped(tr("axiom.chromastudio.count", preview.count(), found.blocks().length));
            if (masked > 0) AxiomPanel.wrapped(tr("axiom.chromastudio.masked", masked));
            AxiomPanel.note(tr("axiom.chromastudio.brush.kinds", kindsBefore, kindsAfter));
            if (found.capped()) AxiomPanel.wrapped(tr("axiom.chromastudio.capped", FoundBuild.CAP));
        }
        if (recolour && fromKey.equals(FROM_BUILD) && found != null) {
            // What the build the tool found holds, darkest first, as that sets the shading.
            AxiomPanel.note(tr("axiom.chromastudio.brush.found"));
            AxiomPanel.swatches("found", foundSwatches);
        }
    }

    private void setMode(Mode m) {
        if (mode == m) return;
        mode = m;
        said = null;
        reset();
        remember();
    }

    /** Which blocks a recolour changes and how it matches them, greyed out for Scatter. */
    private void recolourOptions(List<Palette> all, boolean recolour) {
        ImGui.beginDisabled(!recolour);
        Palette chosen = from();
        String preview = fromKey.equals(FROM_BUILD) ? tr("axiom.chromastudio.from.build") : fromKey.equals(FROM_ANY) ? tr("axiom.chromastudio.from.any") : chosen == null ? tr("axiom.chromastudio.brush.from_choose") : fromLabels.get(all.indexOf(chosen));
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
        AxiomPanel.tip(tr(recolour ? "axiom.chromastudio.brush.from.tip" : "axiom.chromastudio.from.na"));
        for (Recolour.Mode m : Recolour.Mode.values()) {
            String label = tr("axiom.chromastudio.match." + m.name().toLowerCase(Locale.ROOT));
            if (m.ordinal() > 0) AxiomPanel.besideChoice(label);
            if (AxiomPanel.radio(label, match == m, recolour,
                    tr(recolour ? "axiom.chromastudio.match.tip" : "axiom.chromastudio.from.na"))) {
                match = m;
                remember();
            }
        }
    }

    private void setFrom(String key) {
        fromKey = key;
        if (!key.equals(FROM_BUILD)) found = null;
        remember();
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
        // The panel's choice, while it's still in the list: the player may have deleted it in Chroma Studio's window.
        for (int i = 0; i < all.size(); i++) if (keys.get(i).equals(paletteKey)) return all.get(i);
        Palette selected = PaletteStore.get().selected();
        return selected != null ? selected : all.isEmpty() ? null : all.getFirst();
    }

    /**
     * Whether the From choice is a palette that's no longer there, as after it was deleted or renamed. The brush then
     * changes nothing until the player chooses again, rather than every block but the ground in its place.
     */
    private boolean fromGone() { return !fromKey.equals(FROM_BUILD) && !fromKey.equals(FROM_ANY) && from() == null; }

    /** The From palette, while it's still in the list, or null for the build or every block but the ground. */
    private @Nullable Palette from() {
        List<Palette> all = palettes();
        for (int i = 0; i < all.size(); i++) if (keys.get(i).equals(fromKey)) return all.get(i);
        return null;
    }

    /**
     * How the panel's choices name each palette in the settings file: a preset by its id, and the player's own by its
     * file and name together. No two palettes share a file, and a file freed by a rename can only be taken by a palette
     * of another name, so a key never comes to mean a different palette; a renamed one is simply chosen again.
     */
    private static List<String> keys(List<Palette> all) {
        List<String> out = new ArrayList<>();
        for (Palette p : all) {
            String preset = PaletteStore.get().presetId(p), file = PaletteStore.get().fileName(p);
            out.add(preset != null ? "preset:" + preset : file != null ? "file:" + file + ":" + p.name : "own:" + p.name);
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
