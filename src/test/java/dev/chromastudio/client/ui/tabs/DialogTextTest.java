package dev.chromastudio.client.ui.tabs;

import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.blaze3d.font.GlyphProvider;
import com.mojang.blaze3d.font.UnbakedGlyph;
import com.mojang.serialization.JsonOps;
import dev.chromastudio.client.OverflowMode;
import dev.chromastudio.client.action.HotbarFill;
import dev.chromastudio.client.action.HotbarPlan;
import dev.chromastudio.client.axiom.AxiomBridge;
import dev.chromastudio.client.index.TestIndex;
import dev.chromastudio.client.ui.Bounds;
import dev.chromastudio.client.ui.Flow;
import dev.chromastudio.client.ui.Window;
import dev.chromastudio.core.palette.AxiomScript;
import dev.chromastudio.core.palette.Palette;
import dev.chromastudio.core.palette.PaletteEntry;
import dev.chromastudio.core.palette.Recolour;
import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import net.minecraft.client.StringSplitter;
import net.minecraft.client.gui.font.providers.BitmapProvider;
import net.minecraft.core.BlockPos;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.Identifier;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.StringDecomposer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class DialogTextTest {
    private static final List<String> LANGUAGES = List.of("en_us", "de_de", "es_es", "fr_fr", "ja_jp", "ko_kr", "pl_pl", "pt_br", "ru_ru", "zh_cn");
    private static final int UNIFONT_MOST = 9;
    private static final Set<Character.UnicodeBlock> FULL_WIDTH = Set.of(Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS, Character.UnicodeBlock.HIRAGANA,
            Character.UnicodeBlock.KATAKANA, Character.UnicodeBlock.HANGUL_SYLLABLES, Character.UnicodeBlock.CJK_SYMBOLS_AND_PUNCTUATION,
            Character.UnicodeBlock.HALFWIDTH_AND_FULLWIDTH_FORMS);

    private static final Map<String, String> ENGLISH_NAMES = Map.of("tool_masks", "Tool Masks", "file", "File", "import", "Import Schematic...");
    private static final Map<String, Map<String, String>> AXIOM_NAMES = Map.of(
            "de_de", Map.of("tool_masks", "Werkzeugmasken", "file", "Datei", "import", "Schematic Importieren..."),
            "es_es", Map.of("tool_masks", "Máscaras de Herramienta", "file", "Archivo", "import", "Importar Esquema..."),
            "fr_fr", Map.of("tool_masks", "Masque d'outil", "file", "Fichier", "import", "Importer une Schematic…"),
            "ja_jp", Map.of("tool_masks", "マスクツール"),
            "ko_kr", Map.of("tool_masks", "도구 마스크", "file", "파일", "import", "도면(.Schematic) 가져오기..."),
            "pl_pl", Map.of("tool_masks", "Maski narzędzi"),
            "pt_br", Map.of("tool_masks", "Ferramentas de Máscara", "file", "Arquivc", "import", "Importart Esquemática"),
            "ru_ru", Map.of("tool_masks", "Маски инструментов", "file", "Файл", "import", "Импортировать схематику..."),
            "zh_cn", Map.of("tool_masks", "蒙版工具编辑面板", "file", "文件", "import", "导入原理图..."));

    private static final List<GlyphProvider> glyphs = new ArrayList<>();
    private static StringSplitter splitter;
    private static Language before;
    private static String language;
    private static final List<String> failures = new ArrayList<>();

    @BeforeAll
    static void loadFont() throws IOException {
        TestIndex.loadGame();
        TestIndex.index(Map.of("stone", "block/stone", "andesite", "block/andesite", "oak_planks", "block/oak_planks", "oak_stairs", "block/oak_planks",
                "lantern", "block/lantern", "terracotta", "block/terracotta", "birch_planks", "block/birch_planks"));
        try (Reader in = TestIndex.resources().openAsReader(Identifier.withDefaultNamespace("font/include/default.json"))) {
            for (JsonElement p : JsonParser.parseReader(in).getAsJsonObject().getAsJsonArray("providers")) {
                JsonObject provider = p.getAsJsonObject();
                if (!provider.get("type").getAsString().equals("bitmap")) continue;
                BitmapProvider.Definition definition = BitmapProvider.Definition.CODEC.codec().parse(JsonOps.INSTANCE, provider).getOrThrow();
                glyphs.add(definition.unpack().left().orElseThrow().load(TestIndex.resources()));
            }
        }
        splitter = new StringSplitter((c, _) -> advance(c));
        before = Language.getInstance();
    }

    @AfterAll
    static void restore() { Language.inject(before); }

    private static float advance(int c) {
        if (c == ' ') return 4;
        for (GlyphProvider p : glyphs) {
            UnbakedGlyph g = p.getGlyph(c);
            if (g != null) return g.info().getAdvance();
        }
        if (FULL_WIDTH.contains(Character.UnicodeBlock.of(c))) return UNIFONT_MOST;
        throw new AssertionError(language + ": no width known for U+" + Integer.toHexString(c));
    }

    private static void use(String lang, boolean axiomsOwn) throws IOException {
        language = axiomsOwn ? lang + " with Axiom's names" : lang;
        Map<String, String> text = new HashMap<>();
        try (InputStream in = DialogTextTest.class.getResourceAsStream("/assets/chromastudio/lang/" + lang + ".json")) {
            Language.loadFromJson(in, text::put);
        }
        Map<String, String> names = new HashMap<>(ENGLISH_NAMES);
        if (axiomsOwn) names.putAll(AXIOM_NAMES.getOrDefault(lang, Map.of()));
        text.put("axiom.editorui.window.tool_masks", names.get("tool_masks"));
        text.put("axiom.editorui.mainmenu.file", names.get("file"));
        text.put("axiom.editorui.mainmenu.file.import_schematic", names.get("import"));
        Language.inject(new Language() {
            @Override public String getOrDefault(String key, String fallback) { return text.getOrDefault(key, fallback); }
            @Override public boolean has(String key) { return text.containsKey(key); }
            @Override public boolean isDefaultRightToLeft() { return false; }
            @Override public FormattedCharSequence getVisualOrder(FormattedText t) {
                return sink -> t.visit((style, s) -> StringDecomposer.iterateFormatted(s, style, sink) ? Optional.empty() : FormattedText.STOP_ITERATION, Style.EMPTY).isPresent();
            }
        });
    }

    private static int width(Component text) { return (int) Math.ceil(splitter.stringWidth(text)); }

    private static int button(Component label) { return width(label) + 10; }

    private static Component tr(String key, Object... args) { return Component.translatable(key, args); }

    private static int inner(int wanted) {
        Bounds content = new Window().content(427, 240);
        return Math.min(wanted, content.w() - 8) - 10;
    }

    private static void fits(String what, Component text, int width, int lines) {
        PaletteDialogs.Fitted f = PaletteDialogs.fit(splitter, text, width, lines);
        if (f.cut()) failures.add(language + ": " + what + " needs more than " + lines + " lines at " + width + ": " + text.getString());
    }

    private static void within(String what, int used, int room) {
        if (used > room) failures.add(language + ": " + what + " is " + used + " wide, with room for " + room);
    }

    private static List<PaletteEntry> entries(String... ids) {
        List<PaletteEntry> out = new ArrayList<>();
        for (String id : ids) out.add(new PaletteEntry("minecraft:" + id, ""));
        return out;
    }

    @Test
    void theAxiomDialogsTextsFitTheSmallestWindowInEveryLanguage() throws Exception {
        failures.clear();
        for (String lang : LANGUAGES) for (boolean axiomsOwn : List.of(false, true)) {
            use(lang, axiomsOwn);
            scriptDialog();
            hotbarDialog();
            within("the export dialog's Axiom hint", width(AxiomBridge.named("palettes.chromastudio.export.axiom")), inner(290));
            fits("the export dialog's Axiom hint", AxiomBridge.named("palettes.chromastudio.export.axiom"), inner(290), 1);
            gradientActions();
        }
        assertTrue(failures.isEmpty(), String.join("\n", failures));
    }

    private static void scriptDialog() throws Exception {
        int w = inner(PaletteDialogs.Axiom.WIDTH);
        Palette scatter = new Palette("Mixed");
        scatter.entries.addAll(entries("stone", "oak_stairs", "lantern"));
        Palette recolour = new Palette("Greys");
        recolour.entries.addAll(entries("stone", "andesite"));
        List<AxiomBridge.Script> made = List.of(
                AxiomBridge.gradientScript("Tower", entries("terracotta", "copper_chain", "oak_planks", "birch_planks"), AxiomScript.Axis.Y, BlockPos.ZERO),
                AxiomBridge.scatterScript(scatter),
                AxiomBridge.recolourScript(recolour, null, Recolour.Mode.SHADING));
        for (AxiomBridge.Script s : made) {
            PaletteDialogs.Axiom.Lines lines = PaletteDialogs.Axiom.lines(splitter, w, s == made.getFirst() ? 1 : 3, s, null);
            if (lines.scope().cut() || lines.size().cut() || lines.warning().cut() || lines.next().cut() || lines.details().cut()) {
                failures.add(language + ": the script dialog cuts " + s.scope().getString() + " / " + s.size().getString() + " / " + (s.warning() == null ? "" : s.warning().getString()));
            }
        }
        fits("the script's size", tr("axiom.chromastudio.size", 99999), w, 1);
        fits("a recolour's scope", tr("axiom.chromastudio.recolour.done", 999).copy().append(" ").append(tr("axiom.chromastudio.recolour.stay", 999)), w, PaletteDialogs.Axiom.SCOPE_LINES);
        fits("a scatter's scope", tr("axiom.chromastudio.scatter.done", 999).copy().append(" ").append(tr("axiom.chromastudio.script.left_out", 999)), w, PaletteDialogs.Axiom.SCOPE_LINES);
        fits("a gradient's scope", tr("axiom.chromastudio.gradient.done", 999, "Y").copy().append(" ").append(tr("axiom.chromastudio.script.left_out", 999)), w, PaletteDialogs.Axiom.SCOPE_LINES);
        fits("the server warning", tr("axiom.chromastudio.script.over", 1000), w, PaletteDialogs.Axiom.WARNING_LINES);
        for (String key : List.of("axiom.chromastudio.long", "axiom.chromastudio.long.from", "axiom.chromastudio.long.brush"))
            fits("the next step", tr(key), w, PaletteDialogs.Axiom.NEXT_LINES);
        within("the script dialog's buttons", button(tr("axiom.chromastudio.script.summary")) + PanelTab.GAP + button(tr("palettes.chromastudio.copy")), w);
        List<Component> problems = List.of(tr("axiom.chromastudio.nothing_to_change").copy().append(" ").append(tr("axiom.chromastudio.recolour.kept", 999)),
                tr("palettes.chromastudio.need_entries"), tr("axiom.chromastudio.no_blocks"), tr("axiom.chromastudio.gradient.too_short"),
                tr("axiom.chromastudio.gradient_tool.missing"), tr("axiom.chromastudio.too_long", "99.9", "40.0"), tr("axiom.chromastudio.too_long.from", "99.9", "40.0"),
                tr("palettes.chromastudio.index_building"), tr("axiom.chromastudio.scatter.none"));
        for (Component p : problems) fits("a refusal", p, w, PaletteDialogs.Axiom.BODY_LINES);
        fits("the receipt", AxiomBridge.named("axiom.chromastudio.copied"), w, Dialog.STATUS_LINES);
        int labelW = 6;
        for (String key : List.of("axiom.chromastudio.script", "axiom.chromastudio.from", "axiom.chromastudio.match")) labelW = Math.max(labelW, width(tr(key)) + 6);
        for (String key : List.of("axiom.chromastudio.script.recolour", "axiom.chromastudio.script.scatter", "axiom.chromastudio.from.any",
                "axiom.chromastudio.match.shading", "axiom.chromastudio.match.closest")) within(key, labelW + button(tr(key)), w);
        within("Along", width(tr("axiom.chromastudio.axis")) + 6 + 40, w);
    }

    private static void hotbarDialog() {
        int w = inner(PaletteDialogs.Hotbar.WIDTH);
        int control = (w - PanelTab.GAP) / 2;
        within("the overflow heading", width(tr("palettes.chromastudio.hotbar.overflow")), w);
        for (String state : List.of("ON", "OFF")) within("the Axiom hotbars toggle", button(Component.literal(tr("palettes.chromastudio.hotbar.axiom").getString() + ": " + state)), control);
        for (OverflowMode m : OverflowMode.values()) {
            String value = tr("option.chromastudio.overflowmode." + m.name().toLowerCase(Locale.ROOT)).getString();
            within("the shulker box cycler", button(Component.literal(tr("palettes.chromastudio.hotbar.boxes").getString() + ": " + value)), control);
            for (int free : List.of(0, 5, 27)) {
                HotbarPlan plan = HotbarPlan.of(m, 90, free);
                for (Component row : PaletteDialogs.Hotbar.destinations(99, Optional.empty(), plan)) fits("a destination", row, w, 1);
                Component warning = PaletteDialogs.Hotbar.warning(Optional.empty(), PaletteDialogs.Hotbar.dropped(99, plan));
                if (warning != null) fits("the drop warning", warning, w, PaletteDialogs.Hotbar.WARNING_LINES);
            }
        }
        Component hotbars = tr("hotbar.chromastudio.axiom.hotbars", 8, 9);
        Optional<HotbarFill.AxiomPreview> preview = Optional.of(new HotbarFill.AxiomPreview(hotbars, 99));
        for (Component row : PaletteDialogs.Hotbar.destinations(99, preview, HotbarPlan.of(OverflowMode.NEVER, 90, 0))) fits("a destination", row, w, 1);
        fits("the replacement warning", PaletteDialogs.Hotbar.warning(preview, 0), w, PaletteDialogs.Hotbar.WARNING_LINES);
        for (Component receipt : List.of(tr("hotbar.chromastudio.filled.axiom", hotbars, "Left Alt"), tr("hotbar.chromastudio.undone_axiom"),
                tr("hotbar.chromastudio.filled", 9).copy().append(" ").append(tr("hotbar.chromastudio.rest.boxes", 99, 3)), tr("hotbar.chromastudio.undone_dropped")))
            fits("a fill's receipt", receipt, w, PaletteDialogs.Hotbar.RESULT_LINES);
        within("Fill and Undo", button(tr("palettes.chromastudio.hotbar.fill")) + PanelTab.GAP + button(tr("palettes.chromastudio.hotbar.undo")), w);
    }

    private static void gradientActions() {
        Bounds content = new Window().content(427, 240);
        String[] actions = {"hotbar", "copy", "export", "axiom", "saved", "save", "swap"};
        int[] named = new int[actions.length], plain = new int[actions.length];
        for (int i = 0; i < actions.length; i++) {
            named[i] = button(tr("gradient.chromastudio.action." + actions[i]));
            plain[i] = actions[i].equals("axiom") ? button(Component.literal("Axiom")) : named[i];
        }
        int left = content.x() + PanelTab.PAD, right = content.right() - PanelTab.PAD;
        int rows = Flow.rows(Flow.place(named, left, right, PanelTab.GAP)), plainRows = Flow.rows(Flow.place(plain, left, right, PanelTab.GAP));
        if (rows > plainRows) failures.add(language + ": \"Axiom script\" puts the Gradient tab's actions on " + rows + " rows instead of " + plainRows);
    }
}
