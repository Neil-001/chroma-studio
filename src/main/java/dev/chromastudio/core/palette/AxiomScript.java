package dev.chromastudio.core.palette;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Lua scripts for Axiom's Script Brush, made from a palette or a gradient. Axiom places the blocks; these are only
 * text for the player to paste into its script editor.
 *
 * <p>What the scripts lean on, as Axiom 6.1 has it: the brush compiles a script once a stroke and then runs it for
 * each position with the globals {@code x}, {@code y} and {@code z} set, keeping every other global between
 * positions. {@code blocks.stone} is a block's id, {@code blocks.create.casing} one of another mod's, and either is
 * nil for a block the game lacks. A script returns a block, or nil to leave the position alone.
 * {@code $int(Name, default, min, max)$} and its kin anywhere in the text become controls in the brush's window, so
 * no {@code $} from a name may reach the script.
 */
public final class AxiomScript {
    /** The most characters Axiom counts, as {@link #counted}, that a server lets a player without import rights run. */
    public static final int SERVER_LIMIT = 1000;

    private static final Pattern NOT_COUNTED = Pattern.compile("local|function|return|if|for|then|end|and|or|nil|\\s");

    /**
     * One row of a recolour: the block the brush finds, the block it puts there with that block's fixed properties,
     * the properties it copies over from what it found, such as a stair's {@code facing}, and whether the block found
     * stands two high, as a door does, so that its other half changes with it.
     */
    public record Remap(String from, PaletteEntry to, List<String> copied, boolean twoHigh) {
        public Remap {
            copied = List.copyOf(copied);
        }

        /** A row for a block one high. */
        public Remap(String from, PaletteEntry to, List<String> copied) { this(from, to, copied, false); }
    }

    /**
     * The properties by which a gradient script leaves a block alone: a stair, slab, wall, door, chest or any block
     * that faces a way has one, which a full step would reshape, turn or empty, and so do a head, a cauldron and a
     * composter.
     */
    public static final List<String> KEPT_BY_PROPERTY = List.of("waterlogged", "facing", "half", "orientation", "mode", "rotation", "level");

    /**
     * The block tags by which a gradient script leaves a block alone: the ground, and families of blocks a full step
     * would wrongly fill, such as carpets. A block that holds something is never left to a tag, as the world a script
     * runs in may tag it otherwise; the script names it.
     */
    public static final List<String> GRADIENT_TAGS = List.of("substrate_overworld", "sand", "wool_carpets", "candle_cakes", "cauldrons");

    /**
     * What a script leaves alone besides what it always checks: blocks with any of {@code properties}; blocks with
     * any of {@code tags}, only ones the game knows, as Axiom stops a script that asks about another; and the blocks
     * it names, each by its id.
     */
    public record Kept(List<String> properties, List<String> tags, List<String> blocks) {
        public Kept {
            properties = List.copyOf(properties);
            tags = List.copyOf(tags);
            blocks = List.copyOf(blocks);
        }
    }

    /** The axis a gradient runs along. */
    public enum Axis { X, Y, Z }

    // Turns "minecraft:stone" with "axis=y" into a block, or nil when the game lacks it. Shared by every script.
    private static final String BLOCK_FUNCTION = """
            local function block(ns, path, props)
            \tlocal t = blocks
            \tif ns ~= "minecraft" then t = blocks[ns] end
            \tlocal b = t and t[path]
            \tif b == nil then return nil end
            \tif props then
            \t\tfor p in string.gmatch(props, "[^,]+") do b = withBlockProperty(b, p) end
            \tend
            \treturn b
            end
            """;

    private AxiomScript() {}

