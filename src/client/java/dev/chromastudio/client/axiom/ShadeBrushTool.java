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

/**
 * An Axiom brush that shades a build: each block it touches becomes its next shade a step darker or lighter, a block
 * close to it in hue and texture, such as stone bricks to tuff bricks, as {@link ShadeSteps} picks it. A stroke over
 * the same spot steps again, and a stroke the other way steps back. Shapes keep their shape, facing and half, through
 * the same {@link RecolourPlan} the recolour brush uses. Air, liquids, the ground, see-through blocks and blocks with
 * contents stay as they are, as does a block with no shade close enough that way.
 *
 * <p>The shades come from any building block, or only from a palette's, so the shading keeps to a build's materials;
 * among a palette's blocks a step reaches as far as the palette's next block that way.
 * A stroke ({@link BrushStroke}) shows while the button is held and goes to Axiom as one change when it's let go, so
 * one undo takes it back. Every change keeps to the player's tool masks in Axiom ({@link AxiomMask}).
 */
final class ShadeBrushTool implements CustomTool, GuardedTool.PutAway {
    /** The lightness of a step at each size the slider offers, in OKLab. Small is about one shade a builder can see. */
    private static final double[] STEPS = {0.03, 0.045, 0.06, 0.08, 0.1};
    /**
     * The kinds of block the brush can change, which the panel turns on and off. Doors are two blocks, so a ball's edge
     * could part their halves, and trapdoors have see-through textures of their own, unlike their planks.
     */
    private static final List<Kind> CHANGEABLE = List.of(Kind.FULL, Kind.SLAB, Kind.STAIRS, Kind.WALL, Kind.FENCE, Kind.FENCE_GATE);
    private static final Set<Kind> SHAPES = EnumSet.of(Kind.SLAB, Kind.STAIRS, Kind.WALL);

    /** Blocks of coal, lapis and raw ore, which the Building blocks tab lists but no builder takes for a shade of stone. */
    private static final Set<Block> STORAGE = Set.of(Blocks.COAL_BLOCK, Blocks.LAPIS_BLOCK, Blocks.RAW_IRON_BLOCK, Blocks.RAW_COPPER_BLOCK, Blocks.RAW_GOLD_BLOCK);

    private final BrushStroke stroke = new BrushStroke();
    /** Whether a stroke was dropped partway, as when the player picked another tool, for the panel to say so. */
    private boolean dropped;
    private final AxiomPanel.Layout layout = new AxiomPanel.Layout();
    private final BooleanRegion outline = AxiomTools.regions.createBoolean();
    private final int[] radius = {3};
    private int outlineRadius = -1;
    private final int[] stepSize = {3};
    /** The share of the blocks under the brush a stroke changes, in percent. Less than all mixes a shade in. */
    private final int[] strength = {100};
    private Direction direction = Direction.DARKER;
    /** The palette whose blocks are the shades, or null for any building block. */
    private Palette from;
    private final Set<Kind> kinds = EnumSet.copyOf(CHANGEABLE);
    /** The block under the cursor when the button isn't held, for the panel's preview. */
    private @Nullable BlockState hovered;
    private @Nullable Plan plan;

    @Override
    public String name() { return tr("axiom.chromastudio.shade"); }

    @Override
    public void reset() {
        // Axiom resets a tool before it says another was picked, so a stroke dropped here is the one left partway.
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
        // Which blocks a stroke at less than full strength changes, a new choice each stroke, and so part of what the
        // stroke is, as the positions its ball reaches are.
        long seed = System.nanoTime();
        int size = AxiomPanel.radius(radius);
        try {
            return stroke.start(level, queue, size, _ -> painterNow(seed, size));
        } catch (Refused _) {
            return false;
        }
    }

    /**
     * The stroke's painter as the panel is set now: the shades worked out afresh from the index, the palette's blocks
     * and the step size, with the direction, kinds and strength as they are, and no plan kept from before. The stroke
     * makes it as it starts, and again when it's checked before it's sent, which sends nothing unless the stroke
     * painted again with it comes out the same. Null when no block can be a shade, or when Radius isn't
     * {@code size}, the ball the stroke was made with, which Axiom keeps for the whole stroke.
     */
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

    /** Called every client tick. */
    void tick() { stroke.tick(); }

