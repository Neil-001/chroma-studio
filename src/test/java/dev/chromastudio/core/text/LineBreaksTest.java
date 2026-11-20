package dev.chromastudio.core.text;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

class LineBreaksTest {
    /** Every character one unit wide, so a width is a count of characters. */
    private static List<String> split(String text, int width) { return LineBreaks.split(text, width, String::length); }

    @Test
    void aNumberStaysWithItsCounter() {
        // The gradient tool's note in Japanese, which ImGui broke as "…ため、2" and "個の…".
        String text = "フルブロックでないか、このゲームにないため、2 個のブロックを除いています。";
        for (int width = 8; width <= 30; width++) {
            for (String line : split(text, width)) {
                assertFalse(line.endsWith("2"), width + ": " + line);
                assertFalse(line.startsWith("個"), width + ": " + line);
            }
        }
        assertEquals(List.of("フルブロックでないか、このゲームにないため、", "2 個のブロックを除いています。"), split(text, 22));
    }

    @Test
    void closingPunctuationNeverStartsALine() {
        // The eyedropper's note in Japanese, which ImGui left a lone "。" of.
        String text = "ブロックを右クリックすると、その色が「マッチ」タブの目標色になります。";
        List<String> lines = split(text, 33);
        assertEquals(List.of("ブロックを右クリックすると、その色が「マッチ」タブの目標色になりま", "す。"), lines);
        for (int width = 4; width <= 36; width++) {
            for (String line : split(text, width)) {
                assertFalse(line.startsWith("。") || line.startsWith("、") || line.startsWith("」"), width + ": " + line);
                assertFalse(line.endsWith("「"), width + ": " + line);
            }
        }
    }

    @Test
    void everyLineFitsAndNothingIsLost() {
        String text = "Axiom のツールマスクで除外されるため、12 個のブロックはそのままです。Chroma Studio を開く";
        for (int width = 6; width <= 40; width++) {
            List<String> lines = split(text, width);
            // A line wider than asked is only one that has nowhere to break.
            for (String line : lines) assertTrue(line.length() <= width || unbreakable(line), width + ": " + line);
            assertEquals(text.replace(" ", ""), String.join("", lines).replace(" ", ""), "width " + width);
        }
    }

    private static boolean unbreakable(String line) {
        for (int i = 1; i < line.length(); i++) if (LineBreaks.breaksBefore(line, i)) return false;
        return true;
    }

    @Test
    void latinWordsBreakAtSpacesAndStayWhole() {
        assertEquals(List.of("Chroma", "Studio を開く"), split("Chroma Studio を開く", 11));
        // A word wider than the line keeps a line of its own rather than being cut.
        assertEquals(List.of("Chroma", "Studio"), split("Chroma Studio", 3));
    }

    @Test
    void onlyChineseAndJapaneseNeedIt() {
        assertTrue(LineBreaks.needed("右键点击一个方块"));
        assertTrue(LineBreaks.needed("ブロック"));
        assertFalse(LineBreaks.needed("블록 3개가 바뀝니다"));
        assertFalse(LineBreaks.needed("Blocks will change"));
    }

    @Test
    void lineFeedsAlwaysBreak() {
        assertEquals(List.of("一行目", "二行目"), split("一行目\n二行目", 20));
    }
}
