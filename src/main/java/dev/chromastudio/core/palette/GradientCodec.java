package dev.chromastudio.core.palette;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import dev.chromastudio.core.color.Color;
import java.util.ArrayList;
import java.util.List;

/** The saved gradients as JSON, the form of their one file. */
public final class GradientCodec {
    /** The file format's version. A file without it, or with another, is refused rather than guessed at. */
    public static final int FORMAT = 1;

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();

    private GradientCodec() {}

    public static String toJson(List<StoredGradient> gradients) {
        JsonObject o = new JsonObject();
        o.addProperty("format", FORMAT);
        JsonArray all = new JsonArray();
        for (StoredGradient g : gradients) all.add(toJson(g));
        o.add("gradients", all);
        return GSON.toJson(o);
    }

    private static JsonObject toJson(StoredGradient g) {
        JsonObject o = new JsonObject();
        o.addProperty("name", g.name());
        JsonArray stops = new JsonArray();
        for (StoredGradient.Stop s : g.stops()) {
            JsonObject stop = new JsonObject();
            stop.addProperty("colour", Color.ofRgb(s.rgb()).hex());
            if (s.isBlock()) block(stop, s.block());
            stops.add(stop);
        }
        o.add("stops", stops);
        o.addProperty("steps", g.steps());
        o.addProperty("easing", g.easing());
        o.addProperty("arc", g.arc());
        o.addProperty("spread", g.spread());
        o.addProperty("kind", g.kind());
        JsonArray blocks = new JsonArray();
        for (StoredGradient.Step s : g.blocks()) {
            JsonObject step = new JsonObject();
            step.addProperty("ideal", Color.ofRgb(s.ideal()).hex());
            block(step, s.block());
            step.addProperty("by_hand", s.byHand());
            blocks.add(step);
        }
        o.add("blocks", blocks);
        return o;
    }

    private static void block(JsonObject o, PaletteEntry e) {
        o.addProperty("block", e.blockId());
        o.addProperty("properties", e.properties());
    }

    /**
     * Reads the saved gradients. A missing or mistyped field gets its default and unknown fields are ignored, so a
     * file edited by hand still loads. A gradient without a name or two stops is left out, as is a step without a
     * block, since nothing can stand in for them. Throws IllegalArgumentException, saying why, for a file that isn't
     * one of these at all.
     */
    public static List<StoredGradient> fromJson(String json) {
        JsonElement root;
        try {
            root = JsonParser.parseString(json == null ? "" : json);
        } catch (JsonParseException e) {
            throw new IllegalArgumentException("This isn't valid JSON", e);
        }
        if (!root.isJsonObject()) throw new IllegalArgumentException("This isn't a list of gradients");
        JsonObject o = root.getAsJsonObject();
        if (!o.has("format")) throw new IllegalArgumentException("This file has no format number");
        JsonElement format = o.get("format");
        if (!format.isJsonPrimitive() || !format.getAsJsonPrimitive().isNumber() || format.getAsDouble() != FORMAT) {
            throw new IllegalArgumentException("This file has format " + format + ", and only format " + FORMAT + " can be read");
        }
        List<StoredGradient> out = new ArrayList<>();
        if (o.get("gradients") instanceof JsonArray all) {
            for (JsonElement element : all) {
                if (!(element instanceof JsonObject g)) continue;
                StoredGradient read = gradient(g);
                if (read != null) out.add(read);
            }
        }
        return out;
    }

    private static StoredGradient gradient(JsonObject o) {
        String name = string(o.get("name"), "");
        List<StoredGradient.Stop> stops = new ArrayList<>();
        if (o.get("stops") instanceof JsonArray array) {
            for (JsonElement element : array) {
                if (!(element instanceof JsonObject stop)) continue;
                Integer rgb = colour(stop.get("colour"));
                PaletteEntry block = block(stop);
                // A block stop whose colour was lost still has its block; a colour stop needs its colour.
                if (rgb == null && block == null) continue;
                stops.add(new StoredGradient.Stop(rgb == null ? 0 : rgb, block));
            }
        }
        if (name.isBlank() || stops.size() < 2) return null;
        List<StoredGradient.Step> blocks = new ArrayList<>();
        if (o.get("blocks") instanceof JsonArray array) {
            for (JsonElement element : array) {
                if (!(element instanceof JsonObject step)) continue;
                PaletteEntry block = block(step);
                if (block == null) continue;
                Integer ideal = colour(step.get("ideal"));
                blocks.add(new StoredGradient.Step(ideal == null ? 0 : ideal, block, bool(step.get("by_hand"))));
            }
        }
        int steps = o.get("steps") instanceof JsonElement e && e.isJsonPrimitive() && e.getAsJsonPrimitive().isNumber() ? (int) e.getAsDouble() : blocks.size();
        return new StoredGradient(name, stops, steps, string(o.get("easing"), ""), string(o.get("arc"), ""), bool(o.get("spread")),
                string(o.get("kind"), ""), blocks);
    }

    private static PaletteEntry block(JsonObject o) {
        String id = string(o.get("block"), "");
        return id.isBlank() ? null : new PaletteEntry(id, string(o.get("properties"), ""));
    }

    private static Integer colour(JsonElement e) {
        String hex = string(e, "");
        try {
            return hex.isEmpty() ? null : Color.fromHex(hex).rgb();
        } catch (IllegalArgumentException _) {
            return null;
        }
    }

    private static boolean bool(JsonElement e) {
        return e != null && e.isJsonPrimitive() && e.getAsJsonPrimitive().isBoolean() && e.getAsBoolean();
    }

    private static String string(JsonElement e, String fallback) {
        return e != null && e.isJsonPrimitive() && e.getAsJsonPrimitive().isString() ? e.getAsString() : fallback;
    }
}
