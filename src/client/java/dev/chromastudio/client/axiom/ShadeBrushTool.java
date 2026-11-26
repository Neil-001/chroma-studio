package dev.chromastudio.client.axiom;

import static dev.chromastudio.client.axiom.AxiomTools.tr;

import com.moulberry.axiomclientapi.CustomTool;
import com.moulberry.axiomclientapi.Effects;
import com.moulberry.axiomclientapi.IAxiomWorldRenderContext;
import com.moulberry.axiomclientapi.pathers.BallShape;
import com.moulberry.axiomclientapi.regions.BooleanRegion;
import dev.chromastudio.client.ModSettings;
import dev.chromastudio.client.PaletteStore;
import dev.chromastudio.client.action.PaletteBlocks;
import dev.chromastudio.client.axiom.AxiomBridge.Refused;
import dev.chromastudio.client.index.BlockIndex;
import dev.chromastudio.client.index.Entry;
import dev.chromastudio.client.index.Kind;
import dev.chromastudio.client.index.TintContext;
import dev.chromastudio.core.color.Color;
import dev.chromastudio.core.color.Face;
import dev.chromastudio.core.palette.Palette;
import dev.chromastudio.core.palette.PaletteEntry;
import dev.chromastudio.core.palette.QueuedChanges;
import dev.chromastudio.core.palette.Recolour;
import dev.chromastudio.core.palette.ShadeSteps;
import dev.chromastudio.core.palette.ShadeSteps.Direction;
import dev.chromastudio.core.palette.WeightedPick;
import imgui.moulberry92.ImGui;
import imgui.moulberry92.flag.ImGuiHoveredFlags;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.IntPredicate;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.EmptyBlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.Fallable;
import net.minecraft.world.level.block.WeatheringCopper;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

final class ShadeBrushTool implements CustomTool, GuardedTool.PutAway {
    private static final double[] STEPS = {0.03, 0.045, 0.06, 0.08, 0.1};
    private static final List<Kind> CHANGEABLE = List.of(Kind.FULL, Kind.SLAB, Kind.STAIRS, Kind.WALL, Kind.FENCE, Kind.FENCE_GATE);
    private static final Set<Kind> SHAPES = EnumSet.of(Kind.SLAB, Kind.STAIRS, Kind.WALL);

    private static final int MASK_FRAMES = 20;

    private static final Set<Block> STORAGE = Set.of(Blocks.COAL_BLOCK, Blocks.LAPIS_BLOCK, Blocks.RAW_IRON_BLOCK, Blocks.RAW_COPPER_BLOCK, Blocks.RAW_GOLD_BLOCK);

    private final BrushStroke stroke = new BrushStroke();
    private boolean dropped;
    private final AxiomPanel.Layout layout = new AxiomPanel.Layout();
    private final BooleanRegion outline = AxiomTools.regions.createBoolean();
    private final int[] radius = {3};
    private int outlineRadius = -1;
    private final int[] stepSize = {3};
    private final int[] strength = {100};
    private Direction direction = Direction.DARKER;
    private Palette from;
    private final Set<Kind> kinds = EnumSet.copyOf(CHANGEABLE);
    private @Nullable BlockState hovered;
    private @Nullable BlockState pointed;
    private boolean overPanel;
    private @Nullable BlockPos maskedAsked;
    private int maskedFrame;
    private boolean masked;
    private @Nullable Plan plan;

    @Override
    public String name() { return tr("axiom.chromastudio.shade"); }

    @Override
    public void reset() {
        if (stroke.active()) dropped = true;
        stroke.cancel();
    }

    @Override
    public void putAway() { stroke.away(); }

    @Override
    public boolean callUseTool() {
        if (AxiomTools.off()) return false;
        stroke.cancel();
        stroke.forget();
        ClientLevel level = Minecraft.getInstance().level;
        if (level == null) return false;
        QueuedChanges<BlockState> queue = AxiomChanges.queue(level);
        long seed = System.nanoTime();
        int size = AxiomPanel.radius(radius);
        try {
            return stroke.start(level, queue, size, _ -> painterNow(seed, size));
        } catch (Refused _) {
            return false;
        }
    }

