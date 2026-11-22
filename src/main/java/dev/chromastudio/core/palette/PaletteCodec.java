package dev.chromastudio.core.palette;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;

public final class PaletteCodec {
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

    public static Palette fromJson(String json) {
        JsonElement root;
        try {
            root = JsonParser.parseString(json == null ? "" : json);
        } catch (JsonParseException e) {
            throw new UnreadablePalette(UnreadablePalette.Reason.NOT_JSON, e);
        }
        if (!root.isJsonObject()) throw new UnreadablePalette(UnreadablePalette.Reason.NOT_PALETTE);
        JsonObject o = root.getAsJsonObject();
        if (!o.has("format")) throw new UnreadablePalette(UnreadablePalette.Reason.NO_FORMAT);
        if (!isInt(o.get("format"), FORMAT)) {
            throw new UnreadablePalette(UnreadablePalette.Reason.OTHER_FORMAT, String.valueOf(o.get("format")), String.valueOf(FORMAT));
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