    /**
     * A brush that swaps each block it touches for the one a recolour gives it, keeping the properties each row
     * copies, and leaves every other block alone. The rows are put in a table the first time the stroke runs the
     * script, so the cost of a long table is paid once.
     *
     * <p>A recolour of every block runs to a thousand rows or more, and the game copies text through a buffer of
     * 64 KiB, so the script is kept small: each block it puts down is written out once, in a list, and the rows are
     * one long string of {@code from=number} pairs that the script reads into its table.
     *
     * <p>A door changes as a whole: when the rows hold a block two high, the half the brush reaches also sets its
     * other half, which may lie past the edge of the stroke, as the palette brush does. Axiom's {@code setBlock}
     * doesn't ask the tool masks, so neither half is left behind when a mask cuts a door in two. Scripts without such
     * a row leave that part out, as servers limit how long a script may be.
     */
    public static String recolour(String title, List<Remap> rows) {
        if (rows.isEmpty()) throw new IllegalArgumentException("A recolour needs at least one block to change");
        List<String> targets = new ArrayList<>();
        Map<String, Integer> numbers = new HashMap<>();
        List<String> pairs = new ArrayList<>();
        for (Remap r : rows) {
            StringBuilder target = new StringBuilder("{").append(ref(r.to().blockId(), r.to().properties()));
            for (String p : r.copied()) target.append(", ").append(quote(p));
            String t = target.append("}").toString();
            Integer n = numbers.get(t);
            if (n == null) {
                targets.add(t);
                numbers.put(t, n = targets.size());
            }
            pairs.add(shortId(r.from()) + "=" + n);
        }
        StringBuilder s = header(title, "Recolours each block the brush touches; a block it has no row for is left alone.");
        s.append("if chroma_rows == nil then\n");
        s.append(indent(BLOCK_FUNCTION));
        s.append("\t-- What each block becomes, and the properties it keeps from the block it replaces.\n");
        s.append("\tlocal targets = {\n");
        for (String t : targets) s.append("\t\t").append(t).append(",\n");
        s.append("\t}\n");
        s.append("\t-- Each block the brush changes, and the number of what it becomes.\n");
        s.append("\tlocal data = [[\n");
        StringBuilder line = new StringBuilder();
        for (String pair : pairs) {
            if (!line.isEmpty() && line.length() + pair.length() >= 100) {
                s.append(line).append("\n");
                line.setLength(0);
            }
            if (!line.isEmpty()) line.append(' ');
            line.append(pair);
        }
        s.append(line).append("\n]]\n");
        s.append("""
                \tlocal rows = {}
                \tfor from, n in string.gmatch(data, "(%S+)=(%d+)") do
                \t\tlocal ns, path = string.match(from, "^([^:]+):(.+)")
                \t\tif ns == nil then ns, path = "minecraft", from end
                \t\tlocal b, t = block(ns, path), targets[tonumber(n)]
                \t\tif b ~= nil and t[1] ~= nil then rows[b] = t end
                \tend
                \tchroma_rows = rows
                end

                local row = chroma_rows[getBlock(x, y, z)]
                if row == nil then return nil end
                local out = row[1]
                if #row > 1 then
                \tlocal here = getBlockState(x, y, z)
                \tfor i = 2, #row do
                \t\tlocal value = getBlockProperty(here, row[i])
                \t\tif value ~= nil then out = withBlockProperty(out, row[i] .. "=" .. value) end
                \tend
                end
                """);
        if (rows.stream().anyMatch(Remap::twoHigh)) s.append("""
                -- A door changes as a whole: its other half takes the same block, even past the edge of the stroke or a mask.
                local half = getBlockProperty(getBlockState(x, y, z), "half")
                if half == "lower" or half == "upper" then
                \tlocal oy = y + 1
                \tif half == "upper" then oy = y - 1 end
                \tlocal other = getBlockState(x, oy, z)
                \tif getBlock(x, oy, z) == getBlock(x, y, z) and getBlockProperty(other, "half") ~= half then
                \t\tlocal put = row[1]
                \t\tfor i = 2, #row do
                \t\t\tlocal value = getBlockProperty(other, row[i])
                \t\t\tif value ~= nil then put = withBlockProperty(put, row[i] .. "=" .. value) end
                \t\tend
                \t\tsetBlock(x, oy, z, put)
                \tend
                end
                """);
        s.append("return out\n");
        return s.toString();
    }