    private BrushStroke.@Nullable Painter painterNow(long seed, int size) {
        if (AxiomPanel.radius(radius) != size) return null;
        Plan.Key key = key();
        Plan p = key == null ? null : Plan.of(key, BlockIndex.get(), PaletteBlocks.tint());
        if (p == null || p.steps == null) return null;
        Direction d = direction;
        Set<Kind> k = EnumSet.copyOf(kinds);
        double share = AxiomPanel.held(strength, 10, 100) / 100.0;
        return (x, y, z, now, _, put) -> {
            BlockState next = WeightedPick.unit(seed, x, y, z) < share ? p.apply(now, d, k) : null;
            if (next != null) put.put(x, y, z, next);
        };
    }

    void tick() { stroke.tick(); }

    @Override
    public void render(IAxiomWorldRenderContext rc) {
        if (AxiomTools.off()) return;
        ClientLevel level = Minecraft.getInstance().level;
        if (stroke.active()) {
            dropped = false;
            stroke.render(rc);
            return;
        }
        BlockHitResult hit = AxiomTools.tools.raycastBlock();
        boolean onBlock = hit != null && hit.getType() == HitResult.Type.BLOCK && level != null;
        hovered = onBlock ? level.getBlockState(hit.getBlockPos()) : null;
        if (!onBlock) return;
        pointed = hovered;
        BlockPos at = hit.getBlockPos();
        int frame = AxiomTools.frame();
        if (!at.equals(maskedAsked) || frame - maskedFrame >= MASK_FRAMES || frame < maskedFrame) {
            maskedAsked = at.immutable();
            maskedFrame = frame;
            masked = !AxiomMask.scripted() && !AxiomMask.peek(level, at.getX(), at.getY(), at.getZ()).orElse(true);
        }
        int r = AxiomPanel.radius(radius);
        if (outlineRadius != r) {
            outlineRadius = r;
            outline.clear();
            BallShape.SPHERE.fillRegion(outline, outlineRadius);
        }
        outline.render(rc, Vec3.atLowerCornerOf(hit.getBlockPos()), Effects.BLUE | Effects.OUTLINE);
    }

    @Override
    public void displayImguiOptions() {
        overPanel = ImGui.isWindowHovered(ImGuiHoveredFlags.RootAndChildWindows);
        layout.draw(this::options, this::bottom);
    }

    private void options() {
        for (Direction d : List.of(Direction.DARKER, Direction.LIGHTER)) {
            String key = "axiom.chromastudio.shade." + d.name().toLowerCase(Locale.ROOT);
            if (d == Direction.LIGHTER) AxiomPanel.besideChoice(tr(key));
            if (AxiomPanel.radio(tr(key), direction == d, true, tr(key + ".tip"))) layout.later(() -> direction = d);
        }
        AxiomPanel.radius(radius, true, tr("axiom.chromastudio.shade.radius.tip", ModSettings.BRUSH_MAX_RADIUS));
        AxiomPanel.slider(tr("axiom.chromastudio.shade.step"), stepSize, 1, STEPS.length, "%d", true, tr("axiom.chromastudio.shade.step.tip"));
        AxiomPanel.slider(tr("axiom.chromastudio.shade.strength"), strength, 10, 100, "%d%%", true, tr("axiom.chromastudio.shade.strength.tip"));

        List<Palette> all = PaletteStore.get().all();
        Palette chosen = from();
        String any = tr("axiom.chromastudio.shade.any");
        String shown = chosen == null ? any : AxiomBridge.fromName(chosen), fitted = AxiomPanel.fit(shown, AxiomPanel.comboWidth());
        if (ImGui.beginCombo(tr("axiom.chromastudio.shade.from") + "##shades", fitted)) {
            if (ImGui.selectable(any + "##any", chosen == null)) layout.later(() -> from = null);
            for (int i = 0; i < all.size(); i++) {
                Palette p = all.get(i);
                if (ImGui.selectable(AxiomBridge.fromName(p) + "##from" + i, p == chosen)) layout.later(() -> from = p);
            }
            ImGui.endCombo();
        }
        AxiomPanel.tip(fitted.equals(shown) ? tr("axiom.chromastudio.shade.from.tip") : shown + "\n" + tr("axiom.chromastudio.shade.from.tip"));

        String on = kinds.size() == CHANGEABLE.size() ? tr("axiom.chromastudio.shade.kinds.all") : tr("axiom.chromastudio.shade.kinds.some", kinds.size(), CHANGEABLE.size());
        if (AxiomPanel.disclosure(tr("axiom.chromastudio.shade.kinds", on), "shapes")) {
            for (int i = 0; i < CHANGEABLE.size(); i++) {
                Kind k = CHANGEABLE.get(i);
                String label = tr("kind.chromastudio." + k.name().toLowerCase(Locale.ROOT));
                if (i > 0) AxiomPanel.besideChoice(label);
                if (ImGui.checkbox(label + "##kind", kinds.contains(k))) layout.later(() -> {
                    if (!kinds.remove(k)) kinds.add(k);
                });
            }
        }
        AxiomPanel.heading(tr("axiom.chromastudio.shade.preview"));
        preview();
    }

