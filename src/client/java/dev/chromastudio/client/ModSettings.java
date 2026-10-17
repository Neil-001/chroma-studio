package dev.chromastudio.client;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * The mod's own options, saved in {@code .minecraft/config/chromastudio/settings.json}. They're plain static fields:
 * read them anywhere on the client, and call {@link #save()} after changing one. The file's keys are the field names.
 *
 * <p>To add a setting, declare the field with its default, name it in a group in {@link #GROUPS}, and give a number
 * its limits in the static block. The file, the reset and the settings screen all work from those.
 */
public final class ModSettings {
    private ModSettings() {}

    /** The limits the window and the file reader both keep each number within, in GUI pixels. */
    public static final int WINDOW_MIN_W = 400, WINDOW_MIN_H = 220, WINDOW_MAX = 16384;
    /** A window position that means "centred on the screen". */
    public static final int CENTRED = -1;
    /** The viewing distance, in blocks, that means "far": one texel to a block. */
    public static final int VIEW_FAR = 192;
    /** The longest text a setting holds. */
    public static final int TEXT_MAX = 256;

    // Keys and window
    /** The window's top-left corner, or {@link #CENTRED} for either. */
    public static int windowX = CENTRED, windowY = CENTRED;
    public static int windowW = WINDOW_MIN_W, windowH = WINDOW_MIN_H;
    /** Whether the window fills the screen. */
    public static boolean fullscreen = false;
    /** Whether the window opens where and how it was last left. Off, it opens centred at its smallest size. */
    public static boolean rememberWindow = true;
    /** The id of the tab the window shows the first time it opens in a session. Later it reopens on the tab last used. */
    public static String startTab = "match";
    /** How solid a pinned window is drawn, 0.2 to 1. */
    public static double pinOpacity = 0.8;
    /** The order of the title bar's buttons. */
    public static TitleButtons titleButtons = TitleButtons.DEFAULT;
    /** The window's colours. */
    public static ThemeChoice theme = ThemeChoice.DEFAULT;
    /** The colour {@link ThemeChoice#CUSTOM} builds the window's colours from, as {@code #RRGGBB}. */
    public static String customColour = "#243447";
    /** The colour space of the Match tab's sliders. */
    public static ColourFormat colourFormat = ColourFormat.OKLCH;

    // Matching
    public static FaceChoice defaultFace = FaceChoice.SIDE;
    /** Whether faces are darkened as the game lights them. */
    public static boolean shading = false;
    /** The name of a {@code core.color.Metric}. */
    public static String metric = "OKLAB";
    public static int resultCount = 12;
    /** In blocks. {@link #VIEW_FAR} means far. */
    public static int viewingDistance = 24;
    /** The filters a fresh Match tab starts with. */
    public static boolean filterFullCube = false, filterSolid = false, filterOpaque = false, filterSurvival = false;
    public static boolean showDeltaE = true;

    // Index
    public static boolean technicalBlocks = false;
    /** Whether results list one row per block state rather than one per block. */
    public static boolean statesSeparately = false;
    /** The most states kept per block. */
    public static int statesCap = 8;
    /** Whether an animated texture is averaged over its frames. Off, only its first frame counts. */
    public static boolean animatedAveraging = true;
    /** Where the index cache is kept. Empty means {@code config/chromastudio/cache}. */
    public static String cacheFolder = "";
    public static boolean rebuildOnReload = true;

    // Biome
    public static BiomeMode biomeMode = BiomeMode.HERE;
    /** The biome {@link BiomeMode#CHOSEN} uses, as a registry id. */
    public static String biome = "minecraft:plains";

    // Gradient
    public static int gradientSteps = 9;
    /** The name of a {@code core.palette.Easing}. */
    public static String easing = "SMOOTH";
    /** The name of a {@code core.palette.HueArc}. */
    public static String hueArc = "SHORTEST";
    public static boolean spreadMatching = false;

    // Palettes
    public static int generateCount = 6;
    public static int captureCount = 12;
    /** When a hotbar fill packs the entries past the ninth into shulker boxes. */
    public static OverflowMode hotbarOverflowMode = OverflowMode.NEVER;
    /** Whether the 3D diorama is drawn with perspective. Off, its far blocks are as big as its near ones. */
    public static boolean dioramaPerspective = false;
    /** Where exports go. Empty means Litematica's schematics folder when it's loaded, else {@code schematics} in the game folder. */
    public static String exportFolder = "";
    public static LayoutChoice defaultLayout = LayoutChoice.STRIP;
    public static int patchSize = 3;
    /** Whether an export leaves a one-block air gap between entries. */
    public static boolean exportGap = false;

    // Tooltips and HUD
    public static TooltipMode tooltipMode = TooltipMode.SHIFT;
    /** Which lines an item's tooltip gets. */
    public static boolean tooltipSwatch = true, tooltipHex = true, tooltipKind = true, tooltipBadge = true, tooltipDeltaE = true;
    /** How long the eyedropper's readout stays on screen. */
    public static int readoutSeconds = 4;

    // Integrations
    public static PlacementChoice litematicaPlacement = PlacementChoice.LOOKING_AT;

    /** Every setting by the group the settings screen lists it under, in the order shown and saved. */
    public static final Map<String, List<String>> GROUPS = new LinkedHashMap<>();
    /** The settings in the first group that the window keeps itself, so the settings screen has no row for them. */
    public static final List<String> WINDOW_MEMORY = List.of("windowX", "windowY", "windowW", "windowH", "fullscreen");

    private static final Map<String, Field> FIELDS = new LinkedHashMap<>();
    private static final Map<String, Object> DEFAULTS = new HashMap<>();
    private static final Map<String, double[]> LIMITS = new HashMap<>();
    /** Text settings that must be one of a few names. */
    private static final Map<String, List<String>> NAMES = new HashMap<>();
    /** Text settings that must look a certain way. */
    private static final Map<String, Pattern> SHAPES = new HashMap<>();

    static {
        GROUPS.put("window", List.of("windowX", "windowY", "windowW", "windowH", "fullscreen", "rememberWindow", "startTab", "pinOpacity",
                "titleButtons", "theme", "customColour", "colourFormat"));
        GROUPS.put("matching", List.of("defaultFace", "shading", "metric", "resultCount", "viewingDistance",
                "filterFullCube", "filterSolid", "filterOpaque", "filterSurvival", "showDeltaE"));
        GROUPS.put("index", List.of("technicalBlocks", "statesSeparately", "statesCap", "animatedAveraging", "cacheFolder", "rebuildOnReload"));
        GROUPS.put("biome", List.of("biomeMode", "biome"));
        GROUPS.put("gradient", List.of("gradientSteps", "easing", "hueArc", "spreadMatching"));
        GROUPS.put("palettes", List.of("generateCount", "captureCount", "hotbarOverflowMode", "dioramaPerspective", "exportFolder", "defaultLayout", "patchSize", "exportGap"));
        GROUPS.put("tooltips", List.of("tooltipMode", "tooltipSwatch", "tooltipHex", "tooltipKind", "tooltipBadge", "tooltipDeltaE", "readoutSeconds"));
        GROUPS.put("integrations", List.of("litematicaPlacement"));

        limit("windowX", CENTRED, WINDOW_MAX);
        limit("windowY", CENTRED, WINDOW_MAX);
        limit("windowW", WINDOW_MIN_W, WINDOW_MAX);
        limit("windowH", WINDOW_MIN_H, WINDOW_MAX);
        limit("pinOpacity", 0.2, 1);
        limit("resultCount", 3, 60);
        limit("viewingDistance", 1, VIEW_FAR);
        limit("statesCap", 1, 8);
        limit("gradientSteps", 2, 32);
        limit("generateCount", 2, 32);
        limit("captureCount", 1, 64);
        limit("patchSize", 1, 16);
        limit("readoutSeconds", 1, 30);

        NAMES.put("metric", List.of("OKLAB", "CIEDE2000", "CIE76", "SRGB", "MAP_COLOR"));
        NAMES.put("easing", List.of("LINEAR", "SMOOTH", "EASE_IN", "EASE_OUT"));
        NAMES.put("hueArc", List.of("SHORTEST", "LONGEST"));
        SHAPES.put("startTab", Pattern.compile("[a-z0-9_]{1,64}"));
        SHAPES.put("customColour", Pattern.compile("#[0-9A-Fa-f]{6}"));
        SHAPES.put("biome", Pattern.compile("[a-z0-9_.-]+:[a-z0-9_./-]+"));

        // The groups are checked against the fields here, so a setting can't be left out of the file or the screen.
        Map<String, Field> declared = new HashMap<>();
        for (Field f : ModSettings.class.getDeclaredFields()) {
            int m = f.getModifiers();
            if (Modifier.isPublic(m) && Modifier.isStatic(m) && !Modifier.isFinal(m)) declared.put(f.getName(), f);
        }
        for (List<String> names : GROUPS.values())
            for (String name : names) {
                Field f = declared.remove(name);
                if (f == null) throw new IllegalStateException("No setting called " + name);
                FIELDS.put(name, f);
                DEFAULTS.put(name, get(name));
                boolean number = f.getType() == int.class || f.getType() == double.class;
                if (number != LIMITS.containsKey(name)) throw new IllegalStateException(name + (number ? " has no limits" : " isn't a number"));
            }
        if (!declared.isEmpty()) throw new IllegalStateException("Settings in no group: " + declared.keySet());
    }

    private static void limit(String name, double min, double max) { LIMITS.put(name, new double[] {min, max}); }

    private static Field field(String name) {
        Field f = FIELDS.get(name);
        if (f == null) throw new IllegalArgumentException("No setting called " + name);
        return f;
    }

    /** A setting's value by its name: an Integer, Double, Boolean, String or enum constant. */
    public static Object get(String name) {
        try {
            return field(name).get(null);
        } catch (IllegalAccessException e) {
            throw new IllegalStateException(e);
        }
    }

    /**
     * Sets a setting by its name. A number is rounded for a whole-number setting and kept within the limits. Returns
     * false, changing nothing, for a value of the wrong type or text the setting doesn't allow.
     */
    public static boolean set(String name, Object value) {
        Field f = field(name);
        Class<?> type = f.getType();
        Object v;
        if (type == int.class || type == double.class) {
            if (!(value instanceof Number n) || !Double.isFinite(n.doubleValue())) return false;
            double d = Math.max(min(name), Math.min(max(name), n.doubleValue()));
            v = type == int.class ? (Object) (int) Math.rint(d) : (Object) d;
        } else if (type == String.class) {
            if (!(value instanceof String s) || !allows(name, s)) return false;
            v = s;
        } else {
            if (!type.isInstance(value) && !(type == boolean.class && value instanceof Boolean)) return false;
            v = value;
        }
        try {
            f.set(null, v);
        } catch (IllegalAccessException e) {
            throw new IllegalStateException(e);
        }
        return true;
    }

    public static Class<?> type(String name) { return field(name).getType(); }
    public static double min(String name) { return LIMITS.get(name)[0]; }
    public static double max(String name) { return LIMITS.get(name)[1]; }
    /** The names a text setting chooses between, or null when it takes other text. */
    public static List<String> names(String name) { return NAMES.get(name); }

    /** Whether a text setting may hold this text. */
    public static boolean allows(String name, String text) {
        if (text.length() > TEXT_MAX) return false;
        List<String> names = NAMES.get(name);
        if (names != null) return names.contains(text);
        Pattern shape = SHAPES.get(name);
        return shape == null || shape.matcher(text).matches();
    }

    public static void reset() { FIELDS.keySet().forEach(ModSettings::resetOne); }

    /** Puts one group's settings back to their defaults. */
    public static void reset(String group) { GROUPS.get(group).forEach(ModSettings::resetOne); }

    private static void resetOne(String name) { put(name, DEFAULTS.get(name)); }

    private static void put(String name, Object value) {
        try {
            FIELDS.get(name).set(null, value);
        } catch (IllegalAccessException e) {
            throw new IllegalStateException(e);
        }
    }

    /** Every setting's value as it is now, to compare with or go back to later. */
    public static Map<String, Object> snapshot() {
        Map<String, Object> values = new HashMap<>();
        for (String name : FIELDS.keySet()) values.put(name, get(name));
        return values;
    }

    /** Puts every setting back to its value in a snapshot. */
    public static void restore(Map<String, Object> snapshot) { FIELDS.keySet().forEach(name -> put(name, snapshot.get(name))); }

    /** Puts one group's settings back to their values in a snapshot. */
    public static void restore(String group, Map<String, Object> snapshot) { GROUPS.get(group).forEach(name -> put(name, snapshot.get(name))); }

    /** Whether any setting differs from its value in a snapshot. */
    public static boolean changedSince(Map<String, Object> snapshot) { return FIELDS.keySet().stream().anyMatch(name -> !get(name).equals(snapshot.get(name))); }

    /** Whether any of one group's settings differs from its value in a snapshot. */
    public static boolean changedSince(String group, Map<String, Object> snapshot) {
        return GROUPS.get(group).stream().anyMatch(name -> !get(name).equals(snapshot.get(name)));
    }

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();

    private static Path file() { return ChromaStudioClient.platform.configDir().resolve("chromastudio").resolve("settings.json"); }

    public static void load() { load(file()); }
    public static void save() { save(file()); }

    /** Reads the file. A missing or unreadable file leaves every setting at its default. */
    public static void load(Path file) {
        reset();
        if (!Files.isRegularFile(file)) return;
        try {
            if (!read(Files.readString(file)))
                ChromaStudioClient.LOGGER.warn("Couldn't understand {}, so the settings are back to their defaults", file.getFileName());
        } catch (IOException e) {
            ChromaStudioClient.LOGGER.warn("Couldn't read {}, so the settings are back to their defaults", file.getFileName(), e);
        }
    }

    /** Writes the file atomically: to a temporary file first, then over the real one. */
    public static void save(Path file) {
        try {
            Files.createDirectories(file.getParent());
            Path tmp = file.resolveSibling(file.getFileName() + ".tmp");
            Files.writeString(tmp, write(), StandardCharsets.UTF_8);
            try {
                Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (IOException _) {
                Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException e) {
            ChromaStudioClient.LOGGER.warn("Couldn't save the settings", e);
        }
    }

    /**
     * Takes every setting from the JSON text. A setting that's missing, of the wrong type or outside its limits gets
     * its default, and unknown names are ignored. Returns false if the text isn't a JSON object at all.
     */
    public static boolean read(String json) {
        reset();
        JsonObject o;
        try {
            JsonElement root = JsonParser.parseString(json);
            if (!root.isJsonObject()) return false;
            o = root.getAsJsonObject();
        } catch (RuntimeException _) {
            return false;
        }
        for (String name : new ArrayList<>(FIELDS.keySet())) {
            JsonElement e = o.get(name);
            if (e == null || !e.isJsonPrimitive()) continue;
            var p = e.getAsJsonPrimitive();
            Class<?> type = type(name);
            if (type == boolean.class) {
                if (p.isBoolean()) set(name, p.getAsBoolean());
            } else if (type == int.class || type == double.class) {
                if (!p.isNumber()) continue;
                double v;
                try { v = p.getAsDouble(); } catch (NumberFormatException _) { continue; }
                // Unlike set(), a number outside the limits isn't pulled inside them: it gets the default.
                if (v >= min(name) && v <= max(name) && (type == double.class || v == Math.rint(v))) set(name, v);   // NaN fails the comparisons
            } else if (p.isString()) {
                if (type == String.class) set(name, p.getAsString());
                else for (Object c : type.getEnumConstants()) if (((Enum<?>) c).name().equals(p.getAsString())) set(name, c);
            }
        }
        // This was once an on or off under another key, which an older file still holds.
        if (!o.has("hotbarOverflowMode") && o.get("hotbarOverflow") instanceof JsonPrimitive old && old.isBoolean())
            hotbarOverflowMode = old.getAsBoolean() ? OverflowMode.ALWAYS : OverflowMode.NEVER;
        return true;
    }

    public static String write() {
        JsonObject o = new JsonObject();
        for (String name : FIELDS.keySet()) {
            Object v = get(name);
            switch (v) {
                case Boolean b -> o.addProperty(name, b);
                case Number n -> o.addProperty(name, n);
                case Enum<?> e -> o.addProperty(name, e.name());
                default -> o.addProperty(name, v.toString());
            }
        }
        return GSON.toJson(o);
    }
}
