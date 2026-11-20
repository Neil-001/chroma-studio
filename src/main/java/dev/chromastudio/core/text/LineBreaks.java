package dev.chromastudio.core.text;

import java.util.ArrayList;
import java.util.List;
import java.util.function.ToDoubleFunction;

/**
 * Splits Chinese and Japanese text into lines that fit a width. These languages write without spaces between words, so
 * a line may end between any two of their characters; but a line never starts with closing punctuation such as 。 or
 * 」, or a small kana, and never ends with opening punctuation such as 「. The translations put spaces around numbers
 * and Latin words, as in "2 個のブロック", only for looks: a line never breaks at one of those, or a number would end
 * one line and its counter start the next. A space between two Latin words still breaks, as in English.
 *
 * <p>Text with none of these characters, Korean included, which spaces its words, is left to the caller's own wrapping.
 */
public final class LineBreaks {
    private LineBreaks() {}

    /** Characters a line may not start with: closing punctuation, small kana, the long vowel mark and the like. */
    private static final String NO_START = "、。，．・：；？！ー」』）】〕〉》〗〙〟’”｝］…‥々〻ぁぃぅぇぉっゃゅょゎゕゖァィゥェォッャュョヮヵヶ゛゜ゝゞヽヾ!%),.:;?]}";
    /** Characters a line may not end with: opening punctuation. */
    private static final String NO_END = "「『（【〔〈《〖〘〝‘“｛［([{";

    /** Whether the text holds Chinese or Japanese, which {@link #split} knows how to break. */
    public static boolean needed(String text) {
        return text.codePoints().anyMatch(LineBreaks::closeSet);
    }

    /**
     * The lines {@code text} breaks into so that each, measured by {@code width}, fits in {@code max}, or holds a
     * single unbreakable run that doesn't fit anywhere. Line feeds in the text always break. Spaces where a line breaks
     * are dropped.
     */
    public static List<String> split(String text, double max, ToDoubleFunction<String> width) {
        List<String> out = new ArrayList<>();
        for (String paragraph : text.split("\n", -1)) lines(paragraph, max, width, out);
        return out;
    }

    private static void lines(String text, double max, ToDoubleFunction<String> width, List<String> out) {
        int start = 0;
        while (start < text.length()) {
            // The furthest break that still fits, else the first break of all, so an over-long word gets a line of its own.
            int end = start, first = -1;
            for (int i = start + 1; i <= text.length(); i++) {
                if (i < text.length() && !breaksBefore(text, i)) continue;
                if (first < 0) first = i;
                if (width.applyAsDouble(text.substring(start, i).stripTrailing()) > max) break;
                end = i;
            }
            if (end == start) end = first;
            out.add(text.substring(start, end).stripTrailing());
            start = end;
            while (start < text.length() && text.charAt(start) == ' ') start++;
        }
        if (text.isEmpty()) out.add("");
    }

    /** Whether a line may break before the character at {@code i}. */
    static boolean breaksBefore(String text, int i) {
        // Never inside a character that takes two code units, as the rarer Chinese characters do.
        if (Character.isLowSurrogate(text.charAt(i)) && Character.isHighSurrogate(text.charAt(i - 1))) return false;
        int before = text.codePointBefore(i), at = text.codePointAt(i);
        if (before == ' ') {
            // A space breaks only between two words of a language that spaces them.
            if (at == ' ') return false;
            int j = i - 1;
            while (j > 0 && text.charAt(j - 1) == ' ') j--;
            return j > 0 && !closeSet(text.codePointBefore(j)) && !closeSet(at);
        }
        if (at == ' ') return false;
        if (!closeSet(before) && !closeSet(at)) return false;
        return NO_START.indexOf(at) < 0 && NO_END.indexOf(before) < 0;
    }

    /** Whether a character is Chinese or Japanese, written without spaces between words: Han, kana, or their punctuation. */
    private static boolean closeSet(int c) {
        if (Character.UnicodeScript.of(c) == Character.UnicodeScript.HAN) return true;
        Character.UnicodeBlock b = Character.UnicodeBlock.of(c);
        return b == Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS || b == Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS_EXTENSION_A
                || b == Character.UnicodeBlock.CJK_COMPATIBILITY_IDEOGRAPHS || b == Character.UnicodeBlock.HIRAGANA
                || b == Character.UnicodeBlock.KATAKANA || b == Character.UnicodeBlock.KATAKANA_PHONETIC_EXTENSIONS
                || b == Character.UnicodeBlock.CJK_SYMBOLS_AND_PUNCTUATION || b == Character.UnicodeBlock.HALFWIDTH_AND_FULLWIDTH_FORMS;
    }
}