    private static final Pattern PLAIN = Pattern.compile("[a-z_][a-z0-9_]*");
    private static final Set<String> LUA_WORDS = Set.of("and", "break", "do", "else", "elseif", "end", "false", "for", "function", "goto", "if",
            "in", "local", "nil", "not", "or", "repeat", "return", "then", "true", "until", "while");

    /** Whether a block is the game's own, without properties, and named so that {@code blocks.name} reads it. */
    private static boolean plain(String id, String properties) {
        String path = shortId(id);
        return (properties == null || properties.isEmpty()) && PLAIN.matcher(path).matches() && !LUA_WORDS.contains(path);
    }

    /** {@code blocks.stone} for a plain block, and otherwise a call of the function that finds it. */
    private static String lookup(String id, String properties) { return plain(id, properties) ? "blocks." + shortId(id) : ref(id, properties); }

    /** {@code stone} for {@code minecraft:stone}, and any other id as it is. */
    private static String shortId(String id) {
        return id.startsWith(PaletteEntry.DEFAULT_NAMESPACE) ? id.substring(PaletteEntry.DEFAULT_NAMESPACE.length()) : id;
    }

    /**
     * A brush that paints blocks from the palette at random, each as often as its weight says. As the palette brush's
     * Scatter does, it fills air too, unless its Paint air box is cleared, and leaves either half of a door or a tall
     * plant alone, which would otherwise be left standing on its own, and every block {@code kept} covers, which
     * holds items or text, such as a chest or a sign.
     */
    public static String scatter(Palette p, Kept kept) {
        if (p.entries.isEmpty()) throw new IllegalArgumentException("The palette is empty");
        // Only the title, as servers count comments toward the length they allow, and the chat says what it does.
        StringBuilder s = new StringBuilder("-- Chroma Studio: ").append(safe(p.name)).append("\n\n");
        s.append(airControl(true));
        s.append("if chroma_picks == nil then\n");
        if (p.entries.stream().anyMatch(e -> !plain(e.blockId(), e.properties())) || kept.blocks().stream().anyMatch(id -> !plain(id, ""))) {
            s.append(indent(BLOCK_FUNCTION));
        }
        s.append("\tlocal picks, total = {}, 0\n");
        s.append("\tlocal function put(b, weight)\n");
        s.append("\t\tif b ~= nil then total = total + weight; picks[#picks + 1] = {total, b} end\n");
        s.append("\tend\n");
        for (PaletteEntry e : p.entries) s.append("\tput(").append(lookup(e.blockId(), e.properties())).append(", ").append(e.weight()).append(")\n");
        s.append("\tchroma_picks, chroma_total = picks, total\n");
        // A table of its own, so a gradient script run in the same brush can't find this one's.
        s.append(keptTable("chroma_skipped", kept.blocks()));
        s.append("end\n\n");
        s.append("local b = getBlock(x, y, z)\n");
        // Caves are filled with cave air, which Paint air means too.
        s.append("if not paint_air and (b == blocks.air or b == blocks.cave_air or b == blocks.void_air) then return nil end\n");
        s.append("local half = getBlockProperty(getBlockState(x, y, z), \"half\")\n");
        s.append("if chroma_skipped[b] or half == \"upper\" or half == \"lower\" then return nil end\n");
        s.append(checks(kept.properties(), kept.tags(), ""));
        s.append("""
                if chroma_total == 0 then return nil end
                local roll = math.random() * chroma_total
                for i = 1, #chroma_picks do
                \tif roll < chroma_picks[i][1] then return chroma_picks[i][2] end
                end
                return chroma_picks[#chroma_picks][2]
                """);
        return s.toString();
    }

