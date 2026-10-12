package dev.chromastudio.core.palette;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;

/** A palette as JSON, the form of its file and of JSON import and export. */
public final class PaletteCodec {
    /** The file format's version. A file without it, or with another, is refused rather than guessed at. */
    public static final int FORMAT = 1;

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();

    private PaletteCodec() {}

    public static String toJson(Palette p) {
        JsonObject o = new JsonObject();
        o.addProperty("format", FORMAT);
        o.addProperty("name", p.name == null ? "" : p.name);
        JsonArray tags = new JsonArray();
        if (p.tags != null) for (String tag : p.tags) tags.add(tag);
        o.add("tags", tags);
        o.addProperty("notes", p.notes == null ? "" : p.notes);
        JsonArray entries = new JsonArray();
        for (PaletteEntry e : p.entries) {
            JsonObject entry = new JsonObject();
            entry.addProperty("block", e.blockId());
            entry.addProperty("properties", e.properties());
            entry.addProperty("weight", e.weight());
            entries.add(entry);
        }
        o.add("entries", entries);
        return GSON.toJson(o);
    }

    /**
     * Reads a palette. A missing or mistyped field gets its default and unknown fields are ignored, so a file
     * edited by hand still loads. An entry with no block is left out, since nothing can stand in for it.
     */
    public static Palette fromJson(String json) {
        JsonElement root;
        try {
            root = JsonParser.parseString(json == null ? "" : json);
        } catch (JsonParseException e) {
            throw new IllegalArgumentException("This isn't valid JSON", e);
        }
        if (!root.isJsonObject()) throw new IllegalArgumentException("This isn't a palette");
        JsonObject o = root.getAsJsonObject();
        if (!o.has("format")) throw new IllegalArgumentException("This palette has no format number");
        if (!isInt(o.get("format"), FORMAT)) {
            throw new IllegalArgumentException("This palette has format " + o.get("format") + ", and only format " + FORMAT + " can be read");
        }

        Palette p = new Palette(string(o.get("name"), ""));
        p.notes = string(o.get("notes"), "");
        if (o.get("tags") instanceof JsonArray tags) {
            for (JsonElement tag : tags) {
                String s = string(tag, "");
                if (!s.isEmpty()) p.tags.add(s);
            }
        }
        if (o.get("entries") instanceof JsonArray entries) {
            for (JsonElement element : entries) {
                if (!(element instanceof JsonObject entry)) continue;
                String block = string(entry.get("block"), "");
                if (block.isBlank()) continue;
                p.entries.add(new PaletteEntry(block, string(entry.get("properties"), ""), weight(entry.get("weight"))));
            }
        }
        return p;
    }

    private static boolean isInt(JsonElement e, int value) {
        if (e == null || !e.isJsonPrimitive() || !e.getAsJsonPrimitive().isNumber()) return false;
        return e.getAsDouble() == value;
    }

    private static String string(JsonElement e, String fallback) {
        return e != null && e.isJsonPrimitive() && e.getAsJsonPrimitive().isString() ? e.getAsString() : fallback;
    }

    private static int weight(JsonElement e) {
        if (e == null || !e.isJsonPrimitive() || !e.getAsJsonPrimitive().isNumber()) return 1;
        double w = e.getAsDouble();
        return w >= 1 && w <= Integer.MAX_VALUE ? (int) w : 1;
    }
}
