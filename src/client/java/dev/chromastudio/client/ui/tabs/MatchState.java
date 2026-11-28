package dev.chromastudio.client.ui.tabs;

import dev.chromastudio.client.BiomeMode;
import dev.chromastudio.client.FaceChoice;
import dev.chromastudio.client.ModSettings;
import dev.chromastudio.client.index.BlockIndex;
import dev.chromastudio.client.index.Entry;
import dev.chromastudio.client.index.Query;
import dev.chromastudio.client.index.Target;
import dev.chromastudio.client.index.TintContext;
import dev.chromastudio.core.color.Color;
import dev.chromastudio.core.color.Face;
import dev.chromastudio.core.color.Metric;
import dev.chromastudio.core.color.Scale;
import dev.chromastudio.core.color.Viewing;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.state.BlockState;

public final class MatchState {
    private MatchState() {}

    private static final Color START = Color.ofRgb(0x808080);

    private static Color colour = START;
    private static Entry entry;
    private static Face entryFace;
    private static BlockState entryState;
    private static int indexVersion = -1;
    private static List<Object> settingsSeen = List.of();
    private static TintContext entryTint;
    private static Color lastFlat;
    private static MatchFilters filters;
    private static final RecentColours RECENT = new RecentColours();
    private static int revision;

    public static int revision() { return revision; }

    public static void changed() {
        if (entry != null) colour = target().colour();
        revision++;
    }

    private static List<Object> settings() {
        return List.of(ModSettings.defaultFace, ModSettings.metric, ModSettings.viewingDistance, ModSettings.shading, ModSettings.biomeMode,
                ModSettings.biome, ModSettings.resultCount, ModSettings.statesSeparately);
    }

    public static void follow() {
        boolean stale = false;
        List<Object> now = settings();
        if (!now.equals(settingsSeen)) {
            settingsSeen = now;
            stale = true;
        }
        if (filters().followSettings()) stale = true;
        BlockIndex index = BlockIndex.get();
        if (index.version() != indexVersion) {
            indexVersion = index.version();
            Entry current = entry == null ? null : index.entryOf(entryState);
            if (current != null && current != entry) {
                entry = current;
                stale = true;
            }
        }
        if (stale) changed();
    }

    public static Color colour() { return colour; }

    public static Entry entry() { return entry; }

    public static BlockState entryState() { return entryState; }

    public static String entryProperties() { return entryState == null ? "" : Entry.properties(entryState); }

    public static Face entryFace() { return entryFace; }

    public static Color lastFlat() { return lastFlat; }

    public static List<Integer> recent() { return RECENT.list(); }

    public static void remember() { RECENT.push(colour.rgb()); }

    public static MatchFilters filters() {
        if (filters == null) filters = MatchFilters.fromSettings();
        return filters;
    }

    public static void resetFilters() {
        filters = MatchFilters.fromSettings();
        changed();
    }

    public static void setColour(Color c) {
        colour = c;
        lastFlat = c;
        entry = null;
        entryFace = null;
        entryState = null;
        entryTint = null;
        changed();
    }

    public static void setEntry(Entry e, Face face, TintContext tint) { setEntry(e, e.state(), face, tint); }

    public static void setEntry(Entry e, BlockState state, Face face, TintContext tint) {
        entry = e;
        entryState = state;
        entryFace = face == Face.ANY ? firstFace() : face;
        entryTint = tint;
        changed();
    }

    public static Target target() {
        return entry == null ? Target.of(colour) : Target.of(entry, entryState, entryFace, entryTint != null ? entryTint : tint());
    }

    private static Face firstFace() {
        return switch (ModSettings.defaultFace) {
            case ANY, TOP -> Face.UP;
            case SIDE, NORTH -> Face.NORTH;
            case BOTTOM -> Face.DOWN;
            case SOUTH -> Face.SOUTH;
            case WEST -> Face.WEST;
            case EAST -> Face.EAST;
        };
    }

    public static Metric metric() {
        try {
            return Metric.valueOf(ModSettings.metric);
        } catch (IllegalArgumentException ignored) {
            return Metric.OKLAB;
        }
    }

    public static Scale scale() {
        Minecraft mc = Minecraft.getInstance();
        double distance = ModSettings.viewingDistance >= ModSettings.VIEW_FAR ? Viewing.FAR : ModSettings.viewingDistance;
        return Viewing.scaleFor(distance, mc.getWindow().getHeight(), mc.options.fov().get());
    }

    public static TintContext tint() {
        if (ModSettings.biomeMode == BiomeMode.CHOSEN) {
            ResourceLocation id = ResourceLocation.tryParse(ModSettings.biome);
            return id == null ? TintContext.none() : TintContext.biome(ResourceKey.create(Registries.BIOME, id));
        }
        Minecraft mc = Minecraft.getInstance();
        return mc == null || mc.player == null ? TintContext.none() : TintContext.at(mc.player.blockPosition());
    }

    public static Query query(int limit) {
        Query q = new Query().scale(scale()).metric(metric()).shading(ModSettings.shading).tint(tint()).limit(limit);
        face(q, ModSettings.defaultFace);
        filters().apply(q);
        return q;
    }

    static void face(Query q, FaceChoice choice) {
        q.faces.clear();
        q.face = switch (choice) {
            case ANY, SIDE -> Face.ANY;
            case TOP -> Face.UP;
            case BOTTOM -> Face.DOWN;
            case NORTH -> Face.NORTH;
            case SOUTH -> Face.SOUTH;
            case WEST -> Face.WEST;
            case EAST -> Face.EAST;
        };
        if (choice == FaceChoice.SIDE) q.faces.addAll(List.of(Face.NORTH, Face.SOUTH, Face.WEST, Face.EAST));
    }
}
