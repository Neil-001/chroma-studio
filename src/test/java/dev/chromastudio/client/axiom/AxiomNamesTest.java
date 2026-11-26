package dev.chromastudio.client.axiom;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.chromastudio.client.index.TestIndex;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.network.chat.Style;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.StringDecomposer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class AxiomNamesTest {
    private static final List<String> LANGUAGES = List.of("en_us", "de_de", "es_es", "fr_fr", "ja_jp", "ko_kr", "pl_pl", "pt_br", "ru_ru", "zh_cn");
    private static final List<String> NAMES = List.of("Tool Masks", "Script Brush", "Import Schematic", "Tool Options", "Use Tool");
    private static Language before;

    @BeforeAll
    static void loadGame() {
        TestIndex.loadGame();
        before = Language.getInstance();
    }

    @AfterEach
    void restore() { Language.inject(before); }

    private static JsonObject language(String code) throws IOException {
        try (InputStream in = AxiomNamesTest.class.getResourceAsStream("/assets/chromastudio/lang/" + code + ".json")) {
            return JsonParser.parseString(new String(in.readAllBytes(), StandardCharsets.UTF_8)).getAsJsonObject();
        }
    }

    private static void use(Map<String, String> text) {
        Language.inject(new Language() {
            @Override public String getOrDefault(String key, String fallback) { return text.getOrDefault(key, fallback); }
            @Override public boolean has(String key) { return text.containsKey(key); }
            @Override public boolean isDefaultRightToLeft() { return false; }
            @Override public FormattedCharSequence getVisualOrder(FormattedText t) {
                return sink -> t.visit((style, s) -> StringDecomposer.iterateFormatted(s, style, sink) ? Optional.empty() : FormattedText.STOP_ITERATION, Style.EMPTY).isPresent();
            }
        });
    }

    private static Map<String, String> ours(String code) throws IOException {
        Map<String, String> text = new HashMap<>();
        for (var e : language(code).entrySet()) text.put(e.getKey(), e.getValue().getAsString());
        return text;
    }

    @Test
    void withoutAxiomsTranslationsTheEnglishTextsReadAsBefore() throws IOException {
        use(ours("en_us"));
        Map<String, String> was = Map.of(
                "axiom.chromastudio.copied", "Copied. Paste into Axiom's Script Brush.",
                "axiom.chromastudio.gradient.rules", "Starts where you stand. The Script Brush's own controls move its ends.",
                "palettes.chromastudio.export.axiom", "To use it in Axiom's editor: File > Import Schematic.",
                "axiom.chromastudio.masks.tip", "The tool changes only blocks the mask under Tool Masks allows, and only inside the selection if there is one.",
                "axiom.chromastudio.masks.off.tip", "Axiom applies tool masks only while its Tool Masks window is open, docked or minimised.",
                "axiom.chromastudio.mask.lua", "The mask's script has an error. Fix it, or close the error under Tool Masks.",
                "axiom.chromastudio.shade.point", "Point at a block to preview it. Hold Use Tool to shade.");
        assertEquals(was.keySet(), AxiomNames.keys());
        for (var e : was.entrySet()) assertEquals(e.getValue(), AxiomNames.text(e.getKey()).getString());
    }

    @Test
    void axiomsOwnNameIsTakenWithoutItsEllipsis() throws IOException {
        Map<String, String> text = ours("ru_ru");
        text.put("axiom.editorui.window.tool_masks", "Маски инструментов");
        text.put("axiom.editorui.mainmenu.file", "Файл");
        text.put("axiom.editorui.mainmenu.file.import_schematic", "Импортировать схематику...");
        use(text);
        assertEquals("В скрипте маски ошибка. Исправьте его или закройте ошибку в окне «Маски инструментов».", AxiomNames.text("axiom.chromastudio.mask.lua").getString());
        assertEquals("В редакторе Axiom: Файл > Импортировать схематику.", AxiomNames.text("palettes.chromastudio.export.axiom").getString());
        assertEquals("Скопировано. Вставьте в инструмент «Script Brush» в Axiom.", AxiomNames.text("axiom.chromastudio.copied").getString());
    }

    @Test
    void noTextSpellsOutANameAxiomGives() throws IOException {
        for (String code : LANGUAGES) {
            for (var e : language(code).entrySet()) {
                for (String name : NAMES) assertFalse(e.getValue().getAsString().contains(name), code + " " + e.getKey() + " names " + name + " itself");
            }
        }
        for (String key : AxiomNames.keys()) assertTrue(language("en_us").has(key), key);
    }
}
