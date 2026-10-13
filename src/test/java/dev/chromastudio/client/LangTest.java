package dev.chromastudio.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.chromastudio.client.index.Kind;
import dev.chromastudio.core.color.Harmony;
import dev.chromastudio.core.export.Layout;
import dev.chromastudio.core.palette.Analyzer;
import dev.chromastudio.core.palette.WorldEditPattern;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

/**
 * The keys that code builds from names, which nothing else would notice missing from the language file, and the
 * generated translations, which must keep up with the English.
 */
class LangTest {
    /** The generated translations. English is written by hand. */
    private static final List<String> TRANSLATIONS = List.of("zh_cn", "ru_ru", "pt_br", "de_de", "es_es", "fr_fr", "ja_jp", "ko_kr", "pl_pl");
    /** What the game fills in: {@code %s}, {@code %d} and their numbered forms such as {@code %1$s}. */
    private static final Pattern PLACEHOLDER = Pattern.compile("%(?:\\d+\\$)?([sd])");

    private static JsonObject english() throws IOException { return language("en_us"); }

    private static JsonObject language(String code) throws IOException {
        try (InputStream in = LangTest.class.getResourceAsStream("/assets/chromastudio/lang/" + code + ".json")) {
            assertNotNull(in, code + ".json is missing");
            return JsonParser.parseString(new String(in.readAllBytes(), StandardCharsets.UTF_8)).getAsJsonObject();
        }
    }

    /** A text's placeholders in order of kind, so a translation may move them but not drop or add one. */
    private static List<String> placeholders(String text) {
        List<String> found = new ArrayList<>();
        Matcher m = PLACEHOLDER.matcher(text.replace("%%", ""));
        while (m.find()) found.add(m.group(1));
        found.sort(null);
        return found;
    }

    @Test
    void everyTranslationHasEveryKeyAndNoOther() throws IOException {
        JsonObject english = english();
        assertFalse(english.has("_comment"));
        for (String code : TRANSLATIONS) {
            JsonObject translated = language(code);
            assertTrue(translated.has("_comment") && !translated.get("_comment").getAsString().isBlank(), code + " says it is generated");
            Set<String> extra = new TreeSet<>(translated.keySet()), missing = new TreeSet<>(english.keySet());
            extra.removeAll(english.keySet());
            extra.remove("_comment");
            missing.removeAll(translated.keySet());
            assertEquals(Set.of(), extra, code + " has keys English lacks");
            assertEquals(Set.of(), missing, code + " lacks keys");
            for (String key : english.keySet()) {
                String text = translated.get(key).getAsString();
                assertFalse(text.isBlank(), code + " " + key);
                assertEquals(placeholders(english.get(key).getAsString()), placeholders(text), code + " " + key + " fills in the same things");
            }
        }
    }

    @Test
    void everyBuiltKeyHasItsText() throws IOException {
        List<String> keys = new ArrayList<>(Analyzer.KEYS);
        for (String preset : PaletteStore.PRESETS) keys.add("preset.chromastudio." + preset);
        for (Harmony h : Harmony.values()) keys.add("option.chromastudio.harmony." + h.name().toLowerCase(Locale.ROOT));
        for (Layout l : Layout.values()) keys.add("option.chromastudio.layoutchoice." + l.name().toLowerCase(Locale.ROOT));
        keys.add("kind.chromastudio.all");
        for (Kind k : Kind.values()) keys.add("kind.chromastudio." + k.name().toLowerCase(Locale.ROOT));
        for (String tool : List.of("generate", "capture", "image", "analyse", "diorama", "export", "worldedit", "hotbar")) {
            keys.add("palettes.chromastudio.tool." + tool);
            keys.add("palettes.chromastudio.tool." + tool + ".tip");
        }
        for (String action : List.of("new", "rename", "delete", "import", "share", "copy")) {
            keys.add("palettes.chromastudio." + action);
            keys.add("palettes.chromastudio." + action + ".tip");
        }
        for (String action : List.of("hotbar", "copy", "export", "save", "swap")) {
            keys.add("gradient.chromastudio.action." + action);
            keys.add("gradient.chromastudio.action." + action + ".tip");
        }
        JsonObject english = english();
        for (String key : keys) assertTrue(english.has(key) && !english.get(key).getAsString().isBlank(), key);
        // The dialog shows the commands as they're typed, so they need no text of their own, only to be the five expected.
        assertTrue(WorldEditPattern.COMMANDS.containsAll(List.of("set", "replace", "walls", "outline", "overlay")));
    }
}
