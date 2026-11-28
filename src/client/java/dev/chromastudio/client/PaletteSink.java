package dev.chromastudio.client;

import dev.chromastudio.core.palette.PaletteEntry;
import java.util.List;

public interface PaletteSink {
    void add(String blockId, String properties);

    default boolean createPalette(String name, List<PaletteEntry> entries) { return false; }

    static PaletteSink current() { return Holder.current; }

    static void setCurrent(PaletteSink sink) { Holder.current = sink; }

    final class Holder {
        private Holder() {}
        private static PaletteSink current;
    }
}