    @Override
    public void render(IAxiomWorldRenderContext rc) {
        if (AxiomTools.off()) return;
        ClientLevel level = Minecraft.getInstance().level;
        if (stroke.active()) {
            // Cleared here, as Axiom draws the tool every frame but its panel only while Tool Options is open.
            dropped = false;
            hovered = null;
            stroke.render(rc);
            return;
        }
        BlockHitResult hit = AxiomTools.tools.raycastBlock();
        boolean onBlock = hit != null && hit.getType() == HitResult.Type.BLOCK && level != null;
        hovered = onBlock ? level.getBlockState(hit.getBlockPos()) : null;
        if (!onBlock) return;
        int r = AxiomPanel.radius(radius);
        if (outlineRadius != r) {
            outlineRadius = r;
            outline.clear();
            BallShape.SPHERE.fillRegion(outline, outlineRadius);
        }
        outline.render(rc, Vec3.atLowerCornerOf(hit.getBlockPos()), Effects.BLUE | Effects.OUTLINE);
    }

    @Override
    public void displayImguiOptions() { layout.draw(this::options, this::bottom); }

    private void options() {
        List<Palette> all = PaletteStore.get().all();
        Palette chosen = from();
        String any = tr("axiom.chromastudio.shade.any");
        if (ImGui.beginCombo(tr("axiom.chromastudio.shade.from") + "##shades", chosen == null ? any : AxiomBridge.fromName(chosen))) {
            if (ImGui.selectable(any + "##any", chosen == null)) from = null;
            for (int i = 0; i < all.size(); i++) {
                Palette p = all.get(i);
                if (ImGui.selectable(AxiomBridge.fromName(p) + "##from" + i, p == chosen)) from = p;
            }
            ImGui.endCombo();
        }
        AxiomPanel.tip(tr("axiom.chromastudio.shade.from.tip"));

        for (Direction d : List.of(Direction.DARKER, Direction.LIGHTER)) {
            String key = "axiom.chromastudio.shade." + d.name().toLowerCase(Locale.ROOT);
            if (d == Direction.LIGHTER) AxiomPanel.besideChoice(tr(key));
            if (AxiomPanel.radio(tr(key), direction == d, true, tr(key + ".tip"))) direction = d;
        }
        AxiomPanel.radius(radius, true, tr("axiom.chromastudio.radius.tip"));
        AxiomPanel.slider(tr("axiom.chromastudio.shade.step"), stepSize, 1, STEPS.length, "%d", true, tr("axiom.chromastudio.shade.step.tip"));
        AxiomPanel.slider(tr("axiom.chromastudio.shade.strength"), strength, 10, 100, "%d%%", true, tr("axiom.chromastudio.shade.strength.tip"));

        AxiomPanel.text(tr("axiom.chromastudio.shade.kinds"));
        AxiomPanel.tip(tr("axiom.chromastudio.shade.kinds.tip"));
        for (int i = 0; i < CHANGEABLE.size(); i++) {
            Kind k = CHANGEABLE.get(i);
            String label = tr("kind.chromastudio." + k.name().toLowerCase(Locale.ROOT));
            // As many to a row as the panel is wide: a box, the gap after it and the label.
            float width = ImGui.getFrameHeight() + ImGui.getStyle().getItemInnerSpacingX() + ImGui.calcTextSize(label).x;
            if (i > 0) {
                AxiomPanel.sameLine();
                if (ImGui.getContentRegionAvailX() < width) ImGui.newLine();
            }
            if (ImGui.checkbox(label + "##kind", kinds.contains(k))) {
                if (!kinds.remove(k)) kinds.add(k);
            }
        }
        AxiomPanel.note(tr("axiom.chromastudio.stroke.how"));
    }

    /** Why the last stroke wasn't made, and what the brush would do to the block under the cursor, held to the panel's bottom. */
    private void bottom() {
        // Whether the masks bind the change is read before it's made, so it's never scrolled out of sight.
        AxiomPanel.masks();
        Component why = stroke.refused();
        if (why == null && dropped) why = AxiomChanges.refusal(QueuedChanges.Refusal.EDITOR);
        if (why != null) AxiomPanel.wrapped(why);
        preview();
    }

    /** What the brush would make of the block under the cursor, or why it would leave it. */
    private void preview() {
        if (!BlockIndex.get().ready()) {
            AxiomPanel.wrapped(tr("axiom.chromastudio.not_ready"));
            return;
        }
        Plan p = plan();
        if (p == null) {
            AxiomPanel.wrapped(tr("axiom.chromastudio.shade.no_shades"));
            return;
        }
        BlockState here = hovered;
        if (here == null) {
            AxiomPanel.note(tr("axiom.chromastudio.shade.point"));
            return;
        }
        swatch("here", here);
        BlockState next = p.apply(here, direction, kinds);
        if (next == null) {
            AxiomPanel.wrapped(tr(p.whyLeft(here, direction, kinds)));
            return;
        }
        AxiomPanel.text(tr("axiom.chromastudio.shade.becomes"));
        AxiomPanel.sameLine();
        swatch("next", next);
    }

