package dev.chromastudio.client;

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
import org.junit.jupiter.api.Test;

/** The keys that code builds from names, which nothing else would notice missing from the language file. */
class LangTest {
    private static JsonObject english() throws IOException {
        try (InputStream in = LangTest.class.getResourceAsStream("/assets/chromastudio/lang/en_us.json")) {
            return JsonParser.parseString(new String(in.readAllBytes(), StandardCharsets.UTF_8)).getAsJsonObject();
        }
    }

    @Test
    void everyBuiltKeyHasItsText() throws IOException {
        List<String> keys = new ArrayList<>(Analyzer.KEYS);
        for (String preset : PaletteStore.PRESETS) keys.add("preset.chromastudio." + preset);
        for (Harmony h : Harmony.values()) keys.add("option.chromastudio.harmony." + h.name().toLowerCase(Locale.ROOT));
        for (Layout l : Layout.values()) keys.add("option.chromastudio.layoutchoice." + l.name().toLowerCase(Locale.ROOT));
        keys.add("gradient.chromastudio.kind.any");
        for (Kind k : Kind.values()) keys.add("gradient.chromastudio.kind." + k.name().toLowerCase(Locale.ROOT));
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
