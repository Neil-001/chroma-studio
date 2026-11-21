package dev.chromastudio.core.text;

import java.util.ArrayList;
import java.util.List;
import java.util.function.ToDoubleFunction;

public final class LineBreaks {
    private LineBreaks() {}

    private static final String NO_START = "、。，．・：；？！ー」』）】〕〉》〗〙〟’”｝］…‥々〻ぁぃぅぇぉっゃゅょゎゕゖァィゥェォッャュョヮヵヶ゛゜ゝゞヽヾ!%),.:;?]}";
    private static final String NO_END = "「『（【〔〈《〖〘〝‘“｛［([{";

    public static boolean needed(String text) {
        return text.codePoints().anyMatch(LineBreaks::closeSet);
    }

    public static List<String> split(String text, double max, ToDoubleFunction<String> width) {
        List<String> out = new ArrayList<>();
        for (String paragraph : text.split("\n", -1)) lines(paragraph, max, width, out);
        return out;
    }

    private static void lines(String text, double max, ToDoubleFunction<String> width, List<String> out) {
        int start = 0;
        while (start < text.length()) {
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

    static boolean breaksBefore(String text, int i) {
        if (Character.isLowSurrogate(text.charAt(i)) && Character.isHighSurrogate(text.charAt(i - 1))) return false;
        int before = text.codePointBefore(i), at = text.codePointAt(i);
        if (before == ' ') {
            if (at == ' ') return false;
            int j = i - 1;
            while (j > 0 && text.charAt(j - 1) == ' ') j--;
            return j > 0 && !closeSet(text.codePointBefore(j)) && !closeSet(at);
        }
        if (at == ' ') return false;
        if (!closeSet(before) && !closeSet(at)) return false;
        return NO_START.indexOf(at) < 0 && NO_END.indexOf(before) < 0;
    }

    private static boolean closeSet(int c) {
        if (Character.UnicodeScript.of(c) == Character.UnicodeScript.HAN) return true;
        Character.UnicodeBlock b = Character.UnicodeBlock.of(c);
        return b == Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS || b == Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS_EXTENSION_A
                || b == Character.UnicodeBlock.CJK_COMPATIBILITY_IDEOGRAPHS || b == Character.UnicodeBlock.HIRAGANA
                || b == Character.UnicodeBlock.KATAKANA || b == Character.UnicodeBlock.KATAKANA_PHONETIC_EXTENSIONS
                || b == Character.UnicodeBlock.CJK_SYMBOLS_AND_PUNCTUATION || b == Character.UnicodeBlock.HALFWIDTH_AND_FULLWIDTH_FORMS;
    }
}