    /** A block as a swatch and its name, on one line. */
    private static void swatch(String id, BlockState state) {
        String name = state.getBlock().getName().getString();
        Optional<Color> c = BlockIndex.get().colourOf(state, PaletteBlocks.queryFace(), PaletteBlocks.tint());
        if (c.isPresent()) {
            AxiomPanel.swatch(id, new AxiomPanel.Swatch(c.get(), name));
            AxiomPanel.sameLine();
        }
        ImGui.alignTextToFramePadding();
        AxiomPanel.text(name);
    }

    /** The chosen palette, while it's still in the list, or null for any building block. */
    private @Nullable Palette from() {
        for (Palette p : PaletteStore.get().all()) if (p == from) return p;
        return null;
    }

    /**
     * The shades for the panel's choices, made again when one of them or the index changes, as the panel asks every
     * frame. Only the panel uses it: a stroke works its shades out afresh ({@link #painterNow}). Null while the index
     * is building or when no block can be a shade.
     */
    private @Nullable Plan plan() {
        Plan.Key key = key();
        if (key == null) return null;
        if (plan == null || !plan.key.equals(key)) plan = Plan.of(key, BlockIndex.get(), PaletteBlocks.tint());
        return plan.steps == null ? null : plan;
    }

    /** The panel's choices that the shades depend on, as they are now, or null while the index is building. */
    private Plan.@Nullable Key key() {
        BlockIndex index = BlockIndex.get();
        if (!index.ready()) return null;
        Palette shades = from();
        return new Plan.Key(index.version(), shades, shades == null ? 0 : shades.entries.hashCode(), STEPS[AxiomPanel.held(stepSize, 1, STEPS.length) - 1], PaletteBlocks.queryFace(),
                ModSettings.biomeMode + " " + ModSettings.biome);
    }

    /** The blocks that may be shades, and what each block becomes, for one set of choices. */
    private static final class Plan {
        /**
         * The choices a plan is made for. A palette is told apart by which one it is, and by its blocks, as it can be
         * edited; the biome setting, as it tints some blocks.
         */
        record Key(int version, @Nullable Palette palette, int entries, double step, Face face, String biome) {}

        final Key key;
        private final BlockIndex index;
        private final TintContext tint;
        /** The full blocks that may be shades, each standing for its material's shapes. */
        private final List<Entry> shades;
        private final Map<Block, Integer> place = new IdentityHashMap<>();
        /**
         * For a palette, the kinds it lists of each shade's material: a full block lends every shape, while stairs
         * alone allow only stairs. Null when any block may be a shade.
         */
        private final @Nullable Map<Block, Set<Kind>> listed;
        private final @Nullable ShadeSteps steps;
        /** For each shape, which shades' materials come in it. */
        private final Map<Kind, boolean[]> hasShape = new EnumMap<>(Kind.class);
        /** What each block becomes each way, worked out on first use. An empty one leaves it alone. */
        private final Map<Direction, Map<Block, Optional<RecolourPlan>>> changes = new EnumMap<>(Direction.class);

