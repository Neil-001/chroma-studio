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

public final class ModSettings {
    private ModSettings() {}

    public static final int WINDOW_MIN_W = 400, WINDOW_MIN_H = 220, WINDOW_MAX = 16384;
    public static final int CENTRED = -1;
    public static final int VIEW_FAR = 192;
    public static final int TEXT_MAX = 256;
    public static final int BRUSH_MAX_RADIUS = 16;

    public static int windowX = CENTRED, windowY = CENTRED;
    public static int windowW = WINDOW_MIN_W, windowH = WINDOW_MIN_H;
    public static boolean fullscreen = false;
    public static boolean rememberWindow = true;
    public static String startTab = "match";
    public static double pinOpacity = 0.8;
    public static TitleButtons titleButtons = TitleButtons.DEFAULT;
    public static ThemeChoice theme = ThemeChoice.DEFAULT;
    public static String customColour = "#243447";
    public static ColourFormat colourFormat = ColourFormat.OKLCH;

    public static FaceChoice defaultFace = FaceChoice.SIDE;
    public static boolean shading = false;
    public static String metric = "OKLAB";
    public static int resultCount = 12;
    public static int viewingDistance = 24;
    public static boolean filterFullCube = false, filterSolid = false, filterOpaque = false, filterSurvival = false;
    public static boolean showDeltaE = true;

    public static boolean technicalBlocks = false;
    public static boolean statesSeparately = false;
    public static int statesCap = 8;
    public static boolean animatedAveraging = true;
    public static String cacheFolder = "";
    public static boolean rebuildOnReload = true;

    public static BiomeMode biomeMode = BiomeMode.HERE;
    public static String biome = "minecraft:plains";

    public static int gradientSteps = 9;
    public static String easing = "SMOOTH";
    public static String hueArc = "SHORTEST";
    public static boolean spreadMatching = false;

    public static int generateCount = 6;
    public static int captureCount = 12;
    public static OverflowMode hotbarOverflowMode = OverflowMode.NEVER;
    public static boolean dioramaPerspective = false;
    public static String exportFolder = "";
    public static LayoutChoice defaultLayout = LayoutChoice.STRIP;
    public static int patchSize = 3;
    public static boolean exportGap = false;

    public static TooltipMode tooltipMode = TooltipMode.SHIFT;
    public static boolean tooltipSwatch = true, tooltipHex = true, tooltipKind = true, tooltipBadge = true, tooltipDeltaE = true;
    public static int readoutSeconds = 4;

    public static PlacementChoice litematicaPlacement = PlacementChoice.LOOKING_AT;
    public static boolean hotbarAxiom = false;
    public static String brushPalette = "", brushMode = "SCATTER", brushFrom = "build", brushMatch = "SHADING";
    public static int brushRadius = 4;
    public static boolean brushWholeBuild = false;

    public static final Map<String, List<String>> GROUPS = new LinkedHashMap<>();
    public static final List<String> MEMORY = List.of("windowX", "windowY", "windowW", "windowH", "fullscreen",
            "brushPalette", "brushMode", "brushFrom", "brushMatch", "brushRadius", "brushWholeBuild");

    private static final Map<String, Field> FIELDS = new LinkedHashMap<>();
    private static final Map<String, Object> DEFAULTS = new HashMap<>();
    private static final Map<String, double[]> LIMITS = new HashMap<>();
    private static final Map<String, List<String>> NAMES = new HashMap<>();
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
        GROUPS.put("integrations", List.of("litematicaPlacement", "hotbarAxiom", "brushPalette", "brushMode", "brushFrom", "brushMatch",
                "brushRadius", "brushWholeBuild"));

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
        limit("brushRadius", 0, BRUSH_MAX_RADIUS);

        NAMES.put("metric", List.of("OKLAB", "CIEDE2000", "CIE76", "SRGB", "MAP_COLOR"));
        NAMES.put("easing", List.of("LINEAR", "SMOOTH", "EASE_IN", "EASE_OUT"));
        NAMES.put("hueArc", List.of("SHORTEST", "LONGEST"));
        NAMES.put("brushMode", List.of("SCATTER", "RECOLOUR"));
        NAMES.put("brushMatch", List.of("SHADING", "CLOSEST"));
        SHAPES.put("startTab", Pattern.compile("[a-z0-9_]{1,64}"));
        SHAPES.put("customColour", Pattern.compile("#[0-9A-Fa-f]{6}"));
        SHAPES.put("biome", Pattern.compile("[a-z0-9_.-]+:[a-z0-9_./-]+"));

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

    public static Object get(String name) {
        try {
            return field(name).get(null);
        } catch (IllegalAccessException e) {
            throw new IllegalStateException(e);
        }
    }

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
    public static List<String> names(String name) { return NAMES.get(name); }

    public static boolean allows(String name, String text) {
        if (text.length() > TEXT_MAX) return false;
        List<String> names = NAMES.get(name);
        if (names != null) return names.contains(text);
        Pattern shape = SHAPES.get(name);
        return shape == null || shape.matcher(text).matches();
    }

    public static void reset() { FIELDS.keySet().forEach(ModSettings::resetOne); }

    public static void reset(String group) { GROUPS.get(group).forEach(ModSettings::resetOne); }

    private static void resetOne(String name) { put(name, DEFAULTS.get(name)); }

    private static void put(String name, Object value) {
        try {
            FIELDS.get(name).set(null, value);
        } catch (IllegalAccessException e) {
            throw new IllegalStateException(e);
        }
    }

    public static Map<String, Object> snapshot() {
        Map<String, Object> values = new HashMap<>();
        for (String name : FIELDS.keySet()) values.put(name, get(name));
        return values;
    }

    public static void restore(Map<String, Object> snapshot) { FIELDS.keySet().forEach(name -> put(name, snapshot.get(name))); }

    public static void restore(String group, Map<String, Object> snapshot) { GROUPS.get(group).forEach(name -> put(name, snapshot.get(name))); }

    public static boolean changedSince(Map<String, Object> snapshot) { return FIELDS.keySet().stream().anyMatch(name -> !get(name).equals(snapshot.get(name))); }

    public static boolean changedSince(String group, Map<String, Object> snapshot) {
        return GROUPS.get(group).stream().anyMatch(name -> !get(name).equals(snapshot.get(name)));
    }

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();

    private static Path file() { return ChromaStudioClient.platform.configDir().resolve("chromastudio").resolve("settings.json"); }

    public static void load() { load(file()); }
    public static void save() { save(file()); }

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
                if (v >= min(name) && v <= max(name) && (type == double.class || v == Math.rint(v))) set(name, v);
            } else if (p.isString()) {
                if (type == String.class) set(name, p.getAsString());
                else for (Object c : type.getEnumConstants()) if (((Enum<?>) c).name().equals(p.getAsString())) set(name, c);
            }
        }
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
