package dev.chromastudio.client.axiom;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

final class AxiomNames {
    private AxiomNames() {}

    private enum Name {
        TOOL_MASKS("axiom.editorui.window.tool_masks", "Tool Masks"),
        SCRIPT_BRUSH("axiom.tool.script_brush", "Script Brush"),
        FILE_MENU("axiom.editorui.mainmenu.file", "File"),
        IMPORT_SCHEMATIC("axiom.editorui.mainmenu.file.import_schematic", "Import Schematic");

        private final String key;
        private final String english;

        Name(String key, String english) {
            this.key = key;
            this.english = english;
        }

        private Component text() {
            String shown = Language.getInstance().getOrDefault(key, english);
            return Component.literal(ELLIPSIS.matcher(shown).replaceFirst(""));
        }
    }

    private static final Pattern ELLIPSIS = Pattern.compile("\\s*(\\.\\.\\.|…)$");

    private static final Map<String, List<Name>> TEXTS = Map.of(
            "axiom.chromastudio.copied", List.of(Name.SCRIPT_BRUSH),
            "axiom.chromastudio.gradient.rules", List.of(Name.SCRIPT_BRUSH),
            "palettes.chromastudio.export.axiom", List.of(Name.FILE_MENU, Name.IMPORT_SCHEMATIC),
            "axiom.chromastudio.masks.tip", List.of(Name.TOOL_MASKS),
            "axiom.chromastudio.masks.off.tip", List.of(Name.TOOL_MASKS),
            "axiom.chromastudio.mask.lua", List.of(Name.TOOL_MASKS));

    static Set<String> keys() { return TEXTS.keySet(); }

    static MutableComponent text(String key) {
        List<Name> names = TEXTS.get(key);
        if (names == null) throw new IllegalArgumentException(key + " names nothing of Axiom's");
        return Component.translatable(key, names.stream().map(Name::text).toArray());
    }
}