    private void bottom() {
        Optional<AxiomPanel.Status> masks = AxiomPanel.masks();
        if (masks.isPresent()) {
            AxiomPanel.status(masks.get());
            return;
        }
        Component why = stroke.refused();
        if (why == null && dropped) why = AxiomChanges.refusal(QueuedChanges.Refusal.EDITOR);
        if (why != null) {
            AxiomPanel.status(AxiomPanel.refusal(why, "axiom.chromastudio.again.brush"));
            return;
        }
        if (!BlockIndex.get().ready()) {
            AxiomPanel.status(AxiomPanel.Status.of(tr("axiom.chromastudio.not_ready")));
            return;
        }
        if (plan() == null) {
            AxiomPanel.status(AxiomPanel.Status.of(tr("axiom.chromastudio.shade.no_shades")).withTip(tr("axiom.chromastudio.shade.no_shades.tip")));
        }
    }

    private void preview() {
        float top = ImGui.getCursorPosY(), least = ImGui.getFrameHeightWithSpacing() * 3;
        if (previewRows() && stroke.sent() > 0) AxiomPanel.note(AxiomTools.count("axiom.chromastudio.changed", stroke.sent(), stroke.sent()));
        float used = ImGui.getCursorPosY() - top;
        if (used < least) ImGui.dummy(0, least - used);
    }

    private boolean previewRows() {
        Plan p = plan();
        if (p == null || stroke.refused() != null || dropped || !AxiomEditor.known() || AxiomMask.unavailable().isPresent()) return false;
        BlockState here = overPanel ? pointed : hovered;
        if (here == null) {
            AxiomPanel.wrapped(AxiomNames.text("axiom.chromastudio.shade.point"));
            return true;
        }
        named("here", here, here.getBlock().getName().getString());
        BlockState next = masked && !AxiomMask.scripted() ? null : p.apply(here, direction, kinds);
        if (next == null) {
            AxiomPanel.wrapped(tr(masked && !AxiomMask.scripted() ? "axiom.chromastudio.shade.left.masked" : p.whyLeft(here, direction, kinds)));
            return true;
        }
        boolean scripted = AxiomMask.scripted() && !AxiomMask.windowClosed().orElse(false);
        named("next", next, tr(scripted ? "axiom.chromastudio.shade.becomes.scripted" : "axiom.chromastudio.shade.becomes", next.getBlock().getName()));
        return true;
    }

    private static void named(String id, BlockState state, String label) {
        Optional<Color> c = BlockIndex.get().colourOf(state, PaletteBlocks.queryFace(), PaletteBlocks.tint());
        if (c.isPresent()) {
            AxiomPanel.swatch(id, new AxiomPanel.Swatch(c.get(), ""));
            AxiomPanel.sameLine();
        }
        ImGui.alignTextToFramePadding();
        AxiomPanel.wrapped(label);
    }

    private @Nullable Palette from() {
        for (Palette p : PaletteStore.get().all()) if (p == from) return p;
        return null;
    }

    private @Nullable Plan plan() {
        Plan.Key key = key();
        if (key == null) return null;
        if (plan == null || !plan.key.equals(key)) plan = Plan.of(key, BlockIndex.get(), PaletteBlocks.tint());
        return plan.steps == null ? null : plan;
    }

    private Plan.@Nullable Key key() {
        BlockIndex index = BlockIndex.get();
        if (!index.ready()) return null;
        Palette shades = from();
        return new Plan.Key(index.version(), shades, shades == null ? 0 : shades.entries.hashCode(), STEPS[AxiomPanel.held(stepSize, 1, STEPS.length) - 1], PaletteBlocks.queryFace(),
                ModSettings.biomeMode + " " + ModSettings.biome);
    }

    private static final class Plan {
        record Key(int version, @Nullable Palette palette, int entries, double step, Face face, String biome) {}