        private Plan(Key key, BlockIndex index, TintContext tint, List<Entry> candidates, @Nullable Map<Block, Set<Kind>> listed) {
            this.key = key;
            this.listed = listed;
            this.index = index;
            this.tint = tint;
            // A block whose colour the index can't read sits out.
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
                    // A kind the brush never changes, such as a trapdoor, lends nothing.
                    if (base == null || !paletteShade(base) || !CHANGEABLE.contains(e.kind())) continue;
                    if (seen.add(base.block())) shades.add(base);
                    listed.computeIfAbsent(base.block(), _ -> EnumSet.noneOf(Kind.class)).add(e.kind());
                }
            }
            return new Plan(key, index, tint, shades, listed);
        }

        /**
         * The block a block becomes a step that way, with its shape, facing and half, or null to leave it as it is:
         * when it's one this brush never changes, its kind is turned off, or no shade is close enough.
         */
        @Nullable BlockState apply(BlockState here, Direction d, Set<Kind> kinds) {
            Entry e = changeable(here, kinds);
            if (e == null) return null;
            RecolourPlan change = changes.computeIfAbsent(d, _ -> new IdentityHashMap<>()).computeIfAbsent(here.getBlock(), _ -> change(e, d)).orElse(null);
            return change == null ? null : change.apply(here);
        }

        /** The key that says why {@link #apply} leaves a block as it is. */
        String whyLeft(BlockState here, Direction d, Set<Kind> kinds) {
            Entry e = index.entryOf(here);
            if (e == null || never(here, e)) return "axiom.chromastudio.shade.left.never";
            if (RecolourPlan.ground(here)) return "axiom.chromastudio.shade.left.ground";
            if (!kinds.contains(e.kind())) return "axiom.chromastudio.shade.left.kind";
            return "axiom.chromastudio.shade.left." + d.name().toLowerCase(Locale.ROOT);
        }

        /** The block's entry when the brush may change it, or null. */
        private @Nullable Entry changeable(BlockState here, Set<Kind> kinds) {
            Entry e = index.entryOf(here);
            if (e == null || never(here, e) || RecolourPlan.ground(here) || !kinds.contains(e.kind())) return null;
            return e;
        }

        /** Air, liquids, blocks with contents, see-through blocks and technical ones, which no shading touches. */
        private static boolean never(BlockState here, Entry e) {
            // Liquids are of their own kind, which isn't one the brush changes.
            return here.isAir() || here.getBlock() instanceof EntityBlock || e.technical() || !CHANGEABLE.contains(e.kind())
                    || e.kind() == Kind.FULL && !e.opaque();
        }

        /** The recolour that takes a block to its next shade that way, made once per block. */
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

        /** Whether a shade may be given in a kind: always for any block, and for a palette when it lists that kind or the full block. */
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

        /** How a full block looks to the steps, or null when the index can't say. */
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

        /** What a block is made of, as the tool that mines it says: stone, wood, earth or soft growth, or none, as wool. */
        private static String material(BlockState s) {
            if (s.is(BlockTags.MINEABLE_WITH_PICKAXE)) return "pickaxe";
            if (s.is(BlockTags.MINEABLE_WITH_AXE)) return "axe";
            if (s.is(BlockTags.MINEABLE_WITH_SHOVEL)) return "shovel";
            if (s.is(BlockTags.MINEABLE_WITH_HOE)) return "hoe";
            return "";
        }

        /** The last word of a block's id, which names what it's made as: bricks, tiles, planks, terracotta. */
        private static String family(String id) {
            return id.substring(Math.max(id.indexOf(':'), id.lastIndexOf('_')) + 1);
        }

        /** A block's material's full block: itself for a full block, else the first in its material, or null. */
        private static @Nullable Entry base(BlockIndex index, Entry e) {
            if (e.kind() == Kind.FULL) return e;
            for (Entry m : index.materialSet(e)) if (m.kind() == Kind.FULL) return m;
            return null;
        }

        /** Whether a palette's block may be a shade: a solid full block that stays put and has nothing inside. */
        private static boolean paletteShade(Entry e) {
            Block b = e.block();
            return e.kind() == Kind.FULL && e.opaque() && !(b instanceof EntityBlock) && !(b instanceof Fallable);
        }

        /**
         * Whether a block may be a shade when any block may: a plain building block that looks the same whichever
         * way it's placed and stays as it's left. Lights, ores and blocks of them, the ground, blocks that fall, weather or power
         * redstone, blocks with contents, blocks a survival player can't get, blocks that turn, such as logs and
         * glazed terracotta, and chiseled blocks, whose carved motif is an accent rather than a shade, are left out.
         */
        private static boolean anyShade(Entry e) {
            Block b = e.block();
            BlockState s = e.state();
            return paletteShade(e) && !e.technical() && !e.creativeOnly() && !e.lightSource() && !RecolourPlan.ground(s)
                    && !(b instanceof WeatheringCopper) && !s.isSignalSource() && !s.is(BlockTags.BEACON_BASE_BLOCKS) && !STORAGE.contains(b)
                    && s.getDestroySpeed(EmptyBlockGetter.INSTANCE, BlockPos.ZERO) >= 0
                    && !s.hasProperty(BlockStateProperties.AXIS) && !s.hasProperty(BlockStateProperties.HORIZONTAL_FACING)
                    && !s.hasProperty(BlockStateProperties.FACING) && !e.id().contains("chiseled");
        }

        /**
         * The blocks the Building blocks and Coloured blocks tabs list, which keeps ores, plants and machines out.
         * Empty when the tabs haven't been filled, which the index does when a world is joined.
         */
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
