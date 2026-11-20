package dev.chromastudio.core.export;

import java.nio.charset.StandardCharsets;

/**
 * How much text can go to the clipboard. The game hands the clipboard its text as UTF-8 through LWJGL's stack of
 * 64 KiB, and text longer than what's left of that stack crashes the game. {@link #MAX_BYTES} leaves room for
 * whatever else is on the stack at the time.
 */
public final class ClipboardSize {
    private ClipboardSize() {}

    /** The most bytes of UTF-8 that are copied. */
    public static final int MAX_BYTES = 40_000;

    /** The text's length in bytes of UTF-8, as the clipboard gets it. */
    public static int bytes(String text) { return text.getBytes(StandardCharsets.UTF_8).length; }

    /** Whether the text is short enough to copy safely. */
    public static boolean fits(String text) { return bytes(text) <= MAX_BYTES; }

    /** A byte count in whole kilobytes, rounded up, so text just past the limit never reads as within it. */
    public static int kilobytes(int bytes) { return (bytes + 1023) / 1024; }
}