        final Key key;
        private final BlockIndex index;
        private final TintContext tint;
        private final List<Entry> shades;
        private final Map<Block, Integer> place = new IdentityHashMap<>();
        private final @Nullable Map<Block, Set<Kind>> listed;
        private final @Nullable ShadeSteps steps;
        private final Map<Kind, boolean[]> hasShape = new EnumMap<>(Kind.class);
        private final Map<Direction, Map<Block, Optional<RecolourPlan>>> changes = new EnumMap<>(Direction.class);

        private Plan(Key key, BlockIndex index, TintContext tint, List<Entry> candidates, @Nullable Map<Block, Set<Kind>> listed) {
            this.key = key;
            this.listed = listed;
            this.index = index;
            this.tint = tint;
            List<Entry> kept = new ArrayList<>();
            List<ShadeSteps.Look> looks = new ArrayList<>();
            for (Entry e : candidates) {
                ShadeSteps.Look look = look(e);
                if (look == null) continue;
                place.put(e.block(), kept.size());
                kept.add(e);
                looks.add(look);
            }
            this.shades = kept;
            this.steps = looks.isEmpty() ? null : new ShadeSteps(looks, key.step, key.palette != null);
        }

        static Plan of(Key key, BlockIndex index, TintContext tint) {
            List<Entry> shades = new ArrayList<>();
            Set<Block> seen = new HashSet<>();
            Map<Block, Set<Kind>> listed = null;
            if (key.palette == null) {
                Set<Block> building = buildingTabs();
                for (Entry e : index.entries()) {
                    if (e.kind() == Kind.FULL && e.isDefaultState() && anyShade(e) && (building.isEmpty() || building.contains(e.block())) && seen.add(e.block())) shades.add(e);
                }
            } else {
                listed = new IdentityHashMap<>();
                for (PaletteEntry pe : key.palette.entries) {
                    BlockState state = PaletteBlocks.state(pe);
                    Entry e = state == null ? null : index.entryOf(state);
                    Entry base = e == null ? null : base(index, e);
                    if (base == null || !paletteShade(base) || !CHANGEABLE.contains(e.kind())) continue;
                    if (seen.add(base.block())) shades.add(base);
                    listed.computeIfAbsent(base.block(), _ -> EnumSet.noneOf(Kind.class)).add(e.kind());
                }
            }
            return new Plan(key, index, tint, shades, listed);
        }

        @Nullable BlockState apply(BlockState here, Direction d, Set<Kind> kinds) {
            Entry e = changeable(here, kinds);
            if (e == null) return null;
            RecolourPlan change = changes.computeIfAbsent(d, _ -> new IdentityHashMap<>()).computeIfAbsent(here.getBlock(), _ -> change(e, d)).orElse(null);
            return change == null ? null : change.apply(here);
        }

        String whyLeft(BlockState here, Direction d, Set<Kind> kinds) {
            Entry e = index.entryOf(here);
            if (e != null && turns(here, e.kind())) return "axiom.chromastudio.shade.left.turns";
            if (e == null || never(here, e)) return "axiom.chromastudio.shade.left.never";
            if (RecolourPlan.ground(here)) return "axiom.chromastudio.shade.left.ground";
            if (!kinds.contains(e.kind())) return "axiom.chromastudio.shade.left.kind";
            return "axiom.chromastudio.shade.left." + d.name().toLowerCase(Locale.ROOT);
        }

        private @Nullable Entry changeable(BlockState here, Set<Kind> kinds) {
            Entry e = index.entryOf(here);
            if (e == null || never(here, e) || RecolourPlan.ground(here) || !kinds.contains(e.kind())) return null;
            return e;
        }

        private static boolean never(BlockState here, Entry e) {
            return here.isAir() || here.getBlock() instanceof EntityBlock || e.technical() || !CHANGEABLE.contains(e.kind())
                    || e.kind() == Kind.FULL && !e.opaque() || turns(here, e.kind());
        }

        private static boolean turns(BlockState s, Kind kind) {
            return kind == Kind.FULL && (s.hasProperty(BlockStateProperties.AXIS) || s.hasProperty(BlockStateProperties.HORIZONTAL_FACING)
                    || s.hasProperty(BlockStateProperties.FACING));
        }

