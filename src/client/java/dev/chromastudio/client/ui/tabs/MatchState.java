package dev.chromastudio.client.ui.tabs;

import dev.chromastudio.client.BiomeMode;
import dev.chromastudio.client.FaceChoice;
import dev.chromastudio.client.ModSettings;
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
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;

/**
 * What the player is matching and how, shared by the Match and Explore tabs, the eyedropper and the item tooltips.
 * The face, metric, viewing distance, biome and shading are the settings themselves; the target and the filters
 * last for the session. The client thread only.
 */
public final class MatchState {
    private MatchState() {}

    /** Mid grey, until the player picks something. */
    private static final Color START = Color.ofRgb(0x808080);

    private static Color colour = START;
    private static Entry entry;
    private static Face entryFace;
    private static TintContext entryTint;
    private static Color lastFlat;
    private static MatchFilters filters;
    private static final RecentColours RECENT = new RecentColours();
    private static int revision;

    /** A number that changes whenever the target, a filter or a matching setting does. */
    public static int revision() { return revision; }

    /** Tells the tabs something they show has changed. The setters here call it; call it after changing a filter or a setting. */
    public static void changed() { revision++; }

    /** The target's colour: the flat colour, or the block face's average. */
    public static Color colour() { return colour; }

    /** The block the target is, or null for a flat colour. */
    public static Entry entry() { return entry; }

    /** The face of {@link #entry()} that is matched. */
    public static Face entryFace() { return entryFace; }

    /** The last flat colour matched, or null before the first. Find the biome compares against it. */
    public static Color lastFlat() { return lastFlat; }

    public static List<Integer> recent() { return RECENT.list(); }

    /** Adds the target's colour to the recent ones. Call it when the player settles on a colour, not while a slider moves. */
    public static void remember() { RECENT.push(colour.rgb()); }

    public static MatchFilters filters() {
        if (filters == null) filters = MatchFilters.fromSettings();
        return filters;
    }

    public static void resetFilters() {
        filters = MatchFilters.fromSettings();
        changed();
    }

    /** Makes the target a flat colour. */
    public static void setColour(Color c) {
        colour = c;
        lastFlat = c;
        entry = null;
        entryFace = null;
        entryTint = null;
        changed();
    }

    /**
     * Makes the target a block's face. {@code tint} is where the block was looked at, or null to follow the biome
     * control. {@link Face#ANY} takes the face the face control would show first.
     */
    public static void setEntry(Entry e, Face face, TintContext tint) {
        entry = e;
        entryFace = face == Face.ANY ? firstFace() : face;
        entryTint = tint;
        colour = Target.of(e, entryFace, tint != null ? tint : tint()).colour();
        changed();
    }

    /** The target as the index takes it. */
    public static Target target() {
        return entry == null ? Target.of(colour) : Target.of(entry, entryFace, entryTint != null ? entryTint : tint());
    }

    /** The face a block shows when the face control leaves a choice: its top, or north for the sides. */
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
        } catch (IllegalArgumentException _) {
            return Metric.OKLAB;
        }
    }

    /** The scale a block shows at from the viewing distance, in the game window as it is now. */
    public static Scale scale() {
        Minecraft mc = Minecraft.getInstance();
        double distance = ModSettings.viewingDistance >= ModSettings.VIEW_FAR ? Viewing.FAR : ModSettings.viewingDistance;
        return Viewing.scaleFor(distance, mc.getWindow().getHeight(), mc.options.fov().get());
    }

    /**
     * Where tinted blocks are looked at, by the biome control. "Where you stand" is fixed at the player's place now,
     * so a search on another thread sees one place. "All biomes" matches there too, and shows the range beside.
     */
    public static TintContext tint() {
        if (ModSettings.biomeMode == BiomeMode.CHOSEN) {
            Identifier id = Identifier.tryParse(ModSettings.biome);
            return id == null ? TintContext.none() : TintContext.biome(ResourceKey.create(Registries.BIOME, id));
        }
        Minecraft mc = Minecraft.getInstance();
        return mc.player == null ? TintContext.none() : TintContext.at(mc.player.blockPosition());
    }

    /** A query with the controls and the filters as they stand. */
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
