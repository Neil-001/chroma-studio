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
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;

public final class PaletteBlocks {
    private PaletteBlocks() {}

    public static BlockState state(PaletteEntry e) { return SchematicExporter.stateFor(e); }

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

    public static PaletteEntry of(BlockState state) {
        BlockState byDefault = state.getBlock().defaultBlockState();
        List<String> properties = new ArrayList<>();
        state.getValues().forEach((property, value) -> {
            if (!value.equals(byDefault.getValue(property))) properties.add(property.getName() + "=" + valueName(property, value));
        });
        return new PaletteEntry(BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString(), String.join(",", properties));
    }

    public static Component name(PaletteEntry e) {
        BlockState state = state(e);
        return state == null ? Component.literal(e.blockId()) : state.getBlock().getName();
    }

    public static ItemStack icon(PaletteEntry e) {
        BlockState state = state(e);
        return state == null ? ItemStack.EMPTY : new ItemStack(state.getBlock());
    }

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

    public static Face face() {
        Face f = queryFace();
        return f == Face.ANY ? Face.UP : f;
    }

    public static Scale scale() {
        Minecraft mc = Minecraft.getInstance();
        double distance = ModSettings.viewingDistance >= ModSettings.VIEW_FAR ? Viewing.FAR : ModSettings.viewingDistance;
        return Viewing.scaleFor(distance, mc.getWindow().getHeight(), mc.options.fov().get());
    }

    public static TintContext tint() {
        Identifier id = ModSettings.biomeMode == BiomeMode.CHOSEN ? Identifier.tryParse(ModSettings.biome) : null;
        return id == null ? TintContext.HERE : TintContext.biome(ResourceKey.create(Registries.BIOME, id));
    }

    public static Metric metric() {
        try {
            return Metric.valueOf(ModSettings.metric);
        } catch (IllegalArgumentException ignored) {
            return Metric.OKLAB;
        }
    }

    public static Query query() {
        Query q = new Query().face(queryFace()).scale(scale()).metric(metric()).shading(ModSettings.shading).tint(tint());
        q.fullCube = ModSettings.filterFullCube;
        q.solid = ModSettings.filterSolid;
        q.opaque = ModSettings.filterOpaque;
        if (ModSettings.filterSurvival) q.creativeOnly = Query.Tri.EXCLUDE;
        return q;
    }

    public static TextureStats stats(Entry e) { return e.face(face(), tint()); }

    public static TextureStats stats(PaletteEntry e) {
        BlockState state = state(e);
        Entry entry = state == null ? null : entry(state);
        return entry == null ? null : entry.face(face(), state, tint());
    }

    public static Color colour(PaletteEntry e) {
        TextureStats stats = stats(e);
        return stats == null ? null : stats.mean(scale());
    }

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

    @SuppressWarnings("unchecked")
    private static <T extends Comparable<T>> String valueName(Property<T> property, Comparable<?> value) { return property.getName((T) value); }
}
