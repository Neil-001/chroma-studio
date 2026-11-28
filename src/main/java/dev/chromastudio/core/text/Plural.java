package dev.chromastudio.core.text;

public final class Plural {
    private Plural() {}

    public static String key(String key, int n) { return n == 1 ? key + ".one" : key; }
}
