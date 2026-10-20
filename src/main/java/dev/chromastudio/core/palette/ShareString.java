package dev.chromastudio.core.palette;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * A palette as one line of text to paste to someone: {@code chroma1:stone,andesite*3,oak_log[axis=y]/Name}.
 * Entries are separated by commas. The namespace is left off the game's own blocks, properties go in brackets,
 * a weight other than 1 follows {@code *}, and the name follows the first slash. Tags and notes aren't carried.
 */
public final class ShareString {
    public static final String PREFIX = "chroma1:";

    // A slash ends the entries, so the rare block id with one in its path carries it as this. Ids can't hold a
    // percent sign, which keeps it from being mistaken for anything else.
    private static final String SLASH = "%2F";
    private static final Pattern VERSIONED = Pattern.compile("chroma\\d+:.*", Pattern.DOTALL);
    private static final Pattern ID = Pattern.compile("([a-z0-9_.-]+:)?[a-z0-9_./-]+");
    private static final Pattern PROPERTIES = Pattern.compile("[a-z0-9_]+=[a-z0-9_]+(,[a-z0-9_]+=[a-z0-9_]+)*");

    private ShareString() {}

    public static String encode(Palette p) {
        StringBuilder out = new StringBuilder(PREFIX);
        for (int i = 0; i < p.entries.size(); i++) {
            PaletteEntry e = p.entries.get(i);
            if (i > 0) out.append(',');
            out.append(e.withProperties(e.shortId().replace("/", SLASH)));
            if (e.weight() != 1) out.append('*').append(e.weight());
        }
        if (p.name != null && !p.name.isEmpty()) out.append('/').append(p.name);
        return out.toString();
    }

    /** Reads a share string, with any whitespace around it ignored. Says what is wrong when it can't. */
    public static Palette decode(String s) {
        if (s == null || s.isBlank()) throw new IllegalArgumentException("There is no share string");
        String text = s.strip();
        if (!text.startsWith(PREFIX)) {
            if (VERSIONED.matcher(text).matches()) {
                throw new IllegalArgumentException("This share string is from a newer version of Chroma Studio");
            }
            throw new IllegalArgumentException("A share string starts with " + PREFIX);
        }
        int slash = text.indexOf('/', PREFIX.length());
        String entries = text.substring(PREFIX.length(), slash < 0 ? text.length() : slash);
        Palette p = new Palette(slash < 0 ? "" : text.substring(slash + 1));
        if (entries.isBlank()) return p;
        for (String entry : split(entries)) p.entries.add(entry(entry.strip()));
        return p;
    }

    /** Splits at the commas between entries, leaving the ones between properties alone. */
    private static List<String> split(String entries) {
        List<String> out = new ArrayList<>();
        int start = 0;
        boolean bracketed = false;
        for (int i = 0; i < entries.length(); i++) {
            char c = entries.charAt(i);
            if (c == '[') bracketed = true;
            else if (c == ']') bracketed = false;
            else if (c == ',' && !bracketed) {
                out.add(entries.substring(start, i));
                start = i + 1;
            }
        }
        out.add(entries.substring(start));
        return out;
    }

    private static PaletteEntry entry(String text) {
        if (text.isEmpty()) throw new IllegalArgumentException("There is an empty entry between two commas");
        String rest = text;
        int weight = 1;
        int star = rest.lastIndexOf('*');
        if (star >= 0) {
            weight = weight(rest.substring(star + 1), text);
            rest = rest.substring(0, star);
        }
        String properties = "";
        int open = rest.indexOf('[');
        if (open >= 0) {
            if (!rest.endsWith("]")) throw new IllegalArgumentException("The properties of \"" + text + "\" have no closing bracket");
            properties = rest.substring(open + 1, rest.length() - 1);
            if (!PROPERTIES.matcher(properties).matches()) {
                throw new IllegalArgumentException("\"" + properties + "\" in \"" + text + "\" isn't a list of properties like axis=y");
            }
            rest = rest.substring(0, open);
        }
        String id = rest.replace(SLASH, "/");
        if (!ID.matcher(id).matches()) throw new IllegalArgumentException("\"" + rest + "\" isn't a block id");
        return new PaletteEntry(id.contains(":") ? id : PaletteEntry.DEFAULT_NAMESPACE + id, properties, weight);
    }

    private static int weight(String digits, String entry) {
        try {
            int weight = Integer.parseInt(digits);
            if (weight >= 1) return weight;
        } catch (NumberFormatException ignored) {
            // Reported below, with the same message as a weight under 1.
        }
        throw new IllegalArgumentException("The weight in \"" + entry + "\" isn't a whole number from 1 up");
    }
}
