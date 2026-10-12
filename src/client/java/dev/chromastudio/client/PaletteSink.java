package dev.chromastudio.client;

/** Where a block goes when the player adds it to the current palette. */
public interface PaletteSink {
    /** {@code blockId} is a full id such as {@code minecraft:oak_log}; {@code properties} is {@code ""} or like {@code axis=y,lit=true}. */
    void add(String blockId, String properties);

    /** The palette the Palettes tab has selected, or null until that tab sets one. Callers grey their button out while it's null. */
    static PaletteSink current() { return Holder.current; }

    static void setCurrent(PaletteSink sink) { Holder.current = sink; }

    /** An interface's own fields are constants, so the one that changes lives here. */
    final class Holder {
        private Holder() {}
        private static PaletteSink current;
    }
}
