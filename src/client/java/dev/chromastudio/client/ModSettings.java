package dev.chromastudio.client;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

/**
 * The mod's own options, saved in {@code .minecraft/config/chromastudio/settings.json}. They're plain static fields:
 * read them anywhere on the client, and call {@link #save()} after changing one.
 */
public final class ModSettings {
    private ModSettings() {}

    /** The limits the settings screen and the file reader both keep each number within, in GUI pixels. */
    public static final int WINDOW_MIN_W = 400, WINDOW_MIN_H = 220, WINDOW_MAX = 16384;
    /** A window position that means "centred on the screen". */
    public static final int CENTRED = -1;

    /** The window's top-left corner, or {@link #CENTRED} for either. */
    public static int windowX, windowY;
    public static int windowW, windowH;
    /** Whether the window fills the screen. */
    public static boolean fullscreen;

    static { reset(); }

    public static void reset() {
        windowX = CENTRED;
        windowY = CENTRED;
        windowW = WINDOW_MIN_W;
        windowH = WINDOW_MIN_H;
        fullscreen = false;
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
        windowX = whole(o, "windowX", CENTRED, WINDOW_MAX, windowX);
        windowY = whole(o, "windowY", CENTRED, WINDOW_MAX, windowY);
        windowW = whole(o, "windowW", WINDOW_MIN_W, WINDOW_MAX, windowW);
        windowH = whole(o, "windowH", WINDOW_MIN_H, WINDOW_MAX, windowH);
        fullscreen = flag(o, "fullscreen", fullscreen);
        return true;
    }

    public static String write() {
        JsonObject o = new JsonObject();
        o.addProperty("windowX", windowX);
        o.addProperty("windowY", windowY);
        o.addProperty("windowW", windowW);
        o.addProperty("windowH", windowH);
        o.addProperty("fullscreen", fullscreen);
        return GSON.toJson(o);
    }

    private static int whole(JsonObject o, String name, int min, int max, int fallback) {
        JsonElement e = o.get(name);
        if (e == null || !e.isJsonPrimitive() || !e.getAsJsonPrimitive().isNumber()) return fallback;
        double v;
        try { v = e.getAsDouble(); } catch (NumberFormatException _) { return fallback; }
        return v >= min && v <= max && v == Math.rint(v) ? (int) v : fallback;   // NaN fails the comparisons
    }

    private static boolean flag(JsonObject o, String name, boolean fallback) {
        JsonElement e = o.get(name);
        return e != null && e.isJsonPrimitive() && e.getAsJsonPrimitive().isBoolean() ? e.getAsBoolean() : fallback;
    }
}
