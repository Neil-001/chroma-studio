package dev.chromastudio.core.export;

import java.nio.charset.StandardCharsets;

public final class ClipboardSize {
    private ClipboardSize() {}

    public static final int MAX_BYTES = 40_000;

    public static int bytes(String text) { return text.getBytes(StandardCharsets.UTF_8).length; }

    public static boolean fits(String text) { return bytes(text) <= MAX_BYTES; }

    public static int kilobytes(int bytes) { return (bytes + 1023) / 1024; }
}
