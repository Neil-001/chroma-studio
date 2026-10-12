package dev.chromastudio.client.action;

import dev.chromastudio.client.BiomeMode;
import dev.chromastudio.client.ModSettings;
import dev.chromastudio.client.export.SchematicExporter;
import dev.chromastudio.client.index.BlockIndex;
import dev.chromastudio.client.index.Entry;
import dev.chromastudio.client.index.Query;
import dev.chromastudio.client.index.Target;
import dev.chromastudio.client.index.TintContext;
import dev.chromastudio.core.color.Color;
import dev.chromastudio.core.color.Face;
import dev.chromastudio.core.color.Metric;
import dev.chromastudio.core.color.Oklch;
import dev.chromastudio.core.color.Scale;
import dev.chromastudio.core.color.TextureStats;
import dev.chromastudio.core.color.Viewing;
import dev.chromastudio.core.palette.PaletteEntry;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.minecraft.client.Minecraft;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Turns a palette's entries into the game's blocks and the index's entries and back, and reads the settings the
 * palette tools match by: the default face, the viewing distance, the biome and the default filters.
 */
public final class PaletteBlocks {
    private PaletteBlocks() {}

    /** The entry's block state, or null when this game has no such block or state. */
    public static BlockState state(PaletteEntry e) { return SchematicExporter.stateFor(e); }

    /**
     * What the index holds for an entry: its own state where the index lists it, else the block's default state,
     * since the index keeps one entry for states that look the same. Null for a block the index doesn't have.
     */
    public static Entry entry(PaletteEntry e) {
        BlockState state = state(e);
        return state == null ? null : entry(state);
    }

    public static Entry entry(BlockState state) {
        List<Entry> states = BlockIndex.get().statesOf(state.getBlock());
        for (Entry candidate : states) if (candidate.state() == state) return candidate;
        return states.isEmpty() ? null : states.getFirst();
    }

    public static PaletteEntry of(Entry e) { return new PaletteEntry(e.id(), e.properties()); }

    /** The block's name, or its id for a block this game doesn't have. */
    public static Component name(PaletteEntry e) {
        BlockState state = state(e);
        return state == null ? Component.literal(e.blockId()) : state.getBlock().getName();
    }

    /** The block as an item, which is empty for a block that has none, such as water. */
    public static ItemStack icon(PaletteEntry e) {
        BlockState state = state(e);
        return state == null ? ItemStack.EMPTY : new ItemStack(state.getBlock());
    }

    /** The settings' default face for a query. The four sides nearly always look alike, so the north one stands for them. */
    public static Face queryFace() {
        return switch (ModSettings.defaultFace) {
            case ANY -> Face.ANY;
            case TOP -> Face.UP;
            case BOTTOM -> Face.DOWN;
            case SIDE, NORTH -> Face.NORTH;
            case SOUTH -> Face.SOUTH;
            case WEST -> Face.WEST;
            case EAST -> Face.EAST;
        };
    }

    /** The one face an entry's colour is read from. With the setting on Any, that is the top. */
    public static Face face() {
        Face f = queryFace();
        return f == Face.ANY ? Face.UP : f;
    }

    /** The scale the settings' viewing distance comes to in this game window. */
    public static Scale scale() {
        Minecraft mc = Minecraft.getInstance();
        double distance = ModSettings.viewingDistance >= ModSettings.VIEW_FAR ? Viewing.FAR : ModSettings.viewingDistance;
        return Viewing.scaleFor(distance, mc.getWindow().getHeight(), mc.options.fov().get());
    }

    /** Where tints come from, by the biome setting. A range over all biomes has no one tint, so it reads where the player stands. */
    public static TintContext tint() {
        Identifier id = ModSettings.biomeMode == BiomeMode.CHOSEN ? Identifier.tryParse(ModSettings.biome) : null;
        return id == null ? TintContext.HERE : TintContext.biome(ResourceKey.create(Registries.BIOME, id));
    }

    public static Metric metric() {
        try {
            return Metric.valueOf(ModSettings.metric);
        } catch (IllegalArgumentException _) {
            return Metric.OKLAB;
        }
    }

    /** A query with the settings' face, scale, metric, shading, biome and default filters. */
    public static Query query() {
        Query q = new Query().face(queryFace()).scale(scale()).metric(metric()).shading(ModSettings.shading).tint(tint());
        q.fullCube = ModSettings.filterFullCube;
        q.solid = ModSettings.filterSolid;
        q.opaque = ModSettings.filterOpaque;
        if (ModSettings.filterSurvival) q.creativeOnly = Query.Tri.EXCLUDE;
        return q;
    }

    /** An index entry's look on the settings' face in the settings' biome. */
    public static TextureStats stats(Entry e) { return e.face(face(), tint()); }

    /** The entry's average colour at the settings' viewing distance, or null for a block the index doesn't have. */
    public static Color colour(PaletteEntry e) {
        Entry entry = entry(e);
        return entry == null ? null : stats(entry).mean(scale());
    }

    /**
     * The closest block to each colour under the settings' default filters, with no block twice. A colour nothing
     * is left for gives no entry, so the list may come out shorter. Empty until the index is ready.
     */
    public static List<PaletteEntry> closest(List<Oklch> targets) {
        Query q = query().limit(targets.size() + 1);
        List<PaletteEntry> out = new ArrayList<>();
        Set<String> used = new HashSet<>();
        for (Oklch target : targets) {
            for (BlockIndex.Match m : BlockIndex.get().closest(Target.of(Color.ofOklch(target)), q)) {
                if (!used.add(m.entry().id())) continue;
                out.add(of(m.entry()));
                break;
            }
        }
        return out;
    }
}