        private Optional<RecolourPlan> change(Entry e, Direction d) {
            Entry base = base(index, e);
            ShadeSteps.Look look = base == null ? null : look(base);
            if (look == null || steps == null) return Optional.empty();
            Kind kind = e.kind();
            boolean[] shaped = kind == Kind.FULL ? null : hasShape.computeIfAbsent(kind, this::hasShape);
            IntPredicate allowed = i -> (shaped == null || shaped[i]) && lends(i, kind);
            Integer at = place.get(base.block());
            int picked = at != null ? steps.next(at, d, allowed) : steps.next(look, d, allowed);
            if (picked < 0) return Optional.empty();
            Palette to = new Palette(""), source = new Palette("");
            to.entries.add(PaletteBlocks.of(shades.get(picked)));
            source.entries.add(PaletteBlocks.of(e));
            try {
                RecolourPlan change = RecolourPlan.of(to, source, Recolour.Mode.CLOSEST, key.face, tint);
                return change.size() == 0 ? Optional.empty() : Optional.of(change);
            } catch (Refused _) {
                return Optional.empty();
            }
        }

        private boolean lends(int shade, Kind kind) {
            if (listed == null) return true;
            Set<Kind> kinds = listed.get(shades.get(shade).block());
            return kinds.contains(Kind.FULL) || kinds.contains(kind);
        }

        private boolean[] hasShape(Kind kind) {
            boolean[] out = new boolean[shades.size()];
            for (int i = 0; i < out.length; i++) for (Entry m : index.materialSet(shades.get(i))) out[i] |= m.kind() == kind;
            return out;
        }

        private ShadeSteps.@Nullable Look look(Entry e) {
            Optional<Color> colour = index.colourOf(e.state(), key.face, tint);
            if (colour.isEmpty()) return null;
            double spread = 0;
            List<Face> faces = key.face == Face.ANY ? List.of(Face.UP, Face.DOWN, Face.NORTH, Face.SOUTH, Face.WEST, Face.EAST) : List.of(key.face);
            for (Face f : faces) spread += e.face(f, tint).spread() / faces.size();
            boolean shaped = false;
            for (Entry m : index.materialSet(e)) shaped |= SHAPES.contains(m.kind());
            return new ShadeSteps.Look(colour.get(), spread, family(e.id()), material(e.state()), shaped, ShadeSteps.decorated(e.id()));
        }

        private static String material(BlockState s) {
            if (s.is(BlockTags.MINEABLE_WITH_PICKAXE)) return "pickaxe";
            if (s.is(BlockTags.MINEABLE_WITH_AXE)) return "axe";
            if (s.is(BlockTags.MINEABLE_WITH_SHOVEL)) return "shovel";
            if (s.is(BlockTags.MINEABLE_WITH_HOE)) return "hoe";
            return "";
        }

        private static String family(String id) {
            return id.substring(Math.max(id.indexOf(':'), id.lastIndexOf('_')) + 1);
        }

        private static @Nullable Entry base(BlockIndex index, Entry e) {
            if (e.kind() == Kind.FULL) return e;
            for (Entry m : index.materialSet(e)) if (m.kind() == Kind.FULL) return m;
            return null;
        }

        private static boolean paletteShade(Entry e) {
            Block b = e.block();
            return e.kind() == Kind.FULL && e.opaque() && !(b instanceof EntityBlock) && !(b instanceof Fallable) && !turns(e.state(), e.kind());
        }

        private static boolean anyShade(Entry e) {
            Block b = e.block();
            BlockState s = e.state();
            return paletteShade(e) && !e.technical() && !e.creativeOnly() && !e.lightSource() && !RecolourPlan.ground(s)
                    && !(b instanceof WeatheringCopper) && !s.isSignalSource() && !s.is(BlockTags.BEACON_BASE_BLOCKS) && !STORAGE.contains(b)
                    && s.getDestroySpeed(EmptyBlockGetter.INSTANCE, BlockPos.ZERO) >= 0 && !e.id().contains("chiseled");
        }

        private static Set<Block> buildingTabs() {
            Set<Block> out = new HashSet<>();
            for (var key : List.of(CreativeModeTabs.BUILDING_BLOCKS, CreativeModeTabs.COLORED_BLOCKS)) {
                CreativeModeTab tab = BuiltInRegistries.CREATIVE_MODE_TAB.getValue(key);
                if (tab == null) continue;
                for (ItemStack stack : tab.getDisplayItems()) if (stack.getItem() instanceof BlockItem item) out.add(item.getBlock());
            }
            return out;
        }
    }
}