    /**
     * A brush that paints a gradient's blocks along an axis, the first block at {@code start} and the last at
     * {@code start + length - 1}, held at either end past them. Along Y the two ends are controls in the brush's
     * window; along X or Z the start is fixed where the script was made and the length is a control, negative to run
     * the other way. A blend control lets neighbouring steps mix at their edges.
     *
     * <p>It changes what the gradient tool would: solid blocks, but not the ground, and not a block whose shape a full
     * step would lose or whose contents it would empty. The script can't ask a block's shape or whether it holds
     * anything, so it goes by what it can ask: a block with no collision, one with one of {@link #KEPT_BY_PROPERTY},
     * such as a stair, slab, wall, door or chest, or with a property or tag of {@code kept}, such as a turtle egg or a
     * flower pot, is left alone, where the tool would give a stair the step's stair; and so is each block
     * {@code kept} names, which the tool leaves and those checks miss, such as a jukebox. Unlike the tool, it paints
     * any other solid block the brush reaches, not only one build. Every step must be a full block, which the caller
     * sees to.
     */
    public static String gradient(String title, List<PaletteEntry> steps, Axis axis, int start, int length, Kept kept) {
        if (steps.size() < 2) throw new IllegalArgumentException("A gradient needs at least two steps");
        if (length == 0) throw new IllegalArgumentException("A gradient needs a length");
        // Only the title, as servers count comments toward the length they allow, and the chat says what it does.
        StringBuilder s = new StringBuilder("-- Chroma Studio: ").append(safe(title)).append("\n\n");
        String along = axis.name().toLowerCase(Locale.ROOT);
        if (axis == Axis.Y) {
            int end = length > 0 ? start + length - 1 : start + length + 1;
            int low = Math.min(start, end), high = Math.max(start, end);
            s.append("local first = $int(Bottom, ").append(low).append(", -64, 319)$\n");
            s.append("local last = $int(Top, ").append(high).append(", -64, 319)$\n");
        } else {
            int limit = Math.max(256, Math.abs(length));
            s.append("local first = ").append(start).append("\n");
            s.append("local last = first + $int(Length, ").append(length).append(", ").append(-limit).append(", ").append(limit).append(")$\n");
            s.append("if last > first then last = last - 1 else last = last + 1 end\n");
        }
        s.append("local blend = $float(Blend, 0.5, 0.0, 2.0)$\n");
        s.append(airControl(false));
        s.append("if chroma_steps == nil then\n");
        // A plain block of the game is named directly, which keeps the script short enough for a server; only another
        // mod's block, or one with properties, needs the function that finds it.
        if (steps.stream().anyMatch(e -> !plain(e.blockId(), e.properties())) || kept.blocks().stream().anyMatch(id -> !plain(id, ""))) {
            s.append(indent(BLOCK_FUNCTION));
        }
        s.append("\tchroma_steps = {\n");
        for (PaletteEntry e : steps) s.append("\t\t").append(lookup(e.blockId(), e.properties())).append(" or false,\n");
        s.append("\t}\n");
        s.append(keptTable("chroma_kept", kept.blocks()));
        s.append("end\n\n");
        // As the gradient tool does: the ground, plants and blocks a full step would reshape or empty stay. No comment
        // says so in the script, as servers count comments toward the length they allow.
        s.append("""
                local b = getBlock(x, y, z)
                if b == blocks.air then
                \tif not paint_air then return nil end
                elseif chroma_kept[b] or not isSolid(b) then
                \treturn nil
                else
                """);
        List<String> properties = new ArrayList<>(KEPT_BY_PROPERTY);
        for (String name : kept.properties()) if (!properties.contains(name)) properties.add(name);
        s.append(checks(properties, kept.tags(), "\t"));
        s.append("end\n");
        // Each step takes an equal share of the blocks from first to last, both ends included.
        s.append("local n = #chroma_steps\n");
        s.append("local span = last - first\n");
        s.append("local t = ((").append(along).append(" - first) * (span < 0 and -1 or 1) + 0.5) / (math.abs(span) + 1)\n");
        s.append("""
                local i = math.floor(t * n + (math.random() - 0.5) * blend) + 1
                if i < 1 then i = 1 elseif i > n then i = n end
                return chroma_steps[i] or nil
                """);
        return s.toString();
    }

    /** How long Axiom judges a script to be: its length without whitespace or the keywords it doesn't count. */
    public static int counted(String script) { return NOT_COUNTED.matcher(script).replaceAll("").length(); }

    /**
     * A name made safe for a Lua comment: no line breaks, which would end the comment, and no {@code $}, which Axiom
     * would read as the start of a control.
     */
    static String safe(String name) {
        String s = name == null ? "" : name.replaceAll("[\\r\\n$]+", " ").strip();
        return s.isEmpty() ? "Untitled" : s;
    }

    private static StringBuilder header(String title, String what) {
        StringBuilder s = new StringBuilder();
        s.append("-- Chroma Studio: ").append(safe(title)).append("\n");
        s.append("-- ").append(what).append("\n\n");
        return s;
    }

    /**
     * Fills {@code table}, inside a script's first run, with the blocks it leaves alone, by id. The game's own are one
     * string of names, which servers count only by its letters; another mod's are each found by the function that
     * finds a block, which the script must then have.
     */
    private static String keptTable(String table, List<String> ids) {
        StringBuilder s = new StringBuilder("\t").append(table).append(" = {}\n");
        List<String> plain = ids.stream().filter(id -> plain(id, "")).map(AxiomScript::shortId).toList();
        if (!plain.isEmpty()) s.append("\t").append(eachWord(plain, "k")).append(" ").append(table).append("[blocks[k] or false] = true end\n");
        for (String id : ids) if (!plain(id, "")) s.append("\t").append(table).append("[").append(ref(id, "")).append(" or false] = true\n");
        return s.toString();
    }

    /**
     * Leaves block {@code b} alone when it has one of {@code properties} or {@code tags}. Whether a block has a
     * property doesn't depend on its state, so the block's own id is asked.
     */
    private static String checks(List<String> properties, List<String> tags, String indent) {
        StringBuilder s = new StringBuilder();
        if (!properties.isEmpty()) s.append(indent).append(eachWord(properties, "p")).append(" if getBlockProperty(b, p) then return nil end end\n");
        if (!tags.isEmpty()) s.append(indent).append(eachWord(tags, "t")).append(" if isBlockTagged(b, t) then return nil end end\n");
        return s.toString();
    }

    /** {@code for v in string.gmatch("a b c", "%S+") do}, a loop over words, which is shorter than a table of strings. */
    private static String eachWord(List<String> words, String v) {
        return "for " + v + " in string.gmatch(" + quote(String.join(" ", words)) + ", \"%S+\") do";
    }

    private static String airControl(boolean byDefault) { return "local paint_air = $boolean(Paint air, " + byDefault + ")$\n\n"; }


    /** A call that gives a block, written so that any id, however its path is spelled, comes through. */
    static String ref(String id, String properties) {
        int colon = id.indexOf(':');
        String ns = colon < 0 ? "minecraft" : id.substring(0, colon), path = id.substring(colon + 1);
        StringBuilder s = new StringBuilder("block(").append(quote(ns)).append(", ").append(quote(path));
        if (properties != null && !properties.isEmpty()) s.append(", ").append(quote(properties));
        return s.append(")").toString();
    }

    /** A Lua string literal. Ids and property names never need escaping, but a quote or backslash costs nothing to guard. */
    static String quote(String text) {
        return "\"" + text.replace("\\", "\\\\").replace("\"", "\\\"").replace("$", "\\036") + "\"";
    }

    private static String indent(String text) {
        List<String> out = new ArrayList<>();
        for (String line : text.split("\n")) out.add(line.isEmpty() ? line : "\t" + line);
        return String.join("\n", out) + "\n";
    }
}
